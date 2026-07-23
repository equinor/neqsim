package neqsim.quickstart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/**
 * Executes the NeqSim API calls shown in the Google Colab quickstart.
 *
 * @author esol
 * @version 1.0
 */
public class ColabQuickstartDocumentationTest extends NeqSimTest {
  /**
   * Verifies the first thermodynamic calculation shown in the quickstart.
   */
  @Test
  void firstCalculationProducesInitializedGasProperties() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    assertTrue(fluid.getNumberOfPhases() > 0);
    assertTrue(fluid.getDensity("kg/m3") > 0.0);
    assertTrue(fluid.getZ() > 0.0);
  }

  /**
   * Verifies that the process example separates both products and compresses the gas.
   */
  @Test
  void processExampleSeparatesTwoPhasesAndClosesMassBalance() {
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
    feed.setTemperature(30.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Separator separator = new Separator("Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    process.run();

    double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidFlow = separator.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(gasFlow > 100.0);
    assertTrue(liquidFlow > 100.0);
    assertEquals(10000.0, gasFlow + liquidFlow, 1.0e-3);
    assertTrue(compressor.getPower("kW") > 0.0);
  }

  /**
   * Verifies the phase-envelope data keys used by the plotting example.
   */
  @Test
  void phaseEnvelopeExampleProvidesDewAndBubbleCurves() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.calcPTphaseEnvelope();

    double[] dewTemperatures = operations.get("dewT");
    double[] dewPressures = operations.get("dewP");
    double[] bubbleTemperatures = operations.get("bubT");
    double[] bubblePressures = operations.get("bubP");

    assertTrue(dewTemperatures.length > 0);
    assertEquals(dewTemperatures.length, dewPressures.length);
    assertTrue(bubbleTemperatures.length > 0);
    assertEquals(bubbleTemperatures.length, bubblePressures.length);
  }
}
