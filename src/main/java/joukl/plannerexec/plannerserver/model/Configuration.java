package joukl.plannerexec.plannerserver.model;

import java.util.List;

public class Configuration {
    public Configuration() {
    }

    private List<Queue> queues;

    private int port;

    private long clientTimeoutDeadline;
    private long clientNoResponseTime;
    private long taskTimeoutDelay;

    public List<Queue> getQueues() {
        return queues;
    }

    public void setQueues(List<Queue> queues) {
        this.queues = queues;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getClientTimeoutDeadline() {
        return clientTimeoutDeadline;
    }

    public void setClientTimeoutDeadline(long clientTimeoutDeadline) {
        this.clientTimeoutDeadline = clientTimeoutDeadline;
    }

    public long getClientNoResponseTime() {
        return clientNoResponseTime;
    }

    public void setClientNoResponseTime(long clientNoResponseTime) {
        this.clientNoResponseTime = clientNoResponseTime;
    }

    public long getTaskTimeoutDelay() {
        return taskTimeoutDelay;
    }

    public void setTaskTimeoutDelay(long taskTimeoutDelay) {
        this.taskTimeoutDelay = taskTimeoutDelay;
    }
}
