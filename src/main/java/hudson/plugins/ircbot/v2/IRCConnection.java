package hudson.plugins.ircbot.v2;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.Configuration.Builder;
import org.pircbotx.PircBotX;
import org.pircbotx.ProxySocketFactory;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.delay.StaticReadonlyDelay;
import org.pircbotx.exception.NotReadyException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;

import static java.util.logging.Level.WARNING;

/**
 * IRC specific implementation of an {@link IMConnection}.
 *
 * @author kutzi
 */
public class IRCConnection implements IMConnection, JoinListener, InviteListener, PartListener {

    private static final Logger LOGGER = Logger.getLogger(IRCConnection.class.getName());

    private final DescriptorImpl descriptor;
    private final AuthenticationHolder authentication;


    private Thread botThread;

    private final Builder cfg;
    private volatile PircBotX pircConnection;
    private final PircListener listener;

    private List<IMMessageTarget> groupChats;

    private final Map<String, Bot> bots = new HashMap<String, Bot>();

    private final Map<String, Bot> privateChats = new HashMap<String, Bot>();


    @SuppressFBWarnings(value="UR_UNINIT_READ",
        justification="TODO: this is probably a geniune problem but I don't know why")
    public IRCConnection(DescriptorImpl descriptor, AuthenticationHolder authentication) {
        Builder config = new Configuration.Builder();

        // TODO: setVerbose is gone in 2.x - or is it default now?
//        if (LOGGER.isLoggable(Level.FINEST)) {
//            this.pircConnection.setVerbose(true);
//        }
        this.descriptor = descriptor;
        this.authentication = authentication;

        if (descriptor.getDefaultTargets() != null) {
            this.groupChats = descriptor.getDefaultTargets();
        } else {
            this.groupChats = Collections.emptyList();
        }

        config.setServerHostname(descriptor.getHost());
        config.setServerPort(descriptor.getPort());
        if (this.descriptor.isSasl()) {
            LOGGER.info("Enabling SASL");
            config.setCapEnabled(true);
            config.addCapHandler(new SASLCapHandler(this.descriptor.getLogin(), this.descriptor.getSecretPassword().getPlainText()));
        } else {
            if (this.descriptor.getSecretPassword() != null) {
                String password = Util.fixEmpty(this.descriptor.getSecretPassword().getPlainText());
                if (password != null) {
                    config.setServerPassword(password);
                }
            }
        }

        if (this.descriptor.getSecretNickServPassword() != null) {
            final String nickServPassword = Util.fixEmpty(this.descriptor.getSecretNickServPassword().getPlainText());
            if (nickServPassword != null) {
                config.setNickservPassword(nickServPassword);
            }
        }


        String socksHost = Util.fixEmpty(this.descriptor.getSocksHost());

        final SocketFactory sf;
        if (this.descriptor.isSsl()) {
            if (this.descriptor.isTrustAllCertificates()) {
                sf = new UtilSSLSocketFactory().trustAllCertificates();
            } else {
                sf = SSLSocketFactory.getDefault();
            }
        } else if (socksHost != null && this.descriptor.getSocksPort() > 0) {
            sf = new ProxySocketFactory(Proxy.Type.SOCKS, this.descriptor.getSocksHost(), this.descriptor.getSocksPort());
        } else {
            sf = SocketFactory.getDefault();
        }
        config.setSocketFactory(sf);


        config.setLogin(this.descriptor.getLogin());
        config.setName(this.descriptor.getNick());
        config.setMessageDelay(new StaticReadonlyDelay(this.descriptor.getMessageRate()));
        config.setEncoding(Charset.forName(this.descriptor.getCharset()));

        this.listener = new PircListener(this.pircConnection, this.descriptor.getNick());
        this.listener.addJoinListener(this);
        this.listener.addInviteListener(this);
        this.listener.addPartListener(this);


        listener.addMessageListener(this.descriptor.getNick(),
                PircListener.CHAT_ESTABLISHER, new ChatEstablishedListener());

        config.addListener(listener);

        config.setAutoNickChange(false);

        // we're still handling reconnection logic by ourself. Maybe not a good idea in the long run...
        config.setAutoReconnect(false);

        cfg = config;
    }

    //@Override
    public void close() {
        this.listener.explicitDisconnect = true;

//        if (this.pircConnection != null) {
//            if (this.pircConnection.isConnected()) {
//                this.listener.removeJoinListener(this);
//                this.listener.removePartListener(this);
//                this.listener.removeInviteListener(this);
//
//                this.pircConnection.disconnect();
//            }
//
//            // Perform a proper shutdown, also freeing all the resources (input-/output-thread)
//            // Note that with PircBotx 2.x the threads are gone and we can maybe simplify this
//            this.pircConnection.shutdown(true);
//        }

        if (botThread != null) {
            this.botThread.interrupt();
        }
    }

    //@Override
    public boolean isConnected() {
        return this.pircConnection != null && this.pircConnection.isConnected();
    }

