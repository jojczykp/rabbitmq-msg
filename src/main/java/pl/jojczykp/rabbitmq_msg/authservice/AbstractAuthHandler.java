package pl.jojczykp.rabbitmq_msg.authservice;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;

import static java.net.URLDecoder.decode;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

abstract class AbstractAuthHandler implements HttpHandler {

    private static final Charset BODY_CHARSET = Charsets.ISO_8859_1;

    @Override
    public void handle(HttpExchange t) {
        try {
            System.out.println(String.format(">>> %s %s ", t.getRequestMethod(), t.getRequestURI()));

            Map<String, String> params = getParams(t);
            System.out.println("Params: " + params);

            String response = isProperAdmin(params) || handleNonAdmin(params) ? "allow" : "deny";

            sendResponse(t, response);
            System.out.println("<<< " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isProperAdmin(Map<String, String> params) {
        return "guest".equals(params.get("username")) &&
                "guest".equals(params.get("password"));
    }

    private boolean handleNonAdmin(Map<String, String> params) {
        boolean allowed;
        String authToken = params.get("username");
        String authTokenDataStr = authToken.split(" ")[1];
        String[] authTokenData = authTokenDataStr.split(",");
        String userId = authTokenData[0];
        String clientId = authTokenData[1];
        System.out.println("UserId: " + userId + ", ClientId: " + clientId);

        allowed = isAllowed(userId, clientId, params);
        return allowed;
    }

    protected abstract boolean isAllowed(String userId, String clientId, Map<String, String> params);

    private static Map<String, String> getParams(HttpExchange t) throws IOException {
        String bodyString = streamToString(t.getRequestBody());
        String bodyStringDecoded = decode(bodyString, BODY_CHARSET.name());

        return stream(bodyStringDecoded.split("&"))
                .map(paramStr -> paramStr.split("=", 2))
                .collect(toMap(
                        paramArr -> paramArr[0],
                        paramArr -> paramArr[1]));
    }

    private static String streamToString(InputStream stream) throws IOException {
        return CharStreams.toString(new InputStreamReader(stream, BODY_CHARSET));
    }

    private static void sendResponse(HttpExchange xchg, String response) throws IOException {
        xchg.sendResponseHeaders(200, response.length());
        OutputStream os = xchg.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
