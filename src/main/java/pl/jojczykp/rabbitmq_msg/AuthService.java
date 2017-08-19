package pl.jojczykp.rabbitmq_msg;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;

import static java.net.URLDecoder.decode;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

class AuthService implements HttpHandler {

    private static final int LISTEN_PORT = 8000;
    private static final String LISTEN_URL_PREFIX = "/auth/";

    private static final String VHOST = "/";
    private static final String EXCHANGE_NAME = "exchange.direct";

    private static final Charset BODY_CHARSET = Charsets.ISO_8859_1;

    public static void main(String[] args) throws IOException {
        new AuthService().start();
    }

    void start() throws IOException {
        InetAddress localHost = Inet4Address.getByName("0.0.0.0");
        InetSocketAddress inetSocketAddress = new InetSocketAddress(localHost, LISTEN_PORT);

        System.out.println("Waiting on " + inetSocketAddress + ", urls: " + LISTEN_URL_PREFIX + "*");

        HttpServer server = HttpServer.create(inetSocketAddress, 0);

        server.createContext(LISTEN_URL_PREFIX + "user", this);
        server.createContext(LISTEN_URL_PREFIX + "vhost", this);
        server.createContext(LISTEN_URL_PREFIX + "resource", this);
        server.createContext(LISTEN_URL_PREFIX + "topic", this);

        server.start();
    }

    @Override
    public void handle(HttpExchange httpExchange) {
        String msg = httpExchange.getRequestMethod() + " " + httpExchange.getRequestURI();
        try {
            Map<String, String> params = getParams(httpExchange);
            msg += " " + params;

            String entityType = substringAfterLast(httpExchange.getRequestURI().getPath(), '/');
            String response = handle(entityType, params) ? "allow" : "deny";
            msg += " " + response;

            sendResponse(httpExchange, response);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println(msg);
        }
    }

    private boolean handle(String entityType, Map<String, String> params) {
        String userDataStr = params.get("username");
        String[] userData = userDataStr.split(",", 2);

        if (userData.length < 2) {
            System.out.println(String.format("Wrong User Data: %s", userDataStr));
            return false;
        }

        String instanceId = userData[0];
        String authTokenStr = base64Decode(userData[1]);
        String[] authToken = authTokenStr.split(" ");
        String authTokenMethod = authToken[0];

        if (!"Bearer".equals(authTokenMethod)) {
            System.out.println(String.format("%s: Wrong Auth Method: %s", authTokenStr, authTokenMethod));
            return false;
        }

        String authTokenDataStr = authToken[1];
        String[] authTokenData = authTokenDataStr.split(",");

        if (authTokenData.length < 4) {
            System.out.println(String.format("%s: Wrong Auth Token: %s", authTokenStr, authTokenDataStr));
            return false;
        }

        String actualChecksumStr = authTokenData[3];
        Long actualChecksum = parseLongOrNull(actualChecksumStr);

        if (actualChecksum == null || actualChecksum != checksum(substringBeforeLast(authTokenDataStr, ','))) {
            System.out.println(String.format("%s: Wrong Auth Checksum: %s", authTokenStr, actualChecksumStr));
            return false;
        }

        String clientId = authTokenData[0];
        String userId = authTokenData[1];
        String tokenTimestampStr = authTokenData[2];
        Long tokenTimestamp = parseLongOrNull(tokenTimestampStr);

        if (tokenTimestamp == null || tokenTimestamp < System.currentTimeMillis()) {
            System.out.println(String.format("%s.%s.%s: Token expired", clientId, userId, instanceId));
            return false;
        }

        switch (entityType) {
            case "user":
                return isUserAllowed(clientId, userId);
            case "vhost":
                return isVhostAllowed(params.get("vhost"));
            case "resource":
                return isResourceAllowed(userId, instanceId, params);
            default:
                return false;
        }
    }

    private boolean isUserAllowed(String clientId, String userId) {
        return ("producer".equals(clientId) && userId.startsWith("producer")) ||
                ("consumer".equals(clientId) && userId.startsWith("consumer"));
    }

