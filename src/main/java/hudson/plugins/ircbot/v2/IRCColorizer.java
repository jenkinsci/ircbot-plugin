package hudson.plugins.ircbot.v2;

import hudson.model.ResultTrend;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.*;
import java.io.*;
import java.util.concurrent.ThreadLocalRandom;

import org.pircbotx.Colors;

/**
 * Simple support for IRC colors.
 * 
 * @author syl20bnr
 * @author kutzi
 * @author tao 
 * @author hongjae
 * @author austin
 */
// See http://flylib.com/books/en/4.74.1.47/1/ for some tips on IRC colors
public class IRCColorizer {
	
	private static final Logger LOGGER = Logger.getLogger(IRCColorizer.class.getName());
    
    /**
     * Very simple pattern to recognize test results.
     */
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(".*test.*", Pattern.CASE_INSENSITIVE);
    
    //userPattern store the String/Pattern->color preference for each specific user
    //e.g.userPattern[nickname][user_prefered_string_pattern]
    private static HashMap<String, HashMap<String, String> > userPattern; 
    private static HashMap<String, HashMap<ResultTrend, String> > themes; 
    private static String currentTheme;
    private static ArrayList<ResultTrend> result;
        
    /**
     * static constructor
     * unserialize userPattern
     * populate colorMap
     * load a default user pattern
     */
    static {
    	readFile("userPattern.ser");
    	createThemes();
    	currentTheme = "THEME1";
    }
    
    
    
    /**
     * creates default theme
     * @return hashmap for type theme
     */
    public static HashMap<ResultTrend, String> createDefault() {
    	HashMap<ResultTrend, String> theme = new HashMap<ResultTrend, String>();
    	theme.put(ResultTrend.FIXED, Colors.BOLD + Colors.UNDERLINE + Colors.GREEN);
    	theme.put(ResultTrend.SUCCESS, Colors.BOLD + Colors.GREEN);
    	theme.put(ResultTrend.FAILURE, Colors.BOLD + Colors.UNDERLINE + Colors.RED);
    	theme.put(ResultTrend.STILL_FAILING, Colors.BOLD + Colors.RED);
    	theme.put(ResultTrend.UNSTABLE, Colors.BOLD + Colors.UNDERLINE + Colors.BROWN);
    	theme.put(ResultTrend.STILL_UNSTABLE, Colors.BOLD + Colors.BROWN);
    	theme.put(ResultTrend.NOW_UNSTABLE, Colors.BOLD + Colors.MAGENTA);
    	theme.put(ResultTrend.ABORTED, Colors.BOLD + Colors.LIGHT_GRAY);
    	
    	return theme;
    }
    
    /**
     * creates default theme by color to change all result trend text with one color
     * @param color color string
     * @return hashmap for type theme
     */
    public static HashMap<ResultTrend, String> oneColorTheme(String color) {
    	HashMap<ResultTrend, String> theme = new HashMap<ResultTrend, String>();
    	String colorString = IRCColors.lookup(color);
    	theme.put(ResultTrend.FIXED, Colors.BOLD + Colors.UNDERLINE + colorString);
    	theme.put(ResultTrend.SUCCESS, Colors.BOLD + colorString);
    	theme.put(ResultTrend.FAILURE, Colors.BOLD + Colors.UNDERLINE + colorString);
    	theme.put(ResultTrend.STILL_FAILING, Colors.BOLD + colorString);
    	theme.put(ResultTrend.UNSTABLE, Colors.BOLD + Colors.UNDERLINE + colorString);
    	theme.put(ResultTrend.STILL_UNSTABLE, Colors.BOLD + colorString);
    	theme.put(ResultTrend.NOW_UNSTABLE, Colors.BOLD + colorString);
    	theme.put(ResultTrend.ABORTED, Colors.BOLD + colorString);
    	
    	return theme;
    }
    
    /**
     * creates default theme 2
     * @return hashmap for type theme
     */
    public static HashMap<ResultTrend, String> theme2() {
    	HashMap<ResultTrend, String> theme = new HashMap<ResultTrend, String>();
    	theme.put(ResultTrend.FIXED, Colors.BOLD + Colors.UNDERLINE + Colors.BLUE);
    	theme.put(ResultTrend.SUCCESS, Colors.BOLD + Colors.BLUE);
    	theme.put(ResultTrend.FAILURE, Colors.BOLD + Colors.UNDERLINE + Colors.RED);
    	theme.put(ResultTrend.STILL_FAILING, Colors.BOLD + Colors.RED);
    	theme.put(ResultTrend.UNSTABLE, Colors.BOLD + Colors.UNDERLINE + Colors.MAGENTA);
    	theme.put(ResultTrend.STILL_UNSTABLE, Colors.BOLD + Colors.MAGENTA);
    	theme.put(ResultTrend.NOW_UNSTABLE, Colors.BOLD + Colors.RED);
    	theme.put(ResultTrend.ABORTED, Colors.BOLD + Colors.BLACK);
    	
    	return theme;
    }
    
    /**
     * clean userPattern by nickname
     * @param nickname user's nickname
     */
    public static void cleanUserPattern(String nickname)
	{
    	userPattern.remove(nickname);
    	currentTheme = "THEME1";
	}
    
