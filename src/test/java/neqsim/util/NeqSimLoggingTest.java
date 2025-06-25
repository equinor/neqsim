package neqsim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class NeqSimLoggingTest {
  // add logger for class
  private static final Logger logger = LogManager.getLogger(NeqSimLoggingTest.class);

  @Test
  void testSetGlobalLogging() {
    // Enable global logging at DEBUG level
    neqsim.util.NeqSimLogging.setGlobalLogging("DEBUG");
    logger.info("Testing global logging at DEBUG level...");

    assertEquals("DEBUG", logger.getLevel().toString(),
        "neqsim logger should be set to DEBUG level.");

    neqsim.util.NeqSimLogging.setGlobalLogging("INFO");
    logger.info("This logging should not be seen.....Testing global logging at INFO level...");

    // Verify that the neqsim logger is set to OFF
    assertEquals("OFF", logger.getLevel().toString(), "neqsim logger should be set to OFF.");

    neqsim.util.NeqSimLogging.setGlobalLogging("OFF");
    assertEquals("OFF", logger.getLevel().toString(), "neqsim logger should be set to OFF.");


  }
}
