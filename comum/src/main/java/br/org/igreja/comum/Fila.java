package br.org.igreja.comum;

import org.apache.activemq.ActiveMQConnectionFactory;

public final class Fila {
    public static ActiveMQConnectionFactory fabrica(String url) {
        var f = new ActiveMQConnectionFactory(url);
        f.setConnectResponseTimeout(2000);
        f.setSendTimeout(2000);
        f.setCloseTimeout(1000);
        f.setWatchTopicAdvisories(false);
        f.getPrefetchPolicy().setQueuePrefetch(1);
        var retry = f.getRedeliveryPolicy();
        retry.setInitialRedeliveryDelay(250);
        retry.setRedeliveryDelay(250);
        retry.setMaximumRedeliveries(3);
        return f;
    }
    private Fila() {}
}
