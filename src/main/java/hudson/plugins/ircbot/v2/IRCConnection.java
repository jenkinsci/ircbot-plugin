package hudson.plugins.ircbot.v2;

import hudson.Util;
import hudson.plugins.im.AuthenticationHolder;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionListener;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessage;
import hudson.plugins.im.IMMessageListener;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMPresence;
import hudson.plugins.im.bot.Bot;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.plugins.ircbot.IrcPublisher.DescriptorImpl;
import hudson.plugins.ircbot.v2.PircConnection.JoinListener;
import hudson.plugins.ircbot.v2.PircConnection.InviteListener;
import hudson.plugins.ircbot.v2.PircConnection.PartListener;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;

public class IRCConnection implements IMConnection, JoinListener, InviteListener, PartListener {

	private static final Logger LOGGER = Logger.getLogger(IRCConnection.class.getName());
	
	private final DescriptorImpl descriptor;
	private final AuthenticationHolder authentication;
	private PircConnection pircConnection;

	private List<IMMessageTarget> groupChats;

    private final Map<String, Bot> bots = new HashMap<String, Bot>();
	
	private final Map<String, Bot> privateChats = new HashMap<String, Bot>();

	public IRCConnection(DescriptorImpl descriptor, AuthenticationHolder authentication) {
		this.descriptor = descriptor;
		this.authentication = authentication;
		
		if (descriptor.getDefaultTargets() != null) {
			this.groupChats = descriptor.getDefaultTargets();
		} else {
			this.groupChats = Collections.emptyList();
		}
	}
	
	@Override
	public void close() {
		if (this.pircConnection != null && this.pircConnection.isConnected()) {
                    this.pircConnection.removeJoinListener(this);
                    this.pircConnection.removePartListener(this);
                    this.pircConnection.removeInviteListener(this);
			this.pircConnection.disconnect();
			this.pircConnection.dispose();
		}
	}

	@Override
	public boolean isConnected() {
		return this.pircConnection != null && this.pircConnection.isConnected();
	}

	@Override
	public boolean connect() {
		try {
			this.pircConnection = new PircConnection(this.descriptor.getNick(), this.descriptor.isUseNotice());
			
			// always use UTF-8; don't depend on the JVM's default encoding
			this.pircConnection.setEncoding("UTF-8");
			
			this.pircConnection.connect(this.descriptor.getHost(), this.descriptor.getPort(), this.descriptor.getPassword());
			LOGGER.info("connected to IRC");
			this.pircConnection.addJoinListener(this);
            this.pircConnection.addInviteListener(this);
            this.pircConnection.addPartListener(this);
			
	        final String nickServPassword = this.descriptor.getNickServPassword();
            if(Util.fixEmpty(nickServPassword) != null) {
                this.pircConnection.identify(nickServPassword);
            }
			
			for (IMMessageTarget groupChatName : this.groupChats) {
				try {
					getGroupChat(groupChatName);
				} catch (Exception e) {
					// if we got here, the IRC connection could be established, but probably the channel name
					// is invalid
					LOGGER.warning("Unable to connect to channel '" + groupChatName + "'.\n"
							+ "Message: " + ExceptionHelper.dump(e));
				}
			}
			
			pircConnection.addMessageListener(this.descriptor.getNick(),
			        PircConnection.CHAT_ESTABLISHER, new ChatEstablishedListener());
			
			return true;
		} catch (NickAlreadyInUseException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		} catch (IOException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		} catch (IrcException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		}
		return false;
	}

    private GroupChatIMMessageTarget getGroupChatForChannelName(String channelName) {
        for (IMMessageTarget messageTarget : groupChats) {
            if (!(messageTarget instanceof GroupChatIMMessageTarget)) {
                continue;
            }
            GroupChatIMMessageTarget groupChat = (GroupChatIMMessageTarget) messageTarget;
            if (groupChat.getName().equals(channelName)) {
                return groupChat;
            }
        }
        return null;
    }
    
