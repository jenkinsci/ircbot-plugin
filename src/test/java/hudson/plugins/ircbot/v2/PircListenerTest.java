package hudson.plugins.ircbot.v2;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;


public class PircListenerTest {
	
	private String msg = "!jenkins 112843667904431 only rtfreed2,sdavis18 ,  mickeymouse";
	private String dupedArgs = "!jenkins 112843667904431 only sdavis18,sdavis18,sdavis18";
	private String multiArgMsg = "!jenkins asdf qwerty wut,a,a,a,a|a|rtfreed2|   |  only sdavis18,minniemouse";
	
	//regular message parsing
	@Test
	public void getMultiMessageTest() {
		String msgExpected = "!jenkins 112843667904431 ";
		String multiMsg = PircListener.getMultiMessage(msg);
		
		assertEquals(msgExpected, multiMsg);
	}
	
	//regular recipient parsing
	@Test
	public void getRecipientsTest() {
		LinkedHashSet<String> users = new LinkedHashSet<String>(Arrays.asList("rtfreed2", "sdavis18", "mickeymouse"));
		LinkedHashSet<String> recipients = PircListener.getRecipients(msg, "");
		
		assertEquals(users, recipients);
	}
	
	//parsing recipients with multiple arguments to the command
	//also tests sender versus recipient functionality
	@Test
	public void getMultiArgRecipientTest(){
		LinkedHashSet<String> users = new LinkedHashSet<String>(Arrays.asList("sdavis18", "minniemouse"));
		LinkedHashSet<String> recipients = PircListener.getRecipients(multiArgMsg, "rtfreed2");
		assertEquals(users,recipients);
	}
	
	//parsing message with multiple arguments to the command
	@Test
	public void getMultiArgMessageTest(){
		String msgExpected = "!jenkins asdf qwerty wut,a,a,a,a|a|rtfreed2|   |  ";
		String multiMsg = PircListener.getMultiMessage(multiArgMsg);
		
		assertEquals(msgExpected, multiMsg);
	}
	
	//parsing message with no arguments, will generate a warning in console
	@Test
	public void getRecipientsNoArgsTest() {
		String noArgs = "!jenkins 112843667904431 only";
		LinkedHashSet<String> recipients = PircListener.getRecipients(noArgs, "");
		
		assertTrue(recipients.isEmpty());
	}
	
	//parsing recipients with duplicated users
	@Test
	public void getRecipientsDeDupeTest() {
		LinkedHashSet<String> recipients = PircListener.getRecipients(dupedArgs, "");
		
		assertEquals(recipients.size(), 1);
	}
	
	//parsing recipients with duplicated users AND testing sender vs recipient cross-checking
	@Test
	public void getRecipientsDeDupeNoUserTest() {
		LinkedHashSet<String> recipients = PircListener.getRecipients(dupedArgs, "sdavis18");
		
		assertEquals(recipients.size(), 0);
	}
}
