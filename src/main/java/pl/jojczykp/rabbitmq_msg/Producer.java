package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "localhost";
    private final static String EXCHANGE_NAME = "sync-exchange";

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <app-jar> userId");
            System.exit(1);
        }

        String producerId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String userId = args[0];

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        boolean durable = true;
        channel.exchangeDeclare(EXCHANGE_NAME, "direct", durable);

        System.out.println(String.format("Sending messages to %s@%s/%s. To exit press CTRL+C", userId, EXCHANGE_NAME, HOST));
        registerToCloseOnExit(connection, channel);

        int i = 0;
        while (true) {
            sendMessage(channel, userId, String.format("Hello World %d from %s!", ++i, producerId));
        }

    }

    private static void sendMessage(Channel channel, String userId, String message) throws IOException {
        channel.basicPublish(EXCHANGE_NAME, userId, null, message.getBytes());
        System.out.println(String.format("Sent to %s@%s/%s: %s", userId, EXCHANGE_NAME, HOST, message));
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
