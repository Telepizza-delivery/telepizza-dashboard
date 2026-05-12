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

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Builds the dashboard UI and connects it to MQTT data.
 *
 * Layout:
 *   ┌─────────────────────┬──────────────────┐
 *   │  MapCanvas (5×7)    │  Current order   │
 *   │                     │  Order queue     │
 *   │                     │  History         │
 *   │                     │  MQTT status     │
 *   └─────────────────────┴──────────────────┘
 *   │  Status bar (MQTT / map string)         │
 */
public class DashboardController {

    private static final int MAP_COLS = 5;
    private static final int MAP_ROWS = 7;

    // UI components
    private MapCanvas mapCanvas;
    private Label     lblCurrentOrder;
    private Label     lblPickup;
    private Label     lblDelivery;
    private ProgressBar progressBar;
    private Label     lblProgress;
    private ListView<String> listQueue;
    private ListView<String> listHistory;
    private Label     lblStatus;
    private Label     lblMqttStatus;
    private Label     lblMapRaw;
    private ComboBox<String> cbPickup;
    private ComboBox<String> cbDelivery;

    // Data
    private CityMap currentMap;
    private final Queue<Order> orderQueue = new LinkedList<>();
    private Order activeOrder = null;
    private final ObservableList<String> queueItems   = FXCollections.observableArrayList();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();

    // Services
    private final MqttService mqtt = new MqttService();

    // ──────────────────────────────────────────────
    //  UI construction
    // ──────────────────────────────────────────────

    public BorderPane buildUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #f7f7f5;");

        // Map canvas (left)
        mapCanvas = new MapCanvas(420, 588);
        VBox mapBox = new VBox(8, sectionLabel("Mapa de ciudad"), mapCanvas, buildLegend());
        mapBox.setPadding(new Insets(8));
        mapBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        root.setLeft(mapBox);

