package pl.jojczykp.rabbitmq_msg.producer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "localhost";
    private final static String EXCHANGE_NAME = "sample-exchange";

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() + " producerId consumerId");
            System.exit(1);
        }

        String producerId = args[0];
        String producerInstanceId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String consumerId = args[1];

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(createAuthToken(producerId, producerInstanceId));
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        boolean durable = true;
        channel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

        System.out.println(String.format("Sending messages to %s@%s/%s. To exit press CTRL+C", consumerId, EXCHANGE_NAME, HOST));
        registerToCloseOnExit(connection, channel);

        int i = 0;
        while (true) {
            sendMessage(producerId + "/" + producerInstanceId, channel, consumerId, String.format("Hello World %d from %s!", ++i, producerInstanceId));
        }
    }

    private static String createAuthToken(String producerId, String producerInstanceId) {
        long authTokenTimestamp = 12345;
        String authTokenData = producerId + ',' + producerInstanceId + ',' + authTokenTimestamp;
        long authTokenChecksum = 123;
        return "Bearer " + authTokenData + ',' + authTokenChecksum;
    }

    private static void sendMessage(String logPrefix, Channel channel, String userId, String message) throws IOException {
        channel.basicPublish(EXCHANGE_NAME, userId, null, message.getBytes());
        System.out.println(String.format("%s: Sent to %s@%s/%s: %s", logPrefix, userId, EXCHANGE_NAME, HOST, message));
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
