package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "192.168.99.100";
    private final static String EXCHANGE_NAME = "sample-exchange";

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <app-jar> userId");
            System.exit(1);
        }

        String producerId = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String userId = args[0];

        Connection connection = null;
        Channel channel = null;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(HOST);
            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            System.out.println(String.format("Sending messages to %s@%s/%s. To exit press CTRL+C", userId, EXCHANGE_NAME, HOST));

            for (int i = 0; ; i++) {
                sendMessage(channel, userId, String.format("Hello World %d from %s!", i, producerId));
            }
        } catch (RuntimeException e) {
            if (channel != null) {
                channel.close();
            }

            if (connection != null) {
                connection.close();
            }
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
}
