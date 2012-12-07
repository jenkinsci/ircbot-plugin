package hudson.plugins.ircbot.v2;

import org.pircbotx.Colors;

/**
 * Simple support for IRC colors.
 * 
 * @author syl20bnr
 */
public class IRCColor {

    private final String message;

    public IRCColor(String message) {
        this.message = message;
    }

    public String colorize(){
        String color = Colors.DARK_GRAY;
        if(this.message.startsWith("Starting")){
            if (this.message.contains("FAIL")){
                color = Colors.BROWN;
            }
            else if (this.message.contains("FIXED")){
                color = Colors.OLIVE;
            }
            else{
                color = Colors.DARK_GREEN;
            }
        }
        else if(this.message.contains("FIXED")){
           color = Colors.REVERSE + Colors.BOLD + Colors.GREEN;
        }
        else if(this.message.contains("SUCCESS")){
           color = Colors.BOLD + Colors.GREEN;
        }
        else if(this.message.contains("FAILURE")){
           color = Colors.REVERSE + Colors.BOLD + Colors.RED;
        }
        else if(this.message.contains("STILL FAILING")){
           color = Colors.BOLD + Colors.RED;
        }
        return color + this.message;
    }

}
