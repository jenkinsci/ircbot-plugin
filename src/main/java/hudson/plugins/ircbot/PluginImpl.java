package hudson.plugins.ircbot;

import hudson.Plugin;

/**
 * Entry point of the plugin.
 * 
 * @author Renaud Bruyeron
 * @version $Id: PluginImpl.java 22199 2009-09-25 23:22:46Z mindless $
 * @plugin
 */
public class PluginImpl extends Plugin {
    
    /**
     * @see hudson.Plugin#stop()
     */
    @Override
    public void stop() throws Exception {
        IrcPublisher.DESCRIPTOR.stop();
    }

}
