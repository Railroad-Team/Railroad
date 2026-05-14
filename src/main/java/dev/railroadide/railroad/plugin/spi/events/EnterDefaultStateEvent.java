package dev.railroadide.railroad.plugin.spi.events;

import dev.railroadide.railroad.plugin.spi.event.Event;

/**
 * This event is triggered when the application enters the default state.
 * It can be used to perform actions that should occur when the application is in its default state (no editor windows).
 */
public final class EnterDefaultStateEvent implements Event {}
