/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisher;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.im.config.ParameterNames;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.plugins.ircbot.v2.IRCConnectionProvider;
import hudson.plugins.ircbot.v2.IRCMessageTargetConverter;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;

import hudson.util.Scrambler;
import hudson.util.Secret;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
//import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Publishes build results to IRC channels.
 *
 * @author bruyeron (original author)
 * @author $Author: kutzi $ (last change)
 * @version $Id: IrcPublisher.java 39408 2011-05-01 10:52:54Z kutzi $
 */
public class IrcPublisher extends IMPublisher {

    private static final Logger LOGGER = Logger.getLogger(IrcPublisher.class.getName());

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final IMMessageTargetConverter CONVERTER = new IRCMessageTargetConverter();

    /**
     * channels to notify with build status If not empty, this replaces the main
     * channels defined at the descriptor level.
     * @deprecated only used to deserialize old instances. please use {@link #getNotificationTargets()}
     */
    @Deprecated
    public List<String> channels = new ArrayList<String>();

    public IrcPublisher(List<IMMessageTarget> defaultTargets, String notificationStrategy,
            boolean notifyGroupChatsOnBuildStart,
            boolean notifySuspects,
            boolean notifyCulprits,
            boolean notifyFixers,
            boolean notifyUpstreamCommitters,
            BuildToChatNotifier buildToChatNotifier,
            MatrixJobMultiplier matrixMultiplier)
    {
        super(defaultTargets, notificationStrategy, notifyGroupChatsOnBuildStart,
                notifySuspects, notifyCulprits, notifyFixers, notifyUpstreamCommitters,
                buildToChatNotifier, matrixMultiplier);
    }

    /**
     * @see hudson.model.Describable#getDescriptor()
     */
    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    // from IMPublisher:
    @Override
    protected String getConfiguredIMId(User user) {
        IrcUserProperty ircUserProperty = (IrcUserProperty) user.getProperties().get(IrcUserProperty.DESCRIPTOR);
        if (ircUserProperty != null) {
            return ircUserProperty.getNick();
        }
        return null;
    }

    @Override
    protected IMConnection getIMConnection() throws IMException {
        return IRCConnectionProvider.getInstance().currentConnection();
    }

    @Override
    protected String getPluginName() {
        return "IRC notifier plugin";
    }

    // deserialize/migrate old instances
    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        super.readResolve();
        if (this.getNotificationTargets() == null) {
            if (this.channels != null) {
                List<IMMessageTarget> targets = new ArrayList<IMMessageTarget>(this.channels.size());
                for (String channel : channels) {
                    targets.add(new GroupChatIMMessageTarget(channel));
                }
                setNotificationTargets(targets);
            } else {
                setNotificationTargets(Collections.<IMMessageTarget>emptyList());
            }
        }
        this.channels = null;

