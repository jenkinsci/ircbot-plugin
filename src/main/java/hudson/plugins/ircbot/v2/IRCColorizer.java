package hudson.plugins.ircbot.v2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pircbotx.Colors;

/**
 * Simple support for IRC colors.
 * 
 * @author syl20bnr
 */
public class IRCColorizer {
    
    /**
     * Very simple pattern to recognize test results.
     */
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(".*test.*", Pattern.CASE_INSENSITIVE);

    /**
     * Colorize the message line if certain keywords are found in it. 
     */
    public static String colorize(String message){
        
        // TODO: use ResultTrend.getID() instead of magic keyword strings!
        
        String foreground = Colors.DARK_GRAY;
        if(message.contains("Starting ")){
            if (message.contains("STILL FAILING")){
                foreground = Colors.BROWN;
            }
            else if (message.contains("FAILURE")){
                foreground = Colors.BOLD + Colors.YELLOW;
            }
            else{
                foreground = Colors.DARK_GREEN;
            }
        }
        else if(message.contains("FIXED")){
           foreground = Colors.BOLD + Colors.UNDERLINE + Colors.GREEN;
        }
        else if(message.contains("SUCCESS")){
           foreground = Colors.BOLD + Colors.GREEN;
        }
        else if(message.contains("FAILURE")){
           foreground = Colors.BOLD + Colors.UNDERLINE + Colors.RED;
        }
        else if(message.contains("STILL FAILING")){
           foreground = Colors.BOLD + Colors.RED;
        }
        else{
           Matcher m = TEST_CLASS_PATTERN.matcher(message);
           if (m.matches()){
               foreground = Colors.BOLD + Colors.MAGENTA;
           }
        }
        return foreground + message;
    }

}
