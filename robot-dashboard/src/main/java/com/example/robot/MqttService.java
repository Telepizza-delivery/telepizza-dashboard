package com.example.robot;

import java.util.function.Consumer;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Gestiona la conexion MQTT y las suscripciones del dashboard (Clarence).
 *
 * Broker y topics segun el guion de comunicaciones del Equipo E:
 *   Broker:                IP_BROKER
 *   SUB  map                       -> cadena codificada del mapa (cada 60 s)
 *   SUB  Equipo E/odometry         -> JSON {"instructions":[...]} cada 1 s
 *   SUB  Equipo E/status           -> "PEDIDO_RECIBIDO"|"RECOGIDO"|"LISTO"
 *   PUB  Equipo E/orders           -> JSON {"id":"ORD-xxx","pickup":[r,c],"delivery":[r,c]}
 */
public class MqttService {

    public static final String BROKER_URL = "tcp://"
            + System.getenv().getOrDefault("IP_ADDRESS_SERVER", "192.168.137.2")
            + ":"
            + System.getenv().getOrDefault("PORT_SERVER", "1883");
    private static final String CLIENT_ID = System.getenv().getOrDefault(
            "CLIENT_ID"+ System.currentTimeMillis(), "RobotDashboard-JavaFX-" + System.currentTimeMillis());

    public static final String TOPIC_MAP       = "map";
    public static final String TOPIC_ORDERS    = "Equipo E/orders";
    public static final String TOPIC_ODOMETRY  = "Equipo E/odometry";
    public static final String TOPIC_STATUS    = "Equipo E/status";

    private MqttClient client;
    private Consumer<String> onMapReceived;
    private Consumer<String> onOdometryReceived;
    private Consumer<String> onStatusReceived;
    private Consumer<String> onStatusMessage;

    public void setOnMapReceived(Consumer<String> cb)       { this.onMapReceived = cb; }
    public void setOnOdometryReceived(Consumer<String> cb)  { this.onOdometryReceived = cb; }
    public void setOnStatusReceived(Consumer<String> cb)    { this.onStatusReceived = cb; }
    public void setOnStatusMessage(Consumer<String> cb)     { this.onStatusMessage = cb; }

    public void connect() throws MqttException {
        if (client != null && client.isConnected()) return;
        client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());

        System.out.println("BROKER_URL = " + BROKER_URL);
        System.out.println("CLIENT_ID = " + CLIENT_ID);

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);
        opts.setAutomaticReconnect(true);

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                notifyStatus(reconnect
                        ? "MQTT: reconectado a " + serverURI
                        : "MQTT: conectado a "   + serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {
                notifyStatus("MQTT: conexion perdida - intentando reconectar...");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                if (TOPIC_MAP.equals(topic)) {
                    if (onMapReceived != null) onMapReceived.accept(payload);
                } else if (TOPIC_ODOMETRY.equals(topic)) {
                    if (onOdometryReceived != null) onOdometryReceived.accept(payload);
                } else if (TOPIC_STATUS.equals(topic)) {
                    if (onStatusReceived != null) onStatusReceived.accept(payload);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        client.connect(opts);
        client.subscribe(TOPIC_MAP,      1);
        client.subscribe(TOPIC_ODOMETRY, 0);
        client.subscribe(TOPIC_STATUS,   0);
//        notifyStatus("MQTT: conectado a " + BROKER_URL);

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
        if (client == null || !client.isConnected())
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        client.publish(topic, message);
    }

}
