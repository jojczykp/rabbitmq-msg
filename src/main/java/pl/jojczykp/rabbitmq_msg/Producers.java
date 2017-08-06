package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Producers {

    private static final String HOST = "rabbitmq";
    private static final int PORT = 5672;
    private static final String EXCHANGE_NAME = "mqtt.direct";
    private static final long AUTH_TOKEN_PERIOD_MILLIS = 60 * 1000;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 3) {
            System.err.println("Usage: java -cp <app-jar> " + Producers.class.getName() + " producerId initialConsumerId finalConsumerId");
            System.exit(1);
        }

        run(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }

    private static void run(String producerId, int initialConsumerId, int finalConsumerId) throws IOException {
        int instanceId = new Random().nextInt(1000) + 20000;
        int messageNumber = 0;
        Channel channel = null;
        while (true) {
            try {
                if (channel == null) {
                    channel = connect(producerId, instanceId, initialConsumerId, finalConsumerId);
                }
                String message = String.format("%s.%d says Hello %d", producerId, instanceId, messageNumber);
                broadcastMessage(channel, initialConsumerId, finalConsumerId, message);
                messageNumber++;
            } catch (Exception e) {
                System.out.println(String.format("%s.%s: Connection closed - reconnecting", producerId, instanceId));
                if (channel != null) {
                    channel.abort();
                    channel = null;
                }
            }

            sleepSecs(1);
        }
    }

    private static Channel connect(String producerId, int instanceId, int initialConsumerId, int finalConsumerId) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(createAuthToken(producerId, instanceId));
        factory.setPassword("");
        factory.setAutomaticRecoveryEnabled(false);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclarePassive(EXCHANGE_NAME);

        System.out.println(String.format("%s.%s: Connected to %s/%s to (consumer%d to consumer%d).",
                producerId, instanceId, EXCHANGE_NAME, HOST, initialConsumerId, finalConsumerId));
        System.out.println(String.format("%s.%s: To exit press CTRL+C", producerId, instanceId));

        return channel;
    }

    private static void broadcastMessage(Channel channel, int initialConsumerId, int finalConsumerId, String message) throws IOException {
        for (int consumerId = initialConsumerId; consumerId <= finalConsumerId; consumerId++) {
            channel.basicPublish(EXCHANGE_NAME, "consumer" + consumerId, null, message.getBytes());
        }

        System.out.println(String.format("Sent to (consumer%d to consumer%d): %d*[%s]",
                initialConsumerId, finalConsumerId, finalConsumerId - initialConsumerId + 1, message));
    }

    private static String createAuthToken(String producerId, int instanceId) {
        long authTokenExpiryTimestamp = System.currentTimeMillis() + AUTH_TOKEN_PERIOD_MILLIS;
        String authTokenData = producerId + ',' + authTokenExpiryTimestamp;
        long authTokenChecksum = checksum(authTokenData);

        return instanceId + "," + base64Encode("Bearer " + authTokenData + ',' + authTokenChecksum);
    }

    private static long checksum(String data) { // fake - return length :)
        return data.length();
    }

    private static String base64Encode(String data) { // fake - remove leading '[' and tailing ']' :)
        return "[" + data + "]";
    }

    private static void sleepSecs(int n) {
        try {
            Thread.sleep(n * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
