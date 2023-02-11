module joukl.plannerexec.plannerserver {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.ikonli.javafx;

    opens joukl.plannerexec.plannerserver to javafx.fxml;
    exports joukl.plannerexec.plannerserver;
    exports joukl.plannerexec.plannerserver.viewModel;
    opens joukl.plannerexec.plannerserver.viewModel to javafx.fxml;
}