package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Pressure runaway of the interfacial-pressure stabilizer.
 *
 * <p>
 * The stabilizer is required for well-posedness: {@code TwoFluidHyperbolicityTest} shows that without it the
 * characteristics are complex wherever two phases coexist with slip. Enabling it, however, drives the reconstructed
 * inlet pressure far above the feed pressure on a liquid-rich line, so the option cannot be used as it stands.
 * </p>
 *
 * <p>
 * Measured on a 5 km, 0.30 m line at 50 kg/s fed at 60 bara, after 300 s with the outlet held at 55.34 bara:
 * </p>
 *
 * <pre>
 * term off                    inventory 100.9 t   inlet   57.8 bara
 * term on, implicit, CFL 0.5  inventory   8.3 t   inlet 1318.9 bara
 * term on, explicit, CFL 0.5  inventory  57.5 t   inlet 1368.2 bara
 * term on, explicit, CFL 0.05 inventory 103.5 t   inlet  890.3 bara
 * term on, implicit, CFL 0.05 inventory 103.7 t   inlet 1065.6 bara
 * </pre>
 *
 * <p>
 * The runaway appears in every arm where the term is active, including the explicit path at the small CFL number that
 * path was written for, so it is a property of the stabilizer itself rather than of the implicit treatment or of the
 * time step. The interior algebra is not the cause: the flux carries {@code alphaHalf * pHalf} and the source
 * differences those same interface values, so the spurious holdup-gradient force cancels exactly, and
 * {@code applyPressureGradient} is never called, so the pressure gradient is not applied twice.
 * </p>
 *
 * <p>
 * Setting the interfacial pressure coefficient to zero isolates the cause. That removes the Bestion closure entirely
 * and leaves only the cancellation of the spurious holdup-gradient force, and the line still runs away:
 * </p>
 *
 * <pre>
 * term off                    inventory 100.9 t   inlet   57.8 bara
 * coefficient 0.0             inventory  30.8 t   inlet 1253.8 bara
 * coefficient 0.2             inventory  28.2 t   inlet 1351.1 bara
 * coefficient 1.2 (default)   inventory  30.8 t   inlet 1261.5 bara
 * coefficient 5               inventory   8.2 t   inlet 1128.9 bara
 * coefficient 50              inventory   8.2 t   inlet  935.8 bara
 * coefficient 1000            inventory 105.0 t   inlet 1225.1 bara
 * </pre>
 *
 * <p>
 * Reformulating the pressure force does not help either. Removing {@code alpha * p} from the convective momentum flux
 * at both the interfaces and the boundaries, and supplying the force instead from cell-centred pressures through
 * {@code applyPressureGradient}, still gives 872 bara implicit and 1148 bara explicit. So the defect is neither the
 * stabilizer nor the discretisation of the non-conservative term.
 * </p>
 *
 * <p>
 * What every diverging variant has in common is that the net momentum equation becomes {@code -alpha * A * dp/dx}, the
 * physically correct force, while the stable term-off path retains an additional {@code -p * A * d(alpha)/dx} that does
 * not belong there. The model is only stable while that spurious force is present. The first step does not explain it:
 * starting from the steady state, a step of 0.01 s changes the velocities by 0.071 m/s with the term on against 0.074
 * m/s with it off, so the two are equally balanced initially and the difference is in the growth.
 * </p>
 *
 * <p>
 * The inlet pressure is a symptom rather than the defect. Pressure is not a state variable here: it is reconstructed
 * after every step by marching from the fixed outlet with {@code estimatePressureGradient}, a mixture friction and
 * gravity correlation proportional to the square of the mixture velocity. Measuring the velocities shows what the
 * pressure is reporting. After 300 s the term-off line peaks at 8.6 m/s gas and 4.0 m/s liquid, while the stabilized
 * line sits at exactly 100.0 and 50.0 m/s, the clamps applied in {@code TwoFluidSection.extractPrimitiveVariables}. The
 * velocities have saturated, and the reconstruction turns that into 1300 bara.
 * </p>
 *
 * <p>
 * So this is a momentum runaway. With the physically correct force alone the phase momentum equations accelerate
 * without bound, because the pressure driving them comes from a steady mixture correlation rather than from a
 * conservation law, leaving no compressibility feedback to arrest the acceleration. The term-off path masks this by
 * retaining the large spurious force. Making the pressure part of the solution is the structural fix; correcting the
 * momentum force alone is not enough.
 * </p>
 *
 * <p>
 * A linearised volume constraint was tried for that and is not sufficient on its own. Deriving pressure from the change
 * in conserved cell mass through the Wood sound speed, {@code dp = c^2 * d(rho)}, looks convincing for the first 300 s,
 * where the velocities sit at 4.45 m/s and the inlet at 77.0 bara instead of saturating. Followed to 1200 s it
 * oscillates rather than settling: the outlet flow swings between zero and 384 kg/s against a 50 kg/s feed, the inlet
 * between 39 and 77 bara, and the gas velocity reaches the 100 m/s clamp after all. The saturation is delayed, not
 * removed, so the attempt was reverted. Any candidate must therefore be judged over at least 1200 s, because a 300 s
 * window is short enough to land mid-swing and read as a fix. The remaining lead is that the pressure and the momenta
 * are updated in sequence rather than solved together.
 * </p>
 */
public class TwoFluidInterfacialPressureRunawayTest {

  private static TwoFluidPipe build(boolean implicitCoupling, double cfl) {
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
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(implicitCoupling);
    pipe.setCflNumber(cfl);
    pipe.run();
    return pipe;
  }

  private static double inletPressureBara(boolean implicitCoupling, double cfl) {
    TwoFluidPipe pipe = build(implicitCoupling, cfl);
    for (int i = 0; i < 60; i++) {
      pipe.runTransient(5.0, null);
    }
    return pipe.getPressureProfile()[0] / 1.0e5;
  }

  /** A line fed at 60 bara cannot reconstruct an inlet pressure of hundreds of bar. */
  @Test
  @Disabled("Known defect: the non-conservative p*d(alpha)/dx treatment drives the reconstructed inlet pressure to "
      + "about 900-1370 bara on a 60 bara feed, with the stabilizer coefficient at zero as well as at its default.")
  void testStabilizedLineKeepsAPhysicalInletPressure() {
    double implicitLargeStep = inletPressureBara(true, 0.5);
    double explicitSmallStep = inletPressureBara(false, 0.05);

    Assertions.assertTrue(implicitLargeStep < 120.0,
        "implicit stabilizer reconstructed an inlet pressure of " + implicitLargeStep + " bara on a 60 bara feed");
    Assertions.assertTrue(explicitSmallStep < 120.0,
        "explicit stabilizer reconstructed an inlet pressure of " + explicitSmallStep + " bara on a 60 bara feed");
  }

  /** Without the stabilizer the inlet pressure stays physical, which localises the defect to the term. */
  @Test
  void testUnstabilizedLineKeepsAPhysicalInletPressure() {
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
    pipe.run();

    for (int i = 0; i < 60; i++) {
      pipe.runTransient(5.0, null);
    }
    double inlet = pipe.getPressureProfile()[0] / 1.0e5;
    Assertions.assertTrue(inlet < 120.0,
        "the unstabilized line must keep a physical inlet pressure, but it reconstructed " + inlet + " bara");
  }
}
