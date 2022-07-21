package smartrics.iotics.space.api;

import com.google.common.base.Strings;
import com.google.protobuf.StringValue;
import com.iotics.api.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;
import smartrics.iotics.space.Twin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SearchApi {

    private static final Logger logger = LoggerFactory.getLogger(SearchApi.class);

    private final GrpcHost host;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(32);

    public SearchApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<Twin> search(SearchRequest request) {
        SearchAPIGrpc.SearchAPIStub searchApi = SearchAPIGrpc.newStub(host.channel);

        PublishSubject<Twin> subject = PublishSubject.create();
        searchApi.synchronousSearch(request, new StreamObserver<>() {
            @Override
            public void onNext(SearchResponse value) {
                HostID hId = value.getPayload().getRemoteHostId();
                String sHId = null;
                if(!Strings.isNullOrEmpty(hId.getValue())) {
                    sHId = hId.getValue();
                }
                if(value.getPayload().getTwinsCount()>0) {
                    logger.info("received " + value.getPayload().getTwinsCount() + " twins from hostID=" + hId.getValue());
                }
                for (SearchResponse.TwinDetails t : value.getPayload().getTwinsList()) {
                    subject.onNext(new Twin(sHId, t));
                }
            }

            @Override
            public void onError(Throwable t) {
                subject.onError(t);
            }

            @Override
            public void onCompleted() {
                subject.onCompleted();
            }
        });
        scheduler.schedule(() -> subject.onCompleted(), request.getPayload().getExpiryTimeout().getSeconds(), TimeUnit.SECONDS);
        return subject;
    }

    public static SearchRequest aSearchRequest(Headers headers, SearchFilter filter) {
        SearchRequest.Payload.Filter.Builder b = SearchRequest.Payload.Filter.newBuilder();
        filter.text().ifPresent(s -> b.setText(StringValue.newBuilder().setValue(s).build()));
        filter.geoLocation().ifPresent(s -> b.setLocation(s));
        filter.properties().forEach(p -> b.addProperties(p));
        SearchRequest.Payload.Filter f = b.build();
        SearchRequest.Builder rb = SearchRequest.newBuilder();
        SearchRequest.Payload.Builder pb = SearchRequest.Payload.newBuilder().setFilter(f);
        filter.expiryTimeout().ifPresent(t -> pb.setExpiryTimeout(t));
        filter.responseType().ifPresent(t -> pb.setResponseType(t));
        rb.setHeaders(headers).setPayload(pb.build());
        filter.scope().ifPresent(scope -> rb.setScope(scope));
        return rb.build();
    }
}
