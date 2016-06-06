package com.amannmalik.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
public class SlackMessageGateway implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SlackMessageGateway.class);

    private static final String SLACK_RTM_START_URL = "https://slack.com/api/rtm.start";

    private final String token;

    private final JsonSocket socket;

    private final AtomicInteger messageId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Consumer<Boolean>> messageBuffer = new ConcurrentHashMap<>();

    public SlackMessageGateway(String token) {

        this.token = token;

        String websocketUrl;
        try {
            JsonObject metadata = Util.fetchResource(SLACK_RTM_START_URL + "?token=" + this.token);
            websocketUrl = metadata.getString("url");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        this.socket = new JsonSocket(websocketUrl);
        setMessageListener(message -> {
        });
    }


    @Override
    public void close() {
        for (Integer id : messageBuffer.keySet()) {
            Consumer<Boolean> remove = messageBuffer.remove(id);
            remove.accept(false);
        }
        if (socket != null) {
            socket.close();
        } else {
            throw new RuntimeException("socket not initialized");
        }
    }

    public void sendMessage(String channel, String message, Consumer<Boolean> sentHandler) {
        int id = messageId.getAndIncrement();
        JsonObject object = Json.createObjectBuilder()
                .add("id", id)
                .add("type", "message")
                .add("channel", channel)
                .add("text", message)
                .build();
        messageBuffer.put(id, sentHandler);
        socket.send(object);
    }

    public void setMessageListener(Consumer<MessageEvent> listener) {
        socket.setHandler(object -> {
            LOG.debug("{}", object.toString());
            if (object.containsKey("type")) {
                handleEvent(object, listener);
            } else {
                if (object.containsKey("reply_to")) {
                    handleSentConfirmation(object);
                }
            }

        });
    }

    private void handleEvent(JsonObject object, Consumer<MessageEvent> listener) {
        String eventType = object.getString("type");
        switch (eventType) {
            case "message":
                listener.accept(new MessageEvent(
                        Util.convertTimestamp(object.getString("ts")),
                        object.getString("channel"),
                        object.getString("user"),
                        object.getString("text")
                ));
                break;
            case "hello":
                LOG.info("established connection to Slack's Real Time Messaging API");
                break;
            case "error":
                JsonObject errorObject = object.getJsonObject("error");
                int errorCode = errorObject.getInt("code");
                String errorMessage = errorObject.getString("message");
                LOG.error("error code {}: {}", errorCode, errorMessage);
                break;
            default:
                break;
        }
    }

    private void handleSentConfirmation(JsonObject object) {
        int replyToValue = object.getInt("reply_to");
        Consumer<Boolean> sentHandler = messageBuffer.remove(replyToValue);

        boolean okValue = object.getBoolean("ok");
        if (okValue) {
            LOG.debug("message {} confirmed sent", replyToValue);
            sentHandler.accept(true);
        } else {
            JsonObject error = object.getJsonObject("error");
            LOG.error("error in sent confirmation response: {}", error);
            sentHandler.accept(false);
        }
    }

    public boolean addReaction(String emoji, String channel, Instant timestamp) {
        String serializedTimestamp = String.format("%d.%06d", timestamp.getEpochSecond(), timestamp.getNano() / 1000);
        String endpoint = "https://slack.com/api/reactions.add" + "?token=" + this.token + "&name=" + emoji + "&channel=" + channel + "&timestamp=" + serializedTimestamp;

        try {
            JsonObject jsonObject = Util.fetchResource(endpoint);
            if (jsonObject.getBoolean("ok")) {
                return true;
            }
            LOG.warn(jsonObject.toString());

        } catch (IOException ex) {
            LOG.error("failed to add reaction: {}", ex);
            return false;
        }

        return true;
    }


}
