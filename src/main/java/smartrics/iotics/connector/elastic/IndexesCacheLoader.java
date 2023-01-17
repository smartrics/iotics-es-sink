package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.ListenableFuture;
import com.iotics.api.DescribeTwinRequest;
import com.iotics.api.DescribeTwinResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.Builders;
import smartrics.iotics.space.UriConstants;
import smartrics.iotics.space.grpc.TwinDatabag;
import smartrics.iotics.space.twins.Describer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static smartrics.iotics.connector.elastic.PrefixGenerator.*;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;


public class IndexesCacheLoader extends CacheLoader<TwinDatabag, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexesCacheLoader.class);

    private final Describer describer;

    public IndexesCacheLoader(Describer twin) {
        this.describer = twin;
    }

    public String load(TwinDatabag twinData) throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = new CompletableFuture<>();
        twinData.optionalModelTwinID().ifPresentOrElse(modelID -> {
            // makes index from model label
            ListenableFuture<DescribeTwinResponse> fut = describer.getTwinAPIFutureStub()
                    .describeTwin(DescribeTwinRequest.newBuilder()
                            .setHeaders(Builders.newHeadersBuilder(describer.getAgentIdentity().did()).build())
                    .setArgs(DescribeTwinRequest.Arguments.newBuilder().setTwinId(modelID)
                            .build()).build());
            try {
                String val = toCompletable(fut).thenApply(describeTwinResponse -> {
                    List<String> modelLabelAsString = describeTwinResponse
                            .getPayload()
                            .getResult()
                            .getPropertiesList()
                            .stream()
                            .filter(property -> property
                                    .getKey().equals(UriConstants.ON_RDFS_LABEL_PROP))
                            .map(property -> mapValueToJsonKey(property))
                            .toList();
                    return String.join("_", modelLabelAsString);
                }).get();
                result.complete(val);
            } catch (InterruptedException e) {
                Thread.interrupted();
                result.completeExceptionally(new IllegalStateException("Interrupted whilst working out index prefix", e));
            } catch (ExecutionException e) {
                result.completeExceptionally(new IllegalStateException("Unable to work index prefix from twin model", e));
            }
        }, () -> {
            // make prefix from rdf/owl types since model not present
            List<String> classes = twinData.twinDetails().getPropertiesList().stream()
                    .filter(property -> OntConstant.uris()
                            .contains(property.getKey())).map(property -> property.getUriValue())
                    .sorted()
                    .map(s -> mapToPrefix(s))
                    .toList(); // <<  combine into an hash
            if (classes.isEmpty()) {
                result.complete(DEF_PREFIX);
            } else {
                result.complete(String.join("_", classes));
            }

        });
        return result.get();
    }
}
