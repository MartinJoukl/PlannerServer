package joukl.plannerexec.plannerserver.model;

public enum KeyType {
    SERVER_PUBLIC("serverPublic"),
    SERVER_PRIVATE("serverPrivate"),
    CLIENT_PUBLIC("clientPublic");

    private String keyName;


    private KeyType(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}
