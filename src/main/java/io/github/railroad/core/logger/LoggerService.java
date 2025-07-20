package io.github.railroad.core.logger;

import io.github.railroad.logger.Logger;

/**
 * LoggerService interface provides a method to retrieve a Logger instance.
 * This is used for logging purposes within the Railroad application.
 */
public interface LoggerService {
    /**
     * Returns the Logger instance for logging messages.
     *
     * @return the Logger instance
     */
    Logger getLogger();
}
