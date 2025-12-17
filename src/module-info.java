module app {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;

    exports app;

    opens app to javafx.base;
}
