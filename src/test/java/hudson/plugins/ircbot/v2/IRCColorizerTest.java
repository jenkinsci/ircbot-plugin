package hudson.plugins.ircbot.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.pircbotx.Colors;

public class IRCColorizerTest {

	@Test
	@Bug(22360)
	public void shouldColorizeKeywords() {
		String message = "Build job123 is STILL FAILING: https://server.com/build/42";
		String colorizedMessage = IRCColorizer.colorize(message);
		 
		assertEquals("Build job123 is " + Colors.BOLD + Colors.RED + "STILL FAILING" + Colors.NORMAL + ": https://server.com/build/42", colorizedMessage);
	}
	
}
