package com.amannmalik.robot;

import java.time.Instant;

/**
 * @author Amann Malik
 */
public class BasicTest {

    private static final String SLACK_BOT_USER_TOKEN = "";

    //@Test
    public void something() throws InterruptedException {
        try (SlackMessageGateway client = new SlackMessageGateway(SLACK_BOT_USER_TOKEN)) {
            //client.sendMessage("", "test", Assert::assertTrue);
            Thread.sleep(5000);
        }

    }

    //@Test
    public void react() throws InterruptedException {

        int secondsPart = 1456353715;
        int nanoPart = 102 * 1000;
        Instant parsed = Instant.ofEpochSecond(secondsPart, nanoPart);

        String channel = "";

        try (SlackMessageGateway client = new SlackMessageGateway(SLACK_BOT_USER_TOKEN)) {
            client.addReaction("thumbsup", channel, parsed);
            Thread.sleep(5000);
        }

    }
}
