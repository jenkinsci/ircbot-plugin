/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.plugins.im.IMPublisher;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.Publisher;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bruyeron
 * @version $Id: IrcPublisher.java 1834 2007-01-20 07:10:24Z kohsuke $
 */
public class IrcPublisher extends IMPublisher<IrcPublisher> {

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    /**
     * channels to notify with build status
     * If not empty, this replaces the main channels defined at the descriptor level.
     */    
    public List<String> channels = new ArrayList<String>();
    
    /**
	 * 
	 */
	public IrcPublisher() {
	}
	
	/**
	 * @see hudson.plugins.im.IMPublisher#reportFailure(hudson.model.Build)
	 */
	@Override
	protected void reportFailure(Build build) {
		String status = "failed";
		String suspects = calculateSuspectsString(build.getChangeSet());
		DESCRIPTOR.bot.sendNotice(channelList(), build.getProject().getName() + " build " + status + " (" + Hudson.getInstance().getRootUrl() + build.getUrl() + ")" + (suspects == null ? "":suspects));
	}

	/**
	 * @see hudson.plugins.im.IMPublisher#reportSuccess(hudson.model.Build)
	 */
	@Override
	protected void reportSuccess(Build build) {
		String status = "fixed";
		DESCRIPTOR.bot.sendNotice(channelList(), build.getProject().getName() + " build " + status + " (" + Hudson.getInstance().getRootUrl() + build.getUrl() + ")");
	}

	/**
	 * @see hudson.plugins.im.IMPublisher#reportUnstability(hudson.model.Build)
	 */
	@Override
	protected void reportUnstability(Build build) {
		String status = "unstable";
		String suspects = calculateSuspectsString(build.getChangeSet());
		DESCRIPTOR.bot.sendNotice(channelList(), build.getProject().getName() + " build " + status + " (" + Hudson.getInstance().getRootUrl() + build.getUrl() + ")" + (suspects == null ? "":suspects));
	}
	
	private String calculateSuspectsString(ChangeLogSet<? extends Entry> cs){
		if(cs != null && !cs.isEmptySet()){
			StringBuilder sb = new StringBuilder(" last commit(s): ");
			for(Iterator<? extends Entry> it = cs.iterator(); it.hasNext();){
				IrcUserProperty iup = (IrcUserProperty) it.next().getAuthor().getProperties().get(IrcUserProperty.DESCRIPTOR);
				sb.append(iup.getNick());
				if(it.hasNext())
					sb.append(",");
			}
			return sb.toString();
		}
		return null;
	}
	
	private List<String> channelList(){
		return (channels == null || channels.isEmpty()) ? DESCRIPTOR.channels:channels;
	}
	
	/**
	 * For the UI redisplay
	 * 
	 * @return
	 */
	public String getChannels(){
		StringBuilder sb = new StringBuilder();
		if(channels != null){
			for(String c : channels){
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
	 * @version $Id: IrcPublisher.java 1834 2007-01-20 07:10:24Z kohsuke $
	 */
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

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
			} catch(Exception e){
				LOGGER.log(Level.WARNING, "IRC bot could not connect - please review connection details", e);
			}
		}
		
		public void initBot() throws NickAlreadyInUseException, IOException, IrcException {
			if(enabled){
				bot = new IrcBot(nick);				
				bot.connect(hostname, port, password);
				for(String channel : channels){
					bot.joinChannel(channel);
				}
				LOGGER.info("IRC bot connected and channels joined");
			}
		}
		
		public void stop(){
			if(bot != null && bot.isConnected()){
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
			if(enabled){
				hostname = req.getParameter("irc_publisher.hostname");
				password = req.getParameter("irc_publisher.password");
				nick = req.getParameter("irc_publisher.nick");
				try {
					port = Integer.valueOf(req.getParameter("irc_publisher.port"));
					if(port == null){
						port = 194;
					}
				} catch(NumberFormatException e){
					throw new FormException("port field must be an Integer", "irc_publisher.port");
				}
				commandPrefix = req.getParameter("irc_publisher.commandPrefix");
				if(commandPrefix == null || "".equals(commandPrefix.trim())){
					commandPrefix = null;
				} else {
					commandPrefix = commandPrefix.trim() + " ";
				}
				channels = Arrays.asList(req.getParameter("irc_publisher.channels").split(" "));
			}
			save();
			stop();
			try {
				initBot();
			} catch (NickAlreadyInUseException e) {
				throw new FormException("Nick <" + nick + "> already in use on this server", "irc_publisher.nick");
			} catch (IOException e) {
				throw new FormException("Impossible to connect to IRC server", e, null);
			} catch (IrcException e) {
				throw new FormException("Impossible to connect to IRC server", e, null);
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
		
		public String getChannels(){
			StringBuilder sb = new StringBuilder();
			if(channels != null){
				for(String c : channels){
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
			result.channels.addAll(Arrays.asList(req.getParameter("channels").split(" ")));
			// dedup
			result.channels.removeAll(channels);
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
			
			IrcBot(String name){
				setName(name);
				setMessageDelay(5);
			}
			
			protected void sendNotice(List<String> channels, String message){
				for(String channel:channels){
					sendNotice(channel, message);
				}
			}
			
			/**
			 * @see org.jibble.pircbot.PircBot#onMessage(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
			 */
			@Override
			protected void onMessage(String channel, String sender, String login, String hostname, String message) {
				if(commandPrefix != null && message.startsWith(commandPrefix)){
					final String command = message.substring(commandPrefix.length()).trim();
					if("status".equals(command)){
						List<AbstractProject> jobs = Hudson.getInstance().getAllItems(AbstractProject.class);
						if(jobs.isEmpty()){
							sendNotice(sender, "No jobs configured");
						} else {
							for(AbstractProject job : jobs){
                                if(job.getLastBuild() != null){
                                    sendNotice(sender, job.getName() + ": " + job.getLastBuild().getResult().toString() + " (" + Hudson.getInstance().getRootUrl() + job.getLastBuild().getUrl() + ")" + (job.isInQueue() ? ": BUILDING":""));
                                } else {
                                    sendNotice(sender, job.getName() + ": no build");
                                }
                            }
						}
					} else if(command.startsWith("build")){
						String jobName = command.substring(5).trim();
						if(jobName.length() == 0){
							sendNotice(sender, "You must specify a project name");
						} else {
							if(jobName.length() > 0){
								Project project = Hudson.getInstance().getItemByFullName(jobName,Project.class);
								if(project!=null){
                                    if(project.isInQueue()){
										sendNotice(sender, jobName + " is already in build queue");
									} else {
										if(project.isDisabled()){
											sendNotice(sender, jobName + " is disabled");
										} else {
											project.scheduleBuild();
											sendNotice(sender, jobName + " build scheduled");
										}

									}
								}
								}
						}
					}
				}
			}

			/**
			 * @see org.jibble.pircbot.PircBot#onPrivateMessage(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
			 */
			@Override
			protected void onPrivateMessage(String sender, String login, String hostname, String message) {
				if(commandPrefix == null){
					sendNotice(sender, "the property <commandPrefix> must be set on the Hudson configuration screen");
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
