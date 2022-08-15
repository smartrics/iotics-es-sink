package smartrics.iotics.space.api;

import com.iotics.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import smartrics.iotics.space.Feed;
import smartrics.iotics.space.FeedPayload;

public class InterestsApi {
    private static final Logger logger = LoggerFactory.getLogger(InterestsApi.class);

    private final GrpcHost host;

    public InterestsApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<FeedPayload> fetchInterest(Feed feed, FetchInterestRequest request) {
        InterestAPIGrpc.InterestAPIStub api = InterestAPIGrpc.newStub(host.channel);
        logger.info("fetching " + feed);
        StreamObserverMapper<FetchInterestResponse, FeedPayload> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(FetchInterestResponse value) {
                FeedData feedData = value.getPayload().getFeedData();
                FeedPayload feedPayload = new FeedPayload(feed, feedData);
                observableDelegate.onNext(feedPayload);
            }
        };
        api.fetchInterests(request, mapper);
        return mapper.observableDelegate;
    }
}
