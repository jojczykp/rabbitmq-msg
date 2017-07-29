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
    private static final long TIMESTAMP_PERIOD_MILLIS = 15 * 60 * 1000;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() + " producerId consumerId1 consumerId2 ...");
            System.exit(1);
        }

        String producerId = args[0];
        int instanceId = new Random().nextInt(1000) + 20000;
        List<String> consumerIds = Arrays.asList(args).subList(1, args.length);


        try {
            while (true) {
                Connection connection = makeConnection(producerId, instanceId);
                Channel channel = connection.createChannel();
                channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);

                System.out.println(String.format("%s.%s: Connected. Sending messages to %s@%s/%s. To exit press CTRL+C",
                        producerId, instanceId, consumerIds, EXCHANGE_NAME, HOST));

                keepSending(producerId, instanceId, consumerIds, channel);

                System.out.println("Connection closed");

                close(connection, channel);
            }
        } catch (ConnectException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    private static Connection makeConnection(String producerId, int instanceId) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(createAuthToken(producerId, instanceId));
        return factory.newConnection();
    }

    private static void keepSending(String producerId, int instanceId, List<String> consumerIds, Channel channel) throws IOException {
        int i = 0;
        while (channel.isOpen()) {
            sendMessage(producerId, instanceId, channel, consumerIds, ++i);
        }
    }

    private static void close(Connection connection, Channel channel) throws IOException, TimeoutException {
        if (channel.isOpen()) {
            channel.close();
        }
        if (connection.isOpen()) {
            connection.close();
        }
    }

    private static String createAuthToken(String producerId, int instanceId) {
        long authTokenExpiryTimestamp = System.currentTimeMillis() + TIMESTAMP_PERIOD_MILLIS;
        String authTokenData = producerId + ',' + authTokenExpiryTimestamp;
        long authTokenChecksum = 123;

        return instanceId + "," + base64Encode("Bearer " + authTokenData + ',' + authTokenChecksum);
    }

    private static void sendMessage(String producerId, int instanceId, Channel channel, List<String> consumerIds, int i) throws IOException {
        String message = String.format("Hello World %d from %s.%d!", i, producerId, instanceId);

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
}
