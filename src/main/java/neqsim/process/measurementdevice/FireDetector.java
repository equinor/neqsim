package neqsim.process.measurementdevice;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Fire Detector instrument for fire detection and alarm systems.
 * 
 * <p>
 * A fire detector is a binary sensor that detects the presence of fire. It can be used in emergency
 * shutdown (ESD) systems where multiple fire detectors may need to activate before triggering an
 * ESD response (voting logic).
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Binary state: fire detected (active) or no fire detected (inactive)</li>
 * <li>Can be configured with detection threshold and delay</li>
 * <li>Measured value: 1.0 when fire detected, 0.0 when no fire</li>
 * <li>Supports alarm configuration for logging and escalation</li>
 * <li>Reset capability for testing and normal operation restoration</li>
 * </ul>
 * 
 * <p>
 * Typical usage in ESD system with voting logic:
 * 
 * <pre>
 * // Create fire detectors
 * FireDetector fireDetector1 = new FireDetector("FD-101");
 * FireDetector fireDetector2 = new FireDetector("FD-102");
 * 
 * // Configure alarm thresholds
 * AlarmConfig alarmConfig = AlarmConfig.builder().highLimit(0.5).delay(1.0).unit("binary").build();
 * fireDetector1.setAlarmConfig(alarmConfig);
 * fireDetector2.setAlarmConfig(alarmConfig);
 * 
 * // Simulate fire detection
 * fireDetector1.detectFire();
 * fireDetector2.detectFire();
 * 
 * // Check detector states
 * if (fireDetector1.isFireDetected() &amp;&amp; fireDetector2.isFireDetected()) {
 *   // Two fire alarms - activate ESD
 *   esdSystem.activate();
 * }
 * 
 * // After emergency is resolved
 * fireDetector1.reset();
 * fireDetector2.reset();
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class FireDetector extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Indicates if fire is currently detected. */
  private boolean fireDetected = false;

  /** Detection threshold (0.0 to 1.0, where 1.0 means fire confirmed). */
  private double detectionThreshold = 0.5;

  /** Detection delay in seconds (time to confirm fire before alarm). */
  private double detectionDelay = 0.0;

  /** Current signal level (simulates IR/UV sensor reading). */
  private double signalLevel = 0.0;

  /** Detector location/zone identifier. */
  private String location = "";

  /**
   * Constructor for FireDetector.
   *
   * @param name name of fire detector
   */
  public FireDetector(String name) {
    super(name, "binary"); // Unit is "binary" (0 or 1)
    setMaximumValue(1.0);
    setMinimumValue(0.0);
  }

  /**
   * Constructor for FireDetector with location.
   *
   * @param name name of fire detector
   * @param location location or zone where detector is installed
   */
  public FireDetector(String name, String location) {
    this(name);
    this.location = location;
  }

  /**
   * Simulates fire detection - activates the detector.
   * 
   * <p>
   * In real applications, this would be triggered by actual sensor readings (IR, UV, heat, smoke).
   * For simulation purposes, this method directly sets the fire detected state.
   * </p>
   */
  public void detectFire() {
    this.fireDetected = true;
    this.signalLevel = 1.0;
  }

  /**
   * Sets a partial signal level (for testing gradual detection).
   * 
   * <p>
   * If signal level exceeds detection threshold, fire is considered detected.
   * </p>
   *
   * @param level signal level between 0.0 (no fire) and 1.0 (confirmed fire)
   */
  public void setSignalLevel(double level) {
    this.signalLevel = Math.max(0.0, Math.min(1.0, level));
    this.fireDetected = (this.signalLevel >= this.detectionThreshold);
  }

  /**
   * Resets the detector to inactive (no fire detected) state.
   * 
   * <p>
   * This simulates detector reset after emergency is resolved or for testing purposes.
   * </p>
   */
  public void reset() {
    this.fireDetected = false;
    this.signalLevel = 0.0;
  }

  /**
   * Checks if fire is currently detected.
   *
   * @return true if fire is detected
   */
  public boolean isFireDetected() {
    return fireDetected;
  }

  /**
   * Gets the current signal level.
   *
   * @return signal level between 0.0 and 1.0
   */
  public double getSignalLevel() {
    return signalLevel;
  }

  /**
   * Sets the detection threshold.
   *
   * @param threshold threshold level (0.0 to 1.0) above which fire is confirmed
   */
  public void setDetectionThreshold(double threshold) {
    this.detectionThreshold = Math.max(0.0, Math.min(1.0, threshold));
  }

  /**
   * Gets the detection threshold.
   *
   * @return detection threshold level
   */
  public double getDetectionThreshold() {
    return detectionThreshold;
  }

  /**
   * Sets the detection delay.
   *
   * @param delay delay in seconds before confirming fire alarm
   */
  public void setDetectionDelay(double delay) {
    this.detectionDelay = Math.max(0.0, delay);
  }

  /**
   * Gets the detection delay.
   *
   * @return detection delay in seconds
   */
  public double getDetectionDelay() {
    return detectionDelay;
  }

  /**
   * Sets the detector location.
   *
   * @param location location or zone identifier
   */
  public void setLocation(String location) {
    this.location = location;
  }

  /**
   * Gets the detector location.
   *
   * @return location or zone identifier
   */
  public String getLocation() {
    return location;
  }

  /**
   * Gets the measured value of the fire detector.
   * 
   * <p>
   * Returns 1.0 if fire is detected, 0.0 if no fire detected.
   * </p>
   *
   * @return 1.0 if fire detected, 0.0 otherwise
   */
  @Override
  public double getMeasuredValue() {
    return fireDetected ? 1.0 : 0.0;
  }

  /**
   * Gets the measured value in the specified unit.
   * 
   * <p>
   * Fire detector only supports "binary" unit. Returns 1.0 if fire detected, 0.0 otherwise.
   * </p>
   *
   * @param unit engineering unit (only "binary" or "" supported)
   * @return 1.0 if fire detected, 0.0 otherwise
   */
  @Override
  public double getMeasuredValue(String unit) {
    if (unit == null || unit.isEmpty() || unit.equalsIgnoreCase("binary")) {
      return getMeasuredValue();
    }
    throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
        "getMeasuredValue", "unit", "FireDetector only supports 'binary' unit"));
  }

  /**
   * Displays the current state of the fire detector.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("Fire Detector: " + getName());
    if (!location.isEmpty()) {
      System.out.println("  Location: " + location);
    }
    System.out.println("  State: " + (fireDetected ? "FIRE DETECTED" : "NO FIRE"));
    System.out.println("  Signal Level: " + String.format("%.2f", signalLevel));
    System.out.println("  Detection Threshold: " + String.format("%.2f", detectionThreshold));
    System.out.println("  Measured Value: " + getMeasuredValue());
  }

  /**
   * Gets a string representation of the fire detector state.
   *
   * @return string describing detector state
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName()).append(" [Fire Detector");
    if (!location.isEmpty()) {
      sb.append(" @ ").append(location);
    }
    sb.append("] - State: ").append(fireDetected ? "FIRE DETECTED" : "NO FIRE");
    sb.append(", Signal: ").append(String.format("%.2f", signalLevel));
    return sb.toString();
  }
}
