package com.amannmalik.robot;

import org.glassfish.tyrus.client.ClientManager;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.websocket.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
public class JsonSocket {

    private final Consumer<JsonObject> messageHandler;
    private final BiConsumer<Integer, String> closeHandler;

    private Session session = null;

    public JsonSocket(Consumer<JsonObject> messageHandler, BiConsumer<Integer, String> closeHandler) {
        this.messageHandler = messageHandler;
        this.closeHandler = closeHandler;
    }

    public void open(URI serverEndpointUri, long timeoutMilliseconds) {

        if (session != null) {
            throw new IllegalStateException("attempted to open unclosed socket");
        }

        final CountDownLatch latch = new CountDownLatch(1);

        final Consumer<JsonObject> messageHandler = this.messageHandler;
        final BiConsumer<Integer, String> closeHandler = this.closeHandler;
        final Runnable clearSessionTask = this::clearSession;
        Endpoint endpoint = new Endpoint() {

            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String t) {
                        JsonObject object;
                        try (JsonReader parser = Json.createReader(new StringReader(t))) {
                            object = parser.readObject();
                        }
                        messageHandler.accept(object);
                    }
                });
                latch.countDown();
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                int closeCode = closeReason.getCloseCode().getCode();
                String closeReasonPhrase = closeReason.getReasonPhrase();
                clearSessionTask.run();
                closeHandler.accept(closeCode, closeReasonPhrase);
            }

            @Override
            public void onError(Session session, Throwable thr) {
                //TODO: properly handle
                throw new RuntimeException(thr);
            }

        };

        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();

        ClientManager client = ClientManager.createClient("org.glassfish.tyrus.container.jdk.client.JdkClientContainer");

        try {
            this.session = client.connectToServer(endpoint, endpointConfig, serverEndpointUri);
            latch.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        } catch (DeploymentException | IOException | InterruptedException e) {
            // either a network connectivity or config problem
            throw new RuntimeException(e);
        }

    }

    public void close(int closeCode, String closeReasonPhrase) {

        if (this.session == null) {
            throw new IllegalStateException("attempted to close already closed socket");
        }

        if (this.session.isOpen()) {
            CloseReason.CloseCode code = CloseReason.CloseCodes.getCloseCode(closeCode);
            CloseReason closeReason = new CloseReason(code, closeReasonPhrase);
            try {
                this.session.close(closeReason);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.session = null;
        }

    }

    public void send(JsonObject message) {

        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("attempted to send message on closed socket");
        }

        String serializedMessage = message.toString();
        session.getAsyncRemote().sendText(serializedMessage);
    }

    private void clearSession() {
        this.session = null;
    }

}
