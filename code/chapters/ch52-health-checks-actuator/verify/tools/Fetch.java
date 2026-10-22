import java.net.HttpURLConnection;
import java.net.URI;

/** A one-line HTTP client for pods without curl (java.base only): prints "url status" or "url error: ...". */
public final class Fetch {

    public static void main(String[] args) {
        for (String url : args) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setConnectTimeout(2000);
                c.setReadTimeout(3000);
                System.out.println(url + " " + c.getResponseCode());
                c.disconnect();
            } catch (Exception e) {
                System.out.println(url + " error: " + e.getClass().getSimpleName());
            }
        }
    }
}
