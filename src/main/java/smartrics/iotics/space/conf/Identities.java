package smartrics.iotics.space.conf;

public class Identities {
    private String userSeedFile;
    private IdentityData user;
    private String agentSeedFile;
    private IdentityData agent;

    public IdentityData user() {
        return user;
    }

    public void setUser(IdentityData user) {
        this.user = user;
    }

    public IdentityData agent() {
        return agent;
    }

    public void setAgent(IdentityData agent) {
        this.agent = agent;
    }

    public String userSeedFile() {
        return userSeedFile;
    }

    public void setUserSeedFile(String userSeedFile) {
        this.userSeedFile = userSeedFile;
    }

    public String agentSeedFile() {
        return agentSeedFile;
    }

    public void setAgentSeedFile(String agentSeedFile) {
        this.agentSeedFile = agentSeedFile;
    }

    @Override
    public String toString() {
        return "Identities{" +
                "userSeedFile='" + userSeedFile + '\'' +
                ", user=" + user +
                ", agentSeedFile='" + agentSeedFile + '\'' +
                ", agent=" + agent +
                '}';
    }
}
