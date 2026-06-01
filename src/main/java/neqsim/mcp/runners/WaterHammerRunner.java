package neqsim.mcp.runners;

import neqsim.process.operations.WaterHammerStudy;

/**
 * MCP runner for water-hammer and liquid-hammer screening studies.
 *
 * <p>
 * The runner delegates to {@link WaterHammerStudy} so command-line, Java, notebook, and MCP clients
 * use the same route/tag/event normalization and transient calculation logic.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class WaterHammerRunner {

  /**
   * Private constructor for utility class.
   */
  private WaterHammerRunner() {}

  /**
   * Runs a water-hammer study from JSON.
   *
   * @param waterHammerJson JSON input with fluid, pipe or STID route, tag data, and event schedule
   * @return JSON result with surge metrics, envelopes, validation, and warnings
   */
  public static String run(String waterHammerJson) {
    return WaterHammerStudy.run(waterHammerJson);
  }
}