package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pressure closure for the transient two-fluid model.
 *
 * <p>
 * By default the transient pressure profile is marched from the outlet with {@code estimatePressureGradient}, a steady
 * mixture friction and gravity correlation. Pressure then responds to velocity rather than to the conserved state, so
 * the phase momentum equations are driven by a pressure that cannot push back when a cell packs. Combined with the
 * physically correct momentum force that {@code setEnableInterfacialPressure} restores, nothing arrests acceleration
 * and the velocities saturate at the internal clamps.
 * </p>
 *
 * <p>
 * {@code setPressureFromCompressibility} closes the loop instead by linearising the volume constraint: a cell holds a
 * fixed volume, so a change in its conserved mass is a change in its mixture density and the pressure follows through
 * the Wood sound speed. Measured over 300 s on a 5 km liquid-rich line fed at 60 bara:
 * </p>
 *
 * <pre>
 * momentum force   pressure closure   inventory   max gas velocity   inlet
 * default          march              100.9 t       8.58 m/s          57.8 bara
 * corrected        march                8.3 t     100.0 m/s (clamp) 1318.9 bara
 * default          volume              79.5 t       7.58 m/s          38.6 bara
 * corrected        volume             104.8 t       4.45 m/s          77.0 bara
 * </pre>
 *
 * <p>
 * The two belong together. The corrected momentum force needs the volume closure or it runs away, and the volume
 * closure needs the corrected force or it drives the inlet below the outlet, because the default force carries a large
 * spurious term that the march was implicitly balancing. The pair is bounded and physical, but it is not yet a fixed
 * point of the steady state: the inventory still drifts about 16 per cent and the inlet sits above the feed pressure,
 * so the combination is offered as an option rather than as the default.
 * </p>
 */
public class TwoFluidPressureClosureTest {

  private static TwoFluidPipe build(boolean correctedMomentum, boolean volumeClosure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50.0 * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(40);
    pipe.setElevationProfile(new double[40]);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setEnableInterfacialPressure(correctedMomentum);
    pipe.setPressureFromCompressibility(volumeClosure);
    pipe.run();
    return pipe;
  }

  private static double maxGasVelocity(TwoFluidPipe pipe) {
    double maximum = 0.0;
    double[] gas = pipe.getGasVelocityProfile();
    for (int i = 0; i < gas.length; i++) {
      maximum = Math.max(maximum, Math.abs(gas[i]));
    }
    return maximum;
  }

  /** The corrected momentum force with the volume closure must not saturate the velocity clamps. */
  @Test
  void testVolumeClosureKeepsTheCorrectedMomentumBounded() {
    TwoFluidPipe pipe = build(true, true);
    for (int i = 0; i < 60; i++) {
      pipe.runTransient(5.0, null);
    }

    double maxGas = maxGasVelocity(pipe);
    double inlet = pipe.getPressureProfile()[0] / 1.0e5;
    Assertions.assertTrue(maxGas < 20.0,
        "gas velocity must stay bounded, but it reached " + maxGas + " m/s against a clamp of 100");
    Assertions.assertTrue(inlet < 120.0,
        "inlet pressure must stay physical on a 60 bara feed, but it reached " + inlet + " bara");
  }

  /** Without the volume closure the same momentum force saturates, which is why the two are paired. */
  @Test
  void testMarchedPressureSaturatesTheCorrectedMomentum() {
    TwoFluidPipe pipe = build(true, false);
    for (int i = 0; i < 60; i++) {
      pipe.runTransient(5.0, null);
    }

    Assertions.assertTrue(maxGasVelocity(pipe) > 50.0,
        "the marched closure is expected to saturate here; if this now passes, the pairing can be revisited");
  }

  /** The default path must be untouched by the new option. */
  @Test
  void testDefaultPathIsUnchanged() {
    TwoFluidPipe pipe = build(false, false);
    for (int i = 0; i < 60; i++) {
      pipe.runTransient(5.0, null);
    }

    double inlet = pipe.getPressureProfile()[0] / 1.0e5;
    Assertions.assertTrue(maxGasVelocity(pipe) < 20.0, "default path gas velocity");
    Assertions.assertTrue(inlet > 50.0 && inlet < 70.0, "default path inlet pressure was " + inlet + " bara");
  }
}
