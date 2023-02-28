package joukl.plannerexec.plannerserver.model;

import java.util.Date;
import java.util.List;

public class Client {

    public Client(String id, Agent agent, int availableResources, ClientStatus status, Date lastReply, List<Task> workingOnTasks, List<String> subscribedQueues) {
        this.id = id;
        this.agent = agent;
        this.availableResources = availableResources;
        this.status = status;
        this.lastReply = lastReply;
        this.workingOnTasks = workingOnTasks;
        this.subscribedQueues = subscribedQueues;
    }

    private String id;
    private Agent agent;
    private int availableResources;
    private ClientStatus status;
    private Date lastReply;
    private volatile List<Task> workingOnTasks;
    private volatile List<String> subscribedQueues;

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

    public int getAvailableResources() {
        return availableResources;
    }

    public void setAvailableResources(int availableResources) {
        this.availableResources = availableResources;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public Date getLastReply() {
        return lastReply;
    }

    public void setLastReply(Date lastReply) {
        this.lastReply = lastReply;
    }

    public int getNumberOfTasks() {
        return workingOnTasks.size();
    }

    public List<Task> getWorkingOnTasks() {
        return workingOnTasks;
    }

    public void setWorkingOnTasks(List<Task> workingOnTasks) {
        this.workingOnTasks = workingOnTasks;
    }

    public List<String> getSubscribedQueues() {
        return subscribedQueues;
    }

    public void setSubscribedQueues(List<String> subscribedQueues) {
        this.subscribedQueues = subscribedQueues;
    }
}
