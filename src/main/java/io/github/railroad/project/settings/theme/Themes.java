package io.github.railroad.project.settings.theme;

import static io.github.railroad.utility.helper.ColorHelper.fxColourToAwt;

import javafx.scene.paint.Color;

/**
 * A list of default themes
 * 
 * @author matyrobbrt
 *
 */
public class Themes {

	public static final Theme DARK_THEME = new Theme("DarkMode").withButtonColor(fxColourToAwt(Color.DARKGRAY))
			.withBackgroundColor(fxColourToAwt(Color.DIMGRAY))
			.withTextColor(fxColourToAwt(Color.ALICEBLUE))
			.withTextFieldColor(fxColourToAwt(Color.LIGHTGRAY));

	public static final Theme WHITE_THEME = new Theme("WhiteMode").withButtonColor(fxColourToAwt(Color.ANTIQUEWHITE))
			.withBackgroundColor(fxColourToAwt(Color.FLORALWHITE))
			.withTextColor(fxColourToAwt(Color.BLACK))
			.withTextFieldColor(fxColourToAwt(Color.WHITESMOKE));
}
