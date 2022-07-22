package smartrics.iotics.space.api;

import io.grpc.stub.StreamObserver;
import rx.subjects.PublishSubject;

public abstract class StreamObserverMapper<T, R> implements StreamObserver<T> {

    protected final PublishSubject<R> observableDelegate;

    public StreamObserverMapper() {
        this.observableDelegate = PublishSubject.create();
    }

    @Override
    public void onError(Throwable t) {
        observableDelegate.onError(t);
    }

    @Override
    public void onCompleted() {
        observableDelegate.onCompleted();
    }

}
