package neqsim.mcp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Typed result model for process simulations.
 *
 * <p>
 * Contains the simulation report JSON, either a process system or process model reference (for
 * further Java-side inspection), and the process/model names. This is the typed counterpart for the
 * JSON returned by {@code ProcessRunner.run(String)}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessResult {

  private final String processSystemName;
  private final transient ProcessSystem processSystem;
  private final String processModelName;
  private final transient ProcessModel processModel;
  private final List<String> areaNames;
  private final String reportJson;

  /**
   * Creates a process result.
   *
   * @param processSystemName the process system name
   * @param processSystem the process system reference (transient - not serialized)
   * @param reportJson the simulation report as JSON string
   */
  public ProcessResult(String processSystemName, ProcessSystem processSystem, String reportJson) {
    this.processSystemName = processSystemName;
    this.processSystem = processSystem;
    this.processModelName = null;
    this.processModel = null;
    this.areaNames = Collections.emptyList();
    this.reportJson = reportJson;
  }

  /**
   * Creates a process-model result.
   *
   * @param processModelName the process model name
   * @param processModel the process model reference (transient - not serialized)
   * @param reportJson the simulation report as JSON string
   * @param areaNames area names included in the model
   */
  public ProcessResult(String processModelName, ProcessModel processModel, String reportJson,
      List<String> areaNames) {
    this.processSystemName = null;
    this.processSystem = null;
    this.processModelName = processModelName;
    this.processModel = processModel;
    this.areaNames =
        areaNames != null ? new ArrayList<String>(areaNames) : Collections.<String>emptyList();
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
   * Gets the process model name for multi-area simulations.
   *
   * @return the process model name, or null for single-process results
   */
  public String getProcessModelName() {
    return processModelName;
  }

  /**
   * Gets the process model reference for further Java-side access.
   *
   * @return the process model, or null if this result contains a single ProcessSystem
   */
  public ProcessModel getProcessModel() {
    return processModel;
  }

  /**
   * Gets area names for process-model results.
   *
   * @return unmodifiable list of area names, empty for single-process results
   */
  public List<String> getAreaNames() {
    return Collections.unmodifiableList(areaNames);
  }

  /**
   * Indicates whether this result contains a multi-area ProcessModel.
   *
   * @return true if a ProcessModel is present
   */
  public boolean isProcessModel() {
    return processModel != null;
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
