package com.amannmalik.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
public class SlackMessageGateway extends JsonSocket {

    private static final Logger LOG = LoggerFactory.getLogger(SlackMessageGateway.class);

    private static final String SLACK_RTM_START_URL = "https://slack.com/api/rtm.start";

    private final String token;
    private final Consumer<SlackMessageEvent> messageHandler;

    private final AtomicInteger messageId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Consumer<Boolean>> messageBuffer = new ConcurrentHashMap<>();

    public SlackMessageGateway(String token) {
        this(token, message->{});
    }

    public SlackMessageGateway(String token, Consumer<SlackMessageEvent> messageHandler) {
        super(fetchServerEndpointUrl(token), 5L);
        this.token = token;
        this.messageHandler = messageHandler;
    }


    @Override
    public void close() {
        for (Integer id : messageBuffer.keySet()) {
            Consumer<Boolean> remove = messageBuffer.remove(id);
            remove.accept(false);
        }
        super.close();
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
        super.sendMessage(object);
    }


    @Override
    protected void handleMessage(JsonObject message) {
        LOG.debug("{}", message.toString());

        if (message.containsKey("type")) {
            String eventType = message.getString("type");
            switch (eventType) {
                case "message":
                    this.messageHandler.accept(new SlackMessageEvent(
                            Util.convertTimestamp(message.getString("ts")),
                            message.getString("channel"),
                            message.getString("user"),
                            message.getString("text")
                    ));
                    break;
                case "hello":
                    LOG.info("established connection to Slack's Real Time Messaging API");
                    break;
                case "error":
                    JsonObject errorObject = message.getJsonObject("error");
                    int errorCode = errorObject.getInt("code");
                    String errorMessage = errorObject.getString("message");
                    LOG.error("error code {}: {}", errorCode, errorMessage);
                    break;
                default:
                    break;
            }
        } else {
            if (message.containsKey("reply_to")) {
                handleSentConfirmation(message);
            }
        }
    }

    @Override
    protected void handleDisconnect(int closeCode, String closeReasonPhrase) {
        //TODO: does Slack put any useful information here?
    }

    private static URI fetchServerEndpointUrl(String token) {
        try {
            JsonObject metadata = Util.fetchResource(SLACK_RTM_START_URL + "?token=" + token);
            String websocketUrl = metadata.getString("url");
            return new URI(websocketUrl);
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
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
