package hudson.plugins.ircbot.v2;

import hudson.model.ResultTrend;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.Colors;

/**
 * Simple support for IRC colors.
 * 
 * @author syl20bnr
 * @author kutzi
 */
// See http://flylib.com/books/en/4.74.1.47/1/ for some tips on IRC colors
public class IRCColorizer {
    
    /**
     * Very simple pattern to recognize test results.
     */
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(".*test.*", Pattern.CASE_INSENSITIVE);

    /**
     * Colorize the message line if certain keywords are found in it. 
     */
    public static String colorize(String message){
        
        if(message.contains("Starting ")) {
            return message;
        } else {
            String line = colorForBuildResult(message);
            if (line == message) { // line didn't contain a build result
                Matcher m = TEST_CLASS_PATTERN.matcher(message);
                if (m.matches()){
                    return Colors.BOLD + Colors.MAGENTA + line;
                }
            }
            return line;
        }
    }
    
    private static String colorForBuildResult(String line) {
        for (ResultTrend result : ResultTrend.values()) {
            
            String keyword = result.getID();
            
            int index = line.indexOf(keyword);
            if (index != -1) {
                final String color;
                switch (result) {
                    case FIXED: color = Colors.BOLD + Colors.UNDERLINE + Colors.GREEN; break;
                    case SUCCESS: color = Colors.BOLD + Colors.GREEN; break;
                    case FAILURE: color = Colors.BOLD + Colors.UNDERLINE + Colors.RED; break;
                    case STILL_FAILING: color = Colors.BOLD + Colors.RED; break;
                    case UNSTABLE: color = Colors.BOLD + Colors.UNDERLINE + Colors.BROWN; break;
                    case STILL_UNSTABLE: color = Colors.BOLD + Colors.BROWN; break;
                    case NOW_UNSTABLE: color = Colors.BOLD + Colors.MAGENTA; break;
                    case ABORTED: color = Colors.BOLD + Colors.LIGHT_GRAY; break;
                    default: return line;
                }
                
                return line.substring(0, index) + color + keyword + Colors.NORMAL
                        + line.substring(index + keyword.length(), line.length());
            }
        }
        return line;
    }

}
