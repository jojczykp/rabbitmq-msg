package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Producers extends Thread {

    private static final String HOST = "localhost";
    private static final int PORT = 5672;
    private static final String EXCHANGE_NAME = "exchange.direct";
    private static final Integer DELIVERY_MODE_PERSISTENT = 2;

    private final String producerId;
    private final int instanceId;
    private final int initialConsumerId;
    private final int finalConsumerId;
    private Channel channel;

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 4) {
            System.err.println("Usage: java -cp <app-jar> " + Producers.class.getName() +
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
            new Producers("producer" + producerNumber, initialConsumerId, finalConsumerId).start();
        }
    }

    private Producers(String producerId, int initialConsumerId, int finalConsumerId) {
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
                sendMessage(message);
                messageNumber++;
            } catch (Exception e) {
                System.out.println(String.format("%s.%d: %s", producerId, instanceId, e.getMessage()));
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
        channel.confirmSelect();

        System.out.println(String.format("%s.%s: Connected", producerId, instanceId));

        return channel;
    }

    private void sendMessage(String message) throws IOException, InterruptedException {
        System.out.print(String.format("Sending to (consumer%d to consumer%d): [%s] ",
                initialConsumerId, finalConsumerId, message));

        for (int consumerId = initialConsumerId ; consumerId <= finalConsumerId ; consumerId++) {
            BasicProperties props = new BasicProperties.Builder()
//                    .deliveryMode(DELIVERY_MODE_PERSISTENT) // Works with durable queues to make sure queue content survives broker restarts
//                    .headers(ImmutableMap.of("BCC", asList("otherConsumer1", "otherConsumer2"))) // May be used for sending to group, seem lot of memory consumption in tests however
                    .build();
            String to = "consumer" + consumerId;
            channel.basicPublish(EXCHANGE_NAME, to, props, message.getBytes());
        }

        System.out.print("Confirming ");

        channel.waitForConfirmsOrDie();

        System.out.println("OK");

    }

}
