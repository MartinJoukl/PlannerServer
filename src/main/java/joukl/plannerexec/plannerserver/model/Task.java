package joukl.plannerexec.plannerserver.model;

import java.nio.file.Path;
import java.util.List;

public class Task {
    private Path pathToTransferedFile;
    private String id;
    private long cost;
    private Path pathToRunnable;
    private List<String> parameters;
    private int from;
    private int to;
    private boolean isRepeating;
    private long maxTimeInMilis;
    private int priority;
    private Queue queue;
    private TaskStatus status;
   // private

    public Path getPathToTransferedFile() {
        return pathToTransferedFile;
    }

    public void setPathToTransferedFile(Path pathToTransferedFile) {
        this.pathToTransferedFile = pathToTransferedFile;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public Path getPathToRunnable() {
        return pathToRunnable;
    }

    public void setPathToRunnable(Path pathToRunnable) {
        this.pathToRunnable = pathToRunnable;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public long getMaxTimeInMilis() {
        return maxTimeInMilis;
    }

    public void setMaxTimeInMilis(long maxTimeInMilis) {
        this.maxTimeInMilis = maxTimeInMilis;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}
