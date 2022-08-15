package smartrics.iotics.space.api;

import com.iotics.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import smartrics.iotics.space.Feed;
import smartrics.iotics.space.Twin;

import java.util.List;

public class FeedApi {

    private static final Logger logger = LoggerFactory.getLogger(FeedApi.class);

    private final GrpcHost host;

    public FeedApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<Feed> describeFeed(Twin parent, DescribeFeedRequest request) {
        FeedAPIGrpc.FeedAPIStub feedApi = FeedAPIGrpc.newStub(host.channel);
        StreamObserverMapper<DescribeFeedResponse, Feed> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(DescribeFeedResponse value) {
                observableDelegate.onNext(new Feed(parent, value));
            }
        };
        feedApi.describeFeed(request, mapper);
        return mapper.observableDelegate;
    }

}
