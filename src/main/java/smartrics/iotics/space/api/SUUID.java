package smartrics.iotics.space.api;

import org.bitcoinj.core.Base58;

import java.util.concurrent.ThreadLocalRandom;

public final class SUUID {

    public static String New() {
        try {
            final byte[] randomBytes = new byte[10];
            ThreadLocalRandom.current().nextBytes(randomBytes);
            return Base58.encode(randomBytes);
        } catch (Exception e) {
            throw new IllegalStateException("unable to init secure random", e);
        }
    }

}
