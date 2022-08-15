package smartrics.iotics.elastic;

import com.google.protobuf.BoolValue;
import com.iotics.api.*;
import com.iotics.sdk.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;
import smartrics.iotics.space.Feed;
import smartrics.iotics.space.FeedPayload;
import smartrics.iotics.space.api.InterestsApi;

public class Follower {
    private static final Logger logger = LoggerFactory.getLogger(Follower.class);

    private final Identity followerIdentity;
    private final InterestsApi interestsApi;
    private final PublishSubject<FeedPayload> publisher;

    public Follower(InterestsApi interestsApi, Identity followerIdentity) {
        this.followerIdentity = followerIdentity;
        this.interestsApi = interestsApi;
        this.publisher = PublishSubject.create();
    }

    public void follow(Headers headers, Feed feed) {
        FetchInterestRequest request = FetchInterestRequest.newBuilder()
                .setHeaders(headers)
                .setFetchLastStored(BoolValue.newBuilder().setValue(true).build())
                .setArgs(FetchInterestRequest.Arguments.newBuilder()
                        .setInterest(Interest.newBuilder()
                                .setFollowerTwinId(TwinID.newBuilder()
                                        .setValue(this.followerIdentity.did()))
                                .setFollowedFeed(Interest.FollowedFeed.newBuilder()
                                        .setFeed(com.iotics.api.Feed.newBuilder()
                                                .setId(FeedID.newBuilder().setValue(feed.id()).build())
                                                .setTwinId(TwinID.newBuilder().setValue(feed.parent().id()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();
        Observable<FeedPayload> obs = this.interestsApi.fetchInterest(feed, request);
        obs.doOnError(throwable -> {
            Interest.FollowedFeed f = request.getArgs().getInterest().getFollowedFeed();
            logger.error("error occurred for interest " + f, throwable);
        }).doOnEach(n -> logger.info("" + n.getValue())).subscribe(publisher);
    }

}
