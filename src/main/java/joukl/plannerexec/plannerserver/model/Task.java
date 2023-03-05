package joukl.plannerexec.plannerserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Task {
    private String id;
    private int cost;
    private String name;
    @JsonIgnore
    private Client client;
    @JsonIgnore
    private Date startRunningTime;

    @JsonIgnore
    private Date timeoutDeadline;
    private String executePath;
    private String commandToExecute;
    private List<String> pathToResults;
    private List<String> parameters;
    @JsonIgnore
    private String pathToSourceDirectory;
    @JsonIgnore
    private String pathToZipFile;
    private Integer from;

    private Integer to;

    private List<String> parametrizedValues;
    private boolean isRepeating;
    @JsonProperty("timeout")
    private long timeoutInMillis;
    private int priority;
    private Queue queue;
    private TaskStatus status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public List<String> getParametrizedValues() {
        return parametrizedValues;
    }

    public void setParametrizedValues(List<String> parametrizedValues) {
        this.parametrizedValues = parametrizedValues;
    }

    public long getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public void setTimeoutInMillis(long timeoutInMillis) {
        this.timeoutInMillis = timeoutInMillis;
    }

    public int getPriority() {
        return priority;
    }

    public String getPathToSourceDirectory() {
        return pathToSourceDirectory;
    }

    public void setPathToSourceDirectory(String pathToSourceDirectory) {
        this.pathToSourceDirectory = pathToSourceDirectory;
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

    public String getCommandToExecute() {
        return commandToExecute;
    }

    public void setCommandToExecute(String commandToExecute) {
        this.commandToExecute = commandToExecute;
    }

    public List<String> getPathToResults() {
        return pathToResults;
    }

    public void setPathToResults(List<String> pathToResults) {
        this.pathToResults = pathToResults;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPathToZipFile() {
        return pathToZipFile;
    }

    public void setPathToZipFile(String pathToZipFile) {
        this.pathToZipFile = pathToZipFile;
    }

    public String getExecutePath() {
        return executePath;
    }

    public void setExecutePath(String executePath) {
        this.executePath = executePath;
    }

    public Date getStartRunningTime() {
        return startRunningTime;
    }

    public void setStartRunningTime(Date startRunningTime) {
        this.startRunningTime = startRunningTime;
    }

    public Date getTimeoutDeadline() {
        return timeoutDeadline;
    }

    public void setTimeoutDeadline(Date timeoutDeadline) {
        this.timeoutDeadline = timeoutDeadline;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Task(@JsonProperty("cost") int cost, @JsonProperty("name") String name, @JsonProperty("commandToExecute") String commandToExecute,
                @JsonProperty("pathToResults") List<String> pathToResults, @JsonProperty("timeout") long timeoutInMillis,
                @JsonProperty("priority") int priority, @JsonProperty("queue") String queueName, @JsonProperty("executePath") String executePath,
                @JsonProperty("parametrizedFrom") Integer parametrizedFrom, @JsonProperty("parametrizedTo") Integer parametrizedTo,
                @JsonProperty("parametrizedValues") List<String> parametrizedValues) {
        this.cost = cost;
        this.name = name;
        this.commandToExecute = commandToExecute;
        this.pathToResults = pathToResults;
        this.timeoutInMillis = timeoutInMillis;
        this.priority = priority;
        this.queue = Scheduler.getScheduler().getQueueMap().get(queueName);
        this.executePath = executePath;
        this.from = parametrizedFrom;
        this.to = parametrizedTo;
        this.parametrizedValues = parametrizedValues;

        this.id = UUID.randomUUID().toString();

        this.status = TaskStatus.UPLOADING;
    }

    public static boolean validateCorrectParametrization(Task task) {

        if (task.from == null && task.to == null && (task.parametrizedValues == null || task.parametrizedValues.isEmpty())) {
            // validation is correct if task doesn't contain parametrized value
            return !task.getParameters().contains("%%");
        }
        //we need to check that parameters are present at max only once
        if (task.getParameters().stream().filter((p) -> p.equals("%%")).count() > 1) {
            return false;
        }
        //NOTE - we don't check for existence of %% because we will run process X times based on params - we will just not fill in any params
        //else we need to check that correct parameters are filled
        // we have task from, we have to have task to and parametrized values can't be filled
        if (task.from != null) {
            return task.to != null && (task.parametrizedValues == null || task.parametrizedValues.isEmpty());
        } else {
            //else we have to have the parametrized values filled, but task has to be null
            return task.to == null && task.parametrizedValues != null && !task.parametrizedValues.isEmpty();
        }
    }
}
