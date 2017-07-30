package pl.jojczykp.rabbitmq_msg.authservice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

class ExpiryProcessor {

    private static final String RABBIT_HOST = "rabbitmq";
    private static final int RABBIT_PORT = 15672;
    private static final String RABBIT_USER = "admin";
    private static final String RABBIT_PASSWORD = "admin";

    private static final int CLIENT_CONNECTION_TIMEOUT_MS = 60 * 1000;

    // TODO - Use this to decide which connections must be closed
    // TODO - Do check more frequently
    private final ConcurrentHashMap<String, Long> instanceKeyToExpiryTimestamp;

    ExpiryProcessor(ConcurrentHashMap<String, Long> instanceKeyToExpiryTimestamp) {
        this.instanceKeyToExpiryTimestamp = instanceKeyToExpiryTimestamp;
    }

    void start() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    closeExpiredConnections();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, CLIENT_CONNECTION_TIMEOUT_MS, CLIENT_CONNECTION_TIMEOUT_MS);
    }

    private void closeExpiredConnections() throws IOException {
        String connectionsJson = listConnections();
        JSONArray connections = new JSONArray(connectionsJson);

        for (Object connection : connections) {
            closeConnectionIfApplicable((JSONObject) connection);
        }
    }

    private String listConnections() throws IOException {
        HttpURLConnection httpConnection = makeRabbitHttpCall("GET", "?columns=name,user");

        if (httpConnection.getResponseCode() != 200) {
            System.err.println(String.format("Wrong listing response code: %d [%s]",
                    httpConnection.getResponseCode(), httpConnection.getResponseMessage()));
        }

        return getResponseBody(httpConnection);
    }

    private String getResponseBody(HttpURLConnection con) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        return response.toString();
    }

    private void closeConnectionIfApplicable(JSONObject connection) throws IOException {
        String name = connection.getString("name");
        String urlEncodedName = URLEncoder.encode(name, "UTF-8");

        HttpURLConnection httpConnection = makeRabbitHttpCall("DELETE", "/" + urlEncodedName);

        System.out.println(String.format("%s - %d [%s]",
                name, httpConnection.getResponseCode(), httpConnection.getResponseMessage()));
    }

    private HttpURLConnection makeRabbitHttpCall(String method, String suffix) throws IOException {
        String connectionUrlStr = String.format("http://%s:%d/api/connections%s", RABBIT_HOST, RABBIT_PORT, suffix);

        URL connectionUrl = new URL(connectionUrlStr);
        HttpURLConnection httpConnection = (HttpURLConnection) connectionUrl.openConnection();
        httpConnection.setRequestProperty("Authorization", "Basic " + base64Encode(RABBIT_USER + ":" + RABBIT_PASSWORD));
        httpConnection.setRequestMethod(method);

        return httpConnection;
    }

    private String base64Encode(String string) {
        return new String(Base64.getEncoder().encode(string.getBytes()));
    }
}