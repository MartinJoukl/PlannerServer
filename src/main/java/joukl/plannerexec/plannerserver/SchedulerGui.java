package joukl.plannerexec.plannerserver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import joukl.plannerexec.plannerserver.model.Scheduler;

import java.io.IOException;

public class SchedulerGui extends Application {
    public static Stage mainStage;

    @Override
    public void start(Stage stage) throws IOException {
        mainStage = stage;
        FXMLLoader fxmlLoader = new FXMLLoader(SchedulerGui.class.getResource("application-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        Scheduler.getScheduler().startObserver();
        //scene.getStylesheets().add(Objects.requireNonNull(Scheduler.class.getResource("style.css")).toExternalForm());
        stage.setTitle("Scheduler");
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> {
            try {
                Scheduler.getScheduler().stopListening();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //ensure we exit
            Platform.exit();
            System.exit(0);
        });

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}