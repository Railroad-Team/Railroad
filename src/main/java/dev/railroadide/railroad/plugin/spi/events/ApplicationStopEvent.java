package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * Event that is fired when the application is stopping.
 * This event can be used to perform cleanup operations before the application exits.
 */
public class ApplicationStopEvent implements Event {}
