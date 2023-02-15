package joukl.plannerexec.plannerserver.model;

import java.security.Timestamp;
import java.util.List;
public class Client {

    public Client(String id, Agent agent, long resources, ClientStatus status, Timestamp lastReply, int numberOfTasks, List<Task> workingOnTasks, Queue queue) {
        this.id = id;
        this.agent = agent;
        this.resources = resources;
        this.status = status;
        this.lastReply = lastReply;
        this.numberOfTasks = numberOfTasks;
        this.workingOnTasks = workingOnTasks;
        this.queue = queue;
    }

    private String id;
    private Agent agent;
    private long resources;
    private ClientStatus status;
    private Timestamp lastReply;
    private int numberOfTasks;
    private List<Task> workingOnTasks;
    private Queue queue;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public long getResources() {
        return resources;
    }

    public void setResources(long resources) {
        this.resources = resources;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public Timestamp getLastReply() {
        return lastReply;
    }

    public void setLastReply(Timestamp lastReply) {
        this.lastReply = lastReply;
    }

    public int getNumberOfTasks() {
        return numberOfTasks;
    }

    public void setNumberOfTasks(int numberOfTasks) {
        this.numberOfTasks = numberOfTasks;
    }

    public List<Task> getWorkingOnTasks() {
        return workingOnTasks;
    }

    public void setWorkingOnTasks(List<Task> workingOnTasks) {
        this.workingOnTasks = workingOnTasks;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }
}
