/**
 * Created on Oct 4, 2007 11:23:31 PM
 * 
 * Copyright FullSIX
 */
package hudson.plugins.ircbot;

import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author bruyeron
 * @version $Id: IrcNotifier.java 23404 2009-11-01 12:47:17Z kutzi $
 */
public class IrcNotifier {

    private static final Logger LOGGER = Logger.getLogger(IrcNotifier.class.getName());
    
    static final void perform(AbstractBuild<?, ?> build, List<String> channels) {
        if (build.getPreviousBuild() != null) {
            // only broadcast change of status
            if(build.getResult() != null && build.getPreviousBuild().getResult() != null){
                if (!build.getResult().toString().equals(
                        build.getPreviousBuild().getResult().toString())) {
                    publish(build, channels);
                }
            } else {
                LOGGER.warning("results should not be null! current: " + build.getResult() + " previous: " + build.getPreviousBuild().getResult());
            }
        } else {
            // if first build, only broadcast failure/unstable
            if (build.getResult() != Result.SUCCESS) {
                publish(build, channels);
            }
        }
    }

    private static final void publish(AbstractBuild<?, ?> build, List<String> channels) {
        if (build.getResult() == Result.SUCCESS) {
            reportSuccess(build, channels);
        } else if (build.getResult() == Result.FAILURE) {
            reportFailure(build, channels);
        } else if (build.getResult() == Result.UNSTABLE) {
            reportUnstability(build, channels);
        }
    }

    private static void reportFailure(AbstractBuild<?, ?> build, List<String> channels) {
        String status = "failed";
        String suspects = calculateSuspectsString(build.getChangeSet());
        IrcPublisher.DESCRIPTOR.bot.sendNotice(channels, build
                .getProject().getName()
                + " build "
                + status
                + " ("
                + Hudson.getInstance().getRootUrl()
                + build.getUrl() + "console)" + (suspects == null ? "" : suspects));
    }

    private static void reportSuccess(AbstractBuild<?, ?> build, List<String> channels) {
        String status = "fixed";
        IrcPublisher.DESCRIPTOR.bot.sendNotice(channels, build
                .getProject().getName()
                + " build "
                + status
                + " ("
                + Hudson.getInstance().getRootUrl()
                + build.getUrl() + ")");
    }

    private static void reportUnstability(AbstractBuild<?, ?> build, List<String> channels) {
        String status = "unstable";
        String suspects = calculateSuspectsString(build.getChangeSet());
        IrcPublisher.DESCRIPTOR.bot.sendNotice(channels, build
                .getProject().getName()
                + " build "
                + status
                + " ("
                + Hudson.getInstance().getRootUrl()
                + build.getUrl() + "testReport)" + (suspects == null ? "" : suspects));
    }

    private static <T extends Entry> String calculateSuspectsString(
            ChangeLogSet<T> cs) {
        if (cs != null && !cs.isEmptySet()) {
            StringBuilder sb = new StringBuilder(" last commit(s): ");
            for (Iterator<? extends Entry> it = cs.iterator(); it.hasNext();) {
                IrcUserProperty iup = (IrcUserProperty) it.next().getAuthor()
                        .getProperties().get(IrcUserProperty.DESCRIPTOR);
                sb.append(iup.getNick());
                if (it.hasNext())
                    sb.append(",");
            }
            return sb.toString();
        }
        return null;
    }

}
