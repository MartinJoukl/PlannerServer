package joukl.plannerexec.plannerserver.viewModel;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import joukl.plannerexec.plannerserver.SchedulerGui;
import joukl.plannerexec.plannerserver.model.Authorization;
import joukl.plannerexec.plannerserver.model.Persistence;
import joukl.plannerexec.plannerserver.model.Queue;
import joukl.plannerexec.plannerserver.model.Scheduler;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class ApplicationController {
    private final String KEY_ACTIVE = "Key in use";
    private final String KEY_INACTIVE = "Key is not loaded";
    private SimpleBooleanProperty privateKeyStatus = new SimpleBooleanProperty();
    private SimpleBooleanProperty clientKeyStatus = new SimpleBooleanProperty();
    private Authorization authorization;
    private final ObservableList<Queue> queueList = FXCollections.observableList(new ArrayList<>());

    @FXML
    private Label keyStatusLBL;
    @FXML
    private Label clientKeyStatusLBL;
    @FXML
    private ListView<Queue> queueListView;

    @FXML
    private void initialize() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        authorization = Scheduler.getScheduler().getAuthorization();
        privateKeyStatus.setValue(authorization.loadPrivateKeyFromRoot());
        privateKeyStatus.addListener((a) -> {
            refreshServerKeyStatus();
        });
        clientKeyStatus.setValue(authorization.loadClientKeyFromRoot());
        clientKeyStatus.addListener((a) -> {
            refreshClientKeyStatus();
        });
        queueListView.setItems(queueList);

        queueListView.setCellFactory(new QueueCellFactory());

        refreshQueueList();
        refreshServerKeyStatus();
        refreshClientKeyStatus();
    }

    private void refreshServerKeyStatus() {
        if (privateKeyStatus.getValue()) {
            keyStatusLBL.setText(KEY_ACTIVE);
        } else {
            keyStatusLBL.setText(KEY_INACTIVE);
        }
    }

    private void refreshClientKeyStatus() {
        if (clientKeyStatus.getValue()) {
            clientKeyStatusLBL.setText(KEY_ACTIVE);
        } else {
            clientKeyStatusLBL.setText(KEY_INACTIVE);
        }
    }

    public void onActionUploadTaskButton(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File directory = directoryChooser.showDialog(SchedulerGui.mainStage);
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
       // Optional<File> configFile = Arrays.stream(files).fi
    }

    public void onActionChangeJobStatus(ActionEvent actionEvent) {
    }

    @FXML
    public void onActionSelectPrivateKey(ActionEvent actionEvent) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Private key", "*.key"));
        File file = fileChooser.showOpenDialog(SchedulerGui.mainStage);
        if (file == null) {
            return;
        }

        privateKeyStatus.setValue(authorization.changeServerKeys(file));
        if (privateKeyStatus.getValue()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Private key changed");
            alert.setContentText("Key changed successfully. A new public key was also generated.");
            alert.setHeaderText("Private key changed");
            alert.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Private key not changed");
            alert.setContentText("Changing of server key failed.");
            alert.setHeaderText("Changing of server key failed");
            alert.show();
        }
    }

    @FXML
    public void onActionGeneratePrivateKey(ActionEvent actionEvent) throws NoSuchAlgorithmException {
        privateKeyStatus.setValue(authorization.generateServerKeys());
        if (privateKeyStatus.getValue()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Private key generated");
            alert.setContentText("Key generated successfully.");
            alert.setHeaderText("Key generation");
            alert.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Private key generating failed");
            alert.setContentText("Key generation failed.");
            alert.setHeaderText("Key generation");
            alert.show();
        }
    }

    @FXML
    public void onActionExportPublicKey(ActionEvent actionEvent) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (authorization.getServerPublicKey() == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Public key", "*.key"));
        File file = fileChooser.showSaveDialog(SchedulerGui.mainStage);

        if (file == null) {
            return;
        }

        boolean success = Persistence.saveBytesToFile(file.toPath(), authorization.getServerPublicKey().getEncoded());
        if (success) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export was successful");
            alert.setContentText("Key exported successfully and is ready to be given to clients.");
            alert.setHeaderText("Export");
            alert.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export failed");
            alert.setContentText("Exporting of key failed.");
            alert.setHeaderText("Key exporting failed");
            alert.show();
        }
    }

    @FXML
    public void onActionUploadWorkerKey(ActionEvent actionEvent) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Public key", "*.key"));
        File file = fileChooser.showOpenDialog(SchedulerGui.mainStage);
        if (file == null) {
            return;
        }

        clientKeyStatus.setValue(authorization.changeClientKey(file));
        if (clientKeyStatus.getValue()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Client public key changed");
            alert.setContentText("Key changed successfully.");
            alert.setHeaderText("Client public key changed");
            alert.show();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Client public key not changed");
            alert.setContentText("Changing of server key failed.");
            alert.setHeaderText("Changing of client public key failed");
            alert.show();
        }
    }

    @FXML
    public void onActionAddQueue(ActionEvent actionEvent) throws IOException, InterruptedException {
        FXMLLoader fxmlLoader = new FXMLLoader(SchedulerGui.class.getResource("add-queue-form-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        //scene.getStylesheets().add(Objects.requireNonNull(Scheduler.class.getResource("style.css")).toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("Add new queue");
        stage.setResizable(false);
        stage.initOwner(SchedulerGui.mainStage);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        stage.showAndWait(); //the queue controller will add element to authorization queue
        refreshQueueList();
    }

    public void refreshQueueList() {
        Map<String, Queue> queueMap = Scheduler.getScheduler().getQueueMap();
        //it is removal, delete whole list
        if (queueMap.size() < queueList.size()) {
            queueList.clear();
        }

        Scheduler.getScheduler().getQueueMap().forEach((key, value) -> {
            if (!queueList.contains(value)) {
                queueList.add(value);
            }
        });
    }
}