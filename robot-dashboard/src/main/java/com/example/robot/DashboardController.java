package com.example.robot;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * UI principal del dashboard (Clarence).
 *
 * Layout:
 *   ┌─────────────────────┬──────────────────┐
 *   │  MapCanvas (5×7)    │  Pedido actual   │
 *   │                     │  Gestion+Cola    │
 *   │                     │  Historial       │
 *   │                     │  Status semaforo │
 *   └─────────────────────┴──────────────────┘
 *   │  Barra de estado MQTT                   │
 *
 * Protocolo MQTT (Equipo E):
 *   - SUB map                       -> dibujar mapa, inicializar tracker
 *   - SUB Equipo E/odometry         -> recalcular posicion del robot
 *   - SUB Equipo E/status           -> semaforo PEDIDO_RECIBIDO/RECOGIDO/LISTO
 *   - PUB Equipo E/orders           -> {id, pickup:[r,c], delivery:[r,c]}
 */
public class DashboardController {

    private static final int MAP_COLS = 5;
    private static final int MAP_ROWS = 7;
    private static final int START_ROW = 6;
    private static final int START_COL = 0;

    private MapCanvas mapCanvas;
    private Label     lblCurrentOrder;
    private Label     lblPickup;
    private Label     lblDelivery;
    private Label     lblProgress;
    private ListView<String> listQueue;
    private ListView<String> listHistory;
    private Label     lblStatus;
    private Label     lblMqttStatus;
    private Label     lblMapRaw;

    // Semaforo de status del robot
    private Label dotPedidoRecibido;
    private Label dotRecogido;
    private Label dotListo;

    private CityMap currentMap;
    private RobotTracker tracker;

    private final ObservableList<String> queueItems   = FXCollections.observableArrayList();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();

    // Pedidos pendientes en orden FIFO (en paralelo a queueItems para tener el id).
    private final List<String> queuedIds = new ArrayList<>();
    private final java.util.Map<String, String> orderLabels = new java.util.LinkedHashMap<>();
    private String activeOrderId = null;

    // Click-to-select state
    private enum ClickMode { PICKUP, DELIVERY, NONE }
    private ClickMode clickMode = ClickMode.NONE;

    private Label lblPickupSelection;
    private Label lblDeliverySelection;
    private Label lblFormStatus;
    private final int[] orderCounter = {1};

    private int selPickupRow = -1, selPickupCol = -1;
    private int selDeliveryRow = -1, selDeliveryCol = -1;

    private final MqttService mqtt = new MqttService();

    // ──────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────

    public BorderPane buildUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f7f7f5;");

        // ── LEFT: mapa + leyenda + barra MQTT conectado ──
        mapCanvas = new MapCanvas(420, 588);
        mapCanvas.setOnMouseClicked(e -> handleMapClick(e.getX(), e.getY()));
        lblMqttStatus = new Label("Desconectado");
        lblMqttStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
        VBox mqttBarLeft = new VBox(2, lblMqttStatus);
        mqttBarLeft.setPadding(new Insets(4, 0, 0, 0));

        VBox mapBox = new VBox(8, sectionLabel("Mapa de ciudad"), buildLegend(), mapCanvas, mqttBarLeft);
        mapBox.setPadding(new Insets(8));
        mapBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        root.setLeft(mapBox);

        // ── CENTRE: Pedido actual + Gestión de pedidos (sin cola) + Estado del robot ──
        VBox centre = new VBox(10,
                buildCurrentOrderPanel(),
                buildQueuePanelNoQueue(),
                buildStatusSemaphorePanel());
        centre.setPrefWidth(300);
        BorderPane.setMargin(centre, new Insets(0, 10, 0, 10));
        root.setCenter(centre);

        // ── RIGHT: Cola + Historial + MQTT info ──
        VBox rightCol = new VBox(10,
                buildColaPanel(),
                buildHistoryPanel(),
                buildMqttInfoPanel());
        rightCol.setPrefWidth(300);
        root.setRight(rightCol);

        lblStatus = new Label("Iniciando...");
        lblMapRaw = new Label("");
        lblMapRaw.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");
        VBox statusBar = new VBox(2, lblStatus, lblMapRaw);
        statusBar.setPadding(new Insets(8, 0, 0, 0));
        root.setBottom(statusBar);

