/**
 * Created on Dec 21, 2006 2:40:45 PM
 * 
 * Copyright FullSIX
 */
package hudson.plugins.ircbot;

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserPropertyDescriptor;

import org.kohsuke.stapler.StaplerRequest;

/**
 * @author bruyeron
 * @version $Id: IrcUserProperty.java 22199 2009-09-25 23:22:46Z mindless $
 */
public class IrcUserProperty extends hudson.model.UserProperty {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final String nick;

    public IrcUserProperty(String nick) {
        this.nick = nick;
    }

    public String getNick() {
        if (nick != null)
            return nick;

        return user.getId();

    }

    /**
     * @see hudson.model.Describable#getDescriptor()
     */
    @Override
    public UserPropertyDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public DescriptorImpl() {
            super(IrcUserProperty.class);
        }

        public String getDisplayName() {
            return "IRC";
        }

        public IrcUserProperty newInstance(User user) {
            return new IrcUserProperty(null);
        }

        public IrcUserProperty newInstance(StaplerRequest req)
                throws FormException {
            return new IrcUserProperty(req.getParameter("irc.nick"));
        }
    }

}
