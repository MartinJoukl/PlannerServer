package joukl.plannerexec.plannerserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class Queue {
    @JsonIgnore
    public static final Comparator<Task> TASK_COMPARATOR = Comparator.comparingInt(Task::getPriority).reversed();
    private String name;
    private List<Agent> agents;
    //TODO vyřešit kapacitu - je na FE jenom, to je špatně
    private int capacity;
    private PlanningMode planningMode = PlanningMode.PRIORITY_QUEUE;
    //Queue for running tasks
    @JsonIgnore
    private PriorityQueue<Task> taskPriorityQueue = new PriorityQueue<>(TASK_COMPARATOR);
    @JsonIgnore
    private List<Task> taskFIFO = Collections.synchronizedList(new LinkedList<>());
    @JsonIgnore
    private List<Task> nonScheduledTasks = Collections.synchronizedList(new LinkedList<>());
    private int priority;

    public Queue(String name, List<Agent> agents,
                 int capacity,
                 int priority, PlanningMode planningMode) {
        this.name = name;
        this.agents = agents;
        this.capacity = capacity;
        this.priority = priority;
        this.planningMode = planningMode;
    }

    public Queue() {
    }

    public PlanningMode getPlanningMode() {
        return planningMode;
    }

    public void setPlanningMode(PlanningMode planningMode) {
        this.planningMode = planningMode;
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

    public PriorityQueue<Task> getTaskPriorityQueue() {
        return taskPriorityQueue;
    }

    public Collection<Task> getTaskSchedulingQueue() {
        if (planningMode == PlanningMode.PRIORITY_QUEUE) {
            return taskPriorityQueue;
        } else {
            return taskFIFO;
        }
    }

    public void setTaskPriorityQueue(PriorityQueue<Task> taskPriorityQueue) {
        this.taskPriorityQueue = taskPriorityQueue;
    }

    public List<Task> getTaskFIFO() {
        return taskFIFO;
    }

    public void setTaskFIFO(List<Task> taskFIFO) {
        this.taskFIFO = taskFIFO;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    //contains running and uploading tasks only
    public List<Task> getNonScheduledTasks() {
        return nonScheduledTasks;
    }

    public void setNonScheduledTasks(List<Task> nonScheduledTasks) {
        this.nonScheduledTasks = nonScheduledTasks;
    }
}
