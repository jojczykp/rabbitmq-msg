package pl.jojczykp.rabbitmq_msg.producer;

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
    private final static String EXCHANGE_NAME = "sample-exchange";

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() + " producerId consumerId1 consumerId2 ...");
            System.exit(1);
        }

        String producerId = args[0];
        int producerInstanceId = new Random().nextInt(1000) + 20000;
        List<String> consumerIds = Arrays.asList(args).subList(1, args.length);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(createAuthToken(producerId));
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        boolean durable = true;
        channel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

        System.out.println(String.format("%s.%s: Sending messages to %s@%s/%s. To exit press CTRL+C", producerId, producerInstanceId, consumerIds, EXCHANGE_NAME, HOST));
        registerToCloseOnExit(connection, channel);

        int i = 0;
        while (true) {
            sendMessage(producerId, producerInstanceId, channel, consumerIds,
                    String.format("Hello World %d from %s.%d!", ++i, producerId, producerInstanceId));
        }
    }

    private static String createAuthToken(String producerId) {
        long authTokenTimestamp = 12345;
        String authTokenData = producerId + ',' + authTokenTimestamp;
        long authTokenChecksum = 123;
        return "Bearer " + authTokenData + ',' + authTokenChecksum;
    }

    private static void sendMessage(String producerId, int producerInstanceId, Channel channel, List<String> consumerIds, String message) throws IOException {
        for (String consumerId : consumerIds) {
            channel.basicPublish(EXCHANGE_NAME, consumerId, null, message.getBytes());
        }

        System.out.println(String.format("%s.%d: Sent to %s@%s/%s: %s", producerId, producerInstanceId, consumerIds, EXCHANGE_NAME, HOST, message));
        sleepSec();
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
                channel.close();
                connection.close();
                System.out.println("Connection closed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}
