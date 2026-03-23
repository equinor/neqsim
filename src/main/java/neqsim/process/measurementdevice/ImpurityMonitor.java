package neqsim.process.measurementdevice;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Phase-partitioned composition tracking device for monitoring impurity concentrations across gas,
 * liquid, and aqueous phases. Designed for CO2 injection systems where light impurities (H2, N2,
 * CH4) preferentially partition into the gas phase during two-phase conditions.
 *
 * <p>
 * The monitor tracks:
 * <ul>
 * <li>Overall (bulk) composition of each tracked component</li>
 * <li>Gas phase composition and enrichment factor (y/z) for each component</li>
 * <li>Liquid phase composition for each component</li>
 * <li>Phase fractions (gas/liquid mass and mole fractions)</li>
 * <li>Alarm conditions when concentrations exceed configurable thresholds</li>
 * </ul>
 *
 * <p>
 * Usage example:
 *
 * <pre>
 * ImpurityMonitor monitor = new ImpurityMonitor("WH-H2-Monitor", stream);
 * monitor.addTrackedComponent("hydrogen", 0.04); // Track H2, alarm at 4 mol%
 * monitor.addTrackedComponent("nitrogen", 0.10); // Track N2, alarm at 10 mol%
 * // After process.run():
 * double h2InGas = monitor.getGasPhaseMoleFraction("hydrogen");
 * double enrichment = monitor.getEnrichmentFactor("hydrogen");
 * Map&lt;String, Map&lt;String, Double&gt;&gt; report = monitor.getFullReport();
 * </pre>
 *
 * @author neqsim
 * @version 1.0
 */
