package dev.railroadide.core.ui;

import org.kordamp.ikonli.Ikon;

import com.sun.javafx.scene.ParentHelper;
import com.sun.javafx.scene.traversal.ParentTraversalEngine;

import dev.railroadide.core.ui.skin.RRToggleButtonSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

public class RRToggleButton extends RRButton implements Toggle {

	public static final String[] DEFAULT_STYLE_CLASSES = { "rr-button", "rr-toggle-button", "toggle-button" };

	private BooleanProperty selected;
	private ObjectProperty<ToggleGroup> toggleGroup;

	//#region Constructor

	public RRToggleButton() {
        this("");
    }

	public RRToggleButton(String localizationKey, Ikon icon, Object... args) {
        super(localizationKey, icon, args);
    }

	public RRToggleButton(String localizationKey, Node graphic, Object... args) {
		super(localizationKey, graphic, args);
    }

    public RRToggleButton(String localizationKey, Object... args) {
		super(localizationKey, args);
    }

	@Override
	protected void initialize() {
		super.initialize();

		getStyleClass().setAll(RRToggleButton.DEFAULT_STYLE_CLASSES);

		setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
	}

	//#endregion

	//#region Toggle

	/** {@inheritDoc} */
    @Override public void fire() {
        if (!isDisabled()) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent());
        }
    }
	
	@Override
	public ToggleGroup getToggleGroup() {
		return toggleGroup == null ? null : toggleGroup.get();
	}

	@Override
	public void setToggleGroup(ToggleGroup toggleGroup) {
		toggleGroupProperty().set(toggleGroup);
	}

	@Override
    public final ObjectProperty<ToggleGroup> toggleGroupProperty() {
        if (toggleGroup == null) {
            toggleGroup = new ToggleGroupProperty(this);
        }
        return toggleGroup;
    }

	@Override
	public boolean isSelected() {
        return selected == null ? false : selected.get();
	}

	@Override
	public void setSelected(boolean selected) {
		selectedProperty().set(selected);
	}

	@Override
    public final BooleanProperty selectedProperty() {
        if (selected == null) {
            selected = new SelectedProperty(this);
        }
        return selected;
    }

	/** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case SELECTED: return isSelected();
            default: return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

	//#endregion

	//#region Properties
	
	private class ToggleGroupProperty extends ObjectPropertyBase<ToggleGroup> {
        
		private ToggleGroup old;
		private final RRToggleButton toggle;
		private final ChangeListener<Toggle> listener;

		public ToggleGroupProperty(RRToggleButton toggle) {
			this.toggle = toggle;
			this.listener = (o, oV, nV) -> ParentHelper
				.getTraversalEngine(toggle)
				.setOverriddenFocusTraversability(nV != null ? isSelected() : null);
		}

        @Override
		protected void invalidated() {
            final ToggleGroup tg = get();
            
			if (tg != null && !tg.getToggles().contains(toggle)) {
                if (old != null) {
                    old.getToggles().remove(toggle);
                }
				tg.getToggles().add(toggle);
                
				final ParentTraversalEngine parentTraversalEngine = new ParentTraversalEngine(toggle);
                ParentHelper.setTraversalEngine(toggle, parentTraversalEngine);
                
				// If there's no toggle selected, do not override
                parentTraversalEngine.setOverriddenFocusTraversability(tg.getSelectedToggle() != null ? isSelected() : null);
                tg.selectedToggleProperty().addListener(listener);
            
			} else if (tg == null) {
                old.selectedToggleProperty().removeListener(listener);
                old.getToggles().remove(toggle);
                ParentHelper.setTraversalEngine(toggle, null);
            }
			
			old = tg;
        }

        @Override
        public Object getBean() {
            return toggle;
        }

        @Override
        public String getName() {
            return "toggleGroup";
        }
    }

	private class SelectedProperty extends BooleanPropertyBase
	{
		private static final PseudoClass PSEUDO_CLASS_SELECTED =
        	PseudoClass.getPseudoClass("selected");
		
		private final RRToggleButton toggle;

		public SelectedProperty(RRToggleButton toggle) {
			this.toggle = toggle;
		}

        @Override
		protected void invalidated() {
            final boolean selected = get();
            final ToggleGroup tg = toggle.getToggleGroup();
            
			toggle.pseudoClassStateChanged(PSEUDO_CLASS_SELECTED, selected);
            toggle.notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
            
			if (tg != null) {
                if (selected) {
                    tg.selectToggle(toggle);
                } else if (tg.getSelectedToggle() == toggle) {
                    clearSelectedToggle(tg);
                }
            }
        }

		private void clearSelectedToggle(ToggleGroup tg) {
			if (!tg.getSelectedToggle().isSelected()) {
				for (Toggle toggle : tg.getToggles()) {
					if (toggle.isSelected()) {
						return;
					}
				}
			}
			tg.selectToggle(null);
		}

        @Override
        public Object getBean() {
            return toggle;
        }

        @Override
        public String getName() {
            return "selected";
        }
    }

	//#endregion

	//#region Skin

	@Override
	protected Skin<?> createDefaultSkin() {
		return new RRToggleButtonSkin(this);
	}

	//#endregion
}
