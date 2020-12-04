package hudson.plugins.ircbot.v2;

import hudson.plugins.im.DefaultIMMessageTarget;
import hudson.plugins.im.GroupChatIMMessageTarget;
import hudson.plugins.im.IMMessageTarget;
import hudson.plugins.im.IMMessageTargetConversionException;
import hudson.plugins.im.IMMessageTargetConverter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class IRCMessageTargetConverter implements IMMessageTargetConverter {

    //@Override
    public IMMessageTarget fromString(String targetAsString)
            throws IMMessageTargetConversionException {
        if (targetAsString == null || targetAsString.trim().length() == 0) {
            return null;
        }

        targetAsString = targetAsString.trim();

        if (targetAsString.startsWith("#")) {
            return new GroupChatIMMessageTarget(targetAsString);
        } else {
            return new DefaultIMMessageTarget(targetAsString);
        }
    }

    public List<IMMessageTarget> allFromString(final Collection<String> targetsAsString)
            throws IMMessageTargetConversionException {
        List<IMMessageTarget> finalTargets = new LinkedList();
        for(String target : targetsAsString) {
            finalTargets.add(fromString(target));
        }
        return finalTargets;
    }

    //@Override
    public String toString(IMMessageTarget target) {
        return target.toString();
    }
}
