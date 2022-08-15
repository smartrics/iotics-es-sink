package smartrics.iotics.elastic;

import com.iotics.api.GeoCircle;
import com.iotics.api.GeoLocation;
import com.iotics.api.Scope;
import com.iotics.sdk.identity.Identity;
import com.iotics.sdk.identity.SimpleIdentity;
import com.iotics.sdk.identity.experimental.ResolverClient;
import com.iotics.sdk.identity.jna.JnaSdkApiInitialiser;
import com.iotics.sdk.identity.jna.SdkApi;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import smartrics.iotics.space.Feed;
import smartrics.iotics.space.Twin;
import smartrics.iotics.space.api.GrpcHost;
import smartrics.iotics.space.api.InterestsApi;
import smartrics.iotics.space.api.SearchApi;
import smartrics.iotics.space.api.SearchFilter;
import smartrics.iotics.space.api.identity.IdManager;
import smartrics.iotics.space.SpaceData;
import smartrics.iotics.space.conf.Configuration;
import smartrics.iotics.space.conf.IdentityData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        Configuration configuration = newConfiguration(args);
        SpaceData spaceData = new SpaceData(configuration.space(), new SpaceData.Loader(new OkHttpClient()));
        logger.info("Loaded space data: " + spaceData);

        SimpleIdentity simpleIdentity = newSimpleIdentity(configuration, spaceData);
        IdManager idManager = newIdManager(configuration, spaceData, simpleIdentity);
        GrpcHost host = new GrpcHost(spaceData, idManager);

        InterestsApi interestsApi = new InterestsApi(host);

        SearchApi searchApi = new SearchApi(host);
        GeoCircle LONDON = GeoCircle.newBuilder()
                .setRadiusKm(25)
                .setLocation(GeoLocation.newBuilder()
                        .setLat(51.509865)
                        .setLon(-0.118092)
                        .build())
                .build();

        SearchFilter f = SearchFilter.Builder.aSearchFilter()
                .withScope(Scope.GLOBAL)
                .withLocation(LONDON)
                .build();


        Follower follower = new Follower(interestsApi, makeFollowerIdentity(configuration, simpleIdentity, idManager));

        Observable<Twin> obs = searchApi.search(SearchApi.aSearchRequest(host.newHeaders(), f));
        obs.flatMap((Func1<Twin, Observable<Feed>>) twin -> Observable.from(twin.feeds())).subscribe(feed -> {
            follower.follow(host.newHeaders(), feed);
        });


    }

    private static Identity makeFollowerIdentity(Configuration configuration, SimpleIdentity simpleIdentity, IdManager idManager) {
        IdentityData fId = configuration.identities().follower();
        return simpleIdentity.CreateTwinIdentityWithControlDelegation(idManager.agentIdentity(), fId.key(), fId.name());
    }

    @NotNull
    private static SimpleIdentity newSimpleIdentity(Configuration configuration, SpaceData spaceData) throws IOException {
        SdkApi idSkdApi = new JnaSdkApiInitialiser().get();
        SimpleIdentity simpleIdentity = new SimpleIdentity(idSkdApi,
                spaceData.resolverUrl().toString(),
                Files.readString(Path.of(configuration.identities().userSeedFile())),
                Files.readString(Path.of(configuration.identities().agentSeedFile()))
        );
        return simpleIdentity;
    }

    @NotNull
    private static IdManager newIdManager(Configuration configuration, SpaceData spaceData, SimpleIdentity simpleIdentity) {
        ResolverClient resolverClient = new ResolverClient(spaceData.resolverUrl());
        IdManager idManager = new IdManager(resolverClient,
                configuration.identities().user(),
                configuration.identities().agent(),
                simpleIdentity);
        logger.info("user: " + idManager.userIdentity());
        logger.info("agent: " + idManager.agentIdentity());
        return idManager;
    }

    private static Configuration newConfiguration(String[] args) {
        String configFile = "./config.yaml";
        if (args.length == 1) {
            configFile = args[0];
        }
        Configuration configuration = Configuration.NewConfiguration(configFile);
        logger.info("Config loaded!");
        return configuration;
    }
}
