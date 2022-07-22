package smartrics.iotics.space.api;

import com.iotics.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import smartrics.iotics.space.Twin;

public class TwinApi {
    private static final Logger logger = LoggerFactory.getLogger(SearchApi.class);

    private final GrpcHost host;

    public TwinApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<Twin> describeTwin(DescribeTwinRequest request) {
        TwinAPIGrpc.TwinAPIStub api = TwinAPIGrpc.newStub(host.channel);
        StreamObserverMapper<DescribeTwinResponse, Twin> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(DescribeTwinResponse value) {
                observableDelegate.onNext(mapToTwin(value.getPayload()));
            }
        };
        api.describeTwin(request, mapper);
        return mapper.observableDelegate;}

    private Twin mapToTwin(DescribeTwinResponse.Payload payload) {
        return null;
    }

    public Observable<String> makeTwin(CreateTwinRequest request) {
        TwinAPIGrpc.TwinAPIStub api = TwinAPIGrpc.newStub(host.channel);
        StreamObserverMapper<CreateTwinResponse, String> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(CreateTwinResponse value) {
                observableDelegate.onNext(value.getPayload().getTwinId().getValue());
            }
        };
        api.createTwin(request, mapper);
        return mapper.observableDelegate;
    }


}
