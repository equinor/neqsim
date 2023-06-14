package neqsim.processSimulation.measurementDevice;

import java.util.HashMap;
import java.util.Map;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CombustionEmissionsCalculator extends StreamMeasurementDeviceBaseClass {

  // Composition of natural gas (in mole percent)
  private static final Map<String, Double> NATURAL_GAS_COMPOSITION = new HashMap<>();

  // CO2 emissions factor for each component (in kg CO2 per kg of component
  // burned)
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

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(190, 10);
    fluid.addComponent("nitrogen", 0.01);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("methane", 1);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.01);
    fluid.addComponent("n-butane", 0.001);
    fluid.addComponent("i-butane", 0.001);

    Stream stream1 = new Stream("stream1", fluid);
    stream1.setFlowRate(1.0, "kg/hr");
    stream1.run();
    CombustionEmissionsCalculator comp = new CombustionEmissionsCalculator("name1", stream1);
    System.out.println(
        "CO2 emissions from burning natural gas: " + comp.getMeasuredValue("kg/hr") + " kg/hr");
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
