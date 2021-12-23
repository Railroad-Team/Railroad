package io.github.railroad.utility.helper;

/**
 * 
 * @author matyrobbrt
 *
 */
public class TextHelper {
	
	private TextHelper() {
	}

	public static String insertSpaceAfterEachChar(String str) {
		StringBuilder result = new StringBuilder();

		for(int i = 0 ; i < str.length(); i++)
		{
		   result = result.append(str.charAt(i));
		   if(i == str.length()-1)
		      break;
		   result = result.append(' ');
		}

		return (result.toString());
	}
	
}
