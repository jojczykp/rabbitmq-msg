package pl.jojczykp.rabbitmq_msg.producer;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "localhost";
    private static final String EXCHANGE_NAME = "sample-exchange";
    private static final long TIMESTAMP_PERIOD_MILLIS = 15 * 60 * 1000;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() + " producerId consumerId1 consumerId2 ...");
            System.exit(1);
        }

        String producerId = args[0];
        int instanceId = new Random().nextInt(1000) + 20000;
        List<String> consumerIds = Arrays.asList(args).subList(1, args.length);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(createAuthToken(producerId, instanceId));
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        boolean durable = true;
        channel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

        System.out.println(String.format("%s.%s: Sending messages to %s@%s/%s. To exit press CTRL+C", producerId, instanceId, consumerIds, EXCHANGE_NAME, HOST));
        registerToCloseOnExit(connection, channel);

        int i = 0;
        while (channel.isOpen()) {
            sendMessage(producerId, instanceId, channel, consumerIds,
                    String.format("Hello World %d from %s.%d!", ++i, producerId, instanceId));
        }

        System.out.println("Connection closed");
        System.exit(0);
    }

    private static String createAuthToken(String producerId, int instanceId) {
        long authTokenExpiryTimestamp = System.currentTimeMillis() + TIMESTAMP_PERIOD_MILLIS;
        String authTokenData = producerId + ',' + authTokenExpiryTimestamp;
        long authTokenChecksum = 123;

        return instanceId + "," + base64Encode("Bearer " + authTokenData + ',' + authTokenChecksum);
    }

    private static void sendMessage(String producerId, int instanceId, Channel channel, List<String> consumerIds, String message) throws IOException {
        for (String consumerId : consumerIds) {
            try {
                channel.basicPublish(EXCHANGE_NAME, consumerId, null, message.getBytes());
            } catch (AlreadyClosedException e) {
                System.err.println("Channel closed by server");
            }
        }

        System.out.println(String.format("%s.%d: Sent to %s@%s/%s: %s", producerId, instanceId, consumerIds, EXCHANGE_NAME, HOST, message));
        sleepSec();
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

    private static void registerToCloseOnExit(Connection connection, Channel channel) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
                if (connection.isOpen()) {
                    connection.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}
