package pl.jojczykp.rabbitmq_msg.producer;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "localhost";
    private static final String EXCHANGE_NAME = "sample-exchange";
    private static final long AUTH_TOKEN_PERIOD_MILLIS = /*5 **/ 60 * 1000;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() + " producerId consumerId1 consumerId2 ...");
            System.exit(1);
        }

        String producerId = args[0];
        int instanceId = new Random().nextInt(1000) + 20000;
        List<String> consumerIds = Arrays.asList(args).subList(1, args.length);

        int i = 0;
        Channel channel = null;
        while (true) {
            try {
                if (channel == null) {
                    channel = connect(producerId, instanceId, consumerIds);
                }
                broadcastMessage(producerId, instanceId, consumerIds, channel, i);
                i++;
            } catch (Exception e) {
                System.out.println(String.format("%s.%s: Connection closed - reconnecting", producerId, instanceId));
                if (channel != null) {
                    channel.abort();
                    channel = null;
                }
            }

            sleepSec();
        }
    }

    private static Channel connect(String producerId, int instanceId, List<String> consumerIds) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(createAuthToken(producerId, instanceId));
        factory.setPassword("");
        factory.setAutomaticRecoveryEnabled(false);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclarePassive(EXCHANGE_NAME);

        System.out.println(String.format("%s.%s: Connected. Sending messages to %s@%s/%s. To exit press CTRL+C",
                producerId, instanceId, consumerIds, EXCHANGE_NAME, HOST));

        return channel;
    }

    private static void broadcastMessage(String producerId, int instanceId, List<String> consumerIds, Channel channel, int i) throws IOException {
        String message = String.format("Hello World %d from %s.%d!", i, producerId, instanceId);

        for (String consumerId : consumerIds) {
            channel.basicPublish(EXCHANGE_NAME, consumerId, null, message.getBytes());
        }

        System.out.println(String.format("%s.%d: Sent to %s@%s/%s: %s", producerId, instanceId, consumerIds, EXCHANGE_NAME, HOST, message));
    }

    private static String createAuthToken(String producerId, int instanceId) {
        long authTokenExpiryTimestamp = System.currentTimeMillis() + AUTH_TOKEN_PERIOD_MILLIS;
        String authTokenData = producerId + ',' + authTokenExpiryTimestamp;
        long authTokenChecksum = 123;

        return instanceId + "," + base64Encode("Bearer " + authTokenData + ',' + authTokenChecksum);
    }

    private static String base64Encode(String data) { // fake - remove leading '[' and tailing ']' :)
        return "[" + data + "]";
    }

    private static void sleepSec() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
