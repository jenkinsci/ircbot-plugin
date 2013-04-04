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
import hudson.plugins.ircbot.v2.PircListener.InviteListener;
import hudson.plugins.ircbot.v2.PircListener.JoinListener;
import hudson.plugins.ircbot.v2.PircListener.PartListener;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.*;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;
import org.pircbotx.exception.NickAlreadyInUseException;

import static javax.net.ssl.SSLContext.getInstance;

/**
 * IRC specific implementation of an {@link IMConnection}.
 * 
 * @author kutzi
 */
public class IRCConnection implements IMConnection, JoinListener, InviteListener, PartListener {

	private static final Logger LOGGER = Logger.getLogger(IRCConnection.class.getName());
	
	private final DescriptorImpl descriptor;
	private final AuthenticationHolder authentication;
	private final PircBotX pircConnection = new PircBotX();
	private final PircListener listener;

	private List<IMMessageTarget> groupChats;

    private final Map<String, Bot> bots = new HashMap<String, Bot>();
	
	private final Map<String, Bot> privateChats = new HashMap<String, Bot>();

	public IRCConnection(DescriptorImpl descriptor, AuthenticationHolder authentication) {
	    if (LOGGER.isLoggable(Level.FINEST)) {
	        this.pircConnection.setVerbose(true);
	    }
		this.descriptor = descriptor;
		this.authentication = authentication;
		
		if (descriptor.getDefaultTargets() != null) {
			this.groupChats = descriptor.getDefaultTargets();
		} else {
			this.groupChats = Collections.emptyList();
		}
		
	    this.pircConnection.setLogin(this.descriptor.getLogin());
        this.pircConnection.setName(this.descriptor.getNick());
        
        // lower delay between sending 2 messages to 500ms as we will sometimes send
        // output which will consist of multiple lines (see comment in send method)
        // (lowering further than this doesn't seem to work as we will otherwise be easily
        // be throttled by IRC servers)
        this.pircConnection.setMessageDelay(500);
        
        this.listener = new PircListener(this.pircConnection, this.descriptor.getNick());
	}
	
	//@Override
	public void close() {
	    this.listener.explicitDisconnect = true;
	    
		if (this.pircConnection != null && this.pircConnection.isConnected()) {
            this.listener.removeJoinListener(this);
            this.listener.removePartListener(this);
            this.listener.removeInviteListener(this);
            
			this.pircConnection.disconnect();
			//this.pircConnection.shutdown();
		}
	}

	//@Override
	public boolean isConnected() {
		return this.pircConnection != null && this.pircConnection.isConnected();
	}

