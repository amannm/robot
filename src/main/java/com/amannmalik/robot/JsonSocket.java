package com.amannmalik.robot;

import org.glassfish.tyrus.client.ClientManager;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.websocket.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
class JsonSocket implements AutoCloseable {

    private static final long WEBSOCKET_OPEN_TIMEOUT = 10L;

    private final Session session;
    private MessageHandler messageHandler;

    public JsonSocket(String websocketUrl) {

        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        ClientManager client = ClientManager.createClient("org.glassfish.tyrus.container.jdk.client.JdkClientContainer");
        CountDownLatch latch = new CountDownLatch(1);
        try {

            this.session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    System.out.println("websocket opened");
                    latch.countDown();
                }

            }, cec, new URI(websocketUrl));

            latch.await(WEBSOCKET_OPEN_TIMEOUT, TimeUnit.SECONDS);

        } catch (DeploymentException | IOException | URISyntaxException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }

    }

    public void setHandler(Consumer<JsonObject> handler) {

        MessageHandler newHandler = new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String t) {
                JsonObject object;
                try (JsonReader parser = Json.createReader(new StringReader(t))) {
                    object = parser.readObject();
                }
                handler.accept(object);
            }
        };

        if (this.messageHandler != null) {
            this.session.removeMessageHandler(this.messageHandler);
        }
        this.messageHandler = newHandler;

        this.session.addMessageHandler(newHandler);

    }

    public void send(JsonObject message) {
        String serializedMessage = message.toString();
        session.getAsyncRemote().sendText(serializedMessage);
    }

    @Override
    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            throw new RuntimeException("session isn't open");
        }
    }
}
