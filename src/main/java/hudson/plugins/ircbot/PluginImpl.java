package hudson.plugins.ircbot;

import hudson.Plugin;
import hudson.maven.MavenReporters;
import hudson.model.UserProperties;
import hudson.tasks.BuildStep;

/**
 * Entry point of the plugin.
 * 
 * @author Renaud Bruyeron
 * @version $Id: PluginImpl.java 5101 2007-10-04 22:20:46Z bruyeron $
 * @plugin
 */
public class PluginImpl extends Plugin {
    
    public void start() throws Exception {
        // plugins normally extend Hudson by providing custom implementations
        // of 'extension points'. In this case, we'll add one publisher.
        BuildStep.PUBLISHERS.addNotifier(IrcPublisher.DESCRIPTOR);
        UserProperties.LIST.add(IrcUserProperty.DESCRIPTOR);
        MavenReporters.LIST.add(MavenIrcReporter.DescriptorImpl.DESCRIPTOR);
    }

    /**
     * @see hudson.Plugin#stop()
     */
    @Override
    public void stop() throws Exception {
        IrcPublisher.DESCRIPTOR.stop();
    }

}
