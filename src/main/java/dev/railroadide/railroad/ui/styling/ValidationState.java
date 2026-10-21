package dev.railroadide.railroad.ui.styling;

/** Visual validation feedback for text input controls; selecting a state does not validate the input. */
public enum ValidationState {
    /** Neutral appearance with no validation feedback. */
    NONE,
    /** Indicates that the input passed validation. */
    SUCCESS,
    /** Indicates that the input failed validation. */
    ERROR,
    /** Indicates a potential issue that deserves attention. */
    WARNING
}
