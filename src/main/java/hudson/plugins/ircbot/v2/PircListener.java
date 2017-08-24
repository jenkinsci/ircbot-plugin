package hudson.plugins.ircbot.v2;

import hudson.plugins.im.IMConnectionListener;
import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.lang.ArrayUtils;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.InviteEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickAlreadyInUseEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;

/**
 * PircBot listener to react to certain IRC events.
 *
 * @author kutzi (original)
 * @author $Author: kutzi $ (last change)
 */
public class PircListener extends ListenerAdapter<PircBotX> {

	private static final Logger LOGGER = Logger.getLogger(PircListener.class
			.getName());

	@SuppressWarnings(value = "DM_STRING_CTOR", justification = "we want a new instance here to enable reference comparison")
	public static final String CHAT_ESTABLISHER = new String(
			"<<<ChatEstablisher>>>");

	private final List<IMConnectionListener> listeners = new CopyOnWriteArrayList<IMConnectionListener>();

	private final List<MessageListener> msgListeners = new CopyOnWriteArrayList<MessageListener>();

	private final List<JoinListener> joinListeners = new CopyOnWriteArrayList<JoinListener>();

	private final List<InviteListener> inviteListeners = new CopyOnWriteArrayList<InviteListener>();

	private final List<PartListener> partListeners = new CopyOnWriteArrayList<PartListener>();

	volatile boolean explicitDisconnect = false;

	@java.lang.SuppressWarnings("unused")
	private final PircBotX pircBot;
	private final String nick;
	private static final String onlyIndicator = "only";
	private final String fmtIndicator = " set color ";

	/**
	 * PircListener constructor
	 * @param pircBot pircBot
	 * @param nick nickname of a user
	 */
	public PircListener(PircBotX pircBot, String nick) {
		this.pircBot = pircBot;
		this.nick = nick;
	}

	/**
	 * Controls a message sent from PircBotx
	 * when event has only or format indicator, 
	 * message is not send it to other event listener, but modifies in the IRC plugin.
	 * 
	 * @param event user's is received via PircBotx
	 * {@inheritDoc}
	 */
	@Override
	public void onMessage(MessageEvent<PircBotX> event) {
		if (event.getMessage().contains(onlyIndicator)) {
			onMultiMessage(event);
		} else if (event.getMessage().contains(fmtIndicator)) {
			onFormatMessage(event);
		} else {
			for (MessageListener l : this.msgListeners) {
				if (l.target.equals(event.getChannel().getName())) {
					l.listener.onMessage(new IMMessage(event.getUser()
							.getNick(), event.getChannel().getName(), event
							.getMessage()));
				}
			}
		}
	}

	/**
	 * Gets usernames from message passed by PircBotx (with "only" flag).
	 * 
	 * @param msg
	 * @param noUser
	 * @return list of usernames that were passed with the "only" flag
	 */
	public static LinkedHashSet<String> getRecipients(String msg, String noUser) {
		// get the index of the character after the space after the indicator
		// !jenkins ... only asdf,qwert
		// ^
		int index = msg.indexOf(onlyIndicator) + onlyIndicator.length() + 1;
		if (index >= msg.length()) {
			LOGGER.warning("No recipients passed. Usage: !jenkins ... only user1,user2,...");
			return new LinkedHashSet<String>();
		}

		// grabbing recipients from message and removing whitespace (replaceAll)
		String args = msg.substring(index, msg.length()).replaceAll("\\s+", "");
		LinkedHashSet<String> ret = new LinkedHashSet<String>(Arrays.asList(args.split(",")));
		ret.remove(noUser);

		return ret;
	}

