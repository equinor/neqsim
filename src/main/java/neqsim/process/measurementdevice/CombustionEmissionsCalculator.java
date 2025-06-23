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
    if (stream.getFluid().getPhase(0).hasComponent("n-pentane")) {
      NATURAL_GAS_COMPOSITION.put("n-pentane", stream.getFluid().getComponent("n-pentane").getz());
      CO2_EMISSIONS_FACTORS.put("n-pentane", 7.5);
    }
    if (stream.getFluid().getPhase(0).hasComponent("i-pentane")) {
      NATURAL_GAS_COMPOSITION.put("i-pentane", stream.getFluid().getComponent("i-pentane").getz());
      CO2_EMISSIONS_FACTORS.put("i-pentane", 7.5);
    }
    if (stream.getFluid().getPhase(0).hasComponent("hexane")) {
      NATURAL_GAS_COMPOSITION.put("hexane", stream.getFluid().getComponent("hexane").getz());
      CO2_EMISSIONS_FACTORS.put("hexane", 8.5);
    }
    if (stream.getFluid().getPhase(0).hasComponent("nitrogen")) {
      NATURAL_GAS_COMPOSITION.put("Nitrogen", stream.getFluid().getComponent("nitrogen").getz());
      CO2_EMISSIONS_FACTORS.put("Nitrogen", 0.0);
    }
    if (stream.getFluid().getPhase(0).hasComponent("CO2")) {
      NATURAL_GAS_COMPOSITION.put("CO2", stream.getFluid().getComponent("CO2").getz());
      CO2_EMISSIONS_FACTORS.put("CO2", 0.0);
    }
    // Add more components as needed following the same pattern
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
