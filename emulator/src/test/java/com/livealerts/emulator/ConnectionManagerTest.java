package com.livealerts.emulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ConnectionManager} over real sockets against a hand-rolled fake server, since
 * its whole job is socket lifecycle management: connect, send, and — the exercise's "recover
 * from restarts" requirement — reconnect on its own after the connection drops.
 */
class ConnectionManagerTest {

    private ServerSocket serverSocket;
    private ConnectionManager manager;

    @AfterEach
    void tearDown() throws IOException {
        if (manager != null) {
            manager.stop();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    @Test
    void sendsAConnectMessageAsSoonAsItConnects() throws Exception {
        BlockingQueue<String> receivedLines = new LinkedBlockingQueue<>();
        serverSocket = new ServerSocket(0);
        acceptOneConnectionAndCollectLines(serverSocket, receivedLines);

        manager = new ConnectionManager("localhost", serverSocket.getLocalPort(), "emulator-test");
        manager.start();

        String line = receivedLines.poll(5, TimeUnit.SECONDS);
        assertThat(line).contains("\"type\":\"Connect\"").contains("\"clientId\":\"emulator-test\"");
    }

    @Test
    void sendMessageSendsASendMessageLineOverTheOpenConnection() throws Exception {
        BlockingQueue<String> receivedLines = new LinkedBlockingQueue<>();
        serverSocket = new ServerSocket(0);
        acceptOneConnectionAndCollectLines(serverSocket, receivedLines);

        manager = new ConnectionManager("localhost", serverSocket.getLocalPort(), "emulator-test");
        manager.start();
        receivedLines.poll(5, TimeUnit.SECONDS); // the initial Connect

        manager.sendMessage("hello");

        String line = receivedLines.poll(5, TimeUnit.SECONDS);
        assertThat(line).contains("\"type\":\"SendMessage\"").contains("\"text\":\"hello\"");
    }

    @Test
    void sendMessageWhileDisconnectedIsDroppedInsteadOfThrowing() {
        manager = new ConnectionManager("localhost", 1, "emulator-test"); // nothing listens on port 1

        manager.start();
        manager.sendMessage("into the void"); // must not throw
    }

    @Test
    void reconnectsOnItsOwnAfterTheServerDropsTheConnection() throws Exception {
        BlockingQueue<Socket> acceptedSockets = new LinkedBlockingQueue<>();
        serverSocket = new ServerSocket(0);
        Thread acceptThread = new Thread(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    acceptedSockets.add(serverSocket.accept());
                }
            } catch (IOException ignored) {
                // serverSocket closed — thread exits
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();

        manager = new ConnectionManager("localhost", serverSocket.getLocalPort(), "emulator-test");
        manager.start();

        Socket first = acceptedSockets.poll(5, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        first.close(); // simulate the server restarting mid-connection

        Socket second = acceptedSockets.poll(10, TimeUnit.SECONDS);
        assertThat(second).isNotNull();
    }

    private static void acceptOneConnectionAndCollectLines(ServerSocket serverSocket, BlockingQueue<String> lines) {
        Thread thread = new Thread(() -> {
            try (Socket socket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException ignored) {
                // connection closed
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
