package com.amannmalik.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.plugin.dom.exception.InvalidStateException;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Amann Malik
 */
public class DiscordSocket {

    //TODO: properly think about the concurrency situations


    private static final Logger LOG = LoggerFactory.getLogger(DiscordSocket.class);

    private static final String DISCORD_GATEWAY_RESOLUTION_URL = "https://discordapp.com/api/gateway/bot";

    private static final long SOCKET_CONNECTION_TIMEOUT = 5000L;

    private final String token;
    private final JsonSocket socket;

    private URI currentServerUri;

    private PeriodicTask heartbeatTask = new PeriodicTask();
    private int currentHeartbeatInterval = -1;
    private boolean waitingForHeartbeatAcknowledgement = false;

    private AtomicInteger currentSequenceNumber = new AtomicInteger(-1);

    private final CyclicBarrier heartbeatLatch = new CyclicBarrier(2);
    private final CyclicBarrier sessionLatch = new CyclicBarrier(2);


    private DiscordGatewaySession currentSession;

    public DiscordSocket(String token) {
        this.token = token;
        this.socket = new JsonSocket(this::handleMessage, this::handleDisconnect);
    }

    public void connect() {
        LOG.info("initializing connection...");

        this.currentServerUri = fetchServerUrl(token);

        this.socket.open(this.currentServerUri, SOCKET_CONNECTION_TIMEOUT);
        LOG.info("establishing heartbeat...");
        try {
            heartbeatLatch.await(5000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
        heartbeatLatch.reset();

        if(this.currentSession == null) {
            LOG.info("creating new session...");
            sendIdentify();
        } else {
            LOG.info("resuming existing session...");
            sendResume(this.currentSession.id);
        }
        try {
            sessionLatch.await(5000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        sessionLatch.reset();

        LOG.info("connection established");
    }


    //TODO: interrupt and cleanup any  waiting threads
    public void disconnect() {
        this.socket.close(1000, "client requested disconnection");
        if(this.currentSession != null && this.currentSession.isReady()) {
            this.currentSession.setReady(false);
        }
    }


    private void handleMessage(JsonObject message) {
        int opCode = message.getInt("op");
        switch (opCode) {
            case 0: {
                //Event Dispatch
                int sequenceNumber = message.getInt("s");
                currentSequenceNumber.set(sequenceNumber);
                String eventName = message.getString("t");
                JsonObject eventData = message.getJsonObject("d");
                switch (eventName) {
                    case "READY": {
                        if (currentSession != null) {
                            throw new IllegalStateException("existing session state encountered during READY event");
                        }
                        String sessionId = eventData.getString("session_id");
                        currentSession = new DiscordGatewaySession(sessionId);
                        List<String> guilds = eventData.getJsonArray("guilds").stream().map(v->(JsonObject)v).map(o->o.getString("id")).collect(Collectors.toList());
                        currentSession.setGuilds(guilds);
                        currentSession.setReady(true);
                        try {
                            sessionLatch.await(1L, TimeUnit.SECONDS);
                        } catch (InterruptedException | TimeoutException | BrokenBarrierException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    break;
                    case "RESUMED": {
                        if (currentSession == null || currentSession.isReady()) {
                            throw new IllegalStateException("unexpected session state encountered during RESUMED event");
                        }
                        currentSession.setReady(true);
                        try {
                            sessionLatch.await(1L, TimeUnit.SECONDS);
                        } catch (InterruptedException | TimeoutException | BrokenBarrierException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    break;
                }
            }
            break;
            case 1: {
                //Heartbeat Request
                //TODO: properly understand protocol behavior. here we are resetting the heartbeat timer to 0 then immediately responding with a heartbeat
                heartbeatTask.start(this.currentHeartbeatInterval, this::sendHeartbeat);
                sendHeartbeat();
            }
            break;
            case 7: {
                //Reconnect Request
                socket.close(1000, "server requested client reconnect");
                socket.open(this.currentServerUri, SOCKET_CONNECTION_TIMEOUT);
            }
            break;
            case 9: {
                //TODO: all this shit
                //Invalid Session (3)
                if (currentSession == null) {
                    // IDENTIFY failure
                    throw new UnsupportedOperationException("IDENTIFY command failed");
                } else {
                    //TODO: do we always need to check if "d" exists?
                    boolean isResumable = message.containsKey("d") && message.getBoolean("d");
                    if (!currentSession.isReady()) {
                        // RESUME failure
                        throw new UnsupportedOperationException("RESUME command failed");
                    } else {
                        // out of process session invalidation
                        throw new UnsupportedOperationException("active session was invalidated");
                    }
                }
            }
            //break;
            case 10: {
                //Hello
                //TODO: properly understand protocol behavior. here we are waiting the entire interval before sending our initial heartbeat
                JsonObject eventData = message.getJsonObject("d");
                this.currentHeartbeatInterval = eventData.getInt("heartbeat_interval");
                heartbeatTask.start(this.currentHeartbeatInterval, this::sendHeartbeat);
                try {
                    heartbeatLatch.await(1L, TimeUnit.SECONDS);
                } catch (InterruptedException | TimeoutException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }
            break;
            case 11: {
                //Heartbeat ACK
                if (waitingForHeartbeatAcknowledgement) {
                    waitingForHeartbeatAcknowledgement = false;
                } else {
                    throw new InvalidStateException("received unexpected heartbeat acknowledgement");
                }
            }
            break;
            default:
                throw new RuntimeException("received unexpected op code: " + opCode);
        }

    }

    private void handleDisconnect(int closeCode, String closeReasonPhrase) {
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
        LOG.info("socket connection closed by server: " + closeReasonPhrase);
        throw new RuntimeException(closeReasonPhrase);
    }


    private void sendResume(String sessionId) {

        JsonObject object = Json.createObjectBuilder()
                .add("op", 6)
                .add("d", Json.createObjectBuilder()
                        .add("token", this.token)
                        .add("session_id", sessionId)
                        .add("seq", this.currentSequenceNumber.get())
                        .build()
                ).build();

        socket.send(object);
    }

    private void sendIdentify() {

        JsonObject object = Json.createObjectBuilder()
                .add("op", 2)
                .add("d", Json.createObjectBuilder()
                        .add("token", this.token)
                        .add("properties", Json.createObjectBuilder()
                                .add("$os", "linux")
                                .add("$browser", "robot")
                                .add("$device", "robot")
                                .build()
                        ).build()
                ).build();

        socket.send(object);
    }

    private void sendHeartbeat() {
        if (waitingForHeartbeatAcknowledgement) {
            this.socket.close(1008, "heartbeat period elapsed without receiving heartbeat ACK");
            //TODO: better handling
            throw new RuntimeException("heartbeat period elapsed without receiving heartbeat ACK");
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("op", 1);
        if (currentSequenceNumber.get() == -1) {
            builder.addNull("d");
        } else {
            builder.add("d", currentSequenceNumber.get());
        }
        this.socket.send(builder.build());
    }

    private static URI fetchServerUrl(String token) {
        HashMap<String, String> headers = new HashMap<>(1);
        headers.put("Authorization", "Bot " + token);
        try {
            JsonObject metadata = Util.fetchResource(DISCORD_GATEWAY_RESOLUTION_URL, headers);
            String websocketUrl = metadata.getString("url");
            int shardCount = metadata.getInt("shards");
            //TODO: enable sharding use cases?
            return new URI(websocketUrl + "?v=6&encoding=json");
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

}
