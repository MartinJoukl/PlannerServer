package joukl.plannerexec.plannerserver.viewModel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import joukl.plannerexec.plannerserver.model.Agent;
import joukl.plannerexec.plannerserver.model.Client;
import joukl.plannerexec.plannerserver.model.Queue;
import joukl.plannerexec.plannerserver.model.Scheduler;

import java.util.*;

public class AddQueueController {

    @FXML
    public MenuButton agentsMenuButton;
    @FXML
    public Button confirmQueueButton;
    @FXML
    private Spinner<Integer> prioritySpinner;
    @FXML
    private Spinner<Integer> capacitySpinner;
    @FXML
    private TextField queueNameTextField;
    ObservableList<Agent> agents = FXCollections.observableList(Arrays.asList(Agent.values()));
    List<Agent> selectedAgents = new ArrayList<>();

    @FXML
    private void initialize() {
        fillOutSelectedAgents();
        fillOutSpinners();
        queueNameTextField.textProperty().addListener((val) -> {
            validateForm();
        });
        validateForm();
    }

    private void fillOutSpinners() {
        capacitySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        Integer.MAX_VALUE
                )
        );
        prioritySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1,
                        Integer.MAX_VALUE
                )
        );
    }

    private void fillOutSelectedAgents() {
        final List<CustomMenuItem> agentCheckMenuItems = new LinkedList<>();
        agents.forEach((agent -> {
            CustomMenuItem menuItem = new CustomMenuItem(new CheckBox(agent.getAgentName()));
            agentCheckMenuItems.add(menuItem);
            menuItem.hideOnClickProperty().set(false);
            //Triggered twice per click??
            menuItem.setOnAction((actionEvent) -> {
                if (((CheckBox) menuItem.getContent()).isSelected() && !selectedAgents.contains(agent)) {
                    selectedAgents.add(agent);
                } else if (!((CheckBox) menuItem.getContent()).isSelected()) {
                    selectedAgents.remove(agent);
                }
                if (selectedAgents.size() > 0) {
                    String selectedItemsText = selectedAgents.toString();
                    agentsMenuButton.setText(selectedItemsText);
                } else {
                    agentsMenuButton.setText("Select agent/s");
                }
                validateForm();
            });
        }));
        agentsMenuButton.getItems().addAll(agentCheckMenuItems);
    }

    private void validateForm() {
        if (!queueNameTextField.getText().trim().isEmpty() && !selectedAgents.isEmpty()) {
            confirmQueueButton.setDisable(false);
        } else {
            confirmQueueButton.setDisable(true);
        }
    }

    public void onActionConfirm(ActionEvent actionEvent) {
        Queue queue = new Queue(queueNameTextField.getText().trim(), List.copyOf(selectedAgents), capacitySpinner.getValue(), prioritySpinner.getValue());
        Map<String, Queue> queueMap = Scheduler.getScheduler().getQueueMap();
        if (queueMap.containsKey(queue.getName())) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Creation of queue failed");
            alert.setContentText("Creating new queue with name " + queue.getName() + " failed. Queue with given name already exists.");
            alert.setHeaderText("Creation of queue failed");
            alert.show();
            return;
        } else {
            queueMap.put(queue.getName(), queue);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Queue added");
            alert.setContentText("Queue with name " + queue.getName() + " was added successfully.");
            alert.setHeaderText("Queue added");
            alert.show();
        }
        closeStage(actionEvent);
    }

    public void onActionCancel(ActionEvent actionEvent) {
        closeStage(actionEvent);
    }

    private static void closeStage(ActionEvent actionEvent) {
        Node source = (Node) actionEvent.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        if (stage.getOnCloseRequest() != null) {
            stage.getOnCloseRequest().handle(null);
        }
        stage.close();
    }
}
