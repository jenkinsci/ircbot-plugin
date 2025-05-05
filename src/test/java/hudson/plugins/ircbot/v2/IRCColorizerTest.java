package hudson.plugins.ircbot.v2;

import org.jvnet.hudson.test.Issue;

import org.junit.jupiter.api.Test;

import org.pircbotx.Colors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IRCColorizerTest {

    @Test
    @Issue("JENKINS-22360")
    void shouldColorizeKeywords() {
        String message = "Build job123 is STILL FAILING: https://server.com/build/42";
        String colorizedMessage = IRCColorizer.colorize(message);

        assertEquals("Build job123 is " + Colors.BOLD + Colors.RED + "STILL FAILING" + Colors.NORMAL + ": https://server.com/build/42", colorizedMessage);
    }

}
