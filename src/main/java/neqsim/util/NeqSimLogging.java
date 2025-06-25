package neqsim.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class NeqSimLogging {
  private static final Logger logger = LogManager.getLogger("neqsim");

  /**
   * Set global logging for all neqsim classes.
   *
   * @param level the logging level (e.g., DEBUG, INFO, WARN, ERROR)
   */
  public static void setGlobalLogging(String level) {
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    Configuration config = context.getConfiguration();

    Level logLevel = stringToLevel(level);

    // Update root logger
    LoggerConfig rootLoggerConfig = config.getRootLogger();
    rootLoggerConfig.setLevel(logLevel);

    // Update all loggers in the neqsim package
    config.getLoggers().forEach((name, loggerConfig) -> {
      if (name.startsWith("neqsim")) {
        loggerConfig.setLevel(logLevel);
      }
    });

    context.updateLoggers();
  }

  /**
   * Convert a string to a Log4j2 Level.
   *
   * @param level the string representation of the level
   * @return the corresponding Log4j2 Level
   */
  private static Level stringToLevel(String level) {
    try {
      return Level.valueOf(level.toUpperCase());
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid logging level: {}. Defaulting to INFO.", level);
      return Level.INFO;
    }
  }

  /**
   * Reset all loggers to their default configuration based on log4j2.properties.
   */
  public static void resetAllLoggers() {
    try {
      LoggerContext context = (LoggerContext) LogManager.getContext(false);
      context.setConfigLocation(
          NeqSimLogging.class.getClassLoader().getResource("log4j2.properties").toURI());
      context.reconfigure();
    } catch (Exception e) {
      logger.error("Failed to reset loggers: {}", e.getMessage());
    }
  }
}


