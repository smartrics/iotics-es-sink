package smartrics.iotics.space.conf;


public class IdentityData {
    private String key;
    private String name;

    public String key() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Identity{" +
                "key='" + key + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
