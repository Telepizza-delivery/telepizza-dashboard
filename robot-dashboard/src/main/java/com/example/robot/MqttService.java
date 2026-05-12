package com.example.robot;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.function.Consumer;

/**
 * Manages the MQTT connection and subscriptions.
 *
 * Broker details (from project spec):
 *   IP:    192.168.0.108
 *   Port:  1883
 *   Topic "map"             -> city map string, published every 60s
 *   Topic "robot/position"  -> JSON with robot position, published by robot
 *   Topic "robot/order/status" -> JSON with current order status
 */
public class MqttService {

//    private static final String BROKER_URL   = "tcp://127.0.0.1:1884";
    private static final String BROKER_URL   = "tcp://192.168.0.108:1883";
    private static final String CLIENT_ID    = "RobotDashboard-JavaFX" + System.currentTimeMillis();

    public static final String TOPIC_MAP     = "map";
    public static final String TOPIC_POS     = "robot/position";
    public static final String TOPIC_ORDER   = "robot/order/status";

    public static final String TOPIC_ORDERS = "EquipoE/orders";

    private MqttClient client;
    private Consumer<String> onMapReceived;
    private Consumer<String> onPositionReceived;
    private Consumer<String> onOrderReceived;
    private Consumer<String> onStatusMessage;

    public void setOnMapReceived(Consumer<String> callback)       { this.onMapReceived = callback; }
    public void setOnPositionReceived(Consumer<String> callback)  { this.onPositionReceived = callback; }
    public void setOnOrderReceived(Consumer<String> callback)     { this.onOrderReceived = callback; }
    public void setOnStatusMessage(Consumer<String> callback)     { this.onStatusMessage = callback; }

    public void connect() throws MqttException {

        if (client != null && client.isConnected()) return;
        client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);
        opts.setAutomaticReconnect(false);

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                notifyStatus("MQTT: conexión perdida — " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                switch (topic) {
                    case TOPIC_MAP   -> { if (onMapReceived      != null) onMapReceived.accept(payload); }
                    case TOPIC_POS   -> { if (onPositionReceived != null) onPositionReceived.accept(payload); }
                    case TOPIC_ORDER -> { if (onOrderReceived    != null) onOrderReceived.accept(payload); }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        client.connect(opts);
        client.subscribe(TOPIC_MAP,   1);
        client.subscribe(TOPIC_POS,   0);
        client.subscribe(TOPIC_ORDER, 0);
        notifyStatus("MQTT: conectado a " + BROKER_URL);

        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnect));
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) client.disconnect();
        } catch (MqttException ignored) {}
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private void notifyStatus(String msg) {
        if (onStatusMessage != null) onStatusMessage.accept(msg);
    }

    public void publish(String topic, String payload) throws MqttException {
        if (client == null || !client.isConnected()) throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        client.publish(topic, message);
    }
}