public class ImpurityMonitor extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Primary component to report via getMeasuredValue(). */
  private String primaryComponent = "";

  /** Components to track with optional alarm thresholds (mol fraction). */
  private final Map<String, Double> trackedComponents = new LinkedHashMap<>();

  /**
   * Constructor for ImpurityMonitor with default name.
   *
   * @param stream the stream to monitor
   */
  public ImpurityMonitor(StreamInterface stream) {
    this("Impurity Monitor", stream);
  }

  /**
   * Constructor for ImpurityMonitor with specified name.
   *
   * @param name the device name/tag
   * @param stream the stream to monitor
   */
  public ImpurityMonitor(String name, StreamInterface stream) {
    super(name, "mol%", stream);
  }

  /**
   * Adds a component to the tracked list. The alarm threshold is in mole fraction (e.g. 0.04 for
   * 4%).
   *
   * @param componentName the name of the component as registered in the fluid
   * @param alarmThresholdMolFrac the gas phase mole fraction threshold for alarm (0 to disable)
   */
  public void addTrackedComponent(String componentName, double alarmThresholdMolFrac) {
    trackedComponents.put(componentName, alarmThresholdMolFrac);
    if (primaryComponent.trim().isEmpty()) {
      primaryComponent = componentName;
    }
  }

  /**
   * Adds a component to track without an alarm threshold.
   *
   * @param componentName the name of the component
   */
  public void addTrackedComponent(String componentName) {
    addTrackedComponent(componentName, 0.0);
  }

  /**
   * Sets the primary component whose gas phase concentration is returned by
   * {@link #getMeasuredValue(String)}.
   *
   * @param componentName the component name
   */
  public void setPrimaryComponent(String componentName) {
    this.primaryComponent = componentName;
  }

  /**
   * Gets the gas phase mole fraction of the specified component.
   *
   * @param componentName the component name
   * @return the gas phase mole fraction, or NaN if gas phase absent or component not found
   */
  public double getGasPhaseMoleFraction(String componentName) {
    SystemInterface system = stream.getThermoSystem();
    if (system == null || !system.hasPhaseType("gas")) {
      return Double.NaN;
    }
    try {
      return system.getPhase("gas").getComponent(componentName).getx();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Gets the liquid phase mole fraction of the specified component.
   *
   * @param componentName the component name
   * @return the liquid phase mole fraction, or NaN if liquid phase absent or component not found
   */
  public double getLiquidPhaseMoleFraction(String componentName) {
    SystemInterface system = stream.getThermoSystem();
    if (system == null || !system.hasPhaseType("oil")) {
      return Double.NaN;
    }
    try {
      return system.getPhase("oil").getComponent(componentName).getx();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Gets the bulk (overall) mole fraction of the specified component.
   *
   * @param componentName the component name
   * @return the overall mole fraction, or NaN if component not found
   */
  public double getBulkMoleFraction(String componentName) {
    SystemInterface system = stream.getThermoSystem();
    if (system == null) {
      return Double.NaN;
    }
    try {
      return system.getComponent(componentName).getz();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Gets the enrichment factor (K-value) for a component: y_gas / z_feed.
   *
   * @param componentName the component name
   * @return the enrichment factor, or NaN if not in two-phase conditions
   */
  public double getEnrichmentFactor(String componentName) {
    double gasY = getGasPhaseMoleFraction(componentName);
    double bulkZ = getBulkMoleFraction(componentName);
    if (Double.isNaN(gasY) || Double.isNaN(bulkZ) || bulkZ <= 0) {
      return Double.NaN;
    }
    return gasY / bulkZ;
  }

  /**
   * Gets the number of phases in the stream.
   *
   * @return the number of phases
   */
  public int getNumberOfPhases() {
    SystemInterface system = stream.getThermoSystem();
    if (system == null) {
      return 0;
    }
    return system.getNumberOfPhases();
  }

  /**
   * Gets the gas phase mole fraction (beta) of the stream. Returns 0 if single-phase liquid, 1 if
   * single-phase gas.
   *
   * @return the gas phase mole fraction (0 to 1)
   */
  public double getGasPhaseFraction() {
    SystemInterface system = stream.getThermoSystem();
    if (system == null) {
      return Double.NaN;
    }
    if (!system.hasPhaseType("gas")) {
      return 0.0;
    }
    if (system.getNumberOfPhases() == 1) {
      return 1.0;
    }
    int gasPhaseIndex = system.getPhaseNumberOfPhase("gas");
    return system.getBeta(gasPhaseIndex);
  }

  /**
   * Checks whether the gas phase concentration of a component exceeds its alarm threshold.
   *
   * @param componentName the component name
   * @return true if the gas phase mole fraction exceeds the configured alarm threshold
   */
  public boolean isAlarmExceeded(String componentName) {
    Double threshold = trackedComponents.get(componentName);
    if (threshold == null || threshold <= 0.0) {
      return false;
    }
    double gasY = getGasPhaseMoleFraction(componentName);
    return !Double.isNaN(gasY) && gasY > threshold;
  }

  /**
   * Generates a full report with phase-partitioned composition for all tracked components.
   *
   * @return a map of component name to a map of property name to value
   */
  public Map<String, Map<String, Double>> getFullReport() {
    Map<String, Map<String, Double>> report = new LinkedHashMap<>();

    for (String comp : trackedComponents.keySet()) {
      Map<String, Double> compData = new LinkedHashMap<>();
      compData.put("bulk_z", getBulkMoleFraction(comp));
      compData.put("gas_y", getGasPhaseMoleFraction(comp));
      compData.put("liquid_x", getLiquidPhaseMoleFraction(comp));
      compData.put("enrichment_factor", getEnrichmentFactor(comp));
      compData.put("alarm_threshold", trackedComponents.get(comp));
      compData.put("alarm_exceeded", isAlarmExceeded(comp) ? 1.0 : 0.0);
      report.put(comp, compData);
    }

    // Add phase fractions
    Map<String, Double> phaseData = new LinkedHashMap<>();
    phaseData.put("number_of_phases", (double) getNumberOfPhases());
    phaseData.put("gas_phase_fraction", getGasPhaseFraction());
    report.put("_phase_info", phaseData);

    return report;
  }

  /**
   * Returns the gas phase mole fraction of the primary component as a percentage.
   *
   * @return the gas phase mole fraction of the primary component in mol%
   */
  @Override
  public double getMeasuredValue() {
    if (primaryComponent.trim().isEmpty()) {
      return Double.NaN;
    }
    double raw = getGasPhaseMoleFraction(primaryComponent) * 100.0;
    return applySignalModifiers(raw);
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (primaryComponent.trim().isEmpty()) {
      return Double.NaN;
    }
    double molFrac = getGasPhaseMoleFraction(primaryComponent);
    if (Double.isNaN(molFrac)) {
      return Double.NaN;
    }
    double value;
    if (unit.equals("mol%") || unit.equals("mole%")) {
      value = molFrac * 100.0;
    } else if (unit.equals("ppm")) {
      value = molFrac * 1.0e6;
    } else if (unit.equals("mol fraction") || unit.equals("mole fraction")) {
      value = molFrac;
    } else {
      value = molFrac * 100.0; // default to mol%
    }
    return applySignalModifiers(value);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    System.out.println("=== Impurity Monitor: " + getName() + " ===");
    System.out.println("Number of phases: " + getNumberOfPhases());
    System.out.println("Gas phase fraction: " + String.format("%.4f", getGasPhaseFraction()));
    for (String comp : trackedComponents.keySet()) {
      System.out.println(comp + ":");
      System.out.println("  Bulk z  = " + String.format("%.6f", getBulkMoleFraction(comp)));
      System.out.println("  Gas y   = " + String.format("%.6f", getGasPhaseMoleFraction(comp)));
      System.out.println("  Liquid x = " + String.format("%.6f", getLiquidPhaseMoleFraction(comp)));
      System.out
          .println("  Enrichment = " + String.format("%.2f", getEnrichmentFactor(comp)) + "x");
      if (isAlarmExceeded(comp)) {
        System.out.println("  *** ALARM: exceeds " + trackedComponents.get(comp) + " threshold");
      }
    }
  }
}
