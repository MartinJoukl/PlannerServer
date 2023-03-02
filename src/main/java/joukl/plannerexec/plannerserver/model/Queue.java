package joukl.plannerexec.plannerserver.model;

import java.util.*;

public class Queue {
    public static final Comparator<Task> TASK_COMPARATOR = Comparator.comparingInt(Task::getPriority).reversed();
    private String name;
    private List<Agent> agents;
    //TODO vyřešit kapacitu
    private int capacity;
    //Queue for running tasks
    private PriorityQueue<Task> tasks = new PriorityQueue<>(TASK_COMPARATOR);
    private List<Task> nonScheduledTasks = Collections.synchronizedList(new LinkedList<>());
    private int priority;

    public Queue(String name, List<Agent> agents,
                 int capacity,
                 int priority) {
        this.name = name;
        this.agents = agents;
        this.capacity = capacity;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public void setAgents(List<Agent> agents) {
        this.agents = agents;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public PriorityQueue<Task> getTasks() {
        return tasks;
    }

    public void setTasks(PriorityQueue<Task> tasks) {
        this.tasks = tasks;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<Task> getNonScheduledTasks() {
        return nonScheduledTasks;
    }

    public void setNonScheduledTasks(List<Task> nonScheduledTasks) {
        this.nonScheduledTasks = nonScheduledTasks;
    }
}
