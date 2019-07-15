/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.Scrambler;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.logging.Logger;
import java.util.logging.Level;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

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
	    public static final String PARAMETERNAME_USE_NOTICE = PREFIX + "useNotice";
	    public static final String PARAMETERNAME_USE_COLORS = PREFIX + "useColors";
	    public static final String PARAMETERNAME_NICKSERV_PASSWORD = PREFIX + "nickServPassword";

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

        String password = null;

        private boolean sasl;

        String nick = "jenkins-bot";

        String nickServPassword = null;

        private String socksHost = null;

        private Integer socksPort = 1080;

        private Integer messageRate = getMessageRateFromSystemProperty();

        /**
         * Marks if passwords are already scrambled.
         * Needed to migrate old, unscrambled passwords.
         * @since 2.19
         */
        private boolean scrambledPasswords = false;

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
            List<JSONObject> result = null;
            JSONObject chan = root.optJSONObject("channels");
            if (chan != null){
                result = new ArrayList<JSONObject>();
                result.add(chan);
            }
            else{
                JSONArray chans = root.optJSONArray("channels");
                if (chans != null){
                    result = new ArrayList<JSONObject>();
                    for(int i=0; i<chans.size(); ++i){
                        chan = chans.getJSONObject(i);
                        result.add(chan);
                    }
                }
            }
            return result;
        }

        /**
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification="There are, in fact, side effects")
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.scrambledPasswords = true;

            this.enabled = "on".equals(req.getParameter("irc_publisher.enabled"))
                    || "true".equals(req.getParameter("irc_publisher.enabled"));
            if (this.enabled) {
                this.hostname = req.getParameter("irc_publisher.hostname");
                this.login = req.getParameter("irc_publisher.login");
                this.password = Scrambler.scramble(
                        req.getParameter("irc_publisher.password"));
                this.sasl = "on".equals(req.getParameter("irc_publisher.sasl"));
                this.nick = req.getParameter("irc_publisher.nick");
                this.nickServPassword = Scrambler.scramble(
                        req.getParameter(PARAMETERNAME_NICKSERV_PASSWORD));
                try {
                    this.port = Integer.valueOf(req.getParameter("irc_publisher.port"));
                } catch (NumberFormatException e) {
                    throw new FormException("port field must be an Integer",
                            "irc_publisher.port");
                }
                this.socksHost = req.getParameter("irc_publisher.socksHost");
                try {
                    this.socksPort = Integer.valueOf(req.getParameter("irc_publisher.socksPort"));
                } catch (NumberFormatException e) {
                    throw new FormException("SOCKS proxy port field must be an Integer",
                            "irc_publisher.socksPort");
                }
                this.ssl = "on".equals(req.getParameter("irc_publisher.ssl"));
                this.sslTrustAllCertificates = "on".equals(req.getParameter("irc_publisher.ssl_trust_all_certificates"));
                this.commandPrefix = req.getParameter("irc_publisher.commandPrefix");
                this.commandPrefix = Util.fixEmptyAndTrim(commandPrefix);

                this.disallowPrivateChat = "on".equals(req.getParameter("irc_publisher.disallowPrivateChat"));

                this.messageRate = getMessageRateFromSystemProperty();

            	String[] channelsNames = req.getParameterValues("irc_publisher.channel.name");
            	String[] channelsPasswords = req.getParameterValues("irc_publisher.channel.password");
            	// only checked state can be queried, unchecked state are ignored and the size of
            	// notifyOnlys may be lower than the size of channelNames
            	// so getting the values via stapler is unreliable.
            	// String[] notifyOnlys = req.getParameterValues("irc_publisher.chat.notificationOnly");

            	List<IMMessageTarget> targets = Collections.emptyList();
            	if (channelsNames != null) {
            	    // JENKINS-13697: Get the data from the JSON representation which always returns
            	    // a value. The downside is that we are dependent on the data structure.
                    List<JSONObject> jchans = null;
            	    JSONObject enabled = formData.optJSONObject("enabled");
            	    if (enabled != null){
            	        jchans = fillChannelsFromJSON(enabled);
            	    }
            		targets = new ArrayList<IMMessageTarget>(channelsNames.length);
            		for (int i=0; i < channelsNames.length; i++) {

            			if (Util.fixEmptyAndTrim(channelsNames[i]) == null) {
            				throw new FormException("Channel name must not be empty", "channel.name");
            			}

            			String password = Util.fixEmpty(channelsPasswords[i]);
                		boolean notifyOnly = jchans != null ? jchans.get(i).getBoolean("notificationOnly") : false;

        				targets.add(new GroupChatIMMessageTarget(channelsNames[i], password, notifyOnly));
            		}
            	}
            	this.defaultTargets = targets;

                this.hudsonLogin = req.getParameter(getParamNames().getJenkinsLogin());

                this.useNotice = "on".equals(req.getParameter(PARAMETERNAME_USE_NOTICE));

                this.charset = req.getParameter("irc_publisher.charset");

                this.useColors = "on".equals(req.getParameter(PARAMETERNAME_USE_COLORS));

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

        /**
         * @see hudson.model.Descriptor#newInstance(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	String[] channelsNames = req.getParameterValues("irc_publisher.channel.name");
        	String[] channelsPasswords = req.getParameterValues("irc_publisher.channel.password");

        	List<IMMessageTarget> targets = Collections.emptyList();
        	if (channelsNames != null) {
                List<JSONObject> jchans = fillChannelsFromJSON(formData);
        		targets = new ArrayList<IMMessageTarget>(channelsNames.length);
        		for (int i=0; i < channelsNames.length; i++) {

        			if (Util.fixEmptyAndTrim(channelsNames[i]) == null) {
        				throw new FormException("Channel name must not be empty", "channel.name");
        			}

        			String password = Util.fixEmpty(channelsPasswords[i]);
            		boolean notifyOnly = jchans != null ? jchans.get(i).getBoolean("notificationOnly") : false;
    				targets.add(new GroupChatIMMessageTarget(channelsNames[i], password, notifyOnly));
        		}
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
         * Returns the password that should be used to try and identify
         * with NickServ.
         */
        public String getNickServPassword() {
            return Scrambler.descramble(nickServPassword);
        }

        public String getLogin() {
            return this.login;
        }

        //@Override
        public String getPassword() {
            return Scrambler.descramble(password);
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

	    //@Override
	    public IMMessageTargetConverter getIMMessageTargetConverter() {
	        return CONVERTER;
	    }

		/**
		 * Specifies if the bot should use the /notice command
		 * instead of the /msg command to notify.
		 */
		public boolean isUseNotice() {
		    return this.useNotice;
		}

		/**
		 * Specifies if the bot should send message with colors.
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
		private Object readResolve() {
			if (this.defaultTargets == null) {
				if (this.channels != null) {
					this.defaultTargets = new ArrayList<IMMessageTarget>(this.channels.size());
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

			if (!this.scrambledPasswords) {
			    this.password = Scrambler.scramble(this.password);
			    this.nickServPassword = Scrambler.scramble(this.nickServPassword);
			    this.scrambledPasswords = true;
			    // JENKINS-15469: seems to be a bad idea to save in readResolve
			    // as the file to be saved/replaced is currently open for reading and thus
			    // save() will fail on Windows
			    //save();
			}

			return this;
		}

    }
}