    //@Override
    @SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC_ANON",
        justification = "Commented below, no idea how to solve")
    public boolean connect() {
        try {

            LOGGER.info(String.format("Connecting to %s:%s as %s using charset %s",
                    this.descriptor.getHost(), this.descriptor.getPort(), this.descriptor.getNick(), this.descriptor.getCharset()));


            if (botThread != null) {
                botThread.interrupt();
            }

            final CountDownLatch connectLatch = new CountDownLatch(1);

            // SIC_INNER_SHOULD_BE_STATIC_ANON.... whatever that means:
            // The class hudson.plugins.im.build_notify.PrintFailingTestsBuildToChatNotifier$1
            // could be refactored into a named _static_ inner class.
            ListenerAdapter connectListener = new ListenerAdapter() {

                @Override
                public void onConnect(ConnectEvent event)
                        throws Exception {
                    connectLatch.countDown();

                    LOGGER.info("connected to IRC");
                }
            };
            cfg.addListener(connectListener);

            botThread = new Thread("IRC Bot") {
                public void run() {
                    pircConnection = new PircBotX(cfg.buildConfiguration());
                    try {
                        pircConnection.startBot();
                    } catch (Exception e) {
                        LOGGER.warning("Error connecting to irc: " + e);
                    }
                }
            };
            botThread.start();

            try {
                boolean connected = connectLatch.await(2, TimeUnit.MINUTES);

                if (!connected) {
                    LOGGER.warning("Time out waiting for connecting to irc");
                    close();
                    return false;
                }
            } catch (InterruptedException e) {
                LOGGER.warning("Interrupted waiting for connecting to irc: " + e);
                Thread.currentThread().interrupt();
            }

            pircConnection.getConfiguration().getListenerManager().removeListener(connectListener);



//            final String nickServPassword = this.descriptor.getNickServPassword();
//            if(Util.fixEmpty(nickServPassword) != null) {
//                this.pircConnection.identify(nickServPassword);
//
//                if (!this.groupChats.isEmpty()) {
//                    // Sleep some time so chances are good we're already identified
//                    // when we try to join the channels.
//                    // Unfortunately there seems to be no standard way in IRC to recognize
//                    // if one has been identified already.
//                    LOGGER.fine("Sleeping some time to wait for being authenticated");
//                    try {
//                        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
//                    } catch (InterruptedException e) {
//                        // ignore
//                    }
//                }
//            }

            joinGroupChats();

            return pircConnection.isConnected();
        } catch (RuntimeException e) {
            LOGGER.log(WARNING, "Error connecting to irc", e);
            return false;
        }
    }

    private void joinGroupChats() {

        long startTime = System.currentTimeMillis();
        long timeout = TimeUnit.MINUTES.toMillis(2);

        // (Re-)try connecting to channels until timeout of 2 minutes is reached.
        // This is because we might not be connected to nickserv, yet, even with the sleep of 5 seconds, we've done earlier
        Exception ex = null;
        while ((System.currentTimeMillis() - startTime) < timeout) {

            for (IMMessageTarget groupChat : this.groupChats) {
                try {
                    joinGroupChat(groupChat);
                } catch (Exception e) {
                    LOGGER.warning("Unable to connect to channel '" + groupChat + "'.\n"
                            + "Message: " + ExceptionHelper.dump(e));
                    // I we got here something big is broken and we shouldn't continue trying to connect
                    ex = e;
                    break;
                }
            }

            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                // ignore
            }

            if (areWeConnectedToAllChannels()) {
                break;
            }

            LOGGER.info("Still not connected to all channels. Retrying.");
        }

        if (ex == null && !areWeConnectedToAllChannels()) {
            LOGGER.warning("Still not connected to all channels after " + timeout + " minutes. Giving up.");
        }
    }

    private boolean areWeConnectedToAllChannels() {
        Set<String> groupChatNames =
                this.groupChats.stream()
                        .map(GroupChatIMMessageTarget.class::cast)
                        .map(GroupChatIMMessageTarget::getName)
                        .collect(Collectors.toSet());

        Set<String> connectedToChannels =
                this.pircConnection.getUserChannelDao().getAllChannels().stream()
                        .map(Channel::getName)
                        .collect(Collectors.toSet());

        return groupChatNames.equals(connectedToChannels);
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

    private void joinGroupChat(IMMessageTarget groupChat) {
        if (! (groupChat instanceof GroupChatIMMessageTarget)) {
            LOGGER.warning(groupChat + " is no channel. Cannot join.");
            return;
        }

        GroupChatIMMessageTarget channel = (GroupChatIMMessageTarget)groupChat;
        LOGGER.info("Trying to join channel " + channel.getName());

        if (channel.hasPassword()) {
            this.pircConnection.sendIRC().joinChannel(channel.getName(), channel.getSecretPassword().getPlainText());
        } else {
            this.pircConnection.sendIRC().joinChannel(channel.getName());
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
    public void inviteReceived(String channelName) {
        GroupChatIMMessageTarget groupChat = getGroupChatForChannelName(channelName);
        if (groupChat == null) {
            LOGGER.log(Level.INFO, "Invited to channel {0} but I don't seem to belong here", channelName);
            return;
        }
        LOGGER.log(Level.INFO, "Invited to join {0}", channelName);
        joinGroupChat(groupChat);
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
        Channel channel = this.pircConnection.getUserChannelDao().getChannel(target);

        boolean useColors = this.descriptor.isUseColors();
        if (useColors) {
            String mode;
            try {
                mode = channel.getMode();
            } catch (NotReadyException e) {
                throw new IMException(e);
            }
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
                this.pircConnection.sendIRC().notice(target, line);
            } else {
                this.pircConnection.sendIRC().message(target, line);
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
            this.pircConnection.sendRaw().rawLineNow("AWAY " + statusMessage);
        } else {
            this.pircConnection.sendRaw().rawLineNow("AWAY");
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
            if(descriptor.isDisallowPrivateChat()) {
                // ignore private chat, if disallow private chat commands.
                return;
            }

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
}
