package neqsim.PVTsimulation.simulation;

import org.junit.jupiter.api.Test;
import neqsim.thermo.FluidCreator;
import neqsim.thermo.system.SystemInterface;

public class DifferentialLiberationTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = FluidCreator.create("black oil");

    DifferentialLiberation CVDsim = new DifferentialLiberation(tempSystem);
    CVDsim.setPressures(new double[] {300.0, 250.0, 200.0, 150.0, 100.0, 70.0, 50.0, 30.0, 10.0});
    CVDsim.setTemperature(310.0);
    CVDsim.runCalc();
  }
}
