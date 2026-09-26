package application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/MainWindow.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1150, 700);
            scene.getStylesheets().add(
                    getClass().getResource("/application/style.css").toExternalForm());

            primaryStage.initStyle(StageStyle.DECORATED);
            primaryStage.setResizable(true);
            primaryStage.setMaximized(false);
            primaryStage.setTitle("PASTM");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(620);
            primaryStage.centerOnScreen();
            primaryStage.show();

            System.out.println("[Main] PASTM v2 started (Phase 1 — Weeks 1-3)");
        } catch (Exception e) {
            System.err.println("[Main] failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}