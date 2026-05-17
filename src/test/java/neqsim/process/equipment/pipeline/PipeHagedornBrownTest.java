package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for PipeHagedornBrown flow correlation.
 */
public class PipeHagedornBrownTest {

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

    PipeHagedornBrown pipe = new PipeHagedornBrown("HB pipe", inlet);
    pipe.setDiameter(0.3048); // 12 inch
    pipe.setLength(5000.0);
    pipe.setElevation(500.0); // vertical well
    pipe.setNumberOfIncrements(10);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0, "Pressure drop should be positive for upward flow");
    Assertions.assertTrue(dp < 80.0, "Pressure drop should be less than inlet pressure");

    // Profiles should be populated
    Assertions.assertEquals(11, pipe.getPressureProfile().length);
    Assertions.assertEquals(11, pipe.getTemperatureProfile().length);
    Assertions.assertEquals(11, pipe.getLengthProfile().size());

    // First profile point should be inlet pressure
    Assertions.assertEquals(80.0, pipe.getPressureProfile()[0], 0.01);
  }

  @Test
  public void testTwoPhaseFlow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 40.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule(2);

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(200000, "kg/hr");
    inlet.run();

    PipeHagedornBrown pipe = new PipeHagedornBrown("HB pipe 2ph", inlet);
    pipe.setDiameter(0.2032); // 8 inch
    pipe.setLength(3000.0);
    pipe.setElevation(3000.0); // vertical well
    pipe.setNumberOfIncrements(15);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getTotalPressureDrop();
    Assertions.assertTrue(dp > 0, "Two-phase dp should be positive");

    double holdup = pipe.getLiquidHoldup();
    Assertions.assertTrue(holdup >= 0.0 && holdup <= 1.0,
        "Liquid holdup should be between 0 and 1, got: " + holdup);

    double mixDens = pipe.getMixtureDensity();
    Assertions.assertTrue(mixDens > 0, "Mixture density should be positive");
  }

  @Test
  public void testHorizontalFlow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 60.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(30000, "kg/hr");
    inlet.run();

    PipeHagedornBrown pipe = new PipeHagedornBrown("HB horizontal", inlet);
    pipe.setDiameter(0.1524); // 6 inch
    pipe.setLength(10000.0);
    pipe.setElevation(0.0); // horizontal
    pipe.setNumberOfIncrements(20);

    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(pipe);
    process.run();

    double dp = pipe.getTotalPressureDrop();
    // Horizontal single-phase gas: should be friction-only
    Assertions.assertTrue(dp > 0, "Horizontal dp should be positive (friction)");
  }
}
