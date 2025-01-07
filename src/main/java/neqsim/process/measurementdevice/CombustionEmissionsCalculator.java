package neqsim.process.measurementdevice;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * CombustionEmissionsCalculator class.
 * </p>
 *
 * @author Even Solbraa
 */
public class CombustionEmissionsCalculator extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // Composition of natural gas (in mole percent)
  private static final Map<String, Double> NATURAL_GAS_COMPOSITION = new HashMap<>();

  // CO2 emissions factor for each component (in kg CO2 per kg of component
  // burned)
  private static final Map<String, Double> CO2_EMISSIONS_FACTORS = new HashMap<>();

  /**
   * <p>
   * Constructor for CombustionEmissionsCalculator.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CombustionEmissionsCalculator(StreamInterface stream) {
    this("EmissionCalculator", stream);
  }

  /**
   * <p>
   * Constructor for CombustionEmissionsCalculator.
   * </p>
   *
   * @param name Name of WaterDewPointAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CombustionEmissionsCalculator(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  /**
   * <p>
   * setComponents.
   * </p>
   */
  public void setComponents() {
    NATURAL_GAS_COMPOSITION.clear();
    CO2_EMISSIONS_FACTORS.clear();
    if (stream.getFluid().getPhase(0).hasComponent("methane")) {
      NATURAL_GAS_COMPOSITION.put("Methane", stream.getFluid().getComponent("methane").getz());
      CO2_EMISSIONS_FACTORS.put("Methane", 2.75);
    }
    if (stream.getFluid().getPhase(0).hasComponent("ethane")) {
      NATURAL_GAS_COMPOSITION.put("Ethane", stream.getFluid().getComponent("ethane").getz());
      CO2_EMISSIONS_FACTORS.put("Ethane", 3.75);
    }
    if (stream.getFluid().getPhase(0).hasComponent("propane")) {
      NATURAL_GAS_COMPOSITION.put("Propane", stream.getFluid().getComponent("propane").getz());
      CO2_EMISSIONS_FACTORS.put("Propane", 5.5);
    }
    if (stream.getFluid().getPhase(0).hasComponent("n-butane")) {
      NATURAL_GAS_COMPOSITION.put("n-butane", stream.getFluid().getComponent("n-butane").getz());
      CO2_EMISSIONS_FACTORS.put("n-butane", 6.5);
    }
    if (stream.getFluid().getPhase(0).hasComponent("i-butane")) {
      NATURAL_GAS_COMPOSITION.put("i-butane", stream.getFluid().getComponent("i-butane").getz());
      CO2_EMISSIONS_FACTORS.put("i-butane", 6.5);
    }
    // ....
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    setComponents();
    return calculateCO2Emissions(NATURAL_GAS_COMPOSITION, CO2_EMISSIONS_FACTORS)
        * stream.getFluid().getFlowRate(unit);
  }

  /**
   * <p>
   * calculateCO2Emissions.
   * </p>
   *
   * @param composition a {@link java.util.Map} object
   * @param emissionsFactors a {@link java.util.Map} object
   * @return a double
   */
  public static double calculateCO2Emissions(Map<String, Double> composition,
      Map<String, Double> emissionsFactors) {
    double totalEmissions = 0.0;

    for (Map.Entry<String, Double> entry : composition.entrySet()) {
      String component = entry.getKey();
      double molePercent = entry.getValue();

      double emissionsFactor = emissionsFactors.get(component);
      double componentEmissions = molePercent * emissionsFactor;

      totalEmissions += componentEmissions;
    }

    return totalEmissions;
  }
}
