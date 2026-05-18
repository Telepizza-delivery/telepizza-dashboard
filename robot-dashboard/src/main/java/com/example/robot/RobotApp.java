package com.example.robot;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class RobotApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load FXML or build UI programmatically
        DashboardController controller = new DashboardController();
        Scene scene = new Scene(controller.buildUI(), 900, 700);
//        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Robot Delivery Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start MQTT connection after UI is ready
        controller.startMqtt();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // MQTT client disconnects automatically via shutdown hook in MqttService
    }

    public static void main(String[] args) {
        launch(args);
    }
}
