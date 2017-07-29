package pl.jojczykp.rabbitmq_msg.authservice;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.net.URLDecoder.decode;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

class AuthProcessor implements HttpHandler {

    private static final int LISTEN_PORT = 8000;
    private static final String LISTEN_URL_PREFIX = "/auth/";

    private static final String VHOST = "/";
    private static final String EXCHANGE = "sample-exchange";
    private static final Set<String> CONSUMERS = ImmutableSet.of("consumer1", "consumer2", "consumer3");
    private static final Set<String> PRODUCERS = ImmutableSet.of("producer1", "producer2");
    private static final Sets.SetView<String> CONSUMERS_PRODUCERS = Sets.union(CONSUMERS, PRODUCERS);

    private static final Charset BODY_CHARSET = Charsets.ISO_8859_1;

    private final ConcurrentHashMap<String, Long> consumerInstanceKeyToExpiryTimestamp;

    AuthProcessor(ConcurrentHashMap<String, Long> consumerInstanceKeyToExpiryTimestamp) {
        this.consumerInstanceKeyToExpiryTimestamp = consumerInstanceKeyToExpiryTimestamp;
    }

    void start() throws IOException {
        InetAddress localHost = Inet4Address.getLocalHost();
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
        String instanceId = userData[0];
        String authToken = base64Decode(userData[1]);
        String authTokenDataStr = authToken.split(" ")[1];
        String[] authTokenData = authTokenDataStr.split(",");
        String consumerId = authTokenData[0];
        long tokenTimestamp = Long.valueOf(authTokenData[1]);

        if (tokenTimestamp < System.currentTimeMillis()) {
            System.out.println(String.format("%s.%s: Token expired", consumerId, instanceId));
            return false;
        }

        consumerInstanceKeyToExpiryTimestamp.put(userDataStr, tokenTimestamp);

        switch (entityType) {
            case "user":
                return isProducerOrConsumerAllowed(consumerId);
            case "vhost":
                return isVhostAllowed(params.get("vhost"));
            case "resource":
                return isResourceAllowed(consumerId, instanceId, params);
            default:
                return false;
        }
    }

    private boolean isProducerOrConsumerAllowed(String consumerId) {
        return CONSUMERS_PRODUCERS.contains(consumerId);
    }

    private boolean isVhostAllowed(String vhost) {
        return VHOST.equals(vhost);
    }

    private boolean isResourceAllowed(String consumerId, String instanceId, Map<String, String> params) {
        String type = params.get("resource");
        String name = params.get("name");

        switch (type) {
            case "exchange":
                return isExchangeAllowed(consumerId, name, params.get("permission"));
            case "queue":
                return isQueueAllowed(consumerId, instanceId, name);
            default:
                return false;
        }
    }

    private boolean isExchangeAllowed(String consumerId, String exchange, String permission) {
        return EXCHANGE.equals(exchange) &&
                (isProducerAllowed(consumerId, permission) || isProducerOrConsumerAllowed(consumerId, permission));
    }

    private boolean isProducerAllowed(String consumerId, String permission) {
        return PRODUCERS.contains(consumerId) &&
               ImmutableSet.of("write", "configure").contains(permission);
    }

    private boolean isProducerOrConsumerAllowed(String consumerId, String permission) {
        return CONSUMERS.contains(consumerId) &&
               ImmutableSet.of("read").contains(permission);
    }

    private boolean isQueueAllowed(String consumerId, String instanceId, String queueName) {
        String allowedQueueName = consumerId + "." + instanceId;
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

    private static String base64Decode(String data) { // fake - remove leading '[' and tailing ']' :)
        return data.substring(1, data.length() - 1);
    }
}

