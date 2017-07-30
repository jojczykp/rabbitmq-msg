package pl.jojczykp.rabbitmq_msg.authservice;

import javax.json.Json;
import javax.json.stream.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import static javax.json.stream.JsonParser.Event.END_ARRAY;
import static javax.json.stream.JsonParser.Event.END_OBJECT;


class ExpiryProcessor {

    private static final String MANAGEMENT_HOST = "rabbitmq";
    private static final int MANAGEMENT_PORT = 15672;
    private static final String MANAGEMENT_USER = "admin";
    private static final String MANAGEMENT_PASSWORD = "admin";

    private static final int TIMESTAMPS_CHECK_PERIOD_MILLIS = 10 * 1000;

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
        }, TIMESTAMPS_CHECK_PERIOD_MILLIS, TIMESTAMPS_CHECK_PERIOD_MILLIS);
    }

    private void closeExpiredConnections() throws IOException {
        long currentTimestamp = System.currentTimeMillis();

        int before = instanceKeyToExpiryTimestamp.size();

        for (ClientConnection connection : getActiveConnections()) {
            closeConnectionIfExpired(connection, currentTimestamp);
        }

        removeExpiredEntries(currentTimestamp);

        int after = instanceKeyToExpiryTimestamp.size();

        System.out.println(String.format("Attempted to close expired connections: was %d, left %d", before, after));
    }

    private Iterable<ClientConnection> getActiveConnections() throws IOException {
        HttpURLConnection managementConnection = makeManagementCall("GET", "?columns=name,user");

        if (managementConnection.getResponseCode() != 200) {
            System.err.println(String.format("Wrong listing response code: %d [%s]",
                    managementConnection.getResponseCode(), managementConnection.getResponseMessage()));
        }

        InputStream is = managementConnection.getInputStream();
        return () -> new ClientConnectionsIterator(is);
    }

    private void closeConnectionIfExpired(ClientConnection clientConnection, long currentTimestamp) throws IOException {
        if (isUnknown(clientConnection) || isExpired(clientConnection, currentTimestamp)) {
            String urlEncodedName = URLEncoder.encode(clientConnection.name, "UTF-8");
            HttpURLConnection managementConnection = makeManagementCall("DELETE", "/" + urlEncodedName);

            System.out.println(String.format("%s - %d [%s]",
                    clientConnection.name, managementConnection.getResponseCode(), managementConnection.getResponseMessage()));
        }
    }

    private boolean isUnknown(ClientConnection clientConnection) {
        return !instanceKeyToExpiryTimestamp.containsKey(clientConnection.user);
    }

    private boolean isExpired(ClientConnection clientConnection, long currentTimestamp) {
        return instanceKeyToExpiryTimestamp.get(clientConnection.user) < currentTimestamp;
    }

    private void removeExpiredEntries(long currentTimestamp) {
        for (Map.Entry<String, Long> entry : instanceKeyToExpiryTimestamp.entrySet()) {
            Long entryTimestamp = entry.getValue();
            if (entryTimestamp < currentTimestamp) {
                System.out.println("Removing expired entry: " + entry.getKey());
                instanceKeyToExpiryTimestamp.remove(entry.getKey());
            }
        }
    }

    private HttpURLConnection makeManagementCall(String method, String suffix) throws IOException {
        String connectionUrlStr = String.format("http://%s:%d/api/connections%s", MANAGEMENT_HOST, MANAGEMENT_PORT, suffix);

        URL connectionUrl = new URL(connectionUrlStr);
        HttpURLConnection httpConnection = (HttpURLConnection) connectionUrl.openConnection();
        httpConnection.setRequestProperty("Authorization", "Basic " + base64Encode(MANAGEMENT_USER + ":" + MANAGEMENT_PASSWORD));
        httpConnection.setRequestMethod(method);

        return httpConnection;
    }

    private String base64Encode(String string) {
        return new String(Base64.getEncoder().encode(string.getBytes()));
    }

    private class ClientConnectionsIterator implements Iterator<ClientConnection> {
        private final JsonParser parser;
        private ClientConnection next;

        ClientConnectionsIterator(InputStream inputStream) {
            this.parser = Json.createParser(new BufferedReader(new InputStreamReader(inputStream)));
            this.next = fetchNextOrNull();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public ClientConnection next() {
            ClientConnection oldNext = next;
            next = fetchNextOrNull();

            return oldNext;
        }

        private ClientConnection fetchNextOrNull() {
            ClientConnection clientConnection = null;
            JsonParser.Event next;

            do {
                next = parser.next();
                switch (next) {
                    case START_ARRAY:
                        break;
                    case START_OBJECT:
                        clientConnection = new ClientConnection();
                        break;
                    case KEY_NAME:
                        String key = parser.getString();
                        parser.next();
                        switch (key) {
                            case "user":
                                clientConnection.user = parser.getString();
                                break;
                            case "name":
                                clientConnection.name = parser.getString();
                                break;
                        }
                        break;
                    case END_ARRAY:
                        parser.close();
                        break;
                }
            } while ((next != END_OBJECT) && (next != END_ARRAY));

            return clientConnection;
        }
    }

    private class ClientConnection {
        String user;
        String name;
    }

}