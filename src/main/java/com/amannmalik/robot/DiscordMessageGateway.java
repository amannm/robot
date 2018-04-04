package com.amannmalik.robot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Amann Malik
 */
public class DiscordMessageGateway extends JsonSocket {


    public class HeartbeatTracker {

        private boolean lastSentBeatAcknowledged = false;

    }

    private static final Logger LOG = LoggerFactory.getLogger(DiscordMessageGateway.class);

    private static final String DISCORD_GATEWAY_URL = "https://discordapp.com/api/gateway/bot";

    private final String token;

    private final AtomicInteger messageId = new AtomicInteger(1);

    public DiscordMessageGateway(String token) {
        super(fetchServerUrl(token), 5L);
        this.token = token;
    }




    private void sendGatewayMessage(int opCode, JsonObject data) {
        JsonObject object = Json.createObjectBuilder()
                .add("op", opCode)
                .add("d", data)
                .build();
        sendMessage(object);
    }

    @Override
    protected void handleMessage(JsonObject object) {
        int opCode = object.getInt("op");
        JsonValue eventData = object.get("d");
        switch (opCode) {
            case 0:
                int sequenceNumber = object.getInt("s");
                String eventName = object.getString("t");
                break;
            case 1:
                //Heartbeat
                break;
            case 7:
                //Reconnect
                break;
            case 9:
                //Invalid Session
                break;
            case 10:
                //Hello
                JsonObject eventDataObject = (JsonObject) eventData;
                int heartbeatIntervalMilliseconds = eventDataObject.getInt("heartbeat_interval");
                //TODO
                break;
            case 11:
                //Heartbeat ACK
                break;
            default:
                break;
        }
    }

    @Override
    protected void handleDisconnect(int closeCode, String closeReasonPhrase) {
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

    private static URI fetchServerUrl(String token) {
        HashMap<String, String> headers = new HashMap<>(1);
        headers.put("Authorization", "Bot " + this.token);
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
