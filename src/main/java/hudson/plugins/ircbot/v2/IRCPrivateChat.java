package hudson.plugins.ircbot.v2;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

/**
 * Representation of an IRC private (user-to-user) chat.
 * 
 * @author kutzi
 */
public class IRCPrivateChat implements IMChat {
	
	private final PircListener listener;
	private final String nick;
	private final String chatPartner;
    private IRCConnection connection;

	public IRCPrivateChat(IRCConnection connection, PircListener listener, String nick, String chatPartner) {
	    this.connection = connection;
		this.listener = listener;
		this.nick = nick;
		this.chatPartner = chatPartner;
	}

	//@Override
	public String getNickName(String senderId) {
		return senderId;
	}
	
	//@Override
	public String getIMId(String senderId) {
		return senderId;
	}

	//@Override
	public boolean isMultiUserChat() {
		return false;
	}
	
	//@Overrid
	public boolean isCommandsAccepted() {
	    return true;
	}
	
	//@Override
	public void addMessageListener(IMMessageListener listener) {
		this.listener.addMessageListener(this.nick, this.chatPartner, listener);
	}
	
	//@Override
	public void removeMessageListener(IMMessageListener listener) {
		this.listener.removeMessageListener(this.nick, listener);
	}

	//@Override
	public void sendMessage(String message) throws IMException {
		this.connection.send(chatPartner, message);
	}
}
