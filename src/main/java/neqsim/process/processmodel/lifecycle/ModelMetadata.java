package neqsim.process.processmodel.lifecycle;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata for tracking the lifecycle of a process model (digital twin).
 *
 * <p>
 * This class supports:
 * <ul>
 * <li><b>Lifecycle Phase Tracking:</b> Concept → Design → Commissioning → Operation →
 * Late-life</li>
 * <li><b>Validation History:</b> Track when and how the model was validated</li>
 * <li><b>Audit Trail:</b> Record model modifications for compliance and knowledge preservation</li>
 * <li><b>Quality Metrics:</b> Track model accuracy and calibration state</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ModelMetadata metadata = new ModelMetadata();
 * metadata.setAssetId("PLATFORM-A-TRAIN-1");
 * metadata.setLifecyclePhase(LifecyclePhase.OPERATION);
 * metadata.setResponsibleEngineer("john.doe@company.com");
 * metadata.recordValidation("Matched well test data within 2%", "WELL-TEST-2024-01");
 * metadata.recordModification("Tuned compressor curves based on vendor data");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ModelMetadata implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Lifecycle phases for a process model.
   */
  public enum LifecyclePhase {
    /** Early concept screening and feasibility studies. */
    CONCEPT,
    /** Detailed engineering design (FEED, Detailed Design). */
    DESIGN,
    /** Construction and commissioning phase. */
    COMMISSIONING,
    /** Normal operation phase - model is a live digital twin. */
    OPERATION,
    /** Late-life operations, decommissioning planning. */
    LATE_LIFE,
    /** Archived model, no longer in active use. */
    ARCHIVED
  }

  /**
   * Calibration status of the model.
   */
  public enum CalibrationStatus {
    /** Model has never been calibrated against real data. */
    UNCALIBRATED,
    /** Model was calibrated but may be outdated. */
    CALIBRATED,
    /** Calibration is in progress. */
    IN_PROGRESS,
    /** Recent calibration, high confidence. */
    FRESHLY_CALIBRATED,
    /** Calibration failed or model diverged from plant. */
    NEEDS_RECALIBRATION
  }

  private String assetId;
  private String assetName;
  private String facility;
  private String region;
  private LifecyclePhase lifecyclePhase = LifecyclePhase.CONCEPT;
  private String responsibleEngineer;
  private String responsibleTeam;
  private Instant lastValidated;
  private Instant lastCalibrated;
  private CalibrationStatus calibrationStatus = CalibrationStatus.UNCALIBRATED;
  private double calibrationAccuracy = Double.NaN; // Relative error
  private List<ValidationRecord> validationHistory = new ArrayList<>();
  private List<ModificationRecord> modificationHistory = new ArrayList<>();
  private Map<String, String> tags = new HashMap<>();
  private String dataSource;
  private String regulatoryBasis;

  /**
   * Default constructor.
   */
  public ModelMetadata() {}

  /**
   * Records a validation event.
   *
   * @param description what was validated and the outcome
   * @param referenceId reference to validation data (e.g., well test ID)
   */
  public void recordValidation(String description, String referenceId) {
    ValidationRecord record = new ValidationRecord(description, referenceId);
    validationHistory.add(record);
    lastValidated = Instant.now();
  }

  /**
   * Records a model modification.
   *
   * @param description what was changed
   */
  public void recordModification(String description) {
    recordModification(description, null);
  }

  /**
   * Records a model modification with author.
   *
   * @param description what was changed
   * @param author who made the change
   */
  public void recordModification(String description, String author) {
    ModificationRecord record = new ModificationRecord(description, author);
    modificationHistory.add(record);
  }

  /**
   * Updates calibration status.
   *
   * @param status new calibration status
   * @param accuracy relative accuracy achieved (e.g., 0.02 for 2%)
   */
  public void updateCalibration(CalibrationStatus status, double accuracy) {
    this.calibrationStatus = status;
    this.calibrationAccuracy = accuracy;
    if (status == CalibrationStatus.CALIBRATED || status == CalibrationStatus.FRESHLY_CALIBRATED) {
      this.lastCalibrated = Instant.now();
    }
  }

  /**
   * Gets the number of days since last validation.
   *
   * @return days since validation, or -1 if never validated
   */
  public long getDaysSinceValidation() {
    if (lastValidated == null) {
      return -1;
    }
    return java.time.Duration.between(lastValidated, Instant.now()).toDays();
  }

  /**
   * Gets the number of days since last calibration.
   *
   * @return days since calibration, or -1 if never calibrated
   */
  public long getDaysSinceCalibration() {
    if (lastCalibrated == null) {
      return -1;
    }
    return java.time.Duration.between(lastCalibrated, Instant.now()).toDays();
  }

  /**
   * Checks if the model needs revalidation (e.g., more than 90 days old).
   *
   * @param maxDays maximum days allowed since last validation
   * @return true if revalidation is recommended
   */
  public boolean needsRevalidation(long maxDays) {
    long days = getDaysSinceValidation();
    return days < 0 || days > maxDays;
  }

  // Getters and setters

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String assetId) {
    this.assetId = assetId;
  }

  public String getAssetName() {
    return assetName;
  }

  public void setAssetName(String assetName) {
    this.assetName = assetName;
  }

  public String getFacility() {
    return facility;
  }

  public void setFacility(String facility) {
    this.facility = facility;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public LifecyclePhase getLifecyclePhase() {
    return lifecyclePhase;
  }

  public void setLifecyclePhase(LifecyclePhase lifecyclePhase) {
    this.lifecyclePhase = lifecyclePhase;
    recordModification("Lifecycle phase changed to " + lifecyclePhase);
  }

  public String getResponsibleEngineer() {
    return responsibleEngineer;
  }

  public void setResponsibleEngineer(String responsibleEngineer) {
    this.responsibleEngineer = responsibleEngineer;
  }

  public String getResponsibleTeam() {
    return responsibleTeam;
  }

  public void setResponsibleTeam(String responsibleTeam) {
    this.responsibleTeam = responsibleTeam;
  }

  public Instant getLastValidated() {
    return lastValidated;
  }

  public Instant getLastCalibrated() {
    return lastCalibrated;
  }

  public CalibrationStatus getCalibrationStatus() {
    return calibrationStatus;
  }

  public double getCalibrationAccuracy() {
    return calibrationAccuracy;
  }

  public List<ValidationRecord> getValidationHistory() {
    return validationHistory;
  }

  public List<ModificationRecord> getModificationHistory() {
    return modificationHistory;
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public void addTag(String key, String value) {
    tags.put(key, value);
  }

  public String getDataSource() {
    return dataSource;
  }

  public void setDataSource(String dataSource) {
    this.dataSource = dataSource;
  }

  public String getRegulatoryBasis() {
    return regulatoryBasis;
  }

  public void setRegulatoryBasis(String regulatoryBasis) {
    this.regulatoryBasis = regulatoryBasis;
  }

  /**
   * Record of a validation event.
   */
  public static class ValidationRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final Instant timestamp;
    private final String description;
    private final String referenceId;

    public ValidationRecord(String description, String referenceId) {
      this.timestamp = Instant.now();
      this.description = description;
      this.referenceId = referenceId;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public String getDescription() {
      return description;
    }

    public String getReferenceId() {
      return referenceId;
    }
  }

  /**
   * Record of a model modification.
   */
  public static class ModificationRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final Instant timestamp;
    private final String description;
    private final String author;

    public ModificationRecord(String description, String author) {
      this.timestamp = Instant.now();
      this.description = description;
      this.author = author;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public String getDescription() {
      return description;
    }

    public String getAuthor() {
      return author;
    }
  }
}
