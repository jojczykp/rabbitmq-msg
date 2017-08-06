package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class Producer extends Thread {

    private static final String HOST = "localhost";
    private static final int PORT = 5672;
    private static final String EXCHANGE_NAME = "mqtt.direct";
    private static final long AUTH_TOKEN_PERIOD_MILLIS = 60 * 1000;

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
        this.instanceId = new Random().nextInt(1000) + 20000;
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

            sleepSecs(1);
        }
    }

    private Channel connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(createAuthToken());
        factory.setPassword("");
        factory.setAutomaticRecoveryEnabled(false);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclarePassive(EXCHANGE_NAME);

        System.out.println(String.format("%s.%s: Connected", producerId, instanceId));

        return channel;
    }

    private void broadcastMessage(String message) throws IOException {
        for (int consumerId = initialConsumerId; consumerId <= finalConsumerId; consumerId++) {
            channel.basicPublish(EXCHANGE_NAME, "consumer" + consumerId, null, message.getBytes());
        }

        System.out.println(String.format("Sent to (consumer%d to consumer%d): %d*[%s]",
                initialConsumerId, finalConsumerId, finalConsumerId - initialConsumerId + 1, message));
    }

    private String createAuthToken() {
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

    private static void sleepSecs(int n) {
        try {
            Thread.sleep(n * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
