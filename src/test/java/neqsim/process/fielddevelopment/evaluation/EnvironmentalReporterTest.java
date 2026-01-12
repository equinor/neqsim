package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.evaluation.EnvironmentalReporter.PowerSupplyType;

/**
 * Unit tests for EnvironmentalReporter.
 *
 * @author ESOL
 * @version 1.0
 */
public class EnvironmentalReporterTest {
  private EnvironmentalReporter reporter;

  @BeforeEach
  void setUp() {
    reporter = new EnvironmentalReporter();
  }

  @Test
  void testCO2FromPower() {
    double co2 = reporter.getCO2FromPower(1000.0);
    assertTrue(co2 > 0, "CO2 should be positive");
  }

  @Test
  void testCO2EmissionFactorConstants() {
    assertEquals(0.55, EnvironmentalReporter.CO2_GAS_TURBINE, 0.01);
    assertEquals(0.70, EnvironmentalReporter.CO2_DIESEL, 0.01);
    assertEquals(0.02, EnvironmentalReporter.CO2_POWER_FROM_SHORE, 0.01);
  }

  @Test
  void testPowerSupplyTypeEmissionFactors() {
    double gasTurbine = PowerSupplyType.GAS_TURBINE.getEmissionFactor();
    double diesel = PowerSupplyType.DIESEL.getEmissionFactor();
    double shore = PowerSupplyType.POWER_FROM_SHORE.getEmissionFactor();

    assertEquals(0.55, gasTurbine, 0.01, "Gas turbine emission factor");
    assertEquals(0.70, diesel, 0.01, "Diesel emission factor");
    assertEquals(0.02, shore, 0.01, "Power from shore emission factor");
    assertTrue(shore < gasTurbine, "Shore power < gas turbine");
    assertTrue(gasTurbine < diesel, "Gas turbine < diesel");
  }

  @Test
  void testCO2IntensityWithProduction() {
    reporter.setProduction(1000.0, 100000.0, 500.0);
    double intensity = reporter.getCO2Intensity(50000.0);
    assertTrue(intensity > 0, "CO2 intensity should be positive");
  }

  @Test
  void testProcessSystemAnalysis() {
    neqsim.process.processmodel.ProcessSystem system =
        new neqsim.process.processmodel.ProcessSystem();

    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("propane", 0.10);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("Feed", fluid);
    feed.setFlowRate(10000, "kg/hr");

    neqsim.process.equipment.compressor.Compressor comp =
        new neqsim.process.equipment.compressor.Compressor("Compressor", feed);
    comp.setOutletPressure(100.0);

    system.add(feed);
    system.add(comp);
    system.run();

    double power = reporter.getTotalPowerConsumption(system);
    assertTrue(power >= 0, "Power consumption should be non-negative");
  }

  @Test
  void testReportGeneration() {
    neqsim.process.processmodel.ProcessSystem system =
        new neqsim.process.processmodel.ProcessSystem();

    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(288.15, 30.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");

    neqsim.process.equipment.stream.Stream feed =
        new neqsim.process.equipment.stream.Stream("Feed", fluid);
    feed.setFlowRate(5000, "kg/hr");
    system.add(feed);
    system.run();

    EnvironmentalReporter.EnvironmentalReport report = reporter.generateReport(system);
    assertNotNull(report, "Report should not be null");
  }

  @Test
  void testNcsOiwLimit() {
    assertEquals(30.0, EnvironmentalReporter.NCS_OIW_LIMIT, 0.01, "NCS OIW limit should be 30");
  }

  @Test
  void testPowerSupplyTypes() {
    for (PowerSupplyType type : PowerSupplyType.values()) {
      double factor = type.getEmissionFactor();
      assertTrue(factor >= 0, "Emission factor should be non-negative for " + type);
    }
  }

  @Test
  void testSetPowerSupplyType() {
    reporter.setPowerSupplyType(PowerSupplyType.POWER_FROM_SHORE);
    double co2 = reporter.getCO2FromPower(1000.0);
    assertTrue(co2 > 0, "CO2 should be positive even with shore power");
  }
}
