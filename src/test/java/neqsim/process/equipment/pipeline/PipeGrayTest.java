package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link PipeGray} (Gray 1974 vertical-flow correlation for gas/condensate wells).
 */
public class PipeGrayTest {

  private ProcessSystem buildGasCondensateWell(PipeGray.HoldupMethod method) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 60.0, 120.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nC10", 0.03);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(120000.0, "kg/hr");
    inlet.run();

    PipeGray pipe = new PipeGray("Gray well", inlet);
    pipe.setDiameter(0.0889); // 3.5 inch tubing
    pipe.setLength(3000.0);
    pipe.setElevation(3000.0); // vertical well
    pipe.setNumberOfIncrements(10);
    pipe.setHoldupMethod(method);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    return process;
  }

  @Test
  public void testGrayTwoPhaseVerticalFlow() {
    ProcessSystem process = buildGasCondensateWell(PipeGray.HoldupMethod.GRAY);
    process.run();
    PipeGray pipe = (PipeGray) process.getUnit("Gray well");

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0.0, "Pressure drop should be positive for upward flow");
    Assertions.assertTrue(dp < 120.0, "Pressure drop should be below inlet pressure");

    double holdup = pipe.getLiquidHoldup();
    Assertions.assertTrue(holdup >= 0.0 && holdup <= 1.0, "Holdup must be in [0,1], got " + holdup);

    Assertions.assertTrue(pipe.getEffectiveRoughness() >= 0.0, "Effective roughness should be non-negative");
    Assertions.assertTrue(pipe.getSuperficialGasVelocity() > 0.0, "Superficial gas velocity should be positive");
  }

  @Test
  public void testWoldesemayatGhajarHoldupMethod() {
    ProcessSystem process = buildGasCondensateWell(PipeGray.HoldupMethod.WOLDESEMAYAT_GHAJAR);
    process.run();
    PipeGray pipe = (PipeGray) process.getUnit("Gray well");

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0.0, "Pressure drop should be positive with W-G holdup");
    double holdup = pipe.getLiquidHoldup();
    Assertions.assertTrue(holdup >= 0.0 && holdup <= 1.0, "Holdup must be in [0,1], got " + holdup);
    Assertions.assertEquals(PipeGray.HoldupMethod.WOLDESEMAYAT_GHAJAR, pipe.getHoldupMethod());
  }

  @Test
  public void testSinglePhaseGas() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 100.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(80000.0, "kg/hr");
    inlet.run();

    PipeGray pipe = new PipeGray("Gray gas", inlet);
    pipe.setDiameter(0.1016);
    pipe.setLength(2000.0);
    pipe.setElevation(2000.0);
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    Assertions.assertTrue(pipe.getTotalPressureDrop() > 0.0, "Single-phase gas dp should be positive");
  }
}
