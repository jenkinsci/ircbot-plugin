/**
 * Copyright (C) 2010-2014 Leon Blakey <lord.quackstar at gmail.com>
 *
 * This file is part of PircBotX.
 *
 * PircBotX is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * PircBotX is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * PircBotX. If not, see <http://www.gnu.org/licenses/>.
 */
package hudson.plugins.ircbot.v2;


import com.google.common.collect.ImmutableMap;

/**
 * The Colors class provides several static fields and methods that you may find
 * useful when writing an IRC Bot.
 * <p>
 * This class contains constants that are useful for formatting lines sent to
 * IRC servers. These constants allow you to apply various formatting to the
 * lines, such as colours, boldness, underlining and reverse text.
 * <p>
 * The class contains static methods to remove colours and formatting from lines
 * of IRC text.
 * <p>
 * Here are some examples of how to use the constants
 * <pre>
 * message(Colors.BOLD + "A bold hello!");
 * <b>A bold hello!</b>
 * message(Colors.RED + "Red" + Colors.NORMAL + " text");
 * <font color="red">Red</font> text
 * message(Colors.BOLD + Colors.RED + "Bold and red");
 * <b><font color="red">Bold and red</font></b></pre>
 * <p/>
 * Please note that some IRC channels may be configured to reject any messages
 * that use colours. Also note that older IRC clients may be unable to correctly
 * display lines that contain colours and other control characters.
 * <p>
 * Note that this class name has been spelt in the American style in order to
 * remain consistent with the rest of the Java API.
 *
 *
 * @since PircBot 0.9.12
 * @author Origionally by:
 * <a href="http://www.jibble.org/">Paul James Mutton</a> for <a
 * href="http://www.jibble.org/pircbot.php">PircBot</a>
 * <p>
 * Forked and Maintained by Leon Blakey in <a
 * href="http://github.com/thelq/pircbotx">PircBotX</a>
 */
public final class IRCColors {
	/**
	 * Removes all previously applied color and formatting attributes.
	 */
	public static final String NORMAL = "\u000f";
	/**
	 * Bold text.
	 */
	public static final String BOLD = "\u0002";
	/**
	 * Underlined text.
	 */
	public static final String UNDERLINE = "\u001f";
	/**
	 * Reversed text (may be rendered as italic text in some clients).
	 */
	public static final String REVERSE = "\u0016";
	/**
	 * White coloured text.
	 */
	public static final String WHITE = "\u000300";
	/**
	 * Black coloured text.
	 */
	public static final String BLACK = "\u000301";
	/**
	 * Dark blue coloured text.
	 */
	public static final String DARK_BLUE = "\u000302";
	/**
	 * Dark green coloured text.
	 */
	public static final String DARK_GREEN = "\u000303";
	/**
	 * Red coloured text.
	 */
	public static final String RED = "\u000304";
	/**
	 * Brown coloured text.
	 */
	public static final String BROWN = "\u000305";
	/**
	 * Purple coloured text.
	 */
	public static final String PURPLE = "\u000306";
	/**
	 * Olive coloured text.
	 */
	public static final String OLIVE = "\u000307";
	/**
	 * Yellow coloured text.
	 */
	public static final String YELLOW = "\u000308";
	/**
	 * Green coloured text.
	 */
	public static final String GREEN = "\u000309";
	/**
	 * Teal coloured text.
	 */
	public static final String TEAL = "\u000310";
	/**
	 * Cyan coloured text.
	 */
	public static final String CYAN = "\u000311";
	/**
	 * Blue coloured text.
	 */
	public static final String BLUE = "\u000312";
	/**
	 * Magenta coloured text.
	 */
	public static final String MAGENTA = "\u000313";
	/**
	 * Dark gray coloured text.
	 */
	public static final String DARK_GRAY = "\u000314";
	/**
	 * Light gray coloured text.
	 */
	public static final String LIGHT_GRAY = "\u000315";
	/**
	 * Italicized text.
	 */
	public static final String ITALICS = "\u001d";
	/**
	 * Pre-built lookup table by String for all available colors
	 */
	public static final ImmutableMap<String, String> COLORS_TABLE = ImmutableMap.<String, String>builder()
			.put("WHITE", WHITE)
			.put("BLACK", BLACK)
			.put("DARK_BLUE", DARK_BLUE)
			.put("DARK_GREEN", DARK_GREEN)
			.put("RED", RED)
			.put("BROWN", BROWN)
			.put("PURPLE", PURPLE)
			.put("OLIVE", OLIVE)
			.put("YELLOW", YELLOW)
			.put("GREEN", GREEN)
			.put("TEAL", TEAL)
			.put("CYAN", CYAN)
			.put("BLUE", BLUE)
			.put("MAGENTA", MAGENTA)
			.put("DARK_GRAY", DARK_GRAY)
			.put("LIGHT_GRAY", LIGHT_GRAY)
			.build();
	/**
	 * Pre-built lookup table by String for all available formatting options
	 */
	public static final ImmutableMap<String, String> FORMATTING_TABLE = ImmutableMap.<String, String>builder()
			.put("NORMAL", NORMAL)
			.put("BOLD", BOLD)
			.put("UNDERLINE", UNDERLINE)
			.put("REVERSE", REVERSE)
			.put("ITALICS", ITALICS)
			.build();
	/**
	 * Pre-built lookup table by String for all the fields in this class.
	 */
	public static final ImmutableMap<String, String> LOOKUP_TABLE = ImmutableMap.<String, String>builder()
			.putAll(COLORS_TABLE)
			.putAll(FORMATTING_TABLE)
			.build();
	

