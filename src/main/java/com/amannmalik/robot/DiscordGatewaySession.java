package com.amannmalik.robot;

import java.util.Collections;
import java.util.List;

public class DiscordGatewaySession {

    public final String id;

    private boolean ready = false;

    private List<String> guilds = Collections.emptyList();

    public DiscordGatewaySession(String id) {
        this.id = id;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public List<String> getGuilds() {
        return guilds;
    }

    public void setGuilds(List<String> guilds) {
        this.guilds = guilds;
    }

}
