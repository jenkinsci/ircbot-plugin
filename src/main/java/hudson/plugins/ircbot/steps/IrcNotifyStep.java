package hudson.plugins.ircbot.steps;

/* Snatched one-for-one from Jabber plugin implem by akomakom :
 *   https://github.com/jenkinsci/jabber-plugin/pull/18/files#diff-c197962302397baf3a4cc36463dce5ea
 * and renamed some tokens for IRC plugin symbols.
 * Most of the implementation depends on the instmsg plugin changes in PR:
 *   https://github.com/jenkinsci/instant-messaging-plugin/pull/16
 */

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.im.MatrixJobMultiplier;
import hudson.plugins.im.NotificationStrategy;
import hudson.plugins.im.build_notify.BuildToChatNotifier;
import hudson.plugins.im.build_notify.DefaultBuildToChatNotifier;
import hudson.plugins.ircbot.IrcPublisher;
import hudson.plugins.ircbot.v2.IRCMessageTargetConverter;
import hudson.util.ListBoxModel;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step
 */
public class IrcNotifyStep extends Step {

    // NOTE: Bump this number if the class evolves as a breaking change
    // (e.g. serializable fields change)
    private static final long serialVersionUID = 2;

    private final static char TARGET_SEPARATOR_CHAR = ' ';
    private final static IRCMessageTargetConverter CONVERTER = new IRCMessageTargetConverter();

    private String targets;
    private boolean notifyOnStart; // Set to true explicitly in an ircNotify step reporting start of build
    private boolean notifySuspects;
    private boolean notifyCulprits;
    private boolean notifyFixers;
    private boolean notifyUpstreamCommitters;
    private String notificationStrategy = NotificationStrategy.ALL.getDisplayName();
    private BuildToChatNotifier buildToChatNotifier = new DefaultBuildToChatNotifier();
    private MatrixJobMultiplier matrixNotifier = MatrixJobMultiplier.ONLY_PARENT;

    // Append an additional message to usual notifications about build start/completion
    private String extraMessage;

    // Instead of build status messages, send an arbitrary message to specified
    // or default (global config) targets with the pipeline step (and ignoring
    // the strategy and filtering rules options above)
    private String customMessage;

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new IrcNotifyStepExecution(this, context);
    }

    @DataBoundConstructor
    public IrcNotifyStep() {
        this.targets = ""; // Notify all channels subscribed via global config
        this.notifyOnStart = false;
    }

    @DataBoundSetter
    public void setTargets(String targets) {
        this.targets = targets;
    }

    public String getTargets() {
        return targets;
    }

    public boolean isNotifyOnStart() {
        return notifyOnStart;
    }

    @DataBoundSetter
    public void setNotifyOnStart(boolean notifyOnStart) {
        this.notifyOnStart = notifyOnStart;
    }

    public boolean isNotifySuspects() {
        return notifySuspects;
    }

    @DataBoundSetter
    public void setNotifySuspects(boolean notifySuspects) {
        this.notifySuspects = notifySuspects;
    }

    public boolean isNotifyCulprits() {
        return notifyCulprits;
    }

    @DataBoundSetter
    public void setNotifyCulprits(boolean notifyCulprits) {
        this.notifyCulprits = notifyCulprits;
    }

    public boolean isNotifyFixers() {
        return notifyFixers;
    }

    @DataBoundSetter
    public void setNotifyFixers(boolean notifyFixers) {
        this.notifyFixers = notifyFixers;
    }

    public boolean isNotifyUpstreamCommitters() {
        return notifyUpstreamCommitters;
    }

    @DataBoundSetter
    public void setNotifyUpstreamCommitters(boolean notifyUpstreamCommitters) {
        this.notifyUpstreamCommitters = notifyUpstreamCommitters;
    }

    public BuildToChatNotifier getBuildToChatNotifier() {
        return buildToChatNotifier;
    }

    @DataBoundSetter
    public void setBuildToChatNotifier(BuildToChatNotifier buildToChatNotifier) {
        this.buildToChatNotifier = buildToChatNotifier;
    }

    public MatrixJobMultiplier getMatrixNotifier() {
        return matrixNotifier;
    }

    @DataBoundSetter
    public void setMatrixNotifier(MatrixJobMultiplier matrixNotifier) {
        this.matrixNotifier = matrixNotifier;
    }

    public String getNotificationStrategy() {
        return notificationStrategy;
    }

    @DataBoundSetter
    public void setNotificationStrategy(String notificationStrategy) {
        this.notificationStrategy = notificationStrategy;
    }

    public String getExtraMessage() {
        return extraMessage;
    }

    @DataBoundSetter
    public void setExtraMessage(String extraMessage) {
        this.extraMessage = extraMessage;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    @DataBoundSetter
    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }

    private static class IrcNotifyStepExecution extends SynchronousNonBlockingStepExecution<Void> {
        // NOTE: Bump this number if the class evolves as a breaking change
        // (e.g. serializable fields change)
        private static final long serialVersionUID = 2;

        private transient final IrcNotifyStep step;

        public IrcNotifyStepExecution(@Nonnull IrcNotifyStep step, @Nonnull StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
//            getContext().get(TaskListener.class).getLogger().println("IrcNotifyStep: sending message with strategy " + step.notificationStrategy);

            IrcPublisher publisher = new IrcPublisher(
                    step.targets.equals("")
                        ? IrcPublisher.DESCRIPTOR.getDefaultTargets()
                        : CONVERTER.allFromString((List<String>)Arrays.asList(StringUtils.split(step.targets, TARGET_SEPARATOR_CHAR))),
                    step.notificationStrategy,
                    step.notifyOnStart,
                    step.notifySuspects,
                    step.notifyCulprits,
                    step.notifyFixers,
                    step.notifyUpstreamCommitters,
                    step.buildToChatNotifier,
                    step.matrixNotifier
            );
            publisher.setExtraMessage(step.extraMessage);
            publisher.setCustomMessage(step.customMessage);

            publisher.perform(
                    getContext().get(Run.class),
                    getContext().get(FilePath.class),
                    getContext().get(Launcher.class),
                    getContext().get(TaskListener.class));

            return null;
        }
    }


    @Extension(optional=true)
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "ircNotify";
        }

        @Override
        public String getDisplayName() {
            return "IRC Notification";
        }

        public ListBoxModel doFillNotificationStrategyItems() {
            ListBoxModel items = new ListBoxModel();
            for (NotificationStrategy strategy : NotificationStrategy.values()) {
                items.add(strategy.getDisplayName(), strategy.getDisplayName());
            }
            return items;
        }
    }
}
