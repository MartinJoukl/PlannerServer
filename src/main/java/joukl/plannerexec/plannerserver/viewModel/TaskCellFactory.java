package joukl.plannerexec.plannerserver.viewModel;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import joukl.plannerexec.plannerserver.model.Queue;
import joukl.plannerexec.plannerserver.model.Task;

public class TaskCellFactory implements Callback<ListView<Task>, ListCell<Task>> {
        @Override
        public ListCell<Task> call(ListView<Task> param) {
            return new ListCell<>(){
                @Override
                public void updateItem(Task task, boolean empty) {
                    super.updateItem(task, empty);
                    if (empty || task == null) {
                        setText(null);
                    } else {
                        setText("Name: "+task.getName() +", id: "+task.getId()+", status: "+task.getStatus()+", queue: "+task.getQueue().getName()+
                                ", priority: "+task.getPriority());
                    }
                }
            };
        }
    }
