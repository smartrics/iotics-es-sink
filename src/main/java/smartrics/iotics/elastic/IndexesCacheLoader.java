package smartrics.iotics.elastic;

import com.google.common.base.Strings;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.ListenableFuture;
import com.iotics.api.DescribeTwinRequest;
import com.iotics.api.DescribeTwinResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smartrics.iotics.space.Builders;
import smartrics.iotics.space.grpc.TwinData;
import smartrics.iotics.space.twins.Describer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static smartrics.iotics.elastic.PrefixGenerator.*;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;


public class IndexesCacheLoader extends CacheLoader<TwinData, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexesCacheLoader.class);

    private final Describer describer;

    public IndexesCacheLoader(Describer twin) {
        this.describer = twin;
    }

    public String load(TwinData twinData) throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = new CompletableFuture<>();
        twinData.optionalModelTwinID().ifPresentOrElse(modelID -> {
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
                                    .getKey().equals("http://www.w3.org/2000/01/rdf-schema#label"))
                            .map(property -> mapToJsonKey(property))
                            .toList();
                    return String.join("_", modelLabelAsString);
                    // description of model twin - make index prefix for this
                }).get();
                result.complete(val);
            } catch (InterruptedException e) {
                Thread.interrupted();
                result.completeExceptionally(new IllegalStateException("Interrupted whilst working out index prefix", e));
            } catch (ExecutionException e) {
                result.completeExceptionally(new IllegalStateException("Unable to work index prefix from twin model", e));
            }
        }, () -> {
            // make prefix from types ?
            // if types not avail - prefix is ""
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
        String value = result.get();
        LOGGER.info("index_prefix=", value);
        return value;
    }

    private record MappedPrefix(String prefix, String key) {

        public String value() {
            if (Strings.isNullOrEmpty(key)) {
                return prefix;
            }
            return prefix + "_" + key;
        }
    }
}
