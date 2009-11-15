package hudson.plugins.ircbot.v2;

import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageListener;

public class IRCChannel implements IMChat {

	private final String channelName;
	private final PircConnection connection;

	public IRCChannel(String channelName, PircConnection connection) {
		this.channelName = channelName;
		this.connection = connection;
	}
	
	@Override
	public String getNickName(String senderId) {
		return senderId;
	}

	@Override
	public boolean isMultiUserChat() {
		return true;
	}

	@Override
	public void addMessageListener(IMMessageListener listener) {
		this.connection.addMessageListener(this.channelName, listener);
	}
	
	@Override
	public void removeMessageListener(IMMessageListener listener) {
		this.connection.removeMessageListener(this.channelName, listener);
	}

	@Override
	public void sendMessage(String message) throws IMException {
		this.connection.sendIMMessage(channelName, message);
	}
}
