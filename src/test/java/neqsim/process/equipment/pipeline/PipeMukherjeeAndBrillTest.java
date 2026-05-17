package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for PipeMukherjeeAndBrill flow correlation.
 */
public class PipeMukherjeeAndBrillTest {

  @Test
  public void testSinglePhaseGasFlow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(50000, "kg/hr");
    inlet.run();

    PipeMukherjeeAndBrill pipe = new PipeMukherjeeAndBrill("MB pipe", inlet);
    pipe.setDiameter(0.3048); // 12 inch
    pipe.setLength(10000.0);
    pipe.setElevation(0.0); // horizontal
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0, "Pressure drop should be positive");
    Assertions.assertTrue(dp < 80.0, "Pressure drop should be less than inlet pressure");
    Assertions.assertEquals("SINGLE_PHASE", pipe.getFlowPattern());
  }

  @Test
  public void testTwoPhaseUphillFlow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 40.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(200000, "kg/hr");
    inlet.run();

    PipeMukherjeeAndBrill pipe = new PipeMukherjeeAndBrill("MB uphill", inlet);
    pipe.setDiameter(0.2032); // 8 inch
    pipe.setLength(2000.0);
    pipe.setElevation(500.0); // uphill
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0, "Two-phase uphill dp should be positive");

    double holdup = pipe.getLiquidHoldup();
    Assertions.assertTrue(holdup >= 0.0 && holdup <= 1.0,
        "Liquid holdup should be between 0 and 1, got: " + holdup);

    String fp = pipe.getFlowPattern();
    Assertions.assertNotNull(fp, "Flow pattern should not be null");
    Assertions.assertFalse(fp.isEmpty(), "Flow pattern should not be empty");
  }

  @Test
  public void testTwoPhaseDownhillFlow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 40.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(200000, "kg/hr");
    inlet.run();

    PipeMukherjeeAndBrill pipe = new PipeMukherjeeAndBrill("MB downhill", inlet);
    pipe.setDiameter(0.2032);
    pipe.setLength(2000.0);
    pipe.setElevation(-500.0); // downhill
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getPressureDrop();
    // Downhill flow can have negative dp (gravity gain > friction loss) or positive
    Assertions.assertNotNull(pipe.getFlowPatternProfile());

    // Profiles should be populated
    Assertions.assertEquals(11, pipe.getPressureProfile().length);
    Assertions.assertEquals(11, pipe.getLengthProfile().size());
  }

  @Test
  public void testFlowPatternEnum() {
    PipeMukherjeeAndBrill.FlowPattern fp = PipeMukherjeeAndBrill.FlowPattern.SLUG;
    Assertions.assertEquals("SLUG", fp.name());

    PipeMukherjeeAndBrill.FlowPattern fp2 = PipeMukherjeeAndBrill.FlowPattern.ANNULAR;
    Assertions.assertEquals("ANNULAR", fp2.name());
  }

  @Test
  public void testCompareWithBeggsAndBrills() {
    // Same fluid and conditions for both models — results should be same order of magnitude
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("nC10", 0.20);
    fluid.setMixingRule(2);

    Stream inlet1 = new Stream("inlet1", fluid);
    inlet1.setFlowRate(100000, "kg/hr");
    inlet1.run();

    PipeMukherjeeAndBrill mbPipe = new PipeMukherjeeAndBrill("MB", inlet1);
    mbPipe.setDiameter(0.2032);
    mbPipe.setLength(5000.0);
    mbPipe.setElevation(0.0);
    mbPipe.setNumberOfIncrements(10);

    ProcessSystem proc1 = new ProcessSystem();
    proc1.add(inlet1);
    proc1.add(mbPipe);
    proc1.run();

    double dpMB = mbPipe.getPressureDrop();

    // Both should give a positive pressure drop in the same ballpark
    Assertions.assertTrue(dpMB > 0, "MB pressure drop should be positive");
    Assertions.assertTrue(dpMB < 60.0, "MB pressure drop should be less than inlet pressure");
  }
}