	//@Override
	public boolean connect() {
		try {
			this.pircConnection.setEncoding(this.descriptor.getCharset());

			LOGGER.info(String.format("Connecting to %s:%s as %s using charset %s",
			        this.descriptor.getHost(), this.descriptor.getPort(), this.descriptor.getNick(), this.descriptor.getCharset()));
			
			String password = Util.fixEmpty(this.descriptor.getPassword());

            final SocketFactory sf;
            if (this.descriptor.isSsl()) {
                if (this.descriptor.shouldIgnoreSslCert()) {
                    SSLContext ctx = null;
                    try {
                        ctx = getInstance("TLS");
                        ctx.init(null, new TrustManager[]{new AlwaysTrustManager()}, null);
                    } catch (Exception e) {
                        LOGGER.warning("Error creating trust manager,falling back to normal : " + e);
                    }
                    if (ctx != null) {
                        sf = ctx.getSocketFactory();

                    } else {
                        sf = SSLSocketFactory.getDefault();
                    }

                } else {
                    sf = SSLSocketFactory.getDefault();
                }
            } else {
                sf = SocketFactory.getDefault();
            }
			
		    this.pircConnection.connect(this.descriptor.getHost(), this.descriptor.getPort(), password, sf);
			
			LOGGER.info("connected to IRC");
			if (!this.pircConnection.getListenerManager().listenerExists(this.listener)) {
			    this.pircConnection.getListenerManager().addListener(this.listener);
			}
			this.listener.addJoinListener(this);
            this.listener.addInviteListener(this);
            this.listener.addPartListener(this);
			
	        final String nickServPassword = this.descriptor.getNickServPassword();
            if(Util.fixEmpty(nickServPassword) != null) {
                this.pircConnection.identify(nickServPassword);
                
                if (!this.groupChats.isEmpty()) {
	                // Sleep some time so chances are good we're already identified
	                // when we try to join the channels.
	                // Unfortunately there seems to be no standard way in IRC to recognize
	                // if one has been identified already.
	                LOGGER.fine("Sleeping some time to wait for being authenticated");
	                try {
						Thread.sleep(TimeUnit.SECONDS.toMillis(5));
					} catch (InterruptedException e) {
						// ignore
					}
                }
            }
			
			for (IMMessageTarget groupChat : this.groupChats) {
				try {
					getGroupChat(groupChat);
				} catch (Exception e) {
					// if we got here, the IRC connection could be established, but probably the channel name
					// is invalid
					LOGGER.warning("Unable to connect to channel '" + groupChat + "'.\n"
							+ "Message: " + ExceptionHelper.dump(e));
				}
			}
			
			listener.addMessageListener(this.descriptor.getNick(),
			        PircListener.CHAT_ESTABLISHER, new ChatEstablishedListener());
			
			return true;
		} catch (NickAlreadyInUseException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		} catch (IOException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		} catch (IrcException e) {
			LOGGER.warning("Error connecting to irc: " + e);
		} catch (RuntimeException e) {
		    // JENKINS-17017: contrary to Javadoc PircBotx (at least 1.7 and 1.8) sometimes
		    // throw a RuntimeException instead of IOException if connecting fails
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
	
    //@Override
    public void channelJoined(String channelName) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "Joined to channel {0} but I don't seem to belong here", channelName);
            return;
        }
        Bot bot = new Bot(new IRCChannel(channelName, this, this.listener, !groupChat.isNotificationOnly()),
                this.descriptor.getNick(), this.descriptor.getHost(),
                this.descriptor.getCommandPrefix(), this.authentication);
        bots.put(channelName, bot);
        LOGGER.log(Level.INFO, "Joined channel {0} and bot registered", channelName);
    }

    //@Override
    public void inviteReceived(String channelName, String inviter) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "Invited to channel {0} but I don't seem to belong here", channelName);
            return;
        }
        LOGGER.log(Level.INFO, "Invited to join {0}", channelName);
        getGroupChat(groupChat);
    }

    //@Override
    public void channelParted(String channelName) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "I'm leaving {0} but I never seemed to belong there in the first place", channelName);
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

	//@Override
	public void addConnectionListener(IMConnectionListener listener) {
	    this.listener.addConnectionListener(listener);
	}

	//@Override
	public void removeConnectionListener(IMConnectionListener listener) {
		this.listener.removeConnectionListener(listener);
	}

	//@Override
    public void send(IMMessageTarget target, String text) throws IMException {
	    send(target.toString(), text);
	}
	
	public void send(String target, String text) throws IMException {
	    Channel channel = this.pircConnection.getChannel(target);
	    
	    boolean useColors = this.descriptor.isUseColors();
	    if (useColors) {
	        String mode = channel.getMode();
	        if (mode.contains("c")) {
	            LOGGER.warning("Bot is configured to use colors, but channel " + target + " disallows colors!");
	            useColors = false;
	        }
	    }

        // IRC doesn't support multiline messages (see http://stackoverflow.com/questions/7039478/linebreak-irc-protocol)
	    // therefore we split the message on line breaks and send each line as its own message:
        String[] lines = text.split("\\r?\\n|\\r");
        for (String line : lines) {
            if (useColors){
                line = IRCColorizer.colorize(line);
            }
            if (this.descriptor.isUseNotice()) {
                this.pircConnection.sendNotice(channel, line);
            } else {
                this.pircConnection.sendMessage(channel, line);
            }
        }
	}

	//@Override
	public void setPresence(IMPresence presence, String statusMessage)
			throws IMException {
		if (presence.ordinal() >= IMPresence.OCCUPIED.ordinal()) {
			if (statusMessage == null || statusMessage.trim().length() == 0) {
				statusMessage = "away";
			}
			this.pircConnection.sendRawLineNow("AWAY " + statusMessage);
		} else {
			this.pircConnection.sendRawLineNow("AWAY");
		}
	}

	/**
	 * Listens for chat requests from singular users (i.e. private chat requests).
	 * Creates a new bot for each request, if we're not already in a chat with
	 * that user.
	 */
	private class ChatEstablishedListener implements IMMessageListener {

		//@Override
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
				
				IRCPrivateChat chat = new IRCPrivateChat(IRCConnection.this, listener, descriptor.getUserName(), message.getFrom());
				Bot bot = new Bot(chat,
						descriptor.getNick(), descriptor.getHost(),
						descriptor.getCommandPrefix(), authentication);
			
				privateChats.put(message.getFrom(), bot);
				
				// we must replay this message as it could contain a command
				bot.onMessage(message);
			}
		}
	}

    //Used for ignoring SSL certs
    private static class AlwaysTrustManager implements X509TrustManager {

        public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws java.security.cert.CertificateException {
        }

        public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws java.security.cert.CertificateException {
        }

        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
