package smartrics.iotics.space.api;

import com.iotics.sdk.identity.SimpleIdentity;
import io.grpc.*;
import smartrics.iotics.space.api.identity.IdManager;
import smartrics.iotics.space.conf.Identities;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class TokenInjectorClientInterceptor implements ClientInterceptor {

    private final Timer timer;
    private String validToken;

    public TokenInjectorClientInterceptor(IdManager idManager, Duration duration) {
        this.timer = new Timer();
        this.validToken = "";
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // gets a token for thisI am getting an user

                // a token is used to auth this agent and user - the token has a validity. The longer the validity
                // the lower the security - if token is stolen the thief can impersonate
                validToken = idManager.CreateAuthToken(duration);
            }
        }, 0, duration.toMillis() - 100);
    }

    public String currentToken() {
        return validToken;
    }

    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new HeaderAttachingClientCall(next.newCall(method, callOptions));
    }

    public void cancel() {
        this.timer.cancel();
    }

    private final class HeaderAttachingClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
        HeaderAttachingClientCall(ClientCall<ReqT, RespT> call) {
            super(call);
        }

        public void start(Listener<RespT> responseListener, Metadata headers) {
            // Store the token in the gRPC stub
            Metadata.Key<String> AUTHORIZATION_KEY = Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER);
            Metadata metadata = new Metadata();
            metadata.put(AUTHORIZATION_KEY, "bearer " + TokenInjectorClientInterceptor.this.validToken);
            headers.merge(metadata);
            super.start(responseListener, headers);
        }
    }
}
