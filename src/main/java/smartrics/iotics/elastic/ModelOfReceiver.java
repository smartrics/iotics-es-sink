package smartrics.iotics.elastic;

import com.iotics.api.TwinAPIGrpc;
import com.iotics.sdk.identity.SimpleIdentityManager;
import smartrics.iotics.space.twins.GenericModelTwin;

import java.util.concurrent.Executor;

public final class ModelOfReceiver extends GenericModelTwin {
    public ModelOfReceiver(SimpleIdentityManager sim, TwinAPIGrpc.TwinAPIFutureStub stub, Executor executor) {
        super(sim, "follower_model_keyname", stub, executor,
                "Follower Twin Model", "Follower MODEL",
                "https://data.iotics.com/ont/follower" ,"#40E0D0" /* turquoise */ );
    }
}
