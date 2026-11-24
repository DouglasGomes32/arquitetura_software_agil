package br.org.igreja.broker;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.apache.activemq.broker.BrokerService;

/** Infraestrutura da fila, não um terceiro serviço de negócio. */
public final class BrokerApp {
    public static BrokerService iniciar(Path diretorio, int porta) throws Exception {
        var broker = new BrokerService();
        // Nome estável: o diretório do KahaDB não pode mudar ao reiniciar em outra porta.
        broker.setBrokerName("igreja-escalas");
        broker.setPersistent(true);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setSchedulerSupport(false);
        broker.setDataDirectoryFile(diretorio.toFile());
        broker.getSystemUsage().getMemoryUsage().setLimit(64L * 1024 * 1024);
        broker.getSystemUsage().getStoreUsage().setLimit(256L * 1024 * 1024);
        broker.getSystemUsage().getTempUsage().setLimit(128L * 1024 * 1024);
        broker.addConnector("tcp://127.0.0.1:" + porta + "?wireFormat.maxFrameSize=1048576");
        broker.start();
        broker.waitUntilStarted();
        return broker;
    }
    public static void main(String[] args) throws Exception {
        var broker = iniciar(Path.of(System.getProperty("broker.data", "data/broker")), Integer.getInteger("broker.port", 61616));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { broker.stop(); } catch (Exception e) { System.err.println(e); }
        }));
        System.out.println("Fila persistente pronta em " + broker.getTransportConnectors().getFirst().getPublishableConnectString());
        new CountDownLatch(1).await();
    }
}
