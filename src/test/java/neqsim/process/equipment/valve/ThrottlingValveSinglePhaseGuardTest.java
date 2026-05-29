package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Regression tests for the throttling valve's single-phase multi-phase-check guard.
 *
 * <p>
 * Without the guard, large pressure drops on a single-phase gas inlet near the cricondenbar can
 * trip the PR #2099 supplementary stability trials inside the PHflash Newton loop, producing NaN
 * Z-factors and NaN downstream properties.
 */
public class ThrottlingValveSinglePhaseGuardTest {

  private static SystemInterface buildGas(double temperatureK, double pressureBara) {
    SystemInterface gas = new SystemPrEos(temperatureK, pressureBara);
    gas.addComponent("nitrogen", 0.012);
    gas.addComponent("CO2", 0.013);
    gas.addComponent("methane", 0.880);
    gas.addComponent("ethane", 0.053);
    gas.addComponent("propane", 0.033);
    gas.addComponent("n-butane", 0.005);
    gas.addComponent("n-hexane", 0.002);
    gas.addComponent("n-heptane", 0.002);
    gas.setMixingRule("classic");
    return gas;
  }

  @Test
  public void testLargePressureDropStaysFinite() {
    SystemInterface gas = buildGas(273.15 + 30.0, 120.0);
    StreamInterface feed = new Stream("feed", gas);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(20.0);
    valve.run();

    double outT = valve.getOutletStream().getTemperature("C");
    double outP = valve.getOutletStream().getPressure("bara");
    SystemInterface outFluid = valve.getOutletStream().getFluid();
    outFluid.initProperties();
    double outRho = outFluid.getPhase(0).getDensity();
    assertTrue(Double.isFinite(outT), "valve outlet temperature must be finite (was " + outT + ")");
    assertTrue(Math.abs(outP - 20.0) < 1.0e-3,
        "valve outlet pressure must equal setpoint (was " + outP + ")");
    assertTrue(Double.isFinite(outRho) && outRho > 0.0,
        "valve outlet density must be finite and positive (was " + outRho + ")");
  }
}
