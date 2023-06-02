package smartrics.iotics.connector.elastic;

import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.ListenableFuture;
import com.iotics.api.DescribeTwinRequest;
import com.iotics.api.DescribeTwinResponse;
import com.iotics.api.Property;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import smartrics.iotics.space.Builders;
import smartrics.iotics.space.UriConstants;
import smartrics.iotics.space.connector.OntConstant;
import smartrics.iotics.space.connector.PrefixGenerator;
import smartrics.iotics.space.grpc.TwinDataBag;
import smartrics.iotics.space.twins.Describer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static smartrics.iotics.space.connector.PrefixGenerator.DEF_PREFIX;
import static smartrics.iotics.space.grpc.ListenableFutureAdapter.toCompletable;


public class IndexesCacheLoader extends CacheLoader<TwinDataBag, String> {

    // all indexes managed by this connector start with "iot".
    private static final String INDEX_PREFIX = "iot";
    private final PrefixGenerator prefixGenerator;
    private final Connector connector;

    public IndexesCacheLoader(Connector connector, PrefixGenerator prefixGenerator) {
        this.prefixGenerator = prefixGenerator;
        this.connector = connector;
    }

    public String load(TwinDataBag twinData) throws ExecutionException, InterruptedException {
        var result = new CompletableFuture<String>();
        twinData.optionalModelTwinID().ifPresentOrElse(modelID -> {
            if(connector.getDescribers().isEmpty()) {
                result.completeExceptionally(new IllegalStateException("no twins to describe"));
                return;
            }
            for(Describer describer: connector.getDescribers()) {
                ListenableFuture<DescribeTwinResponse> fut = describer.ioticsApi().twinAPIFutureStub()
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
                                .map(prefixGenerator::mapValueToJsonKey)
                                .toList();
                        return String.join("_", modelLabelAsString);
                    }).get();
                    result.complete(String.join("_", INDEX_PREFIX, val));
                } catch (InterruptedException e) {
                    result.completeExceptionally(new IllegalStateException("Interrupted whilst working out index prefix", e));
                } catch (ExecutionException e) {
                    if(e.getCause() instanceof StatusRuntimeException) {
                        StatusRuntimeException sre = (StatusRuntimeException) e.getCause();
                        if(sre.getStatus().getCode() == Status.Code.NOT_FOUND) {
                            List<String> parts = makeDefaultPrefix(twinData);
                            result.complete(String.join("_", parts));
                        }
                    } else {
                        result.completeExceptionally(new IllegalStateException("Unable to work index prefix from twin model", e));
                    }
                }
            }
            // makes index from model label
        }, () -> {
            // make prefix from rdf/owl types since model not present
            List<String> parts = makeDefaultPrefix(twinData);
            result.complete(String.join("_", parts));
        });
        return result.get();
    }

    @NotNull
    private List<String> makeDefaultPrefix(TwinDataBag twinData) {
        List<String> classes = twinData.properties().stream()
                .filter(property -> OntConstant.uris()
                        .contains(property.getKey())).map(Property::getUriValue)
                .sorted()
                .map(prefixGenerator::mapToPrefix)
                .toList(); // <<  combine into an hash
        List<String> parts = new ArrayList<>(classes.size() + 1);
        parts.add(INDEX_PREFIX);
        parts.addAll(classes);
        if (classes.isEmpty()) {
            parts.add(DEF_PREFIX);
        }
        return parts;
    }
}
