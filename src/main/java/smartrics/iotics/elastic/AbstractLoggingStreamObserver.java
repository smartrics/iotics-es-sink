package smartrics.iotics.elastic;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLoggingStreamObserver<T> implements StreamObserver<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLoggingStreamObserver.class);

    @Override
    public void onError(Throwable throwable) {
        LOGGER.error("err", throwable);
    }

    @Override
    public void onCompleted() {
        LOGGER.error("completed");
    }
}
