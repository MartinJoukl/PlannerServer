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
import joukl.plannerexec.plannerserver.model.*;

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

    private final ObservableList<Task> taskList = FXCollections.observableList(new ArrayList<>());

    @FXML
    private Label keyStatusLBL;
    @FXML
    private Label clientKeyStatusLBL;
    @FXML
    private ListView<Queue> queueListView;
    @FXML
    private ListView<Task> plannedJobsListView;
    @FXML
    private Label jobIdLBL;
    @FXML
    private Label taskNameLBL;
    @FXML
    private Label taskPriorityLBL;
    @FXML
    private ListView<String> argumentsListView;
    @FXML
    private ListView<String> resultsLocationListView;
    @FXML
    private Label repeatsLBL;
    @FXML
    private Label costOfJobLBL;
    @FXML
    private Label statusLBL;
    @FXML
    private Label queueLBL;
    @FXML
    private Label listeningStatusLBL;

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
        plannedJobsListView.setItems(taskList);
        plannedJobsListView.setCellFactory(new TaskCellFactory());
        plannedJobsListView.setOnMouseClicked((mouseEvent) -> {
            Task selectedTask = plannedJobsListView.getSelectionModel().getSelectedItem();
            if (selectedTask != null) {
                onSelectedTask(selectedTask);
            }
        });

        refreshQueueList();
        refreshServerKeyStatus();
        refreshClientKeyStatus();
        refreshTaskList();
    }

    private void refreshTaskList() {
        taskList.clear();
        taskList.addAll(Scheduler.getScheduler().getTasksAsList());
        plannedJobsListView.refresh();
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

    @FXML
    public void onActionUploadTaskButton(ActionEvent actionEvent) throws IOException {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File directory = directoryChooser.showDialog(SchedulerGui.mainStage);
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        Optional<File> configFileOptional = Arrays.stream(files).filter((file -> file.getName().equals("taskConfig.json"))).findAny();
        Optional<File> payloadDirOptional = Arrays.stream(files).filter((file -> file.getName().equals("payload"))).findAny();
        if (configFileOptional.isEmpty() || payloadDirOptional.isEmpty() || !payloadDirOptional.get().isDirectory()) {
            showError("Task upload failed", "Configuration file or payload not found", "Directory must contain both taskConfig.json and directory payload");
            return;
        }
        File configFile = configFileOptional.get();
        try {
            Task readTask = Persistence.readTaskConfiguration(configFile);
            readTask.setPathToSourceDirectory(directory.getAbsolutePath());
            if (readTask.getQueue() == null) {
                showError("Task upload failed", "Queue doesn't exist", "Queue specified in config.json was not found.");
                return;
            }
            readTask.getQueue().getTasks().add(readTask);
            showInfo("Task uploaded sucesfully", "Task uploaded successfully", "Task with name: " + readTask.getName() + " has been uploaded successfully to the queue: " + readTask.getQueue().getName() + ".");
            refreshTaskList();
            selectTask(readTask);
        } catch (Exception ex) {
            showError("Unsuccessful parsing", "Configuration file was not parsed successfully", "Task parsing failed with following message: " + ex.getMessage());
        }

    }

    private void selectTask(Task task) {
        plannedJobsListView.getSelectionModel().select(task);
        onSelectedTask(task);
    }

    private void onSelectedTask(Task task) {
        jobIdLBL.setText(task.getId());
        taskNameLBL.setText(task.getName());
        taskPriorityLBL.setText(String.valueOf(task.getPriority()));
        argumentsListView.setItems(FXCollections.observableList(task.getParameters()));
        resultsLocationListView.setItems(FXCollections.observableList(task.getPathToResults()));
        costOfJobLBL.setText(String.valueOf(task.getCost()));
        statusLBL.setText(task.getStatus().toString());
        queueLBL.setText(task.getQueue().getName());
        repeatsLBL.setText(String.valueOf(Math.abs(task.getFrom() - task.getTo())));
    }

    private static void showError(String title, String header, String contentText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(contentText);
        alert.setHeaderText(header);
        alert.show();
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
            showInfo("Private key changed", "Private key changed", "Key changed successfully. A new public key was also generated.");
        } else {
            showError("Private key not changed", "Changing of server key failed", "Changing of server key failed.");
        }
    }

    @FXML
    public void onActionGeneratePrivateKey(ActionEvent actionEvent) throws NoSuchAlgorithmException {
        privateKeyStatus.setValue(authorization.generateServerKeys());
        if (privateKeyStatus.getValue()) {
            showInfo("Private key generated", "Key generation", "Key generated successfully.");
        } else {
            showError("Private key generating failed", "Key generation", "Key generation failed.");
        }
    }

    private static void showInfo(String title, String header, String contentText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(contentText);
        alert.setHeaderText(header);
        alert.show();
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
            showInfo("Export was successful", "Export", "Key exported successfully and is ready to be given to clients.");
        } else {
            showError("Export failed", "Key exporting failed", "Exporting of key failed.");
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
            showInfo("Client public key changed", "Client public key changed", "Key changed successfully.");
        } else {
            showError("Client public key not changed", "Changing of client public key failed", "Changing of server key failed.");
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

    @FXML
    public void onActionStartListening(ActionEvent actionEvent) throws IOException {
        Scheduler scheduler = Scheduler.getScheduler();
        if (!scheduler.isListening()) {
            scheduler.startListening();
        }

        if (!scheduler.isListening()) {
            listeningStatusLBL.setText("Not listening");
        } else {
            listeningStatusLBL.setText("Listening on port: " + scheduler.getServerSocket().getLocalPort());
        }
    }
}