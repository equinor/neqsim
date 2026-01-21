package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link SafetyReliefValve} transient opening and reseating behaviour.
 */
public class SafetyReliefValveTest {
  @Test
  void testTransientOpeningAndClosing() {
    // upstream gas node
    SystemInterface gas = new SystemSrkEos(298.15, 5.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule(2);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    StreamInterface vesselGas = new Stream("VesselGas", gas);
    vesselGas.setPressure(5.0, "bara");
    vesselGas.setTemperature(298.15, "K");

    // downstream flare header
    SystemInterface flareSys = gas.clone();
    StreamInterface flare = new Stream("Flare", flareSys);
    flare.setPressure(1.03, "bara");

    SafetyReliefValve psv = new SafetyReliefValve("PSV-101", vesselGas)
        .configureConventionalSnap(10.0, 0.10, 0.07, 180.0);
    psv.setOutletStream(flare);
    psv.setMinStableOpenFrac(0.0);

    double dt = 0.1; // s
    for (double t = 0.0; t < 20.0; t += dt) {
      double p = 5.0 + 0.4 * t; // ramp pressure
      vesselGas.setPressure(p, "bara");
      psv.runTransient(dt);
    }
    assertTrue(psv.getOpenFraction() > 0.9, "valve should open near fully at high pressure");

    for (double t = 0.0; t < 5.0; t += dt) {
      vesselGas.setPressure(8.0, "bara"); // below reseat
      psv.runTransient(dt);
    }
    assertEquals(0.0, psv.getOpenFraction(), 1e-2, "valve should close after pressure drops");
  }
}

