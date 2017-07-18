package pl.jojczykp.rabbitmq_msg;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Producer {

    private static final String HOST = "192.168.99.100";
    private final static String QUEUE_NAME = "sample-queue";

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        System.out.println("Listening to queue " + QUEUE_NAME + " on host " + HOST);

        for (int i = 0 ; i < 10 ; i++) {
            sendMessage(channel, "Hello World " + i + "!");
        }

        channel.close();
        connection.close();
    }

    private static void sendMessage(Channel channel, String message) throws IOException {
        channel.basicPublish("", QUEUE_NAME, null, message.getBytes());

        System.out.println("Sent: '" + message + "'");
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
