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
    private ComboBox<String> cbPickup;
    private ComboBox<String> cbDelivery;

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
    private String activeOrderId = null;

    private final MqttService mqtt = new MqttService();

    // ──────────────────────────────────────────────
    //  UI
    // ──────────────────────────────────────────────

    public BorderPane buildUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f7f7f5;");

        mapCanvas = new MapCanvas(420, 588);
        VBox mapBox = new VBox(8, sectionLabel("Mapa de ciudad"), mapCanvas, buildLegend());
        mapBox.setPadding(new Insets(8));
        mapBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        root.setLeft(mapBox);

        VBox right = new VBox(10, buildCurrentOrderPanel(), buildQueuePanel(),
                              buildHistoryPanel(), buildStatusSemaphorePanel(),
                              buildMqttInfoPanel());
        right.setPrefWidth(300);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 10));
        root.setCenter(right);

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

    private VBox buildQueuePanel() {
        cbPickup   = new ComboBox<>();
        cbDelivery = new ComboBox<>();
        cbPickup.setPromptText("Punto de recogida");
        cbDelivery.setPromptText("Punto de entrega");
        cbPickup.setMaxWidth(Double.MAX_VALUE);
        cbDelivery.setMaxWidth(Double.MAX_VALUE);

        final int[] orderCounter = {1};

        Button btnAdd = new Button("Anadir pedido");
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setStyle("-fx-font-size: 12;");

        Label lblFormStatus = new Label("");
        lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");

        btnAdd.setOnAction(e -> {
            String pickup   = cbPickup.getValue();
            String delivery = cbDelivery.getValue();

            if (pickup == null || delivery == null) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Selecciona recogida y entrega.");
                return;
            }
            if (pickup.equals(delivery)) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Recogida y entrega no pueden ser iguales.");
                return;
            }

            String orderId = "ORD-" + String.format("%03d", orderCounter[0]++);
            String[] p = pickup.split(",");
            String[] d = delivery.split(",");

            // Formato del guion: {"id":"ORD-xxx","pickup":[r,c],"delivery":[r,c]}
            String json = String.format(
                    "{\"id\":\"%s\",\"pickup\":[%s,%s],\"delivery\":[%s,%s]}",
                    orderId, p[0], p[1], d[0], d[1]);

            try {
                mqtt.publish(MqttService.TOPIC_ORDERS, json);
                queueItems.add(orderId + "  (" + pickup + ") -> (" + delivery + ")");
                queuedIds.add(orderId);
                cbPickup.setValue(null);
                cbDelivery.setValue(null);
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
                lblFormStatus.setText("Pedido " + orderId + " enviado");
            } catch (Exception ex) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Error al publicar: " + ex.getMessage());
            }
        });

        VBox form = new VBox(6, cbPickup, cbDelivery, btnAdd, lblFormStatus);
        listQueue = new ListView<>(queueItems);
        listQueue.setPrefHeight(80);
        listQueue.setStyle("-fx-font-size: 12;");

        return wrapPanel(new VBox(8, sectionLabel("Gestion de pedidos"), form,
                                  sectionLabel("Cola"), listQueue));
    }

    private VBox buildHistoryPanel() {
        listHistory = new ListView<>(historyItems);
        listHistory.setPrefHeight(100);
        listHistory.setStyle("-fx-font-size: 12;");
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
        lblMqttStatus = new Label("Desconectado");
        lblMqttStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
        Label info = new Label(MqttService.BROKER_URL + "  |  Equipo E");
        info.setStyle("-fx-font-size: 11; -fx-text-fill: #aaa;");
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

                // Rellenar combos con los puntos de recogida/entrega detectados.
                List<String> points = new ArrayList<>();
                for (int[] p : map.getPickupPoints()) {
                    points.add(p[0] + "," + p[1]);
                }
                if (cbPickup != null)   cbPickup.getItems().setAll(points);
                if (cbDelivery != null) cbDelivery.getItems().setAll(points);
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
            }
            case "RECOGIDO" -> {
                paintDotActive(dotRecogido, "#1a5fa5");
                lblStatus.setText("Paquete recogido");
            }
            case "LISTO" -> {
                paintDotActive(dotListo, "#3B6D11");
                if (activeOrderId != null) {
                    historyItems.add(0, activeOrderId + "  ENTREGADO");
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
}
