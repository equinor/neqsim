package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies the complete Java quickstart calculations against the current public API. */
class JavaQuickstartDocumentationTest {

  @Test
  void testFirstFlashCalculation() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double densityKgPerCubicMetre = fluid.getDensity("kg/m3");
    double compressibilityFactor = fluid.getZ();

    assertTrue(fluid.getNumberOfPhases() >= 1);
    assertTrue(Double.isFinite(densityKgPerCubicMetre));
    assertTrue(densityKgPerCubicMetre > 0.0);
    assertTrue(Double.isFinite(compressibilityFactor));
    assertTrue(compressibilityFactor > 0.0);
  }

  @Test
  void testFirstProcessSimulation() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    separator.setInternalDiameter(2.0);
    process.add(separator);

    Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    process.run();

    double feedFlowKgPerHour = feed.getFlowRate("kg/hr");
    double gasFlowKgPerHour = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidFlowKgPerHour = separator.getLiquidOutStream().getFlowRate("kg/hr");
    double separatedFlowKgPerHour = gasFlowKgPerHour + liquidFlowKgPerHour;
    double outletTemperatureC = compressor.getOutletStream().getTemperature("C");

    assertTrue(gasFlowKgPerHour > 0.0);
    assertTrue(liquidFlowKgPerHour >= 0.0);
    assertEquals(feedFlowKgPerHour, separatedFlowKgPerHour,
        Math.max(1.0e-6, feedFlowKgPerHour * 1.0e-10));
    assertTrue(compressor.getPower("kW") > 0.0);
    assertTrue(Double.isFinite(outletTemperatureC));
    assertEquals(80.0, compressor.getOutletPressure(), 1.0e-10);
  }
}
