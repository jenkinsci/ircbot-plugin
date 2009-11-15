package hudson.plugins.ircbot.v2;

import hudson.plugins.im.IMConnection;
import hudson.plugins.im.IMConnectionProvider;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMPublisherDescriptor;
import hudson.plugins.ircbot.IrcPublisher;

public class IRCConnectionProvider extends IMConnectionProvider {

    private static final IMConnectionProvider INSTANCE = new IRCConnectionProvider();
    
    public static final synchronized IMConnectionProvider getInstance() {
        return INSTANCE;
    }
    
    public static final synchronized void setDesc(IMPublisherDescriptor desc) throws IMException {
    	INSTANCE.setDescriptor(desc);
    	INSTANCE.releaseConnection();
    	INSTANCE.currentConnection();
    }

    private IRCConnectionProvider() {
    	super();
    }

    @Override
    public synchronized IMConnection createConnection() throws IMException {
        releaseConnection();

        if (getDescriptor() == null) {
        	return null;
        }
        
        IMConnection imConnection = new IRCConnection((IrcPublisher.DescriptorImpl)getDescriptor(),
        		getAuthentication());
        if (imConnection.connect()) {
        	return imConnection;
        }
        return null;
    }

}
