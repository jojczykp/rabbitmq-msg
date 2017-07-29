package pl.jojczykp.rabbitmq_msg.authservice;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    public static void main(String[] args) throws IOException {
        ConcurrentHashMap<String, Long> userInstanceKeyToExpiryTimestamp = new ConcurrentHashMap<>();

        new AuthProcessor(userInstanceKeyToExpiryTimestamp).start();
        new ExpiryProcessor(userInstanceKeyToExpiryTimestamp).start();
    }
}

