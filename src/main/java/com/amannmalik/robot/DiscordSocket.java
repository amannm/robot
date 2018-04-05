package com.amannmalik.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.plugin.dom.exception.InvalidStateException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Amann Malik
 */
public class DiscordSocket extends JsonSocket {

    //TODO: properly think about the concurrency situations


    public enum Status {
        CONNECTING,
        IDENTIFYING,
        RESUMING,
        READY,
        FAILED
    }

    private AtomicReference<Status> currentStatus = new AtomicReference<>(Status.CONNECTING);


    private static final Logger LOG = LoggerFactory.getLogger(DiscordSocket.class);

    private static final String DISCORD_GATEWAY_URL = "https://discordapp.com/api/gateway/bot";

    private final String token;

    private AtomicReference<String> sessionId = new AtomicReference<>(null);
    private AtomicInteger currentSequenceNumber = new AtomicInteger(-1);
    private AtomicBoolean waitingForHeartbeatAcknowledgement = new AtomicBoolean(true);



    public DiscordSocket(String token) {
        super(fetchServerUrl(token), 5L);
        this.token = token;
    }


    @Override
    protected void handleMessage(JsonObject object) {
        int opCode = object.getInt("op");
        JsonValue eventData = object.get("d");
        switch (opCode) {
            case 0:
                //Event Dispatch
                int sequenceNumber = object.getInt("s");
                currentSequenceNumber.set(sequenceNumber);
                String eventName = object.getString("t");
                processEvent(eventName, eventData);
                break;
            case 1:
                //Heartbeat Request
                heartbeatTask.start(currentHeartbeatInterval.get(), this::sendHeartbeat);
                break;
            case 7:
                //Reconnect Request
                disconnect(1000, "server requested disconnection and reconnection");
                break;
            case 9:
                //Invalid Session
                //TODO: actually understand this behavior

                boolean newSessionRequired = !object.containsKey("d") || object.isNull("d") || !object.getBoolean("d");
                switch(currentStatus.get()) {
                    case IDENTIFYING:
                        break;
                    case RESUMING:
                    case READY:
                        if(newSessionRequired) {
                        } else {
                            currentStatus.set(Status.RESUMING);
                            sendResume();
                        }
                        break;
                    default:
                        throw new InvalidStateException("received invalid session event in an unexpected status");
                }
                if()) {
                    sessionId.set(null);
                }
                break;
            case 10:
                //Hello
                JsonObject eventDataObject = (JsonObject) eventData;
                int heartbeatIntervalMilliseconds = eventDataObject.getInt("heartbeat_interval");
                currentHeartbeatInterval.set(heartbeatIntervalMilliseconds);
                heartbeatTask.start(currentHeartbeatInterval.get(), this::sendHeartbeat);
                if(sessionId.get() == null) {
                    sendIdentify();
                    currentStatus.compareAndSet(Status.CONNECTING, Status.IDENTIFYING);
                } else {
                    sendResume();
                    currentStatus.compareAndSet(Status.CONNECTING, Status.RESUMING);
                }
                break;
            case 11:
                //Heartbeat ACK
                if(!waitingForHeartbeatAcknowledgement.compareAndSet(true, false)) {
                    throw new InvalidStateException("received two heartbeat acknowledgements within a single heartbeat period");
                };
                break;
            default:
                break;
        }
    }

    private void resetConnectionState() {
        this.currentStatus.set(Status.CONNECTING);
        this.heartbeatTask.stop();
        this.currentHeartbeatInterval.set(-1);
        this.waitingForHeartbeatAcknowledgement.set(false);
    }

    private void resetSessionState() {
        this.sessionId.set(null);
        this.currentSequenceNumber.set(-1);
    }

    @Override
    protected void handleDisconnect(int closeCode, String closeReasonPhrase) {
        resetConnectionState();
        switch (closeCode) {
            case 4000: //unknown error -- We're not sure what went wrong. Try reconnecting?
                break;
            case 4001: //unknown opcode -- You sent an invalid Gateway opcode or an invalid payload for an opcode. Don't do that!
                break;
            case 4002: //decode error -- You sent an invalid payload to us. Don't do that!
                break;
            case 4003: //not authenticated -- You sent us a payload prior to identifying.
                break;
            case 4004: //authentication failed -- The account token sent with your identify payload is incorrect.
                break;
            case 4005: //already authenticated -- You sent more than one identify payload. Don't do that!
                break;
            case 4007: //invalid seq -- The sequence sent when resuming the session was invalid. Reconnect and start a new session.
                break;
            case 4008: //rate limited -- Woah nelly! You're sending payloads to us too quickly. Slow it down!
                break;
            case 4009: //session timeout -- Your session timed out. Reconnect and start a new one.
                break;
            case 4010: //invalid shard -- You sent us an invalid shard when identifying.
                break;
            case 4011: //sharding required -- The session would have handled too many guilds - you are required to shard your connection in order to connect.
                break;
            default:
                break;
        }
        //TODO: proper handling/recovery
        throw new RuntimeException(closeReasonPhrase);
    }


    private void processEvent(String eventName, JsonValue data) {
        switch(eventName) {
            case "READY":
                JsonObject object = (JsonObject) data;
                String sessionIdString = object.getString("session_id");
                sessionId.set(sessionIdString);
                if(!currentStatus.compareAndSet(Status.IDENTIFYING, Status.READY)) {
                    throw new IllegalStateException("received READY event while not in IDENTIFYING state");
                }
                break;
            case "RESUMED":
                if(!currentStatus.compareAndSet(Status.RESUMING, Status.READY)) {
                    throw new IllegalStateException("received RESUMED event while not in RESUMING state");
                }
                break;
            default:
                break;
        }
    }

    private void sendResume() {

        if(this.sessionId.get() == null) {
            throw new IllegalStateException("cannot resume non-existent session");
        }

        JsonObject object = Json.createObjectBuilder()
                .add("op", 6)
                .add("d", Json.createObjectBuilder()
                        .add("token", this.token)
                        .add("session_id", this.sessionId.get())
                        .add("seq", this.currentSequenceNumber.get())
                        .build()
                ).build();

        sendMessage(object);
    }

    private void sendIdentify() {

        JsonObject object = Json.createObjectBuilder()
                .add("op", 2)
                .add("d", Json.createObjectBuilder()
                    .add("token", this.token)
                    .add("properties", Json.createObjectBuilder()
                            .add("$os","linux")
                            .add("$browser","robot")
                            .add("$device","robot")
                            .build()
                    ).build()
            ).build();

        sendMessage(object);
    }

    private void sendHeartbeat() {
        if(!waitingForHeartbeatAcknowledgement.compareAndSet(true, false)) {
            disconnect(1008, "no heartbeat acknowledgement received within heartbeat interval");
            throw new RuntimeException("no heartbeat acknowledgement received within heartbeat interval");
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("op", 1);
        if(currentSequenceNumber.get() == -1) {
            builder.addNull("d");
        } else {
            builder.add("d", currentSequenceNumber.get());
        }
        sendMessage(builder.build());
    }

    private static URI fetchServerUrl(String token) {
        HashMap<String, String> headers = new HashMap<>(1);
        headers.put("Authorization", "Bot " + token);
        try {
            JsonObject metadata = Util.fetchResource(DISCORD_GATEWAY_URL, headers);
            String websocketUrl = metadata.getString("url");
            int shardCount = metadata.getInt("shards");
            //TODO: enable sharding use cases?
            return new URI(websocketUrl + "?v=6&encoding=json");
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

}
