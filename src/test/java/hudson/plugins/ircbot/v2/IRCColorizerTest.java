package hudson.plugins.ircbot.v2;

import hudson.model.ResultTrend;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import org.fusesource.jansi.Ansi.Color;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.pircbotx.Colors;

public class IRCColorizerTest {
	
	@Test
	public void changeThemeTest() {
		assertEquals(IRCColorizer.changeTheme("THEME3"),"THEME3");
	}

	@Test
	public void changeThemeTest2() {
		IRCColorizer.changeTheme("THEME3");
		assertEquals(IRCColorizer.changeTheme("wut"),"THEME3");
	}

	@Test
	public void generateThemeTestOneColor() {
		String color = "BLUE";
		HashMap<ResultTrend, String> test = IRCColorizer.oneColorTheme(color);
		boolean answer = test.get(ResultTrend.FIXED).equals(Colors.BOLD + Colors.UNDERLINE + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.SUCCESS).equals(Colors.BOLD + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.FAILURE).equals(Colors.BOLD + Colors.UNDERLINE + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.STILL_FAILING).equals(Colors.BOLD + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.UNSTABLE).equals(Colors.BOLD + Colors.UNDERLINE + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.STILL_UNSTABLE).equals(Colors.BOLD + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.NOW_UNSTABLE).equals(Colors.BOLD + IRCColors.lookup(color));
		answer = answer && test.get(ResultTrend.ABORTED).equals(Colors.BOLD + IRCColors.lookup(color));
		assertTrue(answer);
	}

	@Test
	public void generateThemeTestDefault() {
		HashMap<ResultTrend, String> test = IRCColorizer.createDefault();
		boolean answer = test.get(ResultTrend.FIXED).equals(Colors.BOLD + Colors.UNDERLINE + Colors.GREEN);
		answer = answer && test.get(ResultTrend.SUCCESS).equals(Colors.BOLD + Colors.GREEN);
		answer = answer && test.get(ResultTrend.FAILURE).equals(Colors.BOLD + Colors.UNDERLINE + Colors.RED);
		answer = answer && test.get(ResultTrend.STILL_FAILING).equals(Colors.BOLD + Colors.RED);
		answer = answer && test.get(ResultTrend.UNSTABLE).equals(Colors.BOLD + Colors.UNDERLINE + Colors.BROWN);
		answer = answer && test.get(ResultTrend.STILL_UNSTABLE).equals(Colors.BOLD + Colors.BROWN);
		answer = answer && test.get(ResultTrend.NOW_UNSTABLE).equals(Colors.BOLD + Colors.MAGENTA);
		answer = answer && test.get(ResultTrend.ABORTED).equals(Colors.BOLD + Colors.LIGHT_GRAY);
		assertTrue(answer);
	}

	@Test
	public void generateThemeTestTheme2() {
		HashMap<ResultTrend, String> test = IRCColorizer.theme2();
		boolean answer = test.get(ResultTrend.FIXED).equals(Colors.BOLD + Colors.UNDERLINE + Colors.BLUE);
		answer = answer && test.get(ResultTrend.SUCCESS).equals(Colors.BOLD + Colors.BLUE);
		answer = answer && test.get(ResultTrend.FAILURE).equals(Colors.BOLD + Colors.UNDERLINE + Colors.RED);
		answer = answer && test.get(ResultTrend.STILL_FAILING).equals(Colors.BOLD + Colors.RED);
		answer = answer && test.get(ResultTrend.UNSTABLE).equals(Colors.BOLD + Colors.UNDERLINE + Colors.MAGENTA);
		answer = answer && test.get(ResultTrend.STILL_UNSTABLE).equals(Colors.BOLD + Colors.MAGENTA);
		answer = answer && test.get(ResultTrend.NOW_UNSTABLE).equals(Colors.BOLD + Colors.RED);
		answer = answer && test.get(ResultTrend.ABORTED).equals(Colors.BOLD + Colors.BLACK);

		assertTrue(answer);
	}
	@Test
	@Bug(22360)
	public void shouldColorizeKeywords() {
		String message = "Build job123 is STILL FAILING: https://server.com/build/42";
		String colorizedMessage = IRCColorizer.colorize(message);
		 
		assertEquals("Build job123 is " + Colors.BOLD + Colors.RED + "STILL FAILING" + Colors.NORMAL + ": https://server.com/build/42", colorizedMessage);
	}
	
	@Test
	//Check wether setter function could add new user
	public void setterTest1()
	{
		String nickname = "neverused";
		IRCColorizer.cleanUserPattern(nickname);
		int oldSize = IRCColorizer.getSize();
		String pattern = "Build";
		String color = "RED";
		IRCColorizer.setter(nickname, pattern, color);
		int curSize = IRCColorizer.getSize();
		assertEquals(oldSize + 1, curSize);
	}
	
