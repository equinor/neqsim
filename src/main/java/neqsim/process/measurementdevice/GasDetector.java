package neqsim.process.measurementdevice;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Gas Detector instrument for detecting combustible or toxic gases.
 * 
 * <p>
 * A gas detector measures the concentration of specific gases in the air, typically reporting in
 * percentage of Lower Explosive Limit (%LEL) for combustible gases, or ppm for toxic gases. It is
 * commonly used in Fire &amp; Gas (F&amp;G) systems for emergency shutdown (ESD) applications.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Measures gas concentration as %LEL (0-100%) or ppm</li>
 * <li>Configurable alarm thresholds (typically 20% LEL and 60% LEL)</li>
 * <li>Supports both combustible gas (LEL) and toxic gas (ppm) detection</li>
 * <li>Integration with alarm system for multi-level warnings</li>
 * <li>Location/zone identification for spatial awareness</li>
 * <li>Reset capability for testing and normal operation restoration</li>
 * </ul>
 * 
 * <p>
 * Typical usage in ESD system with gas detection:
 * 
 * <pre>
 * // Create gas detector for hydrocarbon detection
 * GasDetector gasDetector = new GasDetector("GD-101", GasDetector.GasType.COMBUSTIBLE);
 * gasDetector.setLocation("Separator Area");
 * 
 * // Configure two-level alarm (20% LEL warning, 60% LEL high alarm)
 * AlarmConfig alarmConfig = AlarmConfig.builder().highLimit(20.0) // 20% LEL - Warning
 *     .highHighLimit(60.0) // 60% LEL - High alarm
 *     .delay(2.0) // 2 second confirmation
 *     .unit("%LEL").build();
 * gasDetector.setAlarmConfig(alarmConfig);
 * 
 * // Simulate gas detection
 * gasDetector.setGasConcentration(25.0); // 25% LEL detected
 * 
 * // Check detector state
 * if (gasDetector.getGasConcentration() &gt; 20.0) {
 *   System.out.println("Gas alarm - evacuate area");
 * }
 * 
 * if (gasDetector.getGasConcentration() &gt; 60.0) {
 *   // Activate ESD
 *   esdSystem.activate();
 * }
 * </pre>
 *
 * <p>
 * Common applications:
 * <ul>
 * <li>Hydrocarbon leak detection (methane, propane, etc.)</li>
 * <li>H2S (hydrogen sulfide) monitoring</li>
 * <li>CO (carbon monoxide) detection</li>
 * <li>Toxic gas monitoring</li>
 * <li>Confined space monitoring</li>
 * </ul>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class GasDetector extends MeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Enumeration of gas detector types.
   */
  public enum GasType {
    /** Combustible gas detector (measures %LEL). */
    COMBUSTIBLE("% LEL"),
    /** Toxic gas detector (measures ppm). */
    TOXIC("ppm"),
    /** Oxygen deficiency detector (measures %O2). */
    OXYGEN("% O2");

    private final String defaultUnit;

    GasType(String defaultUnit) {
      this.defaultUnit = defaultUnit;
    }

    /**
     * Gets the default unit for this gas type.
     *
     * @return default unit string
     */
    public String getDefaultUnit() {
      return defaultUnit;
    }
  }

  /** Type of gas being detected. */
  private GasType gasType = GasType.COMBUSTIBLE;

  /** Current gas concentration reading. */
  private double gasConcentration = 0.0;

  /** Gas species being detected (e.g., "methane", "H2S", "CO"). */
  private String gasSpecies = "hydrocarbon";

  /** Detector location/zone identifier. */
  private String location = "";

  /** Lower Explosive Limit in ppm (for combustible gases). */
  private double lowerExplosiveLimit = 50000.0; // Default: 5% vol = 50,000 ppm

  /** Detector response time in seconds. */
  private double responseTime = 10.0;

  /**
   * Constructor for GasDetector with combustible gas type (LEL).
   *
   * @param name name of gas detector
   */
  public GasDetector(String name) {
    this(name, GasType.COMBUSTIBLE);
  }

  /**
   * Constructor for GasDetector with specified gas type.
   *
   * @param name name of gas detector
   * @param gasType type of gas being detected (COMBUSTIBLE, TOXIC, or OXYGEN)
   */
  public GasDetector(String name, GasType gasType) {
    super(name, gasType.getDefaultUnit());
    this.gasType = gasType;
    configureRangeForType();
  }

  /**
   * Constructor for GasDetector with location.
   *
   * @param name name of gas detector
   * @param gasType type of gas being detected
   * @param location location or zone where detector is installed
   */
  public GasDetector(String name, GasType gasType, String location) {
    this(name, gasType);
    this.location = location;
  }

  /**
   * Configures measurement range based on gas type.
   */
  private void configureRangeForType() {
    switch (gasType) {
      case COMBUSTIBLE:
        setMinimumValue(0.0);
        setMaximumValue(100.0); // 0-100% LEL
        break;
      case TOXIC:
        setMinimumValue(0.0);
        setMaximumValue(1000.0); // 0-1000 ppm (configurable)
        break;
      case OXYGEN:
        setMinimumValue(0.0);
        setMaximumValue(25.0); // 0-25% O2
        break;
      default:
        setMinimumValue(0.0);
        setMaximumValue(100.0);
    }
  }

  /**
   * Sets the gas concentration reading.
   * 
   * <p>
   * For COMBUSTIBLE type: value is in %LEL (0-100%) For TOXIC type: value is in ppm For OXYGEN
   * type: value is in %O2
   * </p>
   *
   * @param concentration gas concentration in appropriate units for detector type
   */
  public void setGasConcentration(double concentration) {
    this.gasConcentration = Math.max(0.0, Math.min(concentration, getMaximumValue()));
  }

  /**
   * Gets the current gas concentration.
   *
   * @return gas concentration in detector's units
   */
  public double getGasConcentration() {
    return gasConcentration;
  }

  /**
   * Checks if gas is detected above threshold.
   * 
   * <p>
   * For combustible gases: typically &gt; 20% LEL is considered a detection For toxic gases:
   * depends on gas type (e.g., H2S &gt; 10 ppm)
   * </p>
   *
   * @param threshold threshold value in detector's units
   * @return true if gas concentration exceeds threshold
   */
  public boolean isGasDetected(double threshold) {
    return gasConcentration > threshold;
  }

  /**
   * Checks if high alarm condition exists (typically 60% LEL or high ppm).
   *
   * @param highThreshold high alarm threshold
   * @return true if concentration exceeds high threshold
   */
  public boolean isHighAlarm(double highThreshold) {
    return gasConcentration >= highThreshold;
  }

  /**
   * Resets the detector reading to zero (for testing or calibration).
   */
  public void reset() {
    this.gasConcentration = 0.0;
  }

  /**
   * Sets the gas species being detected.
   *
   * @param species gas species name (e.g., "methane", "H2S", "CO", "propane")
   */
  public void setGasSpecies(String species) {
    this.gasSpecies = species;
  }

  /**
   * Gets the gas species being detected.
   *
   * @return gas species name
   */
  public String getGasSpecies() {
    return gasSpecies;
  }

  /**
   * Gets the detector type.
   *
   * @return gas detector type
   */
  public GasType getGasType() {
    return gasType;
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
   * Sets the Lower Explosive Limit for the gas being detected.
   * 
   * <p>
   * This is used to convert between ppm and %LEL. For example: - Methane: 50,000 ppm (5% vol) -
   * Propane: 21,000 ppm (2.1% vol) - Hydrogen: 40,000 ppm (4% vol)
   * </p>
   *
   * @param lel Lower Explosive Limit in ppm
   */
  public void setLowerExplosiveLimit(double lel) {
    this.lowerExplosiveLimit = lel;
  }

  /**
   * Gets the Lower Explosive Limit.
   *
   * @return LEL in ppm
   */
  public double getLowerExplosiveLimit() {
    return lowerExplosiveLimit;
  }

  /**
   * Converts ppm to %LEL based on the gas's LEL.
   *
   * @param ppm concentration in parts per million
   * @return concentration as percentage of LEL
   */
  public double convertPpmToPercentLEL(double ppm) {
    return (ppm / lowerExplosiveLimit) * 100.0;
  }

  /**
   * Converts %LEL to ppm based on the gas's LEL.
   *
   * @param percentLEL concentration as percentage of LEL
   * @return concentration in ppm
   */
  public double convertPercentLELToPpm(double percentLEL) {
    return (percentLEL / 100.0) * lowerExplosiveLimit;
  }

  /**
   * Sets the detector response time.
   *
   * @param responseTime response time in seconds (T90 time)
   */
  public void setResponseTime(double responseTime) {
    this.responseTime = Math.max(0.0, responseTime);
  }

  /**
   * Gets the detector response time.
   *
   * @return response time in seconds
   */
  public double getResponseTime() {
    return responseTime;
  }

  /**
   * Gets the measured value of the gas detector.
   * 
   * <p>
   * Returns current gas concentration in detector's units (%LEL, ppm, or %O2).
   * </p>
   *
   * @return gas concentration
   */
  @Override
  public double getMeasuredValue() {
    return gasConcentration;
  }

  /**
   * Gets the measured value in the specified unit.
   * 
   * <p>
   * Supported units depend on gas type: - COMBUSTIBLE: "%LEL", "% LEL" - TOXIC: "ppm" - OXYGEN:
   * "%O2", "% O2"
   * </p>
   *
   * @param unit engineering unit
   * @return gas concentration in specified unit
   */
  @Override
  public double getMeasuredValue(String unit) {
    if (unit == null || unit.isEmpty() || unit.equalsIgnoreCase(this.unit)
        || unit.equalsIgnoreCase(gasType.getDefaultUnit())) {
      return getMeasuredValue();
    }

    // Allow variations in unit naming
    String normalizedUnit = unit.trim().toLowerCase().replace(" ", "");
    String detectorUnit = this.unit.trim().toLowerCase().replace(" ", "");

    if (normalizedUnit.equals(detectorUnit)) {
      return getMeasuredValue();
    }

    throw new RuntimeException(
        new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
            "GasDetector '" + getName() + "' only supports '" + this.unit + "' unit"));
  }

  /**
   * Displays the current state of the gas detector.
   */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("Gas Detector: " + getName());
    System.out.println("  Type: " + gasType);
    System.out.println("  Gas Species: " + gasSpecies);
    if (!location.isEmpty()) {
      System.out.println("  Location: " + location);
    }
    System.out.println("  Concentration: " + String.format("%.2f %s", gasConcentration, unit));
    System.out.println("  Response Time: " + String.format("%.1f s", responseTime));

    if (gasType == GasType.COMBUSTIBLE) {
      System.out.println("  LEL: " + String.format("%.0f ppm", lowerExplosiveLimit));
      if (gasConcentration >= 60.0) {
        System.out.println("  Status: HIGH ALARM - EVACUATE!");
      } else if (gasConcentration >= 20.0) {
        System.out.println("  Status: WARNING - GAS DETECTED");
      } else if (gasConcentration > 0.0) {
        System.out.println("  Status: Low level detection");
      } else {
        System.out.println("  Status: NORMAL");
      }
    } else if (gasType == GasType.TOXIC) {
      System.out.println("  Status: " + (gasConcentration > 0 ? "GAS DETECTED" : "NORMAL"));
    } else if (gasType == GasType.OXYGEN) {
      if (gasConcentration < 19.5) {
        System.out.println("  Status: OXYGEN DEFICIENCY - DANGER!");
      } else if (gasConcentration > 23.5) {
        System.out.println("  Status: OXYGEN ENRICHMENT - DANGER!");
      } else {
        System.out.println("  Status: NORMAL");
      }
    }
  }

  /**
   * Gets a string representation of the gas detector state.
   *
   * @return string describing detector state
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName()).append(" [Gas Detector - ").append(gasType);
    if (!location.isEmpty()) {
      sb.append(" @ ").append(location);
    }
    sb.append("] - ");
    sb.append(String.format("%.1f %s", gasConcentration, unit));

    if (gasType == GasType.COMBUSTIBLE) {
      if (gasConcentration >= 60.0) {
        sb.append(" (HIGH ALARM)");
      } else if (gasConcentration >= 20.0) {
        sb.append(" (WARNING)");
      }
    }

    sb.append(" [").append(gasSpecies).append("]");

    return sb.toString();
  }
}
