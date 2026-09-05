package dev.railroadide.railroad.project.minecraft.pistonmeta;

/**
 * Minecraft game and JVM launch arguments.
 *
 * @param game the arguments passed to the game entry point
 * @param jvm the arguments passed to the Java virtual machine
 */
public record Arguments(CLIArguments game, CLIArguments jvm) {
}
