package neqsim.mcp.model;

import neqsim.process.processmodel.ProcessSystem;

/**
 * Typed result model for process simulations.
 *
 * <p>
 * Contains the simulation report JSON, the process system reference (for further Java-side
 * inspection), and the process system name. This is the typed counterpart for the JSON returned by
 * {@code ProcessRunner.run(String)}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessResult {

  private final String processSystemName;
  private final transient ProcessSystem processSystem;
  private final String reportJson;

  /**
   * Creates a process result.
   *
   * @param processSystemName the process system name
   * @param processSystem the process system reference (transient — not serialized)
   * @param reportJson the simulation report as JSON string
   */
  public ProcessResult(String processSystemName, ProcessSystem processSystem, String reportJson) {
    this.processSystemName = processSystemName;
    this.processSystem = processSystem;
    this.reportJson = reportJson;
  }

  /**
   * Gets the process system name.
   *
   * @return the name
   */
  public String getProcessSystemName() {
    return processSystemName;
  }

  /**
   * Gets the process system reference for further Java-side access.
   *
   * @return the process system, or null if not available
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Gets the simulation report as a JSON string.
   *
   * @return the report JSON
   */
  public String getReportJson() {
    return reportJson;
  }
}
