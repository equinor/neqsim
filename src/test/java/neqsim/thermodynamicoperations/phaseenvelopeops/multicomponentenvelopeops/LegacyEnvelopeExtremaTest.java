package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exercises extrema published by callers' direct use of the legacy continuation operation.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class LegacyEnvelopeExtremaTest {
  /** Both extrema of a pure methane saturation curve coincide at the SRK critical endpoint. */
  @Test
  void pureMethaneExtremaApproachTheCriticalEndpoint() {
    SystemSrkEos methane = new SystemSrkEos(300.0, 50.0);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule(2);
    PTphaseEnvelope trace = new PTphaseEnvelope(methane, null, 1e-10, 1.0, true);
    trace.run();
    for (String name : new String[] {"cricondenbar", "cricondentherm"}) {
      assertEquals(methane.getComponent(0).getTC(), trace.get(name)[0], 0.02, name);
      assertEquals(methane.getComponent(0).getPC(), trace.get(name)[1], 0.02, name);
    }
  }

  /** A failed trace between two successful traces must not publish the feed state or a previous result. */
  @Test
  void failedTraceBetweenBinaryEnvelopes() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("propane", 0.2);
    fluid.setMixingRule(2);
    PTphaseEnvelope trace = new PTphaseEnvelope(fluid, null, 1e-10, 1.0, true);
    trace.run();
    double[] bar = trace.get("cricondenbar").clone();
    double[] therm = trace.get("cricondentherm").clone();
    assertTrue(bar[1] > therm[1] && therm[1] > 1.0);
    assertTrue(therm[0] > bar[0] && bar[0] > 190.0);

    fluid.setTemperature(300.0);
    fluid.setPressure(50.0);
    trace.lowPres = Double.NaN;
    trace.run();
    for (String name : new String[] {"cricondenbar", "cricondentherm"}) {
      assertTrue(Double.isNaN(trace.get(name)[0]), name);
      assertTrue(Double.isNaN(trace.get(name)[1]), name);
    }

    fluid.setTemperature(300.0);
    fluid.setPressure(50.0);
    trace.lowPres = 1.0;
    trace.run();
    for (int i = 0; i < 2; i++) {
      assertEquals(bar[i], trace.get("cricondenbar")[i], 1e-4);
      assertEquals(therm[i], trace.get("cricondentherm")[i], 1e-4);
    }
  }
}
