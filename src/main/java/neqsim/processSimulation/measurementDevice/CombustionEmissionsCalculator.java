package neqsim.processSimulation.measurementDevice;

import java.util.HashMap;
import java.util.Map;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

public class CombustionEmissionsCalculator extends StreamMeasurementDeviceBaseClass {

  // Composition of natural gas (in mole percent)
  private static final Map<String, Double> NATURAL_GAS_COMPOSITION = new HashMap<>();

  static {
    NATURAL_GAS_COMPOSITION.put("Methane", 0.80);
    NATURAL_GAS_COMPOSITION.put("Ethane", 0.10);
    NATURAL_GAS_COMPOSITION.put("Propane", 0.05);
    NATURAL_GAS_COMPOSITION.put("Butane", 0.03);
    NATURAL_GAS_COMPOSITION.put("Others", 0.02);
  }

  // CO2 emissions factor for each component (in kg CO2 per kg of component burned)
  private static final Map<String, Double> CO2_EMISSIONS_FACTORS = new HashMap<>();

  static {
    CO2_EMISSIONS_FACTORS.put("Methane", 2.75);
    CO2_EMISSIONS_FACTORS.put("Ethane", 3.75);
    CO2_EMISSIONS_FACTORS.put("Propane", 5.50);
    CO2_EMISSIONS_FACTORS.put("Butane", 6.50);
    CO2_EMISSIONS_FACTORS.put("Others", 4.25);
  }

  /**
   * <p>
   * Constructor for CombustionEmissionsCalculator.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
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
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public CombustionEmissionsCalculator(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  public static void main(String[] args) {
    double co2Emissions = calculateCO2Emissions(NATURAL_GAS_COMPOSITION, CO2_EMISSIONS_FACTORS);
    System.out.println("CO2 emissions from burning natural gas: " + co2Emissions + " kg");
  }

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
