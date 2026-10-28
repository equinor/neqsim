package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the steady initial state of the {@link WaterHammerPipe} valve boundary. Before the fix the valve
 * reference head equalled the outlet head, so the first transient step saw no driving head and produced a start-up
 * surge of about half the Joukowsky value even when the valve did not move.
 */
class WaterHammerPipeValveInitializationTest {
  private static WaterHammerPipe newPipe(double backPressureBara) {
    SystemInterface water = new SystemSrkEos(298.15, 60.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    Stream feed = new Stream("feed", water);
    feed.setFlowRate(12000.0, "kg/hr");
    feed.run();
    WaterHammerPipe pipe = new WaterHammerPipe("line", feed);
    pipe.setLength(60.0);
    pipe.setDiameter(0.0737);
    pipe.setWallThickness(0.00762);
    pipe.setNumberOfNodes(40);
    pipe.setWaveSpeed(1450.0);
    if (backPressureBara > 0) {
      pipe.setValveBackPressure(backPressureBara);
    }
    pipe.run(UUID.randomUUID());
    return pipe;
  }

  private static double maxEnvelopeBar(WaterHammerPipe pipe) {
    double max = 0.0;
    for (double p : pipe.getMaxPressureEnvelope("bar")) {
      max = Math.max(max, p);
    }
    return max;
  }

  @Test
  void openValveWithoutEventStaysAtSteadyState() {
    WaterHammerPipe pipe = newPipe(-1.0);
    double p0 = pipe.getPressureProfile("bar")[0];
    double dt = 0.5 * pipe.getMaxStableTimeStep();
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 2000; i++) {
      pipe.runTransient(dt, id);
    }
    assertTrue(maxEnvelopeBar(pipe) - p0 < 0.05,
        "no valve movement must not create a surge; rise = " + (maxEnvelopeBar(pipe) - p0) + " bar");
  }

  /** A receiver about 0.5 bar below the outlet (full-bore valve) must still start at equilibrium. */
  @Test
  void smallValvePressureDropKeepsSteadyState() {
    WaterHammerPipe pipe = newPipe(59.5);
    assertEquals(59.5, pipe.getValveBackPressure(), 1e-12);
    double p0 = pipe.getPressureProfile("bar")[0];
    double dt = 0.5 * pipe.getMaxStableTimeStep();
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 1000; i++) {
      pipe.runTransient(dt, id);
    }
    assertTrue(maxEnvelopeBar(pipe) - p0 < 0.05, "steady state must hold with a 0.5 bar valve");
  }

  @Test
  void instantaneousClosureApproachesJoukowsky() {
    WaterHammerPipe pipe = newPipe(59.5);
    double p0 = pipe.getPressureProfile("bar")[pipe.getPressureProfile("bar").length - 1];
    double v = pipe.getVelocityProfile()[0];
    double joukowsky = pipe.calcJoukowskyPressureSurge(v, "bar");
    double dt = 0.5 * pipe.getMaxStableTimeStep();
    UUID id = UUID.randomUUID();
    pipe.setValveOpening(0.0);
    for (int i = 0; i < 2000; i++) {
      pipe.runTransient(dt, id);
    }
    double surge = maxEnvelopeBar(pipe) - p0;
    assertTrue(surge > 0.8 * joukowsky && surge < 1.3 * joukowsky,
        "instantaneous closure surge " + surge + " bar vs Joukowsky " + joukowsky + " bar");
  }
}
