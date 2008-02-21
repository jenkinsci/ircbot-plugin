/**
 * Created on Oct 11, 2007 9:47:22 AM
 * 
 * Copyright FullSIX
 */
package hudson.plugins.ircbot;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.plugins.ircbot.IrcPublisher.DescriptorImpl.IrcBot;

import java.util.List;
import java.util.logging.Logger;

/**
 * Commands for the IrcBot
 * 
 * @author bruyeron
 * @version $Id: BotCommands.java 7173 2008-02-21 14:39:33Z bruyeron $
 */
public enum BotCommands {

    status {

        /**
         * @see hudson.plugins.ircbot.BotCommands#execute(hudson.plugins.ircbot.IrcPublisher.DescriptorImpl.IrcBot, java.lang.String, java.lang.String, java.lang.String, java.util.List)
         */
        @Override
        void execute(IrcBot bot, String parameter, String channel, String sender, List<String> channels) {
            List<AbstractProject> jobs = Hudson.getInstance()
            .getAllItems(AbstractProject.class);
            if (jobs.isEmpty()) {
                bot.sendNotice(sender, "No jobs configured");
            } else {
                if(jobs.size() < 5){
                    for (AbstractProject job : jobs) {
                        if (!job.isDisabled()) {
                            if (job.getLastBuild() != null) {
                                bot.sendNotice(
                                        sender,
                                        job.getName()
                                        + ": "
                                        + job.getLastBuild()
                                        .getResult()
                                        .toString()
                                        + " ("
                                        + Hudson.getInstance()
                                        .getRootUrl()
                                        + job.getLastBuild()
                                        .getUrl()
                                        + ")"
                                        + (job.isInQueue() ? ": BUILDING"
                                                : ""));
                            } else {
                                bot.sendNotice(sender, job.getName()
                                        + ": no build");
                            }
                        }
                    }
                } else {
                    // more than 5 projects usually results in a ban/kick from
                    // the server because of flooding
                    bot.sendNotice(sender, "Too many projects (" + jobs.size() + ")");
                }
            }
        }

    },
    build {

        /**
         * @see hudson.plugins.ircbot.BotCommands#execute(hudson.plugins.ircbot.IrcPublisher.DescriptorImpl.IrcBot, java.lang.String, java.lang.String, java.lang.String, java.util.List)
         */
        @Override
        void execute(IrcBot bot, String parameter, String channel, String sender, List<String> channels) {
            String jobName = parameter.trim();
            if (jobName.length() == 0) {
                bot.sendNotice(sender,
                "You must specify a project name");
            } else {
                if (jobName.length() > 0) {
                    Project project = Hudson.getInstance()
                    .getItemByFullName(jobName,
                            Project.class);
                    if (project != null) {
                        if (project.isInQueue()) {
                            bot.sendNotice(sender, jobName
                                    + " is already in build queue");
                        } else {
                            if (project.isDisabled()) {
                                bot.sendNotice(sender, jobName
                                        + " is disabled");
                            } else {
                                project.scheduleBuild();
                                bot.sendNotice(sender, jobName
                                        + " build scheduled");
                            }

                        }
                    } else {
                        bot.sendNotice(sender, jobName
                                + " not found");
                    }
                }
            }

        }

    };
    
    private static final Logger LOGGER = Logger.getLogger(BotCommands.class.getName());

    abstract void execute(IrcBot bot, String parameter, String channel, String sender, List<String> channels);
}
