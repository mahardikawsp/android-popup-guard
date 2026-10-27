import id.local.videopopupguard.UrlPolicy;
public class UrlPolicyTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError("URL policy failed");
    }
    public static void main(String[] args) {
        check(UrlPolicy.isHttp("https://example.com/watch?v=1"));
        check(UrlPolicy.isHttp("http://192.168.1.88:3300/video"));
        check(!UrlPolicy.isHttp("javascript:alert(1)"));
        check(!UrlPolicy.isHttp("intent://video#Intent;end"));
        check(!UrlPolicy.isHttp("file:///sdcard/test.html"));
        check(!UrlPolicy.isHttp("https://user:password@example.com"));
        check(!UrlPolicy.isHttp("https://"));
        check(!UrlPolicy.isHttp("example.com"));
        check(UrlPolicy.host("https://EXAMPLE.com/watch").equals("example.com"));
        System.out.println("PASS: HTTP/HTTPS, LAN URL, external schemes, credentials and hostname normalization.");
    }
}
