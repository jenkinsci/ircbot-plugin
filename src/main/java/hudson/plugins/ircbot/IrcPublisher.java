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
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.tools.ExceptionHelper;
import hudson.plugins.ircbot.v2.IRCConnectionProvider;
import hudson.plugins.ircbot.v2.IRCMessageTargetConverter;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Publishes build results to IRC channels.
 * 
 * @author bruyeron
 * @author $Author: kutzi $ (last change)
 * @version $Id: IrcPublisher.java 26294 2010-01-23 10:20:31Z kutzi $
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
    		boolean notifyUpstreamCommitters)
    {
        super(defaultTargets, notificationStrategy, notifyGroupChatsOnBuildStart,
        		notifySuspects, notifyCulprits, notifyFixers, notifyUpstreamCommitters);
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
    private Object readResolve() {
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
        public static final String PARAMETERNAME_STRATEGY = PREFIX + "strategy";
        public static final String PARAMETERNAME_NOTIFY_START = PREFIX + "notifyStart";
        public static final String PARAMETERNAME_NOTIFY_SUSPECTS = PREFIX + "notifySuspects";
        public static final String PARAMETERNAME_NOTIFY_CULPRITS = PREFIX + "notifyCulprits";
        public static final String PARAMETERNAME_NOTIFY_FIXERS = PREFIX + "notifyFixers";
        public static final String PARAMETERNAME_NOTIFY_UPSTREAM_COMMITTERS = PREFIX + "notifyUpstreamCommitters";
        
		public static final String PARAMETERVALUE_STRATEGY_DEFAULT = NotificationStrategy.STATECHANGE_ONLY.getDisplayName();;
		public static final String[] PARAMETERVALUE_STRATEGY_VALUES = NotificationStrategy.getDisplayNames();
		public static final String PARAMETERNAME_HUDSON_LOGIN = PREFIX + "hudsonLogin";
	    public static final String PARAMETERNAME_HUDSON_PASSWORD = PREFIX + "hudsonPassword";
	    public static final String PARAMETERNAME_USE_NOTICE = PREFIX + "useNotice";

		boolean enabled = false;

        String hostname = null;

        Integer port = 194;

        String password = null;

        String nick = null;

        /**
         * channels to join
         * 
         * @deprecated Only to deserialize old descriptors
         */
        @Deprecated
		List<String> channels;
        
        private List<IMMessageTarget> defaultTargets;

        String commandPrefix = null;
        
        private String hudsonLogin;
        private String hudsonPassword;
        
        private boolean useNotice;

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
            this.enabled = "on".equals(req.getParameter("irc_publisher.enabled"))
                    || "true".equals(req.getParameter("irc_publisher.enabled"));
            if (this.enabled) {
                this.hostname = req.getParameter("irc_publisher.hostname");
                this.password = req.getParameter("irc_publisher.password");
                this.nick = req.getParameter("irc_publisher.nick");
                try {
                    this.port = Integer.valueOf(req.getParameter("irc_publisher.port"));
                } catch (NumberFormatException e) {
                    throw new FormException("port field must be an Integer",
                            "irc_publisher.port");
                }
                this.commandPrefix = req.getParameter("irc_publisher.commandPrefix");
                this.commandPrefix = Util.fixEmptyAndTrim(commandPrefix);
                
            	String[] channelsNames = req.getParameterValues("irc_publisher.channel.name");
            	String[] channelsPasswords = req.getParameterValues("irc_publisher.channel.password");
            	
            	List<IMMessageTarget> targets = Collections.emptyList();
            	if (channelsNames != null) {
            		targets = new ArrayList<IMMessageTarget>(channelsNames.length);
            		for (int i=0; i < channelsNames.length; i++) {
            			
            			if (Util.fixEmptyAndTrim(channelsNames[i]) == null) {
            				throw new FormException("Channel name must not be empty", "channel.name");
            			}
            			
            			if (Util.fixEmpty(channelsNames[i]) != null) {
            				targets.add(new GroupChatIMMessageTarget(channelsNames[i], channelsPasswords[i]));
            			} else {
            				targets.add(new GroupChatIMMessageTarget(channelsNames[i]));
            			}
            		}
            	}
            	this.defaultTargets = targets;
            	
                this.hudsonLogin = req.getParameter(PARAMETERNAME_HUDSON_LOGIN);
                this.hudsonPassword = req.getParameter(PARAMETERNAME_HUDSON_PASSWORD);
                
                this.useNotice = "on".equals(req.getParameter(PARAMETERNAME_USE_NOTICE));
                
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
        		targets = new ArrayList<IMMessageTarget>(channelsNames.length);
        		for (int i=0; i < channelsNames.length; i++) {
        			
        			if (Util.fixEmptyAndTrim(channelsNames[i]) == null) {
        				throw new FormException("Channel name must not be empty", "channel.name");
        			}
        			
        			if (Util.fixEmpty(channelsNames[i]) != null) {
        				targets.add(new GroupChatIMMessageTarget(channelsNames[i], channelsPasswords[i]));
        			} else {
        				targets.add(new GroupChatIMMessageTarget(channelsNames[i]));
        			}
        		}
        	}
        	
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
            boolean notifyUpstream = "on".equals(req.getParameter(PARAMETERNAME_NOTIFY_UPSTREAM_COMMITTERS));

            return new IrcPublisher(targets, n, notifyStart, notifySuspects, notifyCulprits,
                		notifyFixers, notifyUpstream);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * @return the commandPrefix
         */
        @Override
        public String getCommandPrefix() {
            return commandPrefix;
        }

        /**
         * @return the hostname
         */
        @Override
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
			return this.hudsonLogin;
		}
		
		@Override
		public String getHudsonPassword() {
			return this.hudsonPassword;
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
		
		@Override
		public List<IMMessageTarget> getDefaultTargets() {
			if (this.defaultTargets == null) {
				return Collections.emptyList();
			}
			
			return this.defaultTargets;
		}
		
	    @Override
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
		 * Deserialize old descriptors.
		 */
		private Object readResolve() {
			if (this.defaultTargets == null) {
				if (this.channels != null) {
					this.defaultTargets = new ArrayList<IMMessageTarget>(this.channels.size());
					for (String channel : this.channels) {
						this.defaultTargets.add(new GroupChatIMMessageTarget(channel));
					}
					
					this.channels = null;
					save();
				}
			}
			
			return this;
		}
		
    }
}
