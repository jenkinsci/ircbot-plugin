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

/**
 * @author bruyeron
 * @version $Id: IrcNotifier.java 5179 2007-10-08 08:24:15Z bruyeron $
 */
public class IrcNotifier {

    static final void perform(AbstractBuild build, List<String> channels) {
        if (build.getPreviousBuild() != null) {
            // only broadcast change of status
            if (!build.getResult().toString().equals(
                    build.getPreviousBuild().getResult().toString())) {
                publish(build, channels);
            }
        } else {
            // if first build, only broadcast failure/unstable
            if (build.getResult() != Result.SUCCESS) {
                publish(build, channels);
            }
        }
    }

    private static final void publish(AbstractBuild build, List<String> channels) {
        if (build.getResult() == Result.SUCCESS) {
            reportSuccess(build, channels);
        } else if (build.getResult() == Result.FAILURE) {
            reportFailure(build, channels);
        } else if (build.getResult() == Result.UNSTABLE) {
            reportUnstability(build, channels);
        }
    }

    private static void reportFailure(AbstractBuild build, List<String> channels) {
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

    private static void reportSuccess(AbstractBuild build, List<String> channels) {
        String status = "fixed";
        IrcPublisher.DESCRIPTOR.bot.sendNotice(channels, build
                .getProject().getName()
                + " build "
                + status
                + " ("
                + Hudson.getInstance().getRootUrl()
                + build.getUrl() + ")");
    }

    private static void reportUnstability(AbstractBuild build, List<String> channels) {
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