	/**
	 * This class should not be constructed.
	 */
	private IRCColors() {
	}

	/**
	 * Lookup color as a String
	 *
	 * @param colorName Name of field in this class, will be converted to
	 * uppercase
	 * @return Color value or null
	 */
	public static String lookup(String colorName) {
		return LOOKUP_TABLE.get(colorName.toUpperCase());
	}

	/**
	 * Removes all colours from a line of IRC text.
	 *
	 * @since PircBot 1.2.0
	 *
	 * @param line the input text.
	 *
	 * @return the same text, but with all colours removed.
	 */
	public static String removeColors(String line) {
		int length = line.length();
		StringBuilder buffer = new StringBuilder();
		int i = 0;
		while (i < length) {
			char ch = line.charAt(i);
			if (ch == '\u0003') {
				i++;
				// Skip "x" or "xy" (foreground color).
				if (i < length) {
					ch = line.charAt(i);
					if (Character.isDigit(ch)) {
						i++;
						if (i < length) {
							ch = line.charAt(i);
							if (Character.isDigit(ch))
								i++;
						}
						// Now skip ",x" or ",xy" (background color).
						if (i < length) {
							ch = line.charAt(i);
							if (ch == ',') {
								i++;
								if (i < length) {
									ch = line.charAt(i);
									if (Character.isDigit(ch)) {
										i++;
										if (i < length) {
											ch = line.charAt(i);
											if (Character.isDigit(ch))
												i++;
										}
									} else
										// Keep the comma.
										i--;
								} else
									// Keep the comma.
									i--;
							}
						}
					}
				}
			} else if (ch == '\u000f')
				i++;
			else {
				buffer.append(ch);
				i++;
			}
		}
		return buffer.toString();
	}

	/**
	 * Remove formatting from a line of IRC text.
	 *
	 * @since PircBot 1.2.0
	 *
	 * @param line the input text.
	 *
	 * @return the same text, but without any bold, underlining, reverse, etc.
	 */
	public static String removeFormatting(String line) {
		int length = line.length();
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < length; i++) {
			char ch = line.charAt(i);
			//Filter characters
			if (ch != '\u000f' && ch != '\u0002' && ch != '\u001f' && ch != '\u0016')
				buffer.append(ch);
		}
		return buffer.toString();
	}

	/**
	 * Removes all formatting and colours from a line of IRC text.
	 *
	 * @since PircBot 1.2.0
	 *
	 * @param line the input text.
	 *
	 * @return the same text, but without formatting and colour characters.
	 *
	 */
	public static String removeFormattingAndColors(String line) {
		return removeFormatting(removeColors(line));
	}
}
