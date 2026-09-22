module com.example.pastm {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;

    opens com.example.pastm to javafx.fxml;
    exports com.example.pastm;
}