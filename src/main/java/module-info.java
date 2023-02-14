module joukl.plannerexec.plannerserver {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    opens joukl.plannerexec.plannerserver to javafx.fxml, com.fasterxml.jackson.databind, com.fasterxml.jackson.annotation ,com.fasterxml.jackson.core;
    exports joukl.plannerexec.plannerserver;
    exports joukl.plannerexec.plannerserver.model;
    exports joukl.plannerexec.plannerserver.viewModel;
    opens joukl.plannerexec.plannerserver.viewModel to javafx.fxml, com.fasterxml.jackson.databind, com.fasterxml.jackson.annotation ,com.fasterxml.jackson.core;
}