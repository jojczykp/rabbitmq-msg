package pl.jojczykp.rabbitmq_msg;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Producer extends Thread {

    private static final String HOST = "localhost";
    private static final int PORT = 5672;
    private static final String EXCHANGE_NAME = "exchange.direct";
    private static final long AUTH_TOKEN_PERIOD_MILLIS = 30 * 60 * 1000;

    private final String producerId;
    private final int instanceId;
    private final int initialConsumerId;
    private final int finalConsumerId;
    private Channel channel;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 4) {
            System.err.println("Usage: java -cp <app-jar> " + Producer.class.getName() +
                    " initialProducerId finalProducerId initialConsumerId finalConsumerId");
            System.exit(1);
        }

        int initialProducerId = Integer.parseInt(args[0]);
        int finalProducerId = Integer.parseInt(args[1]);
        int initialConsumerId = Integer.parseInt(args[2]);
        int finalConsumerId = Integer.parseInt(args[3]);

        System.out.println(String.format("Sending to %s@%s:%d", EXCHANGE_NAME, HOST, PORT));
        System.out.println("To exit press CTRL+C");
        System.out.println("- initialProducerId: " + initialProducerId);
        System.out.println("- finalProducerId: " + finalProducerId);
        System.out.println("- initialConsumerId: " + initialConsumerId);
        System.out.println("- finalConsumerId: " + finalConsumerId);
        System.out.println();

        for (int producerNumber = initialProducerId ; producerNumber <= finalProducerId ; producerNumber++) {
            new Producer("producer" + producerNumber, initialConsumerId, finalConsumerId).start();
        }
    }

    private Producer(String producerId, int initialConsumerId, int finalConsumerId) {
        this.producerId = producerId;
        this.instanceId = new Random().nextInt(1000) + 20_000;
        this.initialConsumerId = initialConsumerId;
        this.finalConsumerId = finalConsumerId;
        this.channel = null;
    }

    @Override
    public void run() {
        int messageNumber = 0;
        while (true) {
            try {
                if (channel == null) {
                    channel = connect();
                }
                String message = String.format("%s.%d says Hello %d", producerId, instanceId, messageNumber);
                broadcastMessage(message);
                messageNumber++;
            } catch (Exception e) {
                System.out.println(String.format("%s.%d: Reconnecting with new Access Token", producerId, instanceId));
                if (channel != null) {
                    try {
                        channel.abort();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    channel = null;
                }
            }

            sleepRoughlyMillis(5_000);
        }
    }

    private Channel connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername("producer");
        factory.setPassword("producer");
        factory.setAutomaticRecoveryEnabled(false);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclarePassive(EXCHANGE_NAME);

        System.out.println(String.format("%s.%s: Connected", producerId, instanceId));

        return channel;
    }

    private void broadcastMessage(String message) throws IOException {
        String to = "consumer" + initialConsumerId;
        List<String> bcc = IntStream.range(initialConsumerId + 1, finalConsumerId + 1)
                .mapToObj(i -> "consumer" + i)
                .collect(Collectors.toList());

        BasicProperties props = new BasicProperties.Builder()
                .headers(ImmutableMap.of("BCC", bcc))
                .build();

        channel.basicPublish(EXCHANGE_NAME, to, props, message.getBytes());

        System.out.println(String.format("Sent to (consumer%d to consumer%d): [%s]",
                initialConsumerId, finalConsumerId, message));
    }

    private void sleepRoughlyMillis(double millis) {
        double random = Math.random();
        double deviation = Math.round(random * millis - millis / 2);
        long duration = (long) (millis + deviation);

        if (duration == 0) {
            System.out.println(String.format("%s.%s: No delay between sends!", producerId, instanceId));
            return;
        }

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
