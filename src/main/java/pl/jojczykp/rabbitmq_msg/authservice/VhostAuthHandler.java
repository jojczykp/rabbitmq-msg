package pl.jojczykp.rabbitmq_msg.authservice;

import java.util.Map;

class VhostAuthHandler extends AbstractAuthHandler {

    private final String vhost;

    VhostAuthHandler(String vhost) {
        this.vhost = vhost;
    }

    @Override
    protected boolean isAllowed(String userId, Map<String, String> params) {
        return vhost.equals(params.get("vhost"));
    }
}
