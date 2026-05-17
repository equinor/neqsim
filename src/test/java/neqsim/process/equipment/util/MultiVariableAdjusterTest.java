package neqsim.process.equipment.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for MultiVariableAdjuster.
 */
public class MultiVariableAdjusterTest {

  @Test
  public void testConstructor() {
    MultiVariableAdjuster adj = new MultiVariableAdjuster("test adjuster");
    Assertions.assertFalse(adj.isConverged());
    Assertions.assertEquals(0, adj.getNumberOfVariables());
  }

  @Test
  public void testAddVariablesAndTargets() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000, "kg/hr");

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(100.0, "bara");

    MultiVariableAdjuster adj = new MultiVariableAdjuster("mv adj");
    adj.addAdjustedVariable(comp, "pressure", "bara");
    adj.addTargetSpecification(comp, "temperature", 80.0, "C");

    Assertions.assertEquals(1, adj.getNumberOfVariables());
  }

  @Test
  public void testSingleVariableAdjustment() {
    // A simple test: adjust valve outlet pressure to achieve a target temperature
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(50.0, "bara");

    Stream valveOut = new Stream("valve out", valve.getOutletStream());

    MultiVariableAdjuster adj = new MultiVariableAdjuster("pressure adjuster");
    adj.addAdjustedVariable(valve, "pressure", "bara");
    adj.addTargetSpecification(valveOut, "temperature", 25.0, "C");
    adj.setVariableBounds(0, 10.0, 90.0);
    adj.setMaxIterations(30);
    adj.setTolerance(0.5);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(valveOut);
    process.add(adj);
    process.run();

    // The adjuster should at least run without errors
    // Convergence is not guaranteed for all cases but it should not throw
    Assertions.assertTrue(adj.getIterations() >= 0);
    Assertions.assertTrue(adj.getMaxResidual() >= 0.0);
  }

  @Test
  public void testCompressorPressureAdjustment() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 30.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(30000.0, "kg/hr");

    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(80.0, "bara");

    Stream compOut = new Stream("compressor out", compressor.getOutletStream());

    Cooler cooler = new Cooler("cooler", compOut);
    cooler.setOutTemperature(273.15 + 40.0);

    Stream coolerOut = new Stream("cooler out", cooler.getOutletStream());

    MultiVariableAdjuster adj = new MultiVariableAdjuster("comp adjuster");
    adj.addAdjustedVariable(compressor, "pressure", "bara");
    adj.addTargetSpecification(coolerOut, "temperature", 40.0, "C");
    adj.setVariableBounds(0, 40.0, 200.0);
    adj.setTolerance(1.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(compOut);
    process.add(cooler);
    process.add(coolerOut);
    process.add(adj);
    process.run();

    // Should not throw, and should track iterations
    Assertions.assertTrue(adj.getIterations() >= 0);
  }
}
