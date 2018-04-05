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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
abstract class JsonSocket {

    private Session session;
    private URI serverUri;
    private long connectionTimeoutMilliseconds;

    public final void connect() {

        disconnect(1000, "client is reconnecting");

        final CountDownLatch latch = new CountDownLatch(1);

        final Consumer<JsonObject> messageHandler = this::handleMessage;
        final BiConsumer<Integer, String> closeHandler = this::handleDisconnect;
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
            this.session = client.connectToServer(endpoint, endpointConfig, serverUri);
            latch.await(connectionTimeoutMilliseconds, TimeUnit.SECONDS);
        } catch (DeploymentException | IOException | InterruptedException e) {
            // either a network connectivity or config problem
            throw new RuntimeException(e);
        }

    }

    protected abstract void handleMessage(JsonObject message);

    protected abstract void handleDisconnect(int closeCode, String closeReasonPhrase);

    protected void sendMessage(JsonObject message) {
        String serializedMessage = message.toString();
        session.getAsyncRemote().sendText(serializedMessage);
    }

    public void disconnect(int closeCode, String closeReasonPhrase) {
        if(this.session != null) {
            if(this.session.isOpen()) {
                CloseReason.CloseCode code = CloseReason.CloseCodes.getCloseCode(closeCode);
                CloseReason closeReason = new CloseReason(code, closeReasonPhrase);
                try {
                    this.session.close(closeReason);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.session = null;
        }
    }

}
