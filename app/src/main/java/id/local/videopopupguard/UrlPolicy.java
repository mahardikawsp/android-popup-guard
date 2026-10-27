package id.local.videopopupguard;

import java.net.URI;
import java.util.Locale;

public final class UrlPolicy {
    private UrlPolicy() {}
    public static boolean isHttp(String input) {
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme();
            return scheme != null && ("https".equalsIgnoreCase(scheme) ||
                "http".equalsIgnoreCase(scheme)) && uri.getHost() != null &&
                uri.getRawUserInfo() == null;
        } catch (Exception error) { return false; }
    }
    public static String host(String input) {
        try { return new URI(input).getHost().toLowerCase(Locale.ROOT); }
        catch (Exception error) { return ""; }
    }
    public static String ensureHttps(String input) {
        if (input == null) return "";
        input = input.trim();
        if (input.isEmpty()) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.startsWith("//")) {
            return "https:" + input;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "https://" + input;
        }
        return input;
    }
}
