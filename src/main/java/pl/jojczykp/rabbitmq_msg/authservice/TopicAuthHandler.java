package pl.jojczykp.rabbitmq_msg.authservice;

import java.util.Map;

class TopicAuthHandler extends AbstractAuthHandler {

    @Override
    protected boolean isAllowed(String userId, Map<String, String> params) {
        return false;
    }
}
