package systems.cauldron.service.robot;

import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * @author Amann Malik
 */
public class BasicTest {

    private static final String SLACK_BOT_USER_TOKEN = "";
    private static final String DISCORD_BOT_USER_TOKEN = "";
    private static final String DISCORD_TEST_GUILD_ID = "";
    private static final String DISCORD_TEST_CHANNEL_ID = "";


    @Test
    public void basic_discord_connect() throws InterruptedException {
        DiscordSocket socket = new DiscordSocket(DISCORD_BOT_USER_TOKEN);
        socket.connect();
        Thread.sleep(500);
        socket.createMessage(DISCORD_TEST_CHANNEL_ID, "fuck all of you");
        Thread.sleep(500);
        socket.disconnect();
    }


    @Test
    public void something() throws InterruptedException {
        SlackSocket client = new SlackSocket(SLACK_BOT_USER_TOKEN);
        client.connect();
        //client.sendMessage("", "test", Assert::assertTrue);
        Thread.sleep(5000);
        client.disconnect();
    }

    @Test
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
