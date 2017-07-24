package pl.jojczykp.rabbitmq_msg.authservice;

import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

class ResourceAuthHandler extends AbstractAuthHandler {

    private String exchange;
    private Set<String> producers;
    private Set<String> consumers;

    ResourceAuthHandler(String exchange, Set<String> producers, Set<String> consumers) {
        this.exchange = exchange;
        this.producers = producers;
        this.consumers = consumers;
    }

    @Override
    protected boolean isAllowed(String userId, Map<String, String> params) {
        String type = params.get("resource");
        String name = params.get("name");

        switch (type) {
            case "exchange":
                return isExchangeAllowed(name, userId, params.get("permission"));
            case "queue":
                return isQueueAllowed(userId, name);
            default:
                return false;
        }
    }

    private boolean isExchangeAllowed(String actualExchange, String userId, String permission) {
        ImmutableSet<String> producersPermissions = ImmutableSet.of("write", "configure");
        ImmutableSet<String> consumersPermissions = ImmutableSet.of("read");

        return exchange.equals(actualExchange) && (
                (producers.contains(userId) && producersPermissions.contains(permission)) ||
                (consumers.contains(userId) && consumersPermissions.contains(permission))
        );
    }

    private boolean isQueueAllowed(String userId, String name) {
        return name.startsWith(userId + ".");
    }
}