    private boolean isVhostAllowed(String vhost) {
        return VHOST.equals(vhost);
    }

    private boolean isResourceAllowed(String userId, String instanceId, Map<String, String> params) {
        String type = params.get("resource");
        String name = params.get("name");

        switch (type) {
            case "exchange":
                return isExchangeAllowed(userId, name, params.get("permission"));
            case "topic":
            return isTopicAllowed(userId, name, params.get("permission"));
            case "queue":
                return isQueueAllowed(userId, instanceId, name);
            default:
                return false;
        }
    }

    private boolean isExchangeAllowed(String userId, String name, String permission) {
        return EXCHANGE_NAME.equals(name) &&
                (isProducerAllowed(userId, permission) || isConsumerAllowed(userId, permission));
    }

    private boolean isTopicAllowed(String userId, String name, String permission) {
        return userId.equals(name) && "read".equals(permission);
    }

    private boolean isProducerAllowed(String producerId, String permission) {
        return producerId.startsWith("producer") && "write".equals(permission);
    }

    private boolean isConsumerAllowed(String consumerId, String permission) {
        return consumerId.startsWith("consumer") && "read".equals(permission);
    }

    private boolean isQueueAllowed(String consumerId, String instanceId, String queueName) {
        return isQueueAmqpAllowed(consumerId, instanceId, queueName) ||
                isQueueMqttAllowed(consumerId, instanceId, queueName) ||
                isQueueStompAllowed(consumerId, instanceId, queueName) ||
                isQueueWsProxyAllowed(consumerId, instanceId, queueName);
    }

    private boolean isQueueAmqpAllowed(String consumerId, String instanceId, String queueName) {
        String allowedQueueName = "amqp.subscription." + consumerId + "." + instanceId;
        return allowedQueueName.equals(queueName);
    }

    private boolean isQueueMqttAllowed(String consumerId, String instanceId, String queueName) {
        String allowedQueueName = "mqtt-subscription-" + consumerId + "-" + instanceId + "-qos1";
        return allowedQueueName.equals(queueName);
    }

    private boolean isQueueStompAllowed(String consumerId, String instanceId, String queueName) {
        String allowedQueueName = "stomp-subscription-" + consumerId + "-" + instanceId;
        return allowedQueueName.equals(queueName);
    }

    private boolean isQueueWsProxyAllowed(String consumerId, String instanceId, String queueName) {
        String allowedQueueName = "ws-proxy-subscription-" + consumerId + "-" + instanceId;
        return allowedQueueName.equals(queueName);
    }

    private Map<String, String> getParams(HttpExchange httpExchange) throws IOException {
        String bodyString = streamToString(httpExchange.getRequestBody());
        String bodyStringDecoded = decode(bodyString, BODY_CHARSET.name());

        return stream(bodyStringDecoded.split("&"))
                .map(paramStr -> paramStr.split("=", 2))
                .collect(toMap(
                        paramArr -> paramArr[0],
                        paramArr -> paramArr[1]));
    }

    private void sendResponse(HttpExchange xchg, String response) throws IOException {
        xchg.sendResponseHeaders(200, response.length());
        OutputStream os = xchg.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private static String streamToString(InputStream stream) throws IOException {
        return CharStreams.toString(new InputStreamReader(stream, BODY_CHARSET));
    }

    private static String substringAfterLast(String str, char ch) {
        int i = str.lastIndexOf(ch);
        return str.substring(i + 1);
    }

    private static String substringBeforeLast(String str, char ch) {
        int i = str.lastIndexOf(ch);
        return str.substring(0, i);
    }

    private static String base64Decode(String data) { // fake - remove leading '[' and tailing ']' :)
        return data.substring(1, data.length() - 1);
    }

    private long checksum(String data) { // fake
        return data.length();
    }

    private Long parseLongOrNull(String tokenTimestampStr) {
        try {
            return Long.valueOf(tokenTimestampStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