	@Test
	//Check wether setter function could add new pattern->color to a specific user
	public void setterTest2()
	{
		String nickname = "neverused";
		IRCColorizer.cleanUserPattern(nickname);
		
		String pattern1 = "Build";
		String color1 = "RED";
		String pattern2 = "[0-9]{3}";
		String color2 = "BLUE";
		
		IRCColorizer.setter(nickname, pattern1, color1);
		int oldPatternSize = IRCColorizer.getSizeByNickName(nickname);

		IRCColorizer.setter(nickname, pattern2, color2);
		int curPatternSize = IRCColorizer.getSizeByNickName(nickname);
		assertEquals(oldPatternSize + 1, curPatternSize);
	}
	
	@Test
	//Check wether setter function could add an existing pattern->color to a specific user
	public void setterTest3()
	{
		String nickname = "neverused";
		IRCColorizer.cleanUserPattern(nickname);
		
		String pattern1 = "Build";
		String color1 = "RED";
		String pattern2 = "Build";
		String color2 = "BLUE";
		
		IRCColorizer.setter(nickname, pattern1, color1);
		int oldPatternSize = IRCColorizer.getSizeByNickName(nickname);

		IRCColorizer.setter(nickname, pattern2, color2);
		int curPatternSize = IRCColorizer.getSizeByNickName(nickname);
		assertEquals(oldPatternSize, curPatternSize);
	}
	
	
	
	@Test
	public void basicColorizeTest() {
		String nickName = "foo";
		String pattern = "Build";
		String color = "RED";
		
		String message = "Build job123 is STILL FAILING: https://server.com/build/42";
		
		IRCColorizer.setter(nickName, pattern, color);
		
		String colorizedMessage = IRCColorizer.colorize(nickName, message);
		System.out.println(colorizedMessage);
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/build/42", colorizedMessage);
	}
	
	@Test
	public void duplicateColorizeTest() {
		String nickName = "foo";
		String pattern = "Build";
		String color = "RED";
		
		String message = "Build job123 is STILL FAILING: https://server.com/Build/42";
		
		IRCColorizer.setter(nickName, pattern, color);
		
		String colorizedMessage = IRCColorizer.colorize(nickName, message);
//		System.out.println(colorizedMessage);
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
				Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage);
	}
	
	@Test
	public void duplicateNewLineColorizeTest() {
		String nickName = "foo";
		String pattern = "Build";
		String color = "RED\n";
		
		String message = "Build job123 is STILL FAILING: https://server.com/Build/42";
		
		IRCColorizer.setter(nickName, pattern, color);
		
		String colorizedMessage = IRCColorizer.colorize(nickName, message);
//		System.out.println(colorizedMessage);
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
				Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage);
	}
	
	@Test
	public void regexSimpleMatch1()
	{

		String nickName = "foo2";
		String pattern = "B[a-z]*d";
		String color = "RED\n";
		String message = "Build job123 is STILL FAILING: https://server.com/Bird/Build/42";
		IRCColorizer.setter(nickName, pattern, color);
		String colorizedMessage = IRCColorizer.colorize(nickName, message);
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
		Colors.RED + "Bird" + Colors.NORMAL + "/" + Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage);

	}
	
	@Test
	public void regexMultipleColorsTest()
	{

		String nickName = "foo6";
		String pattern1 = "B[a-z]*d";
		String color1 = "RED\n";
		
		String pattern2 = "[0-9]{3}";
		String color2 = "BLUE\n";
		
		String message = "Build job123 is STILL FAILING: https://server.com/Bird/Build/42";
		IRCColorizer.setter(nickName, pattern1, color1);
		IRCColorizer.setter(nickName, pattern2, color2);
		String colorizedMessage = IRCColorizer.colorize(nickName, message);
		//assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
		//		Colors.RED + "Bird" + Colors.NORMAL + "/" + Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage);

		
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job" + Colors.BLUE + "123" + Colors.NORMAL + " is STILL FAILING: https://server.com/"+
		Colors.RED + "Bird" + Colors.NORMAL + "/" + Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage);

	}
	
	@Test
	public void regexMultipleUserDifferentColorTest()
	{

		String nickName1 = "A";
		String pattern1 = "B[a-z]*d";
		String color1 = "RED\n";
		
		String nickName2 = "B";
		String pattern2 = "B[a-z]*d";
		String color2 = "BLUE\n";
		
		String message = "Build job123 is STILL FAILING: https://server.com/Bird/Build/42";
		
		IRCColorizer.setter(nickName1, pattern1, color1);
		IRCColorizer.setter(nickName2, pattern2, color2);
		
		String colorizedMessage1 = IRCColorizer.colorize(nickName1, message);
		String colorizedMessage2 = IRCColorizer.colorize(nickName2, message);
		
		assertEquals(Colors.RED + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
				Colors.RED + "Bird" + Colors.NORMAL + "/" + Colors.RED + "Build" + Colors.NORMAL + "/42", colorizedMessage1);
		assertEquals(Colors.BLUE + "Build" + Colors.NORMAL + " job123 is STILL FAILING: https://server.com/"+
				Colors.BLUE + "Bird" + Colors.NORMAL + "/" + Colors.BLUE + "Build" + Colors.NORMAL + "/42", colorizedMessage2);

	}
}
