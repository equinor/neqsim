package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reproducible context for a root-cause investigation.
 *
 * <p>
 * A diagnosis case records what equipment and time window were evaluated, where the data came from, which process model
 * was used, and the operating and data-quality context needed to reproduce or audit the result. All fields are optional
 * except the equipment name so existing callers can adopt the model incrementally.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DiagnosisCase implements Serializable {

  private static final long serialVersionUID = 1000L;

  private String caseId;
  private final String equipmentName;
  private Double windowStartEpochSeconds;
  private Double windowEndEpochSeconds;
  private String processModelId = "";
  private String processModelRevision = "";
  private final Map<String, String> dataSources = new LinkedHashMap<String, String>();
  private final Map<String, String> operatingContext = new LinkedHashMap<String, String>();
  private final Map<String, String> dataQuality = new LinkedHashMap<String, String>();

  /**
   * Creates a diagnosis case for an equipment item.
   *
   * @param equipmentName diagnosed equipment name
   */
  public DiagnosisCase(String equipmentName) {
    if (equipmentName == null || equipmentName.trim().isEmpty()) {
      throw new IllegalArgumentException("equipmentName must not be null or empty");
    }
    this.equipmentName = equipmentName;
    this.caseId = equipmentName;
  }

  /**
   * Creates a diagnosis case populated from a historian window.
   *
   * @param equipmentName diagnosed equipment name
   * @param timestamps historian timestamps in epoch seconds, or {@code null}
   * @param historianData historian series keyed by tag
   * @return populated diagnosis case
   */
  public static DiagnosisCase fromHistorian(String equipmentName, double[] timestamps,
      Map<String, double[]> historianData) {
    DiagnosisCase diagnosisCase = new DiagnosisCase(equipmentName);
    if (timestamps != null && timestamps.length > 0) {
      double minimum = Double.POSITIVE_INFINITY;
      double maximum = Double.NEGATIVE_INFINITY;
      for (double timestamp : timestamps) {
        if (!Double.isNaN(timestamp)) {
          minimum = Math.min(minimum, timestamp);
          maximum = Math.max(maximum, timestamp);
        }
      }
      if (minimum != Double.POSITIVE_INFINITY) {
        diagnosisCase.setTimeWindow(minimum, maximum);
      }
    }

    int tagCount = historianData == null ? 0 : historianData.size();
    int validCount = 0;
    int missingCount = 0;
    if (historianData != null) {
      for (double[] values : historianData.values()) {
        if (values == null) {
          missingCount++;
          continue;
        }
        for (double value : values) {
          if (Double.isNaN(value) || Double.isInfinite(value)) {
            missingCount++;
          } else {
            validCount++;
          }
        }
      }
    }
    diagnosisCase.addDataSource("historian", "in-memory historian series");
    diagnosisCase.addDataQuality("tagCount", Integer.toString(tagCount));
    diagnosisCase.addDataQuality("validValueCount", Integer.toString(validCount));
    diagnosisCase.addDataQuality("missingOrInvalidValueCount", Integer.toString(missingCount));
    return diagnosisCase;
  }

  /**
   * Returns the stable caller-supplied or derived case identifier.
   *
   * @return case identifier
   */
  public String getCaseId() {
    return caseId;
  }

  /**
   * Sets a stable identifier used to correlate repeated analysis of the same case.
   *
   * @param caseId case identifier
   * @return this instance
   */
  public DiagnosisCase setCaseId(String caseId) {
    this.caseId = caseId == null || caseId.trim().isEmpty() ? equipmentName : caseId;
    return this;
  }

  /**
   * Returns the diagnosed equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Returns the time-window start.
   *
   * @return window start in epoch seconds, or {@code null}
   */
  public Double getWindowStartEpochSeconds() {
    return windowStartEpochSeconds;
  }

  /**
   * Returns the time-window end.
   *
   * @return window end in epoch seconds, or {@code null}
   */
  public Double getWindowEndEpochSeconds() {
    return windowEndEpochSeconds;
  }

  /**
   * Sets the historian time window.
   *
   * @param startEpochSeconds start in epoch seconds
   * @param endEpochSeconds end in epoch seconds
   * @return this instance
   */
  public DiagnosisCase setTimeWindow(double startEpochSeconds, double endEpochSeconds) {
    if (endEpochSeconds < startEpochSeconds) {
      throw new IllegalArgumentException("time-window end must not precede start");
    }
    this.windowStartEpochSeconds = startEpochSeconds;
    this.windowEndEpochSeconds = endEpochSeconds;
    this.caseId = equipmentName + "@" + Double.toString(startEpochSeconds) + "-" + Double.toString(endEpochSeconds);
    return this;
  }

  /**
   * Returns the process-model identifier.
   *
   * @return process-model identifier
   */
  public String getProcessModelId() {
    return processModelId;
  }

  /**
   * Returns the process-model revision.
   *
   * @return process-model revision
   */
  public String getProcessModelRevision() {
    return processModelRevision;
  }

  /**
   * Sets process-model identity and revision.
   *
   * @param modelId model identifier
   * @param revision model revision, commit, or configuration version
   * @return this instance
   */
  public DiagnosisCase setProcessModel(String modelId, String revision) {
    this.processModelId = modelId == null ? "" : modelId;
    this.processModelRevision = revision == null ? "" : revision;
    return this;
  }

  /**
   * Adds source provenance.
   *
   * @param source source name
   * @param reference source reference, query, dataset, or version
   * @return this instance
   */
  public DiagnosisCase addDataSource(String source, String reference) {
    dataSources.put(source, reference);
    return this;
  }

  /**
   * Adds an operating-context item.
   *
   * @param key context key
   * @param value context value including units where applicable
   * @return this instance
   */
  public DiagnosisCase addOperatingContext(String key, String value) {
    operatingContext.put(key, value);
    return this;
  }

  /**
   * Adds a data-quality item.
   *
   * @param key quality metric or qualification
   * @param value metric value or qualification
   * @return this instance
   */
  public DiagnosisCase addDataQuality(String key, String value) {
    dataQuality.put(key, value);
    return this;
  }

  /**
   * Returns source provenance.
   *
   * @return unmodifiable source-provenance map
   */
  public Map<String, String> getDataSources() {
    return Collections.unmodifiableMap(dataSources);
  }

  /**
   * Returns operating context.
   *
   * @return unmodifiable operating-context map
   */
  public Map<String, String> getOperatingContext() {
    return Collections.unmodifiableMap(operatingContext);
  }

  /**
   * Returns data-quality metadata.
   *
   * @return unmodifiable data-quality map
   */
  public Map<String, String> getDataQuality() {
    return Collections.unmodifiableMap(dataQuality);
  }
}
