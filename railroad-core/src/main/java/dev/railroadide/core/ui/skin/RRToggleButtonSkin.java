package dev.railroadide.core.ui.skin;

import com.sun.javafx.scene.control.behavior.BehaviorBase;

import dev.railroadide.core.ui.RRToggleButton;
import dev.railroadide.core.ui.behavior.RRToggleButtonBehavior;
import javafx.scene.control.skin.LabeledSkinBase;

public class RRToggleButtonSkin extends LabeledSkinBase<RRToggleButton>
{
    private final BehaviorBase<RRToggleButton> behavior;

	public RRToggleButtonSkin(RRToggleButton control) {
        super(control);

        behavior = new RRToggleButtonBehavior<RRToggleButton>(control);
    }

    @Override
    public void dispose() {
        super.dispose();

        if (behavior != null) {
            behavior.dispose();
        }
    }
}
