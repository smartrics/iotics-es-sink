package smartrics.iotics.elastic;

import com.google.common.base.Strings;
import com.iotics.api.Property;
import com.iotics.api.Uri;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class PrefixGenerator {
    public static final String DEF_PREFIX = "def";


    public static String mapToJsonKey(Property property) {
        if(!Strings.isNullOrEmpty(property.getUriValue().getValue())) {
            return removeInvalidJsonChars(property.getUriValue().getValue());
        }
        if(!Strings.isNullOrEmpty(property.getLangLiteralValue().getValue())) {
            return removeInvalidJsonChars(property.getLangLiteralValue().getValue());
        }
        if(!Strings.isNullOrEmpty(property.getLiteralValue().getValue())) {
            return removeInvalidJsonChars(property.getLiteralValue().getValue());
        }
        if(!Strings.isNullOrEmpty(property.getStringLiteralValue().getValue())) {
            return removeInvalidJsonChars(property.getStringLiteralValue().getValue());
        }
        return DEF_PREFIX;
    }

    public static String mapToPrefix(Uri urlString) {
        URI uri = URI.create(urlString.getValue());
        // remove query from uri
        URI uriWithoutQuery = null;
        try {
            uriWithoutQuery = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, uri.getFragment());
            String fragment = uriWithoutQuery.getFragment();
            String prefix = hostnameToValidKey(uriWithoutQuery.getHost());
            if (fragment != null) {
                return prefix + "_" + fragment;
            }
            URL urlWithoutQuery = uriWithoutQuery.toURL();
            String[] parts = urlWithoutQuery.getPath().split("/");
            return prefix + "_" + parts[parts.length - 1];
        } catch (URISyntaxException | MalformedURLException e) {
            return DEF_PREFIX;
        }
    }

    private static String hostnameToValidKey(String hostname) {
        String[] parts = hostname.split(".");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + "_" + parts[1];
    }


    private static String removeInvalidJsonChars(String input) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isValidJsonChar(c)) {
                output.append(c);
            }
        }
        return output.toString();
    }

    private static boolean isValidJsonChar(char c) {
        return (c == '_' || c == '$' || c == '-' || c == '+'|| (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
    }}
