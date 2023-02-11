package joukl.plannerexec.plannerserver.model;

import java.util.*;

public class Scheduler {
    private static final Scheduler SCHEDULER= new Scheduler();

    private Scheduler(){
    }

    public static Scheduler getScheduler() {
        return SCHEDULER;
    }
    private List<Client> clients = new ArrayList<>();
    private Map<String,Queue> queueMap = new TreeMap<>();
    private Authorization authorization = new Authorization();
    //internal for
    //private PriorityQueue queuesPriority = new PriorityQueue<>(Comparator.comparingInt(Queue::getPriority));

    //TODO metody plánování

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public Map<String,Queue> getQueueMap() {
        return queueMap;
    }

    public void setQueueMap(Map<String,Queue> queueMap) {
        this.queueMap = queueMap;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }
}
