package pl.jojczykp.rabbitmq_msg.authservice;

import com.google.common.collect.ImmutableSet;
import com.sun.net.httpserver.HttpServer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import static com.google.common.collect.Sets.union;


public class AuthService {

    private static final int PORT = 8000;
    private static final String URL_PREFIX = "/sync/auth/";

    private static final String EXCHANGE = "sample-exchange";
    private static final String VHOST = "/";
    private static final Set<String> PRODUCERS = ImmutableSet.of("producer1", "producer2");
    private static final Set<String> CONSUMERS = ImmutableSet.of("consumer1", "consumer2");

    public static void main(String[] args) throws Exception {
        InetAddress localHost = Inet4Address.getLocalHost();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(localHost, PORT);

        System.out.println("Waiting on " + inetSocketAddress + ", urls: " + URL_PREFIX + "*");

        HttpServer server = HttpServer.create(inetSocketAddress, 0);
        server.createContext(URL_PREFIX + "user", new UserAuthHandler(union(PRODUCERS, CONSUMERS)));
        server.createContext(URL_PREFIX + "vhost", new VhostAuthHandler(VHOST));
        server.createContext(URL_PREFIX + "resource", new ResourceAuthHandler(EXCHANGE, PRODUCERS, CONSUMERS));
        server.createContext(URL_PREFIX + "topic", new TopicAuthHandler());

        server.start();
    }

}