	// Controls color format of a message.
	// Message looks like this.
	// !jenkins set color THEME1
	// !jenkins set color regex COLORS(e.g, RED, GREED)
	private void onFormatMessage(GenericMessageEvent<PircBotX> event) {
		String msg = event.getMessage();
		String nick = event.getUser().getNick();

		// parse out jenkins prefix, nickname, pattern and color from message
		// !jenkins pattern color
		String[] tokens = msg.split(" ");
		LOGGER.info("----- start ------");

		String color;
		if (tokens.length == 5) {
			// token 0: jenkins prefix
			// token 1: regex pattern
			// token 2: color

			// check for color code in Colors to see if it can be applied
			color = IRCColors.lookup(tokens[4]);
			if (color != null) {
				IRCColorizer.setter(nick, tokens[3], tokens[4]);
				LOGGER.info("FORMAT " + nick + " " + tokens[3] + " "
						+ tokens[4]);
			} else {
				LOGGER.warning("color not found!\n");
			}
		} else if (tokens.length == 4) {
			if (tokens[3].equals("CLEAR")) {
				IRCColorizer.cleanUserPattern(nick);
			} else if (tokens[3].startsWith("THEME")) {
				IRCColorizer.changeTheme(tokens[3]);
			}
		} else {
			LOGGER.warning("could not set color! wrong number of tokens\n");
		}

	}

	/**
	 * Gets part of message from irc that does not include any "only" params.
	 * 
	 * @param msg
	 * @return message sent from irc up to the only indicator ("only")
	 */
	public static String getMultiMessage(String msg) {
		return msg.substring(0, msg.indexOf(onlyIndicator));
	}

	private void onMultiMessage(GenericMessageEvent<PircBotX> event) {
		String msg = event.getMessage();
		ArrayList<String> recipients = new ArrayList<String>();
		recipients.add(event.getUser().getNick());
		// add all recipients to list
		recipients.addAll(getRecipients(msg, recipients.get(0)));
		// modify message to not contain only
		msg = getMultiMessage(msg);
		if (msg.contains(onlyIndicator)) {
			LOGGER.warning("Multiple only indicators in message! I'm ignoring your command!");
			return;
		}
		for (String r : recipients) {
			// create a new private message event to dispatch to user
			PrivateMessageEvent<PircBotX> newEvent = new PrivateMessageEvent<PircBotX>(
					event.getBot(), event.getBot().getUserChannelDao()
							.getUser(r), msg);
			onPrivateMessage(newEvent);
		}
	}

	/**
	 * Controls a private message sent from PircBotx,
	 * but when the only indicator is sent up then the IRC plugin controls its message.
	 * 
	 * @param event user's is received via PircBotx
	 * {@inheritDoc}
	 */
	@Override
	public void onPrivateMessage(PrivateMessageEvent<PircBotX> event) {
		String sender = event.getUser().getNick();
		String message = event.getMessage();
		if (message.contains(onlyIndicator)) {
			onMultiMessage(event);
		} else {
			for (MessageListener l : this.msgListeners) {
				if (this.nick.equals(l.target)) {
					if (l.sender == CHAT_ESTABLISHER || sender.equals(l.sender)) {
						l.listener.onMessage(new IMMessage(sender, this.nick,
								message));
					}
				}
			}
		}
	}

