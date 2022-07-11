package smartrics.iotics.space;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iotics.sdk.identity.Identity;
import com.iotics.sdk.identity.SimpleIdentity;
import com.iotics.sdk.identity.experimental.ResolverClient;
import smartrics.iotics.space.conf.IdentityData;

import java.io.IOException;
import java.util.Iterator;

public class IdManager {
    private static String myAuthDelegationName = "#auth-delegation-0";

    private final ResolverClient client;
    private final SimpleIdentity simpleIdentity;
    private final IdentityData agentIdentityData;
    private final IdentityData userIdentityData;
    private Identity userIdentity;
    private Identity agentIdentity;
    private JsonObject userDocJson;
    private JsonObject agentDocJson;

    public IdManager(ResolverClient client,
                     IdentityData userIdentityData,
                     IdentityData agentIdentityData,
                     SimpleIdentity simpleIdentity) {
        this.client = client;
        this.userIdentityData = userIdentityData;
        this.agentIdentityData = agentIdentityData;
        this.simpleIdentity = simpleIdentity;

        try {
            setup();
        } catch (IOException e) {
            throw new IllegalStateException("unable to setup identities", e);
        }
    }

    public Identity agentIdentity() {
        return agentIdentity;
    }

    public Identity userIdentity() {
        return userIdentity;
    }

    private void setup() throws IOException {
        Gson gson = new Gson();

        this.userIdentity = this.simpleIdentity.CreateUserIdentity(userIdentityData.key(), userIdentityData.name());
        ResolverClient.Result userDoc = this.client.discover(this.userIdentity.did());
        this.userDocJson = gson.fromJson(userDoc.content(), JsonElement.class).getAsJsonObject();

        this.agentIdentity = this.simpleIdentity.CreateAgentIdentity(agentIdentityData.key(), agentIdentityData.name());
        ResolverClient.Result agentDoc = this.client.discover(this.agentIdentity.did());
        this.agentDocJson = gson.fromJson(agentDoc.content(), JsonElement.class).getAsJsonObject();

        JsonArray delegations = userDocJson.getAsJsonObject("doc").getAsJsonArray("delegateAuthentication");
        Iterator<JsonElement> iter = delegations.iterator();
        boolean found = false;
        while(iter.hasNext()) {
            JsonElement delegation = iter.next();
            String delegationName = delegation.getAsJsonObject().getAsJsonPrimitive("id").getAsString();
            if(myAuthDelegationName.equals(delegationName)) {
                found = true;
                break;
            }
        }
        if(!found) {
            this.simpleIdentity.UserDelegatesAuthenticationToAgent(agentIdentity, userIdentity, myAuthDelegationName);
        }

    }

}
