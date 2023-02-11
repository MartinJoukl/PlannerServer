package joukl.plannerexec.plannerserver.viewModel;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import joukl.plannerexec.plannerserver.model.Queue;

public class QueueCellFactory implements Callback<ListView<Queue>, ListCell<Queue>> {
        @Override
        public ListCell<Queue> call(ListView<Queue> param) {
            return new ListCell<>(){
                @Override
                public void updateItem(Queue queue, boolean empty) {
                    super.updateItem(queue, empty);
                    if (empty || queue == null) {
                        setText(null);
                    } else {
                        setText("Name: "+queue.getName() +" ,capacity: "+ queue.getCapacity() + ", priority: "+queue.getPriority()+", agents: "+ queue.getAgents().toString());
                    }
                }
            };
        }
    }
