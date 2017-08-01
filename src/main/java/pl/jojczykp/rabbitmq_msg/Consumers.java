package pl.jojczykp.rabbitmq_msg;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.CompletableFuture.runAsync;

public class Consumers {

    private static final String HOST = "localhost";
    private static final int PORT = 5672;
    private static final String EXCHANGE_NAME = "sample.exchange";
    private static final long AUTH_TOKEN_PERIOD_MILLIS = 60 * 1000;

    public static void main(String[] args) throws IOException, TimeoutException {
        new Consumers().run(args);
    }

    private void run(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.out.println("Usage: java -cp <app-jar> " + Consumers.class.getName() + " numOfConsumers numOfInstancesPerConsumer");
            System.exit(1);
        }

        int numOfConsumers = Integer.parseInt(args[0]);
        int numOfInstancesPerConsumers = Integer.parseInt(args[1]);

        for (int c = 1; c < numOfConsumers + 1; c++) {
            for (int i = 1; i < numOfInstancesPerConsumers + 1; i++) {
                startConsumerAsync("consumer" + c, i);
            }
        }

        while (true) {
            sleepSec();
        }
    }

    private void startConsumerAsync(String consumerId, int instanceId) {
        runAsync(() -> {
            try {
                String queueName = consumerId + "." + instanceId;

                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(HOST);
                factory.setPort(PORT);
                factory.setUsername(createAuthToken(consumerId, instanceId));
                factory.setPassword("");
                factory.setAutomaticRecoveryEnabled(false);

                Connection connection = factory.newConnection();
                connection.addShutdownListener(cause -> {
                    System.out.println(String.format("%s.%s: Connection to closed. Reconnecting...", consumerId, instanceId, EXCHANGE_NAME, HOST));
                    startConsumerAsync(consumerId, instanceId);
                });

                Channel channel = connection.createChannel();
                channel.queueDeclare(queueName, true, false, false, ImmutableMap.of());
                channel.queueBind(queueName, EXCHANGE_NAME, consumerId);

                System.out.println(String.format("%s.%s: Connected. Receiving messages from %s/%s. To exit press CTRL+C",
                        consumerId, instanceId, EXCHANGE_NAME, HOST));

                channel.basicConsume(queueName, false, new DefaultConsumer(channel) { // noAutoAck = false
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope,
                                               AMQP.BasicProperties properties, byte[] body) throws IOException {
                        String message = new String(body, "UTF-8");
                        System.out.println(String.format("%s.%s: Received %s", consumerId, instanceId, message));
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    }
                });
            } catch (IOException | TimeoutException e) {
                sleepSec();
                System.out.println(String.format("%s.%s: Connection to %s/%s failed. Reconnecting...", consumerId, instanceId, EXCHANGE_NAME, HOST));
                startConsumerAsync(consumerId, instanceId);
            }
        });
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

    private static void sleepSec() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