        if (getNotificationStrategy() == null) {
            // set to the only available strategy in ircbot <= 1.7
            setNotificationStrategy(NotificationStrategy.STATECHANGE_ONLY);
        }
        return this;
    }

    /**
     * Descriptor for {@link IrcPublisher}
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> implements IMPublisherDescriptor {

        private static final String PREFIX = "irc_publisher.";

        public static final String[] CHARSETS;

        static {
            SortedMap<String, Charset> availableCharsets = Charset.availableCharsets();
            String[] cs = new String[availableCharsets.size()];
            cs[0] = "UTF-8";
            int i = 1;
            for (String csName : availableCharsets.keySet()) {
                if (!"UTF-8".equals(csName)) {
                    cs[i++] = csName;
                }
            }
            CHARSETS = cs;
        }

        boolean enabled = false;

        String hostname = null;

        Integer port = 194;

        private boolean ssl;

        private boolean disallowPrivateChat;

        private String login = "PircBotx";

        private boolean sslTrustAllCertificates;

        @Deprecated
        transient String password = null;
        Secret secretPassword;

        private boolean sasl;

        String nick = "jenkins-bot";

        @Deprecated
        transient String nickServPassword = null;
        Secret secretNickServPassword;

        private String socksHost = null;

        private Integer socksPort = 1080;

        private Integer messageRate = getMessageRateFromSystemProperty();

        /**
         * channels to join
         *
         * @deprecated Only to deserialize old descriptors
         */
        @Deprecated
        List<String> channels;

        private List<IMMessageTarget> defaultTargets;

        String commandPrefix = "!jenkins";

        private String hudsonLogin;

        private boolean useNotice;

        private String charset;

        private boolean useColors;

        DescriptorImpl() {
            super(IrcPublisher.class);
            load();

            if (isEnabled()) {
                try {
                    IRCConnectionProvider.setDesc(this);
                } catch (final Exception e) {
                    // Server temporarily unavailable or misconfigured?
                    LOGGER.warning(ExceptionHelper.dump(e));
                }
            } else {
                try {
                    IRCConnectionProvider.setDesc(null);
                } catch (IMException e) {
                    // ignore
                }
            }
        }

        /**
         * Check boxes values are not passed in the posted form when they are unchecked.
         * The workaround consists in acceding these values via the JSON representation.
         */
        private static List<JSONObject> fillChannelsFromJSON(JSONObject root){
            throw new UnsupportedOperationException();
        }

        /**
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification="There are, in fact, side effects")
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.enabled = "on".equals(req.getParameter("irc_publisher.enabled"))
                    || "true".equals(req.getParameter("irc_publisher.enabled"));
            if (this.enabled) {
                JSONObject enabled = formData.getJSONObject("enabled");
                req.bindJSON(this, enabled);

                // try to establish the connection
                try {
                    IRCConnectionProvider.setDesc(this);
                    IRCConnectionProvider.getInstance().currentConnection();
                } catch (final Exception e) {
                    LOGGER.warning(ExceptionHelper.dump(e));
                }
            } else {
                IRCConnectionProvider.getInstance().releaseConnection();
                try {
                    IRCConnectionProvider.setDesc(null);
                } catch (IMException e) {
                    // ignore
                }
            }

            save();
            return super.configure(req, formData);
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

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (req == null) {
                throw new IllegalArgumentException("req must be non null");
            }

            List<IMMessageTarget> targets = new ArrayList<>();
            JSONArray jchans = formData.getJSONArray("notificationTargets");

            for (int i = 0; i < jchans.size(); i++) {
                JSONObject channel = jchans.getJSONObject(i);
                String name = channel.getString("name");
                if (Util.fixEmptyAndTrim(name) == null) {
                    throw new FormException("Channel name must not be empty", "channel.name");
                }
                Secret password = Secret.fromString(channel.getString("secretPassword"));
                boolean notificationOnly = channel.getBoolean("notificationOnly");

                targets.add(new GroupChatIMMessageTarget(name, password, notificationOnly));
            }

            String n = req.getParameter(getParamNames().getStrategy());
            if (n == null) {
                n = PARAMETERVALUE_STRATEGY_DEFAULT;
            } else {
                boolean foundStrategyValueMatch = false;
                for (final String strategyValue : PARAMETERVALUE_STRATEGY_VALUES) {
                    if (strategyValue.equals(n)) {
                        foundStrategyValueMatch = true;
                        break;
                    }
                }
                if (! foundStrategyValueMatch) {
                    n = PARAMETERVALUE_STRATEGY_DEFAULT;
                }
            }
            boolean notifyStart = "on".equals(req.getParameter(getParamNames().getNotifyStart()));
            boolean notifySuspects = "on".equals(req.getParameter(getParamNames().getNotifySuspects()));
            boolean notifyCulprits = "on".equals(req.getParameter(getParamNames().getNotifyCulprits()));
            boolean notifyFixers = "on".equals(req.getParameter(getParamNames().getNotifyFixers()));
            boolean notifyUpstream = "on".equals(req.getParameter(getParamNames().getNotifyUpstreamCommitters()));

            MatrixJobMultiplier matrixJobMultiplier = MatrixJobMultiplier.ONLY_CONFIGURATIONS;
            if (formData.has("matrixNotifier")) {
                String o = formData.getString("matrixNotifier");
                matrixJobMultiplier = MatrixJobMultiplier.valueOf(o);
            }

            return new IrcPublisher(targets, n, notifyStart, notifySuspects, notifyCulprits,
                        notifyFixers, notifyUpstream,
                        req.bindJSON(BuildToChatNotifier.class,formData.getJSONObject("buildToChatNotifier")),
                        matrixJobMultiplier);
        }

        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * @return the commandPrefix
         */
        //@Override
        public String getCommandPrefix() {
            return commandPrefix;
        }

        /**
         * @return the hostname
         */
        //@Override
        public String getHostname() {
            return hostname;
        }

        /**
         * Returns the nickname that should be used to identify against the IRC server.
         *
         * @return the nick
         */
        public String getNick() {
            return nick;
        }

        /**
         * @return The password that should be used to try and identify
         * with NickServ.
         *
         * @deprecated use {@link #getSecretNickServPassword()}
         */
        @Deprecated
        public String getNickServPassword() {
            return getSecretNickServPassword().getPlainText();
        }

        public Secret getSecretNickServPassword() {
            return secretNickServPassword;
        }

        public String getLogin() {
            return this.login;
        }

        //@Override
        public Secret getSecretPassword() {
            return secretPassword;
        }

        public boolean isSasl() {
            return this.sasl;
        }

        //@Override
        public int getPort() {
            return port;
        }

        public String getSocksHost() {
            return socksHost;
        }

        public int getSocksPort() {
            return socksPort;
        }

        public boolean isSsl() {
            return this.ssl;
        }

        public boolean isTrustAllCertificates() {
            return this.sslTrustAllCertificates;
        }

        public boolean isDisallowPrivateChat() {
            return this.disallowPrivateChat;
        }

        public Integer getMessageRate() { return this.messageRate; }

        //@Override
        public boolean isEnabled() {
            return enabled;
        }

        //@Override
        public String getDefaultIdSuffix() {
            // not implemented for IRC, yet
            return null;
        }

        //@Override
        public String getHost() {
            return this.hostname;
        }
        //@Override
        public String getHudsonUserName() {
            return this.hudsonLogin;
        }

        //@Override
        public String getPluginDescription() {
            return "IRC notifier plugin";
        }

        //@Override
        public String getUserName() {
            return this.nick;
        }

        //@Override
        public boolean isExposePresence() {
            return true;
        }

        //@Override
        public List<IMMessageTarget> getDefaultTargets() {
            if (this.defaultTargets == null) {
                return Collections.emptyList();
            }

            return this.defaultTargets;
        }


        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public void setDisallowPrivateChat(boolean disallowPrivateChat) {
            this.disallowPrivateChat = disallowPrivateChat;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public boolean isSslTrustAllCertificates() {
            return sslTrustAllCertificates;
        }

        public void setSslTrustAllCertificates(boolean sslTrustAllCertificates) {
            this.sslTrustAllCertificates = sslTrustAllCertificates;
        }

        public void setSecretPassword(Secret secretPassword) {
            this.secretPassword = secretPassword;
        }

        public void setSasl(boolean sasl) {
            this.sasl = sasl;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public void setSecretNickServPassword(Secret secretNickServPassword) {
            this.secretNickServPassword = secretNickServPassword;
        }

        public void setSocksHost(String socksHost) {
            this.socksHost = socksHost;
        }

        public void setSocksPort(Integer socksPort) {
            this.socksPort = socksPort;
        }

        public void setMessageRate(Integer messageRate) {
            this.messageRate = messageRate;
        }

        public void setDefaultTargets(List<IMMessageTarget> defaultTargets) {
            this.defaultTargets = defaultTargets;
        }

        public void setCommandPrefix(String commandPrefix) {
            this.commandPrefix = commandPrefix;
        }

        public String getHudsonLogin() {
            return hudsonLogin;
        }

        public void setHudsonLogin(String hudsonLogin) {
            this.hudsonLogin = hudsonLogin;
        }

        public void setUseNotice(boolean useNotice) {
            this.useNotice = useNotice;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }

        public void setUseColors(boolean useColors) {
            this.useColors = useColors;
        }

        //@Override
        public IMMessageTargetConverter getIMMessageTargetConverter() {
            return CONVERTER;
        }

        /**
         * @return Boolean flag which specifies if the bot should use
         * the /notice command instead of the /msg command to notify.
         */
        public boolean isUseNotice() {
            return this.useNotice;
        }

        /**
         * @return Boolean flag which specifies if the bot should
         * send message with colors.
         */
        public boolean isUseColors() {
            return this.useColors;
        }

        public String getCharset() {
            return this.charset;
        }



        public ParameterNames getParamNames() {
            return new ParameterNames() {
                @Override
                protected String getPrefix() {
                    return PREFIX;
                }
            };
        }

        /**
         * Fetches message rate, defaults to 0.5 second if none are set or invalid value.
         * @return message rate in milliseconds
         */
        protected Integer getMessageRateFromSystemProperty() {
            try {
                return Integer.parseInt(System.getProperty("hudson.plugins.ircbot.messageRate", "500"));
            } catch (NumberFormatException nfe) {
                return new Integer(500);
            }
        }

        /**
         * Deserialize old descriptors.
         */
        @SuppressWarnings("deprecation")
        protected Object readResolve() {
            if (this.defaultTargets == null) {
                if (this.channels != null) {
                    this.defaultTargets = new ArrayList<>(this.channels.size());
                    for (String channel : this.channels) {
                        this.defaultTargets.add(new GroupChatIMMessageTarget(channel));
                    }

                    this.channels = null;
                }
            }

            if (this.charset == null) {
                this.charset = "UTF-8";
            }

            if (this.messageRate == null) {
                this.messageRate = getMessageRateFromSystemProperty();
            }

            if (StringUtils.isNotBlank(this.password)) {
                this.secretPassword = Secret.fromString(Scrambler.descramble(this.password));
                this.password = null;
            }
            if (StringUtils.isNotBlank(this.nickServPassword)) {
                this.secretNickServPassword = Secret.fromString(Scrambler.descramble(this.nickServPassword));
                this.nickServPassword = null;
            }

            return this;
        }

    }
}
