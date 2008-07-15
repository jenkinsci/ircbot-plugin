/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author bruyeron
 * @version $Id: IrcPublisher.java 10807 2008-07-14 18:56:05Z btosabre $
 */
public class IrcPublisher extends Publisher {

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * channels to notify with build status If not empty, this replaces the main
     * channels defined at the descriptor level.
     */
    public List<String> channels = new ArrayList<String>();

    /**
     * 
     */
    public IrcPublisher() {
    }

    /**
     * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher,
     *      hudson.model.BuildListener)
     */
    public boolean perform(Build<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        IrcNotifier.perform(build, channelList());
        return true;
    }

    private List<String> channelList() {
        return (channels == null || channels.isEmpty()) ? DESCRIPTOR.channels
                : channels;
    }

    /**
     * For the UI redisplay
     * 
     * @return
     */
    public String getChannels() {
        StringBuilder sb = new StringBuilder();
        if (channels != null) {
            for (String c : channels) {
                sb.append(c).append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * @see hudson.model.Describable#getDescriptor()
     */
    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor for {@link IrcPublisher}
     * 
     * @author bruyeron
     * @version $Id: IrcPublisher.java 10807 2008-07-14 18:56:05Z btosabre $
     */
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        private static final Logger LOGGER = Logger
                .getLogger(DescriptorImpl.class.getName());

        boolean enabled = false;

        String hostname = null;

        Integer port = 194;

        String password = null;

        String nick = null;

        /**
         * channels to join
         */
        List<String> channels;

        String commandPrefix = null;

        /**
         * the IRC bot
         */
        transient volatile IrcBot bot;

        /**
         */
        DescriptorImpl() {
            super(IrcPublisher.class);
            load();
            try {
                initBot();
            } catch (Exception e) {
                LOGGER
                        .log(
                                Level.WARNING,
                                "IRC bot could not connect - please review connection details",
                                e);
            }
        }

        public void initBot() throws NickAlreadyInUseException, IOException,
                IrcException {
            if (enabled) {
                bot = new IrcBot(nick);
                bot.connect(hostname, port, password);
                for (String channel : channels) {
                    bot.joinChannel(channel);
                }
                LOGGER.info("IRC bot connected and channels joined");
            }
        }

        public void stop() {
            if (bot != null && bot.isConnected()) {
                bot.quitServer("mama grounded me!");
                bot.dispose();
                bot = null;
                LOGGER.info("IRC bot stopped");
            }
        }

        /**
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            enabled = "on".equals(req.getParameter("irc_publisher.enabled"))
                    || "true".equals(req.getParameter("irc_publisher.enabled"));
            if (enabled) {
                hostname = req.getParameter("irc_publisher.hostname");
                password = req.getParameter("irc_publisher.password");
                nick = req.getParameter("irc_publisher.nick");
                try {
                    port = Integer.valueOf(req
                            .getParameter("irc_publisher.port"));
                    if (port == null) {
                        port = 194;
                    }
                } catch (NumberFormatException e) {
                    throw new FormException("port field must be an Integer",
                            "irc_publisher.port");
                }
                commandPrefix = req.getParameter("irc_publisher.commandPrefix");
                if (commandPrefix == null || "".equals(commandPrefix.trim())) {
                    commandPrefix = null;
                } else {
                    commandPrefix = commandPrefix.trim() + " ";
                }
                channels = Arrays.asList(req.getParameter(
                        "irc_publisher.channels").split(" "));
            }
            save();
            stop();
            try {
                initBot();
            } catch (NickAlreadyInUseException e) {
                throw new FormException("Nick <" + nick
                        + "> already in use on this server",
                        "irc_publisher.nick");
            } catch (IOException e) {
                throw new FormException("Impossible to connect to IRC server",
                        e, null);
            } catch (IrcException e) {
                throw new FormException("Impossible to connect to IRC server",
                        e, null);
            }
            return super.configure(req);
        }

        /**
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return "IRC Notification";
        }

        /**
         * @see hudson.model.Descriptor#getHelpFile()
         */
        @Override
        public String getHelpFile() {
            return "/plugin/ircbot/help.html";
        }

        public String getChannels() {
            StringBuilder sb = new StringBuilder();
            if (channels != null) {
                for (String c : channels) {
                    sb.append(c).append(" ");
                }
            }
            return sb.toString().trim();
        }

        /**
         * @see hudson.model.Descriptor#newInstance(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        public Publisher newInstance(StaplerRequest req) throws FormException {
            IrcPublisher result = new IrcPublisher();
            String channelParam = req.getParameter("channels");
            if (channelParam != null) {
                for (String c : Arrays.asList(channelParam.split(" "))) {
                    if (c.trim().length() > 0) {
                        result.channels.add(c.trim());
                    }
                }
            }
            return result;
        }

        /**
         * @return the commandPrefix
         */
        public String getCommandPrefix() {
            return commandPrefix;
        }

        /**
         * @return the hostname
         */
        public String getHostname() {
            return hostname;
        }

        /**
         * @return the nick
         */
        public String getNick() {
            return nick;
        }

        /**
         * @return the password
         */
        public String getPassword() {
            return password;
        }

        class IrcBot extends PircBot {

            IrcBot(String name) {
                setName(name);
                setMessageDelay(5);
            }

            protected void sendNotice(List<String> channels, String message) {
                for (String channel : channels) {
                    LOGGER.info("sending notice to channel " + channel);
                    sendNotice(channel, message);
                }
            }

            /**
             * @see org.jibble.pircbot.PircBot#onMessage(java.lang.String,
             *      java.lang.String, java.lang.String, java.lang.String,
             *      java.lang.String)
             */
            @Override
            protected void onMessage(String channel, String sender,
                    String login, String hostname, String message) {
                if (commandPrefix != null && message.startsWith(commandPrefix)) {
                    final String command = message.substring(
                            commandPrefix.length()).trim();
                    String[] tokens = command.split("\\s+", 2);
                    try {
                        BotCommands commandInstance = BotCommands.valueOf(tokens[0]);
                        String parameter = null;
                        if(tokens.length > 1){
                            parameter = tokens[1];
                        }
                        commandInstance.execute(this, parameter, channel, sender, channels);
                    } catch(IllegalArgumentException e){
                        sendNotice(sender, "Invalid command: " + tokens[0]);
                    }
                }
            }

            /**
             * @see org.jibble.pircbot.PircBot#onPrivateMessage(java.lang.String,
             *      java.lang.String, java.lang.String, java.lang.String)
             */
            @Override
            protected void onPrivateMessage(String sender, String login,
                    String hostname, String message) {
                if (commandPrefix == null) {
                    sendNotice(sender,
                            "the property <commandPrefix> must be set on the Hudson configuration screen");
                } else {
                    onMessage(null, sender, login, hostname, message);
                }
            }

        }

        /**
         * @return the port
         */
        public int getPort() {
            return port;
        }

        /**
         * @return the enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

    }
}
