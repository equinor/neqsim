package neqsim.process.util.report.safety;

/**
 * Configuration object containing threshold values that drive severity grading for safety
 * reporting.
 */
public class ProcessSafetyThresholds {
  private double entropyChangeWarning = 5.0; // kJ/K
  private double entropyChangeCritical = 10.0; // kJ/K
  private double exergyChangeWarning = 1.0e3; // kJ
  private double exergyChangeCritical = 2.0e3; // kJ
  private double minSafetyMarginWarning = 0.15; // 15 % remaining margin
  private double minSafetyMarginCritical = 0.05; // 5 % remaining margin
  private double reliefUtilisationWarning = 0.5; // 50 % open
  private double reliefUtilisationCritical = 0.8; // 80 % open

  /**
   * Create an instance with default thresholds.
   */
  public ProcessSafetyThresholds() {}

  /**
   * Copy constructor.
   *
   * @param other thresholds to copy
   */
  public ProcessSafetyThresholds(ProcessSafetyThresholds other) {
    if (other != null) {
      entropyChangeWarning = other.entropyChangeWarning;
      entropyChangeCritical = other.entropyChangeCritical;
      exergyChangeWarning = other.exergyChangeWarning;
      exergyChangeCritical = other.exergyChangeCritical;
      minSafetyMarginWarning = other.minSafetyMarginWarning;
      minSafetyMarginCritical = other.minSafetyMarginCritical;
      reliefUtilisationWarning = other.reliefUtilisationWarning;
      reliefUtilisationCritical = other.reliefUtilisationCritical;
    }
  }

  public double getEntropyChangeWarning() {
    return entropyChangeWarning;
  }

  public ProcessSafetyThresholds setEntropyChangeWarning(double entropyChangeWarning) {
    this.entropyChangeWarning = entropyChangeWarning;
    return this;
  }

  public double getEntropyChangeCritical() {
    return entropyChangeCritical;
  }

  public ProcessSafetyThresholds setEntropyChangeCritical(double entropyChangeCritical) {
    this.entropyChangeCritical = entropyChangeCritical;
    return this;
  }

  public double getExergyChangeWarning() {
    return exergyChangeWarning;
  }

  public ProcessSafetyThresholds setExergyChangeWarning(double exergyChangeWarning) {
    this.exergyChangeWarning = exergyChangeWarning;
    return this;
  }

  public double getExergyChangeCritical() {
    return exergyChangeCritical;
  }

  public ProcessSafetyThresholds setExergyChangeCritical(double exergyChangeCritical) {
    this.exergyChangeCritical = exergyChangeCritical;
    return this;
  }

  public double getMinSafetyMarginWarning() {
    return minSafetyMarginWarning;
  }

  public ProcessSafetyThresholds setMinSafetyMarginWarning(double minSafetyMarginWarning) {
    this.minSafetyMarginWarning = minSafetyMarginWarning;
    return this;
  }

  public double getMinSafetyMarginCritical() {
    return minSafetyMarginCritical;
  }

  public ProcessSafetyThresholds setMinSafetyMarginCritical(double minSafetyMarginCritical) {
    this.minSafetyMarginCritical = minSafetyMarginCritical;
    return this;
  }

  public double getReliefUtilisationWarning() {
    return reliefUtilisationWarning;
  }

  public ProcessSafetyThresholds setReliefUtilisationWarning(double reliefUtilisationWarning) {
    this.reliefUtilisationWarning = reliefUtilisationWarning;
    return this;
  }

  public double getReliefUtilisationCritical() {
    return reliefUtilisationCritical;
  }

  public ProcessSafetyThresholds setReliefUtilisationCritical(double reliefUtilisationCritical) {
    this.reliefUtilisationCritical = reliefUtilisationCritical;
    return this;
  }
}
