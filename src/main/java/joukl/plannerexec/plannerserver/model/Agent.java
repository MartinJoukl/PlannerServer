package joukl.plannerexec.plannerserver.model;

public enum Agent {
    WINDOWS("Windows"),
    LINUX("Linux");

    private final String agentName;

    Agent(String agentName) {
        this.agentName = agentName;
    }

    Agent getByAgentName(String value) {
        if (value.equalsIgnoreCase(WINDOWS.agentName)) {
            return WINDOWS;
        } else if (value.equalsIgnoreCase(LINUX.agentName)) {
            return LINUX;
        } else {
            throw new RuntimeException("Unsupported agent" + agentName);
        }
    }

    @Override
    public String toString() {
        return agentName;
    }


    public String getAgentName() {
        return agentName;
    }
}