        return root;
    }

    private VBox buildCurrentOrderPanel() {
        lblCurrentOrder = new Label("Sin pedido activo");
        lblCurrentOrder.setFont(Font.font("Arial", 13));
        lblCurrentOrder.setStyle("-fx-font-weight: bold;");

        lblPickup   = new Label("Recogida: --");
        lblDelivery = new Label("Entrega:  --");
        lblPickup.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
        lblDelivery.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");

        lblProgress = new Label("0 instrucciones completadas");
        lblProgress.setStyle("-fx-font-size: 11; -fx-text-fill: #777;");

        VBox inner = new VBox(4, lblCurrentOrder, lblPickup, lblDelivery, lblProgress);
        inner.setPadding(new Insets(8));
        inner.setStyle("-fx-background-color: #f0f4f8; -fx-background-radius: 6;");

        return wrapPanel(new VBox(6, sectionLabel("Pedido actual"), inner));
    }

    /** Centro: formulario de gestión de pedidos SIN la lista de cola. */
    private VBox buildQueuePanelNoQueue() {
        // Punto de origen
        Label lblPickupTag = new Label("Punto de origen");
        lblPickupTag.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        lblPickupSelection = new Label("— toca un punto en el mapa");
        lblPickupSelection.setStyle("-fx-font-size: 12; -fx-text-fill: #999;");

        Button btnSelectPickup = new Button("Seleccionar recogida");
        btnSelectPickup.setMaxWidth(Double.MAX_VALUE);
        styleButton(btnSelectPickup, false);
        btnSelectPickup.setOnAction(e -> {
            clickMode = ClickMode.PICKUP;
            lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
            lblFormStatus.setText("Haz clic en el mapa para elegir el punto de recogida.");
        });

        // Punto de destino
        Label lblDeliveryTag = new Label("Punto de destino");
        lblDeliveryTag.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        lblDeliveryTag.setPadding(new Insets(4, 0, 0, 0));
        lblDeliverySelection = new Label("— toca un punto en el mapa");
        lblDeliverySelection.setStyle("-fx-font-size: 12; -fx-text-fill: #999;");

        Button btnSelectDelivery = new Button("Seleccionar entrega");
        btnSelectDelivery.setMaxWidth(Double.MAX_VALUE);
        styleButton(btnSelectDelivery, false);
        btnSelectDelivery.setOnAction(e -> {
            clickMode = ClickMode.DELIVERY;
            lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
            lblFormStatus.setText("Haz clic en el mapa para elegir el punto de entrega.");
        });

        Button btnAdd = new Button("Añadir pedido");
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        styleButton(btnAdd, true);

        lblFormStatus = new Label("");
        lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");

        btnAdd.setOnAction(e -> {
            if (selPickupRow < 0 || selDeliveryRow < 0) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Selecciona recogida y entrega en el mapa.");
                return;
            }
            if (selPickupRow == selDeliveryRow && selPickupCol == selDeliveryCol) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Recogida y entrega no pueden ser iguales.");
                return;
            }

            String orderId = "ORD-" + String.format("%03d", orderCounter[0]++);
            String json = String.format(
                    "{\"id\":\"%s\",\"pickup\":[%d,%d],\"delivery\":[%d,%d]}",
                    orderId, selPickupRow, selPickupCol, selDeliveryRow, selDeliveryCol);

            try {
                mqtt.publish(MqttService.TOPIC_ORDERS, json);
                queueItems.add(orderId + "  (" + selPickupRow + "," + selPickupCol
                        + ") -> (" + selDeliveryRow + "," + selDeliveryCol + ")");
                System.out.println("Queue size: " + queueItems.size());
                queuedIds.add(orderId);
                orderLabels.put(orderId, "(" + selPickupRow + "," + selPickupCol + ") → (" + selDeliveryRow + "," + selDeliveryCol + ")");
                // Reset selections
                selPickupRow = selPickupCol = selDeliveryRow = selDeliveryCol = -1;
                mapCanvas.clearSelections();
                lblPickupSelection.setText("— toca un punto en el mapa");
                lblPickupSelection.setStyle("-fx-font-size: 12; -fx-text-fill: #999;");
                lblDeliverySelection.setText("— toca un punto en el mapa");
                lblDeliverySelection.setStyle("-fx-font-size: 12; -fx-text-fill: #999;");
                clickMode = ClickMode.NONE;
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
                lblFormStatus.setText("Pedido " + orderId + " enviado");
            } catch (Exception ex) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Error al publicar: " + ex.getMessage());
            }
        });

        VBox form = new VBox(5,
                lblPickupTag, lblPickupSelection, btnSelectPickup,
                lblDeliveryTag, lblDeliverySelection, btnSelectDelivery,
                btnAdd, lblFormStatus);

        return wrapPanel(new VBox(8, sectionLabel("Gestion de pedidos"), form));
    }

    /** Derecha: cola de pedidos (lista grande y scrollable). */
    private VBox buildColaPanel() {
        listQueue = new ListView<>(queueItems);
        listQueue.setPrefHeight(220);
        listQueue.setStyle("-fx-font-size: 13;");
        VBox.setVgrow(listQueue, Priority.ALWAYS);
        return wrapPanel(new VBox(6, sectionLabel("Cola"), listQueue));
    }

    /** Called when the user clicks anywhere on the MapCanvas. */
    private void handleMapClick(double pixelX, double pixelY) {
        if (currentMap == null || clickMode == ClickMode.NONE) return;

        // Mirror the MARGIN + cellSize arithmetic from MapCanvas.redraw()
        final double MARGIN = 22;
        double gridW = mapCanvas.getWidth()  - MARGIN;
        double gridH = mapCanvas.getHeight() - MARGIN;
        double cellW = gridW / currentMap.getCols();
        double cellH = gridH / currentMap.getRows();

        int col = (int) ((pixelX - MARGIN) / cellW);
        int row = (int) ((pixelY - MARGIN) / cellH);

        if (row < 0 || row >= currentMap.getRows() || col < 0 || col >= currentMap.getCols()) return;

        boolean isValid = false;
        for (int[] p : currentMap.getPickupPoints()) {
            if (p[0] == row && p[1] == col) { isValid = true; break; }
        }
        if (!isValid) {
            lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
            lblFormStatus.setText("Ese tile no es un punto de recogida/entrega.");
            return;
        }

        String coord = row + "," + col;
        if (clickMode == ClickMode.PICKUP) {
            selPickupRow = row; selPickupCol = col;
            mapCanvas.setSelectedPickup(row, col);
            lblPickupSelection.setText("✔ (" + coord + ")");
            lblPickupSelection.setStyle("-fx-font-size: 12; -fx-text-fill: #00aa33; -fx-font-weight: bold;");
            clickMode = ClickMode.DELIVERY;   // auto-advance to delivery
            lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
            lblFormStatus.setText("Recogida fijada. Ahora haz clic en el punto de entrega.");
        } else {
            selDeliveryRow = row; selDeliveryCol = col;
            mapCanvas.setSelectedDelivery(row, col);
            lblDeliverySelection.setText("✔ (" + coord + ")");
            lblDeliverySelection.setStyle("-fx-font-size: 12; -fx-text-fill: #1a5fa5; -fx-font-weight: bold;");
            clickMode = ClickMode.NONE;
            lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
            lblFormStatus.setText("Entrega fijada. Pulsa 'Añadir pedido' para confirmar.");
        }
    }

    private VBox buildHistoryPanel() {
        listHistory = new ListView<>(historyItems);
        listHistory.setPrefHeight(220);
        listHistory.setStyle("-fx-font-size: 13;");
        VBox.setVgrow(listHistory, Priority.ALWAYS);
        return wrapPanel(new VBox(6, sectionLabel("Historial"), listHistory));
    }

    private VBox buildStatusSemaphorePanel() {
        dotPedidoRecibido = statusDot("Pedido recibido");
        dotRecogido       = statusDot("Recogido");
        dotListo          = statusDot("Entregado");
        resetSemaphore();

        VBox dots = new VBox(4, dotPedidoRecibido, dotRecogido, dotListo);
        return wrapPanel(new VBox(6, sectionLabel("Estado del robot"), dots));
    }

    private VBox buildMqttInfoPanel() {
        Label info = new Label(MqttService.BROKER_URL + "  |  Equipo E");
        info.setStyle("-fx-font-size: 11; -fx-text-fill: #aaa;");
        // lblMqttStatus already created in buildUI
        return wrapPanel(new VBox(4, sectionLabel("MQTT"), lblMqttStatus, info));
    }

    // ──────────────────────────────────────────────
    //  MQTT startup
    // ──────────────────────────────────────────────

    public void startMqtt() {
        mqtt.setOnStatusMessage(msg -> Platform.runLater(() -> {
            lblMqttStatus.setText(msg);
            lblStatus.setText(msg);
        }));

        mqtt.setOnMapReceived(this::handleMapPayload);
        mqtt.setOnOdometryReceived(this::handleOdometryPayload);
        mqtt.setOnStatusReceived(this::handleStatusPayload);

        new Thread(() -> {
            try {
                mqtt.connect();
            } catch (Exception e) {
                Platform.runLater(() -> lblStatus.setText("MQTT error: " + e.getMessage()));
            }
        }, "mqtt-connect").start();
    }

    // ──────────────────────────────────────────────
    //  Handlers MQTT (llegan en hilo MQTT -> Platform.runLater)
    // ──────────────────────────────────────────────

    private void handleMapPayload(String payload) {
        System.out.println("Map received, length: " + payload.length());
        try {
            CityMap map = new CityMap(payload, MAP_COLS, MAP_ROWS);
            Platform.runLater(() -> {
                currentMap = map;
                mapCanvas.setMap(map);

                // Inicializar tracker con la orientacion deducida del bloque (6,0).
                Tile startTile = map.getTile(START_ROW, START_COL);
                RobotTracker.Heading h0 = RobotTracker.initialHeading(startTile);
                tracker = new RobotTracker(START_ROW, START_COL, h0);
                mapCanvas.setRobotPosition(START_ROW, START_COL);

                lblMapRaw.setText("Map: " + payload.substring(0, Math.min(40, payload.length())) + "...");
                lblStatus.setText("Mapa actualizado - " + map.getPickupPoints().size()
                        + " puntos. Robot mira " + h0 + " desde (" + START_ROW + "," + START_COL + ")");
            });
        } catch (Exception e) {
            System.out.println("PARSE ERROR: " + e.getMessage());
            Platform.runLater(() -> lblStatus.setText("Error al parsear mapa: " + e.getMessage()));
        }
    }

    /** Llega {"instructions":["MOVE","TURN_LEFT",...]} cada 1 s aprox. */
    private void handleOdometryPayload(String payload) {
        if (tracker == null) return;
        try {
            List<String> done = parseInstructionsArray(payload);
            Platform.runLater(() -> {
                tracker.applyCompleted(done);
                mapCanvas.setRobotPosition(tracker.getRow(), tracker.getCol());
                lblProgress.setText(done.size() + " instrucciones completadas ("
                        + tracker.getHeading() + ")");
            });
        } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Error odometria: " + e.getMessage()));
        }
    }

    /** Status: cadena "PEDIDO_RECIBIDO" | "RECOGIDO" | "LISTO" (con o sin comillas). */
    private void handleStatusPayload(String payload) {
        String raw = payload.trim();
        // Aceptar tanto "LISTO" (string JSON) como LISTO (texto plano) como
        // {"status":"LISTO"}
        String estado;
        if (raw.startsWith("{")) {
            int kIdx = raw.indexOf("\"status\"");
            if (kIdx < 0) return;
            int q1 = raw.indexOf('"', raw.indexOf(':', kIdx) + 1);
            int q2 = raw.indexOf('"', q1 + 1);
            estado = raw.substring(q1 + 1, q2);
        } else if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            estado = raw.substring(1, raw.length() - 1);
        } else {
            estado = raw;
        }
        final String fin = estado;
        Platform.runLater(() -> applyStatus(fin));
    }

    private void applyStatus(String estado) {
        switch (estado) {
            case "PEDIDO_RECIBIDO" -> {
                // Empieza el pedido que estaba en cabeza de la cola.
                if (!queuedIds.isEmpty()) {
                    activeOrderId = queuedIds.remove(0);
                    if (!queueItems.isEmpty()) queueItems.remove(0);
                    lblCurrentOrder.setText("#" + activeOrderId);
                }
                resetSemaphore();
                paintDotActive(dotPedidoRecibido, "#FFB300");
                lblStatus.setText("Pedido " + activeOrderId + " en marcha");
                String coords = orderLabels.getOrDefault(activeOrderId, "--");
                String[] parts = coords.split("→");
                lblPickup.setText("Recogida: " + (parts.length > 0 ? parts[0].trim() : "--"));
                lblDelivery.setText("Entrega:  " + (parts.length > 1 ? parts[1].trim() : "--"));
            }
            case "RECOGIDO" -> {
                paintDotActive(dotRecogido, "#1a5fa5");
                lblStatus.setText("Paquete recogido");
            }
            case "LISTO" -> {
                paintDotActive(dotListo, "#3B6D11");
                if (activeOrderId != null) {
                    historyItems.add(0, activeOrderId + "  ENTREGADO");
                    orderLabels.remove(activeOrderId);
                }
                if (tracker != null) tracker.commitSnapshot();
                lblStatus.setText("Pedido " + activeOrderId + " entregado");
                activeOrderId = null;
                lblCurrentOrder.setText("Sin pedido activo");
                lblPickup.setText("Recogida: --");
                lblDelivery.setText("Entrega:  --");
                lblProgress.setText("0 instrucciones completadas");
            }
            default -> { /* desconocido, ignorar */ }
        }
    }

    // ──────────────────────────────────────────────
    //  Parsing utils (sin librerias externas)
    // ──────────────────────────────────────────────

    /** Parsea {"instructions":["MOVE","TURN_LEFT",...]} y devuelve la lista. */
    private static List<String> parseInstructionsArray(String json) {
        List<String> out = new ArrayList<>();
        int kIdx = json.indexOf("\"instructions\"");
        if (kIdx < 0) return out;
        int bracket = json.indexOf('[', kIdx);
        int end = json.indexOf(']', bracket);
        if (bracket < 0 || end < 0) return out;
        String body = json.substring(bracket + 1, end).trim();
        if (body.isEmpty()) return out;
        for (String item : body.split(",")) {
            String s = item.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    // ──────────────────────────────────────────────
    //  UI helpers
    // ──────────────────────────────────────────────

    private static Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-font-size: 10; -fx-text-fill: #999; -fx-letter-spacing: 0.06em;");
        return l;
    }

    private static void styleButton(Button btn, boolean isPrimary) {
        String base = isPrimary
                ? "-fx-background-color: #2a2a2a; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: #ebebeb; -fx-text-fill: #333; -fx-font-weight: normal;";
        String shared = " -fx-font-size: 12; -fx-background-radius: 6; -fx-border-width: 0; -fx-cursor: hand; -fx-padding: 6 10 6 10;";
        btn.setStyle(base + shared);
        String hoverColor = isPrimary ? "#444444" : "#d6d6d6";
        btn.setOnMouseEntered(e -> btn.setStyle(base + shared + " -fx-background-color: " + hoverColor + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + shared));
    }

    private static VBox wrapPanel(VBox inner) {
        inner.setPadding(new Insets(10));
        inner.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        return inner;
    }

    private static HBox buildLegend() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 0, 0));
        box.getChildren().addAll(
                legendDot("#e24b4a", "Edificio"),
                legendDot("#0e6e56", "Calle (verde)"),
                legendDot("#1a5fa5", "Calle (azul)"),
                legendDot("#FF6B00", "Robot")
        );
        return box;
    }

    private static HBox legendDot(String color, String text) {
        Label dot = new Label("  ");
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3; -fx-border-color: #ccc; -fx-border-radius:3;");
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        HBox h = new HBox(4, dot, lbl);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Label statusDot(String text) {
        Label l = new Label("  " + text);
        l.setStyle("-fx-font-size: 12;");
        return l;
    }

    private void resetSemaphore() {
        paintDotInactive(dotPedidoRecibido);
        paintDotInactive(dotRecogido);
        paintDotInactive(dotListo);
    }

    private void paintDotInactive(Label l) {
        l.setStyle("-fx-font-size: 12; -fx-text-fill: #aaa; -fx-background-color: #f0f0f0; -fx-background-radius: 4; -fx-padding: 4 6 4 6;");
    }

    private void paintDotActive(Label l, String colorHex) {
        l.setStyle("-fx-font-size: 12; -fx-text-fill: white; -fx-background-color: " + colorHex
                + "; -fx-background-radius: 4; -fx-padding: 4 6 4 6; -fx-font-weight: bold;");
    }
    public void stopMqtt() {
        mqtt.disconnect();
    }
}