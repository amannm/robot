package com.amannmalik.robot;

import org.junit.Test;

import java.time.Instant;

/**
 * @author Amann Malik
 */
public class BasicTest {

    private static final String SLACK_BOT_USER_TOKEN = "";
    private static final String DISCORD_BOT_USER_TOKEN = "";

    @Test
    public void basic_discord_connect() {

    }



    //@Test
    public void something() throws InterruptedException {
        SlackSocket client = new SlackSocket(SLACK_BOT_USER_TOKEN);
        client.connect();
        //client.sendMessage("", "test", Assert::assertTrue);
        Thread.sleep(5000);
        client.disconnect();
    }

    //@Test
    public void react() throws InterruptedException {

        int secondsPart = 1456353715;
        int nanoPart = 102 * 1000;
        Instant parsed = Instant.ofEpochSecond(secondsPart, nanoPart);

        String channel = "";

        SlackSocket client = new SlackSocket(SLACK_BOT_USER_TOKEN);
        client.connect();
        client.addReaction("thumbsup", channel, parsed);
        Thread.sleep(5000);
        client.disconnect();

    }
}