        // Right panel
        VBox right = new VBox(10, buildCurrentOrderPanel(), buildQueuePanel(), buildHistoryPanel(), buildMqttInfoPanel());
        right.setPrefWidth(300);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 10));
        root.setCenter(right);

        // Status bar (bottom)
        lblStatus   = new Label("Iniciando...");
        lblMapRaw   = new Label("");
        lblMapRaw.setStyle("-fx-font-family: monospace; -fx-font-size: 10;");
        VBox statusBar = new VBox(2, lblStatus, lblMapRaw);
        statusBar.setPadding(new Insets(8, 0, 0, 0));
        root.setBottom(statusBar);

        return root;
    }

    // ──────────────────────────────────────────────
    //  Panel builders
    // ──────────────────────────────────────────────

    private VBox buildCurrentOrderPanel() {
        lblCurrentOrder = new Label("Sin pedido activo");
        lblCurrentOrder.setFont(Font.font("Arial", 13));
        lblCurrentOrder.setStyle("-fx-font-weight: bold;");

        lblPickup   = new Label("Recogida: —");
        lblDelivery = new Label("Entrega:  —");
        lblPickup.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
        lblDelivery.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #185FA5;");

        lblProgress = new Label("0%");
        lblProgress.setStyle("-fx-font-size: 11; -fx-text-fill: #777;");

        VBox inner = new VBox(4, lblCurrentOrder, lblPickup, lblDelivery, progressBar, lblProgress);
        inner.setPadding(new Insets(8));
        inner.setStyle("-fx-background-color: #f0f4f8; -fx-background-radius: 6;");

        VBox panel = new VBox(6, sectionLabel("Pedido actual"), inner);
        return wrapPanel(panel);
    }

    private VBox buildQueuePanel() {
        // Pickup dropdowns
        cbPickup = new ComboBox<>();
        cbDelivery = new ComboBox<>();
        cbPickup.setPromptText("Punto de recogida");
        cbDelivery.setPromptText("Punto de entrega");
        cbPickup.setMaxWidth(Double.MAX_VALUE);
        cbDelivery.setMaxWidth(Double.MAX_VALUE);

        // Populate with known pickup/delivery coords from spec
//        java.util.List<String> points = java.util.List.of(
//                "0,0", "0,1", "0,3", "3,0", "4,4",
//                "5,0", "5,2", "6,0", "6,2", "6,4"
//        );
//        cbPickup.getItems().addAll(points);
//        cbDelivery.getItems().addAll(points);

        // Order counter
        final int[] orderCounter = {1};

        Button btnAdd = new Button("Añadir pedido →");
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

            String json = String.format(
                    "{\"id\":\"%s\",\"pickupRow\":%s,\"pickupCol\":%s,\"deliveryRow\":%s,\"deliveryCol\":%s}",
                    orderId, p[0], p[1], d[0], d[1]
            );

            try {
                mqtt.publish(MqttService.TOPIC_ORDERS, json);
                queueItems.add(orderId + "  (" + pickup + ") → (" + delivery + ")");
                cbPickup.setValue(null);
                cbDelivery.setValue(null);
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #3B6D11;");
                lblFormStatus.setText("Pedido " + orderId + " enviado ✓");
            } catch (Exception ex) {
                lblFormStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #e24b4a;");
                lblFormStatus.setText("Error al publicar: " + ex.getMessage());
            }
        });

        VBox form = new VBox(6, cbPickup, cbDelivery, btnAdd, lblFormStatus);
        listQueue = new ListView<>(queueItems);
        listQueue.setPrefHeight(80);
        listQueue.setStyle("-fx-font-size: 12;");

        return wrapPanel(new VBox(8, sectionLabel("Gestión de pedidos"), form, sectionLabel("Cola"), listQueue));
    }

    private VBox buildHistoryPanel() {
        listHistory = new ListView<>(historyItems);
        listHistory.setPrefHeight(100);
        listHistory.setStyle("-fx-font-size: 12;");
        return wrapPanel(new VBox(6, sectionLabel("Historial"), listHistory));
    }

    private VBox buildMqttInfoPanel() {
        lblMqttStatus = new Label("Desconectado");
        lblMqttStatus.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
        Label info = new Label("localhost  |  topic: map");
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
        mqtt.setOnPositionReceived(this::handlePositionPayload);
        mqtt.setOnOrderReceived(this::handleOrderPayload);

        new Thread(() -> {
            try {
                mqtt.connect();
            } catch (Exception e) {
                Platform.runLater(() -> lblStatus.setText("MQTT error: " + e.getMessage()));
            }
        }, "mqtt-connect").start();
    }

    // ──────────────────────────────────────────────
    //  MQTT payload handlers  (called from MQTT thread → must use Platform.runLater)
    // ──────────────────────────────────────────────

    private void handleMapPayload(String payload) {
        System.out.println("Map received, length: " + payload.length() + " content: " + payload);
        try {
            CityMap map = new CityMap(payload, MAP_COLS, MAP_ROWS);
            // ... rest
        } catch (Exception e) {
            System.out.println("PARSE ERROR: " + e.getMessage()); // ADD THIS
            Platform.runLater(() -> lblStatus.setText("Error al parsear mapa: " + e.getMessage()));
        }
        try {
            CityMap map = new CityMap(payload, MAP_COLS, MAP_ROWS);
            Platform.runLater(() -> {
                currentMap = map;
                mapCanvas.setMap(map);
                lblMapRaw.setText("Map: " + payload.substring(0, Math.min(40, payload.length())) + "…");
                lblStatus.setText("Mapa actualizado — " + map.getPickupPoints().size() + " puntos de recogida/entrega");
                List<String> points = map.getPickupPoints().stream().map(p -> p[0] + "," + p[1]).collect(java.util.stream.Collectors.toList());
                if (cbPickup != null) cbPickup.getItems().setAll(points);
                if (cbDelivery != null) cbDelivery.getItems().setAll(points);
            });
        } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Error al parsear mapa: " + e.getMessage()));
        }
    }

    /**
     * Expects JSON like: {"row":2,"col":3}
     * Simple manual parse to avoid needing a JSON library.
     */
    private void handlePositionPayload(String payload) {
        try {
            int row = extractJsonInt(payload, "row");
            int col = extractJsonInt(payload, "col");
            Platform.runLater(() -> mapCanvas.setRobotPosition(row, col));
        } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Error posición: " + e.getMessage()));
        }
    }

    /**
     * Expects JSON like: {"id":"ORD-042","progress":0.6,"status":"IN_PROGRESS","pickupRow":0,"pickupCol":0,"deliveryRow":3,"deliveryCol":4}
     */
    private void handleOrderPayload(String payload) {
        try {
            String  id       = extractJsonString(payload, "id");
            double  progress = extractJsonDouble(payload, "progress");
            String  status   = extractJsonString(payload, "status");
            int pRow = extractJsonInt(payload, "pickupRow");
            int pCol = extractJsonInt(payload, "pickupCol");
            int dRow = extractJsonInt(payload, "deliveryRow");
            int dCol = extractJsonInt(payload, "deliveryCol");

            Platform.runLater(() -> {
                lblCurrentOrder.setText("#" + id);
                lblPickup.setText("Recogida: (" + pRow + "," + pCol + ")");
                lblDelivery.setText("Entrega:  (" + dRow + "," + dCol + ")");
                progressBar.setProgress(progress);
                lblProgress.setText((int)(progress * 100) + "%");
            });
        } catch (Exception e) {
            Platform.runLater(() -> lblStatus.setText("Error pedido: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────
    //  Order management (called from UI thread)
    // ──────────────────────────────────────────────

    /** Add a new order to the queue and refresh list */
    public void addOrder(Order order) {
        orderQueue.add(order);
        refreshQueueList();
    }

    private void refreshQueueList() {
        queueItems.clear();
        for (Order o : orderQueue) {
            queueItems.add(o.getId() + "  " + o.getPickupLabel() + " → " + o.getDeliveryLabel());
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers
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

    // ──────────────────────────────────────────────
    //  Minimal JSON field extractors (no extra lib)
    // ──────────────────────────────────────────────

    private static int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) throw new IllegalArgumentException("Key not found: " + key);
        int colon = json.indexOf(':', idx);
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    private static double extractJsonDouble(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) throw new IllegalArgumentException("Key not found: " + key);
        int colon = json.indexOf(':', idx);
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) end++;
        return Double.parseDouble(json.substring(start, end));
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) throw new IllegalArgumentException("Key not found: " + key);
        int q1 = json.indexOf('"', json.indexOf(':', idx) + 1);
        int q2 = json.indexOf('"', q1 + 1);
        return json.substring(q1 + 1, q2);
    }
}