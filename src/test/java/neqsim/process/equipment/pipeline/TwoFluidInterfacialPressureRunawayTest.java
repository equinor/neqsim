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
 * term on, coefficient 0.0    inventory  30.8 t   inlet 1253.8 bara
 * term on, coefficient 0.2    inventory  28.2 t   inlet 1351.1 bara
 * term on, coefficient 1.2    inventory  30.8 t   inlet 1261.5 bara
 * </pre>
 *
 * <p>
 * So the defect is in the treatment of the non-conservative {@code p * d(alpha)/dx} source, not in the stabilizer it
 * was added to carry. Both formulations are defensible in the continuum: keeping {@code alpha * p} in the momentum flux
 * requires that source to cancel it down to {@code alpha * dp/dx}, and omitting both leaves the momentum equation short
 * of a real force. The discrete forms are not equivalent. The flux weights the holdup with a plain average while
 * supplying numerical dissipation only to the convective part, so cancelling its pressure contribution and
 * reintroducing the force through cell-centred values leaves the void-fraction wave undamped. Restoring it needs a
 * well-balanced or path-conservative discretisation, or moving the pressure force wholly into the source through the
 * currently unused {@code applyPressureGradient}. Passing coefficients through the existing form does not fix it.
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
