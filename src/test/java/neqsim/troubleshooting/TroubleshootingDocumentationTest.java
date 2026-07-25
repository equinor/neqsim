package neqsim.troubleshooting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Executes the API workflows documented in {@code docs/troubleshooting/index.md}.
 *
 * @author esolbra
 */
class TroubleshootingDocumentationTest {
  @Test
  void diagnosesFluidCompositionAndDensity() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("CO2", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double overallTotal = 0.0;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      overallTotal += fluid.getPhase(0).getComponent(i).getz();
    }
    double bulkDensity = fluid.getDensity("kg/m3");
    assertTrue(fluid.hasPhaseType("gas"));
    double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");

    assertEquals(1.0, overallTotal, 1.0e-12);
    assertTrue(bulkDensity > 0.0);
    assertTrue(gasDensity > 0.0);
  }

  @Test
  void runsDocumentedProcessDiagnosis() {
    SystemInterface feedFluid = new SystemSrkEos(298.15, 50.0);
    feedFluid.addComponent("methane", 0.90);
    feedFluid.addComponent("ethane", 0.06);
    feedFluid.addComponent("propane", 0.03);
    feedFluid.addComponent("CO2", 0.01);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("feed", feedFluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Separator separator = new Separator("separator", feed);
    Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.run();

    assertEquals(10000.0, separator.getGasOutStream().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(228.4339, compressor.getPower("kW"), 0.1);
  }

  @Test
  void configuresRecycleAndRunsPhaseEnvelope() {
    Recycle recycle = new Recycle("recycle");
    recycle.setTolerance(1.0e-4);
    recycle.setMaxIterations(50);
    recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);

    assertEquals(50, recycle.getMaxIterations());
    assertEquals(AccelerationMethod.WEGSTEIN, recycle.getAccelerationMethod());
    assertEquals(1.0e-4, recycle.getFlowTolerance(), 0.0);
    assertEquals(1.0e-4, recycle.getCompositionTolerance(), 0.0);
    assertEquals(1.0e-4, recycle.getTemperatureTolerance(), 0.0);
    assertEquals(1.0e-4, recycle.getPressureTolerance(), 0.0);

    SystemInterface envelopeFluid = new SystemSrkEos(283.15, 10.0);
    envelopeFluid.addComponent("methane", 0.85);
    envelopeFluid.addComponent("ethane", 0.08);
    envelopeFluid.addComponent("propane", 0.04);
    envelopeFluid.addComponent("n-butane", 0.03);
    envelopeFluid.setMixingRule("classic");

    ThermodynamicOperations envelopeOperations = new ThermodynamicOperations(envelopeFluid);
    envelopeOperations.calcPTphaseEnvelope();
    double[] dewTemperatures = envelopeOperations.get("dewT");
    double[] bubbleTemperatures = envelopeOperations.get("bubT");

    assertTrue(dewTemperatures.length > 2);
    assertTrue(bubbleTemperatures.length > 2);
  }
}