	/**
	 * Someone send me a notice. Possibly NickServ after identifying.
	 * 
	 * @param event user's is received via PircBotx
	 */
	@Override
	public void onNotice(NoticeEvent<PircBotX> event) {
		String sourceNick = event.getUser().getNick();
		String notice = event.getMessage();
		LOGGER.info("Notice from " + sourceNick + ": '" + normalize(notice)
				+ "'");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onJoin(JoinEvent<PircBotX> event) {
		String sender = event.getUser().getNick();
		for (JoinListener l : this.joinListeners) {
			if (this.nick.equals(sender)) {
				l.channelJoined(event.getChannel().getName());
			}
		}
	}

	@Override
	public void onPart(PartEvent<PircBotX> event) {
		String sender = event.getUser().getNick();
		for (PartListener l : this.partListeners) {
			if (this.nick.equals(sender)) {
				l.channelParted(event.getChannel().getName());
			}
		}
	}

	@Override
	public void onKick(KickEvent<PircBotX> event) {
		String recipientNick = event.getRecipient().getNick();
		for (PartListener l : this.partListeners) {
			if (this.nick.equals(recipientNick)) {
				l.channelParted(event.getChannel().getName());
			}
		}
	}

	@Override
	public void onServerResponse(ServerResponseEvent<PircBotX> event) {
		int code = event.getCode();

		if (code == 433) {
			return; // should be handled by onNickAlreadyInUse
		}

		if (code >= 400 && code <= 599) {
			LOGGER.warning("IRC server responded error " + code + " Message:\n"
					+ event.getParsedResponse());
		}
	}

	public void onNickChange(NickChangeEvent<PircBotX> event) {
		LOGGER.info("Nick '" + event.getOldNick()
				+ "' was changed. Now it is used nick '" + event.getNewNick()
				+ "'.");
	}

	public void onNickAlreadyInUse(NickAlreadyInUseEvent<PircBotX> event) {
		LOGGER.warning("Nick '" + nick + "' is already in use ");
	}

	@Override
	public void onDisconnect(DisconnectEvent<PircBotX> event) {

		if (!explicitDisconnect) {
			for (IMConnectionListener l : this.listeners) {
				l.connectionBroken(null);
			}
		}
		explicitDisconnect = false;
	}

	@Override
	public void onInvite(InviteEvent<PircBotX> event) {
		for (InviteListener listener : inviteListeners) {
			listener.inviteReceived(event.getChannel(), event.getUser());
		}
	}

	// Note that the add/removeXyzListener methods needn't be synchronized
	// because of the CopyOnWriteLists

	public void addConnectionListener(IMConnectionListener listener) {
		this.listeners.add(listener);
	}

	public void removeConnectionListener(IMConnectionListener listener) {
		this.listeners.remove(listener);
	}

	public void addMessageListener(String target, IMMessageListener listener) {
		this.msgListeners.add(new MessageListener(target, listener));
	}

	public void addMessageListener(String target, String sender,
			IMMessageListener listener) {
		this.msgListeners.add(new MessageListener(target, sender, listener));
	}

	public void removeMessageListener(String target, IMMessageListener listener) {
		this.msgListeners.remove(new MessageListener(target, listener));
	}

	public void addJoinListener(JoinListener listener) {
		this.joinListeners.add(listener);
	}

	public void removeJoinListener(JoinListener listener) {
		this.joinListeners.remove(listener);
	}

	public void addInviteListener(InviteListener listener) {
		this.inviteListeners.add(listener);
	}

	public void removeInviteListener(InviteListener listener) {
		this.inviteListeners.remove(listener);
	}

	public void addPartListener(PartListener listener) {
		this.partListeners.add(listener);
	}

	public void removePartListener(PartListener listener) {
		this.partListeners.remove(listener);
	}

	private static final class MessageListener {
		private final String target;
		private final String sender;
		private final IMMessageListener listener;

		public MessageListener(String expectedMessageTarget,
				IMMessageListener listener) {
			this.target = expectedMessageTarget;
			this.sender = null;
			this.listener = listener;
		}

		public MessageListener(String expectedMessageTarget,
				String expectedMessageSender, IMMessageListener listener) {
			this.target = expectedMessageTarget;
			this.sender = expectedMessageSender;
			this.listener = listener;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((listener == null) ? 0 : listener.hashCode());
			result = prime * result
					+ ((target == null) ? 0 : target.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MessageListener other = (MessageListener) obj;
			if (listener == null) {
				if (other.listener != null)
					return false;
			} else if (!listener.equals(other.listener))
				return false;
			if (target == null) {
				if (other.target != null)
					return false;
			} else if (!target.equals(other.target))
				return false;
			return true;
		}
	}

	private static String normalize(String ircMessage) {
		String msg = ircMessage.replace("\u0001", "");
		msg = msg.replace("\u0002", "");
		msg = msg.replace("\u0016", "");
		msg = msg.replace("\u000F", "");

		return msg;
	}

	public interface JoinListener {
		/**
		 * Is called when the ircbot joins a channel.
		 */
		void channelJoined(String channelName);
	}

	public interface InviteListener {
		/**
		 * Is called when the ircbot is invited to a channel.
		 */
		void inviteReceived(String channelName, String inviter);
	}

	public interface PartListener {
		/**
		 * Is called when the ircbot is disconnected (leaves or is kicked) from
		 * a channel.
		 */
		void channelParted(String channelName);
	}

}