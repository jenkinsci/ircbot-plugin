package hudson.plugins.ircbot.v2;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

public class IRCPrivateChat implements IMChat {
	
	private final PircConnection connection;
	private final String nick;
	private final String chatPartner;

	public IRCPrivateChat(PircConnection connection, String nick, String chatPartner) {
		super();
		this.connection = connection;
		this.nick = nick;
		this.chatPartner = chatPartner;
	}

	@Override
	public String getNickName(String senderId) {
		return senderId;
	}

	@Override
	public boolean isMultiUserChat() {
		return false;
	}
	@Override
	public void addMessageListener(IMMessageListener listener) {
		this.connection.addMessageListener(this.nick, this.chatPartner, listener);
	}
	
	@Override
	public void removeMessageListener(IMMessageListener listener) {
		this.connection.removeMessageListener(this.nick, listener);
	}

	@Override
	public void sendMessage(String message) throws IMException {
		this.connection.sendIMMessage(chatPartner, message);
	}
}
