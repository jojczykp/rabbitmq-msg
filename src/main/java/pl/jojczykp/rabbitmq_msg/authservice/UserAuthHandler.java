package pl.jojczykp.rabbitmq_msg.authservice;

import java.util.Map;
import java.util.Set;

class UserAuthHandler extends AbstractAuthHandler {

    private final Set<String> users;

    UserAuthHandler(Set<String> users) {
        this.users = users;
    }

    @Override
    protected boolean isAllowed(String userId, String clientId, Map<String, String> params) {
        return users.contains(userId);
    }
}
