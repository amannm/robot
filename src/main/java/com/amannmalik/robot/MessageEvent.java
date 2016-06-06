package com.amannmalik.robot;

import java.time.Instant;

/**
 * @author Amann Malik
 */
public class MessageEvent {

    public final Instant timestamp;
    public final String channel;
    public final String user;
    public final String text;

    public MessageEvent(Instant timestamp, String channel, String user, String text) {
        this.timestamp = timestamp;
        this.channel = channel;
        this.user = user;
        this.text = text;
    }
}
