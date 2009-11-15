/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;
import hudson.plugins.im.IMPublisher;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.plugins.ircbot.v2.IRCConnectionProvider;
import hudson.plugins.ircbot.v2.IRCMessageTargetConverter;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * @author bruyeron
 * @version $Id: IrcPublisher.java 23739 2009-11-15 22:06:53Z kutzi $
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
     */
    public List<String> channels = new ArrayList<String>();

    public IrcPublisher(final String targetsAsString, final String notificationStrategy,
    		final boolean notifyGroupChatsOnBuildStart,
    		final boolean notifySuspects,
    		final boolean notifyCulprits,
    		final boolean notifyFixers) throws IMMessageTargetConversionException
    {
        super(targetsAsString, notificationStrategy, notifyGroupChatsOnBuildStart,
        		notifySuspects, notifyCulprits, notifyFixers);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
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
		IrcUserProperty jabberUserProperty = (IrcUserProperty) user.getProperties().get(IrcUserProperty.DESCRIPTOR);
		if (jabberUserProperty != null) {
			return jabberUserProperty.getNick();
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
    
    @Override
    protected IMMessageTargetConverter getIMMessageTargetConverter() {
        return CONVERTER;
    }
    
    @Override
    protected List<IMMessageTarget> getNotificationTargets() {
    	List<IMMessageTarget> perJobTargets = super.getNotificationTargets();
    	if (perJobTargets == null || perJobTargets.isEmpty()) {
    		// get the ones from the descriptor
    		List<String> descChannels = DESCRIPTOR.channels;
    		
    		List<IMMessageTarget> result = new ArrayList<IMMessageTarget>(descChannels.size());
    		for (String s : descChannels) {
    			try {
					IMMessageTarget target = getIMMessageTargetConverter().fromString(s);
					if (target != null) {
						result.add(target);
					}
				} catch (IMMessageTargetConversionException e) {
					// ignore
				}
    		}
    		return result;
    	} else {
    		return perJobTargets;
    	}
    }
    
    // deserialize/migrate old instances
    private Object readResolve() {
    	if (this.getNotificationTargets() == null) {
    		if (this.channels != null) {
    			StringBuilder targets = new StringBuilder();
    			for (String channel : channels) {
    				targets.append(channel).append(" ");
    			}
    			try {
					setTargets(targets.toString().trim());
				} catch (IMMessageTargetConversionException e) {
					LOGGER.warning(ExceptionHelper.dump(e));
				}
    		} else {
    			try {
					setTargets("");
				} catch (IMMessageTargetConversionException e) {
					LOGGER.warning(ExceptionHelper.dump(e));
				}
    		}
    	}
    	
    	if (getNotificationStrategy() == null) {
    		// set to the fixed strategy in ircbot <= 1.7
    		setNotificationStrategy(NotificationStrategy.STATECHANGE_ONLY);
    	}
    	return this;
    }

	/**
     * Descriptor for {@link IrcPublisher}
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> implements IMPublisherDescriptor {

    	private static final String PREFIX = "irc_publisher.";
        public static final String PARAMETERNAME_TARGETS = PREFIX + "targets";
        public static final String PARAMETERNAME_STRATEGY = PREFIX + "strategy";
        public static final String PARAMETERNAME_NOTIFY_START = PREFIX + "notifyStart";
        public static final String PARAMETERNAME_NOTIFY_SUSPECTS = PREFIX + "notifySuspects";
        public static final String PARAMETERNAME_NOTIFY_CULPRITS = PREFIX + "notifyCulprits";
        public static final String PARAMETERNAME_NOTIFY_FIXERS = PREFIX + "notifyFixers";
		public static final String PARAMETERVALUE_STRATEGY_DEFAULT = NotificationStrategy.STATECHANGE_ONLY.getDisplayName();;
		public static final String[] PARAMETERVALUE_STRATEGY_VALUES = NotificationStrategy.getDisplayNames();

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
         */
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
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            enabled = "on".equals(req.getParameter("irc_publisher.enabled"))
                    || "true".equals(req.getParameter("irc_publisher.enabled"));
            if (enabled) {
                hostname = req.getParameter("irc_publisher.hostname");
                password = req.getParameter("irc_publisher.password");
                nick = req.getParameter("irc_publisher.nick");
                try {
                    port = Integer.valueOf(req.getParameter("irc_publisher.port"));
                } catch (NumberFormatException e) {
                    throw new FormException("port field must be an Integer",
                            "irc_publisher.port");
                }
                commandPrefix = req.getParameter("irc_publisher.commandPrefix");
                commandPrefix = Util.fixEmptyAndTrim(commandPrefix);
                channels = Arrays.asList(req.getParameter(
                        "irc_publisher.channels").split(" "));

                // try to establish the connection
                try {
                	IRCConnectionProvider.setDesc(this);
                	IRCConnectionProvider.getInstance().currentConnection();
                } catch (final Exception e) {
                    //throw new FormException("Unable to create Client: " + ExceptionHelper.dump(e), null);
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
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	final String t = req.getParameter(PARAMETERNAME_TARGETS);
            String n = req.getParameter(PARAMETERNAME_STRATEGY);
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
            boolean notifyStart = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_START));
            boolean notifySuspects = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_SUSPECTS));
            boolean notifyCulprits = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_CULPRITS));
            boolean notifyFixers = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_FIXERS));
            try
            {
                return new IrcPublisher(t, n, notifyStart, notifySuspects, notifyCulprits,
                		notifyFixers);
            }
            catch (final IMMessageTargetConversionException e)
            {
                throw new FormException(e, PARAMETERNAME_TARGETS);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
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

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
		public String getDefaultIdSuffix() {
			// not implemented for IRC, yet
			return null;
		}

		@Override
		public String getHost() {
			return this.hostname;
		}
		@Override
		public String getHudsonUserName() {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public String getHudsonPassword() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getPluginDescription() {
			return "IRC notifier plugin";
		}

		@Override
		public String getUserName() {
			return this.nick;
		}

		@Override
		public boolean isExposePresence() {
			return true;
		}
    }
}
