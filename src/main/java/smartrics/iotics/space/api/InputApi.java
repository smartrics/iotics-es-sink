package smartrics.iotics.space.api;

import com.iotics.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import smartrics.iotics.space.Input;
import smartrics.iotics.space.Twin;

import java.util.List;

public class InputApi {

    private static final Logger logger = LoggerFactory.getLogger(InputApi.class);

    private final GrpcHost host;

    public InputApi(GrpcHost host) {
        this.host = host;
    }

    public Observable<Input> describeInput(Twin parent, DescribeInputRequest request) {
        InputAPIGrpc.InputAPIStub feedApi = InputAPIGrpc.newStub(host.channel);
        StreamObserverMapper<DescribeInputResponse, Input> mapper = new StreamObserverMapper<>() {
            @Override
            public void onNext(DescribeInputResponse value) {
                observableDelegate.onNext(new Input(parent, value));
            }
        };
        feedApi.describeInput(request, mapper);
        return mapper.observableDelegate;
    }

}
