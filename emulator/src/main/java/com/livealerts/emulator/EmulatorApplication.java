package com.livealerts.emulator;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class EmulatorApplication {

    public static void main(String[] args) throws IOException {
        EmulatorConfig config = EmulatorConfig.fromEnv();
        Log.info("Starting emulator: clientId=" + config.clientId()
                + " server=" + config.serverHost() + ":" + config.serverPort()
                + " intervalMs=" + config.intervalMs()
                + " controlPort=" + config.controlPort());

        ConnectionManager connectionManager = new ConnectionManager(config.serverHost(), config.serverPort(), config.clientId());
        connectionManager.start();

        AtomicInteger messageCounter = new AtomicInteger();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> connectionManager.sendMessage("periodic message #" + messageCounter.incrementAndGet()),
                config.intervalMs(), config.intervalMs(), TimeUnit.MILLISECONDS);

        ControlServer controlServer = new ControlServer(config.controlPort(),
                () -> connectionManager.sendMessage("manual trigger #" + messageCounter.incrementAndGet()));
        controlServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down");
            controlServer.stop();
            scheduler.shutdownNow();
            connectionManager.stop();
        }));
    }

    private EmulatorApplication() {
    }
}
