/**
 * Created on Oct 4, 2007 11:15:03 PM
 * 
 * Copyright FullSIX
 */
package hudson.plugins.ircbot;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenBuild;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.model.BuildListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author bruyeron
 * @version $Id: MavenIrcReporter.java 23404 2009-11-01 12:47:17Z kutzi $
 */
public class MavenIrcReporter extends MavenReporter {

    /**
     * channels to notify with build status If not empty, this replaces the main
     * channels defined at the descriptor level.
     */
    protected List<String> channels = new ArrayList<String>();
    
    /**
     * @see hudson.maven.MavenReporter#end(hudson.maven.MavenBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        IrcNotifier.perform(build, channelList());
        return true;
    }
    
    private List<String> channelList() {
        return (channels == null || channels.isEmpty()) ? IrcPublisher.DESCRIPTOR.channels
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
     * @see hudson.maven.MavenReporter#getDescriptor()
     */
    @Override
    public MavenReporterDescriptor getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {

        @Extension
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(MavenIrcReporter.class);
        }

        @Override
		public String getDisplayName() {
            return "IRC Notification";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/ircbot/help.html";
        }

        @Override
        public MavenIrcReporter newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            MavenIrcReporter result = new MavenIrcReporter();
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
    }
}
