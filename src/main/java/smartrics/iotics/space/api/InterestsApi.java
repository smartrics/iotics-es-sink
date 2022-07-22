package smartrics.iotics.space.api;

import com.iotics.api.FeedData;
import com.iotics.api.FetchInterestRequest;
import com.iotics.api.FetchInterestResponse;
import com.iotics.api.InterestAPIGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class InterestsApi {
    private static final Logger logger = LoggerFactory.getLogger(SearchApi.class);

    private final GrpcHost host;

    public InterestsApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<FeedData> fetchInterest(FetchInterestRequest request) {
        InterestAPIGrpc.InterestAPIStub api = InterestAPIGrpc.newStub(host.channel);
        StreamObserverMapper<FetchInterestResponse, FeedData> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(FetchInterestResponse value) {
                observableDelegate.onNext(value.getPayload().getFeedData());
            }
        };
        api.fetchInterests(request, mapper);
        return mapper.observableDelegate;
    }
}
