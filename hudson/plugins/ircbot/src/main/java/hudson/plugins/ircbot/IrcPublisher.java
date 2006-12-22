/**
 * Created on Dec 6, 2006 9:25:19 AM
 */
package hudson.plugins.ircbot;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.tasks.Publisher;

import java.io.IOException;
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
 * @version $Id: IrcPublisher.java 1349 2006-12-17 01:38:09Z kohsuke $
 */
public class IrcPublisher extends Publisher {

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
	 * 
	 */
	public IrcPublisher() {
	}

	/**
	 * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher, hudson.model.BuildListener)
	 */
	public boolean perform(Build build, Launcher launcher,
			BuildListener listener) {
		if(build.getPreviousBuild() != null){
			if(build.getResult() != build.getPreviousBuild().getResult()){
				DESCRIPTOR.bot.publish(build);
			}
		} else {
			if(build.getResult() != Result.SUCCESS){
				DESCRIPTOR.bot.publish(build);
			}
		}
		return true;
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
	 * @version $Id: IrcPublisher.java 1349 2006-12-17 01:38:09Z kohsuke $
	 */
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

    	boolean enabled = false;
    	String hostname = null;
    	Integer port = 194;
    	String password = null;
    	String nick = null;
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
					commandPrefix = commandPrefix + " ";
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
			return new IrcPublisher();
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
			}
			
			void publish(Build build){
				String status = null;
				if(build.getResult() ==  Result.SUCCESS){
					status = "fixed";
				} else if(build.getResult() == Result.FAILURE){
					status = "failed";
				} else if(build.getResult() == Result.UNSTABLE){
					status = "unstable";
				}
				if(status != null){
					for(String channel:channels){
						sendNotice(channel, build.getProject().getName() + " build " + status + " (#" + build.getNumber() + ")");
					}
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
						List<Job> jobs = Hudson.getInstance().getJobs();
						if(jobs.isEmpty()){
							sendNotice(sender, "No jobs configured");
						} else {
							for(Job job : jobs){
								if(job instanceof Project){
									if(job.getLastBuild() != null){
										sendNotice(sender, job.getName() + ": " + job.getLastBuild().getResult().toString() + " (#" + job.getLastBuild().getNumber() + ")");
									} else {
										sendNotice(sender, job.getName() + ": no build");
									}
								}
							}
						}
					} else if(command.startsWith("build")){
						String jobName = command.substring(5).trim();
						if(jobName.length() == 0){
							sendNotice(sender, "You must specify a project name");
						} else {
							if(jobName != null && jobName.length() > 0){
								Job j = Hudson.getInstance().getJob(jobName);
								if(j!=null && (j instanceof Project)){
									Project p = (Project) j;
									if(p.isInQueue()){
										sendNotice(sender, jobName + " is already in build queue");
									} else {
										if(p.isDisabled()){
											sendNotice(sender, jobName + " is disabled");
										} else {
											p.scheduleBuild();
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