	private void getGroupChat(IMMessageTarget groupChat) {
		if (! (groupChat instanceof GroupChatIMMessageTarget)) {
			LOGGER.warning(groupChat + " is no channel. Cannot join.");
			return;
		}
		
		GroupChatIMMessageTarget channel = (GroupChatIMMessageTarget)groupChat;
	    LOGGER.info("Trying to join channel " + channel.getName());
	    
	    if (channel.hasPassword()) {
	    	this.pircConnection.joinChannel(channel.getName(), channel.getPassword());
	    } else {
	    	this.pircConnection.joinChannel(channel.getName());
	    }
	}
	
    @Override
    public void channelJoined(String channelName) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "Joined to channel {0} but I don''t seem to belong here", channelName);
            return;
        }
        Bot bot = new Bot(new IRCChannel(channelName, this.pircConnection),
                this.descriptor.getNick(), this.descriptor.getHost(),
                this.descriptor.getCommandPrefix(), this.authentication);
        bots.put(channelName, bot);
        LOGGER.log(Level.INFO, "Joined channel {0} and bot registered", channelName);
    }

    @Override
    public void inviteReceived(String channelName, String inviter) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "Invited to channel {0} but I don''t seem to belong here", channelName);
            return;
        }
        LOGGER.log(Level.INFO, "Invited to join {0}", channelName);
        getGroupChat(groupChat);
    }

    @Override
    public void channelParted(String channelName) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "I''m leaving {0} but I never seemed to belong there in the first place", channelName);
            return;
        }
        if (bots.containsKey(channelName)) {
            Bot bot = bots.remove(channelName);
            bot.shutdown();
            LOGGER.log(Level.INFO, "I have left {0}", channelName);
        } else {
            LOGGER.log(Level.INFO, "No bot ever registered for {0}", channelName);
        }
    }

	@Override
	public void addConnectionListener(IMConnectionListener listener) {
		if (this.pircConnection != null)
		 this.pircConnection.addConnectionListener(listener);
	}

	@Override
	public void removeConnectionListener(IMConnectionListener listener) {
		if (this.pircConnection != null)
			this.pircConnection.removeConnectionListener(listener);
	}

	@Override
	public void send(IMMessageTarget target, String text) throws IMException {
		this.pircConnection.sendIMMessage(target.toString(), text);
	}

	@Override
	public void setPresence(IMPresence presence, String statusMessage)
			throws IMException {
		if (presence.ordinal() >= IMPresence.OCCUPIED.ordinal()) {
			if (statusMessage == null || statusMessage.trim().length() == 0) {
				statusMessage = "away";
			}
			this.pircConnection.sendRawLineViaQueue("AWAY " + statusMessage);
		} else {
			this.pircConnection.sendRawLineViaQueue("AWAY");
		}
	}

	/**
	 * Listens for chat requests from singular users (i.e. private chat requests).
	 * Creates a new bot for each request, if we're not already in a chat with
	 * that user.
	 */
	private class ChatEstablishedListener implements IMMessageListener {

		@Override
		public void onMessage(IMMessage message) {
			if(!message.getTo().equals(descriptor.getNick())) {
				throw new IllegalStateException("Intercepted message to '" + message.getTo()
						+ "'. That shouldn't happen!");
			}
			
			synchronized (privateChats) {
				if (privateChats.containsKey(message.getFrom())) {
					// ignore. We're already in a chat with partner
					return;
				}
				
				IRCPrivateChat chat = new IRCPrivateChat(pircConnection, descriptor.getUserName(), message.getFrom());
				Bot bot = new Bot(chat,
						descriptor.getNick(), descriptor.getHost(),
						descriptor.getCommandPrefix(), authentication);
			
				privateChats.put(message.getFrom(), bot);
				
				// we must replay this message as it could contain a command
				bot.onMessage(message);
			}
		}
	}
}