    /**
     * changes theme to specific theme
     * @param theme
     */
    public static String changeTheme(String theme) {
    	//check to make sure the theme exists
        if (themes.get(theme) == null) {
            return currentTheme;
        }
        currentTheme = theme;
        return currentTheme;
    }
    
    /**
     * get userPattern size
     * @return size of userPattern
     */
    public static int getSize()
	{
    	return userPattern.size();
	}
    
    /**
     * get user's Pattern->color size by nickname
     * @param nickname user's nickname
     * @return size of user pattern related to nickname
     */
    public static int getSizeByNickName(String nickname)
	{
    	return userPattern.get(nickname).size();
	}
    
    
    /**
     * Colorize the message line if certain keywords are found in it. 
     * @param message message that needs to be changed
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
    
    /**
     * Colorize the message line if certain keywords are found in it. 
     * @param nickname user's nickname
     * @param message jenkin's message
     */
    public static String colorize(String nickname, String message) {
    	if(userPattern.containsKey(nickname)){
            return user_colorize(nickname, message);
    	} else {
    		return colorize(message);
    	}
    }
    
    /**
     * set the user defined color for specific string,
     * will be called by onMessage() method in PircListener.java
     * @param nickname user's nickname
     * @param pattern patterns that user wants to change, it also includes regex
     * @param color e.g RED, BLUE, GREEN
     */
    public static void setter(String nickname, String pattern, String color) 
    {
        // get hashmap for userid or create a new one
        HashMap<String, String> user_hash;
        if(userPattern.containsKey(nickname)){
            user_hash = userPattern.get(nickname);
        } else {
            user_hash = new HashMap<String, String>();
        }
        
        for(String name: user_hash.keySet()) {
        	System.out.println(name + " : " + user_hash.get(name));
        }

        // with user_hash add Pattern and String-color
        user_hash.put(pattern, color);
        
        // put user_hash back into userPattern
        userPattern.put(nickname, user_hash);
        
        // Serialize userPattern to disk
        writeFile("userPattern.ser");        
    }
    
    // creates default Themes
    private static void createThemes() {
    	themes = new HashMap<String, HashMap<ResultTrend,String>>();
    	themes.put("THEME1", createDefault());
    	themes.put("THEME2", oneColorTheme("OLIVE"));
    	themes.put("THEME3", theme2());
    }
    
    //set the message with user preference if the preference can be retrieved in userPattern
    private static String user_colorize(String nickname, String message)
    {
    	if(!userPattern.containsKey(nickname)) {
        	return message;
        }
        // get hashmap for userid or create a new one
        HashMap<String, String> user_hash;
        
        user_hash = userPattern.get(nickname);
        
        // replace var
        String new_text;

        for(Map.Entry<String, String> user_patterns_iter: user_hash.entrySet()) {
            // we are getting each pattern 1 at a time
            String upString = user_patterns_iter.getKey();
            Pattern up = Pattern.compile(upString);
            String ucolor = IRCColors.lookup(user_patterns_iter.getValue().trim());

            // collect all the matches in the group
            HashSet<String> matches = new HashSet<String>();
            Matcher m = up.matcher(message);
            while(m.find()) {
                
            	matches.add(m.group());
            }

            // for each work in the set of matches
            // replace will color
            Iterator<String> matches_iter = matches.iterator();
            while(matches_iter.hasNext()) {
                String text = matches_iter.next();
                new_text = ucolor + text + Colors.NORMAL;
                message = message.replaceAll(text, new_text);
            }
        }
        return message;
    }

    // Receives a string and applies colors based on theme colors to specific string
    private static String colorForBuildResult(String line) {
        for (ResultTrend result : ResultTrend.values()) {
            String keyword = result.getID();
            
            int index = line.indexOf(keyword);
            if (index != -1) {
                final String color;
                HashMap<ResultTrend, String> temp = themes.get(currentTheme);
                color = temp.get(result);
                
                return line.substring(0, index) + color + keyword + Colors.NORMAL
                        + line.substring(index + keyword.length(), line.length());
            }
        }
        return line;
    }
    
    // write data into a file
    private static void writeFile(String fileName) {
        ObjectOutputStream oos = null;
    	try {
    		FileOutputStream fout = new FileOutputStream("./"+fileName);
    		oos = new ObjectOutputStream(fout);
    		oos.writeObject(userPattern);
    		oos.close();
    	} catch(IOException ex) {
    		ex.printStackTrace();
    	} 
    }
    
    // read data from a file
    private static void readFile(String fileName) {
    	if(userPattern == null) {
    		userPattern = new HashMap<String, HashMap<String, String> >();
    	}
    	
    	try {
    		FileInputStream fis = new FileInputStream("./"+fileName);
    		InputStream buffer = new BufferedInputStream(fis);
    		ObjectInput input = new ObjectInputStream(buffer);
    		
    		userPattern = (HashMap<String, HashMap<String, String>>)input.readObject();
    		buffer.close();
    	} catch (ClassNotFoundException ex) {
    		ex.printStackTrace();
    	} catch(IOException ex) {
    		ex.printStackTrace();
    	}
    }
}
