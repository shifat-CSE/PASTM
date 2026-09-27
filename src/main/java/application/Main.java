package application;

import database.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import service.ThreadManager;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            Database.initializeDatabase();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/application/MainWindow.fxml"));
            Parent root = loader.load();
            MainWindowController controller = loader.getController();

            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());
            scene.getStylesheets().add(
                    getClass().getResource("/application/style.css").toExternalForm());

            primaryStage.initStyle(StageStyle.DECORATED);
            primaryStage.setResizable(true);
            primaryStage.setTitle("PASTM");
            primaryStage.setScene(scene);

            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());

            primaryStage.setOnCloseRequest(e -> {
                System.out.println("[Main] window closing — graceful shutdown");
                controller.stopAll();
                ThreadManager.getInstance().shutdown();
            });

            primaryStage.show();
            System.out.println("[Main] PASTM v2 started");
        } catch (Exception e) {
            System.err.println("[Main] failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                ThreadManager.getInstance().shutdown(), "shutdown-hook"));
        launch(args);
    }
}