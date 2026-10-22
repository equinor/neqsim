package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Null test of {@link TwoFluidPipe#runTransient(double, java.util.UUID)} on an undulating route that uses the explicit
 * cell-face elevation convention.
 *
 * <p>
 * A converged steady state must be a fixed point of the transient. Before the 2026-10 fix, the first transient step on
 * this undulating liquid-full line moved the inlet pressure by 0.43 bar (6 % of the 7.1 bar steady drop) with constant
 * boundary conditions, and the cell mass flow settled into an odd-even pattern of 23 and 55 kg/s around the 40 kg/s
 * feed, inflating the transient friction by 17 % against the steady solve. Two causes were separated with the OLGA
 * comparison and are both pinned here:
 * </p>
 * <ul>
 * <li>the transient imposed the constant outlet pressure on the last cell centre, whereas the steady solve imposes it
 * on the outlet face (half-cell friction plus gravity head, 0.43 bar on this line);</li>
 * <li>the external inlet and outlet momentum fluxes carried the end-cell centre pressure as their traction while the
 * interior faces carry the mean of two cells, so each end cell received half of its pressure force against the full
 * gravity and friction of the cell. The deficit scales with the local pressure gradient, which is why it was ten times
 * larger on the slopes than on a flat line, and it seeded the odd-even velocity mode in the first step.</li>
 * </ul>
 * <p>
 * With the outlet-face placement and the second-order extrapolation of the external face pressures the inlet pressure
 * moves by less than 0.01 bar in 60 s and the velocity spread falls to 0.015 m/s.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TwoFluidPipeTerrainTransientNullTest {

  private static final int LEGS = 50;
  private static final double LEG_LENGTH = 100.0;
  private static final double DIAMETER = 0.20;
  private static final double OUTLET_PRESSURE_BARA = 10.0;

  /**
   * Build and solve a steady undulating liquid-full dead-oil line.
   *
   * @param massFlowKgPerSec inlet mass flow in kg/s
   * @return the pipe after its steady solve
   */
  private static TwoFluidPipe buildTerrainPipe(double massFlowKgPerSec) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 15.0);
    fluid.addComponent("n-heptane", 30.0);
    fluid.addComponent("n-octane", 20.0);
    fluid.addComponent("nC10", 30.0);
    fluid.addComponent("nC12", 20.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(massFlowKgPerSec, "kg/sec");
    feed.setTemperature(40.0, "C");
    feed.setPressure(15.0, "bara");
    feed.run();

    double[] faces = new double[LEGS + 1];
    for (int i = 0; i <= LEGS; i++) {
      double s = i * LEG_LENGTH;
      faces[i] = 40.0 * Math.sin(2.0 * Math.PI * s / 2500.0) + 50.0 * s / (LEGS * LEG_LENGTH);
    }

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(LEGS * LEG_LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setNumberOfSections(LEGS);
    pipe.setCellFaceElevationProfile(faces);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setOutletPressure(OUTLET_PRESSURE_BARA, "bara");
    pipe.run();
    return pipe;
  }

  @Test
  void testSteadyTerrainStateIsAFixedPointOfTheTransientPressureLevel() {
    TwoFluidPipe pipe = buildTerrainPipe(40.0);
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "steady solve must converge");
    double[] steady = pipe.getPressureProfile();
    double steadyDrop = (steady[0] - steady[steady.length - 1]) / 1.0e5;
    double inletBefore = steady[0] / 1.0e5;
    double outletBefore = steady[steady.length - 1] / 1.0e5;

    for (int i = 0; i < 30; i++) {
      pipe.runTransient(2.0, null);
    }
    double[] after = pipe.getPressureProfile();
    double inletAfter = after[0] / 1.0e5;
    double outletAfter = after[after.length - 1] / 1.0e5;

    Assertions.assertTrue(Math.abs(inletAfter - inletBefore) < 0.01 * steadyDrop, "constant boundaries moved the inlet"
        + " pressure from " + inletBefore + " to " + inletAfter + " bara (steady drop " + steadyDrop + " bar)");
    Assertions.assertEquals(outletBefore, outletAfter, 0.01,
        "the transient must keep the prescribed outlet pressure on the outlet face, as the steady solve does");

    double vMin = Double.POSITIVE_INFINITY;
    double vMax = Double.NEGATIVE_INFINITY;
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      vMin = Math.min(vMin, section.getLiquidVelocity());
      vMax = Math.max(vMax, section.getLiquidVelocity());
    }
    Assertions.assertTrue(vMax - vMin < 0.05,
        "a liquid-full line at constant feed must carry a uniform velocity, but it spans " + vMin + " to " + vMax);
  }

  @Test
  void testEndCellsCarryTheFullPressureForceAfterOneStep() {
    TwoFluidPipe pipe = buildTerrainPipe(40.0);
    double area = Math.PI * DIAMETER * DIAMETER / 4.0;
    pipe.runTransient(2.0, null);
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    double inletMassFlow = sections[0].getLiquidHoldup() * sections[0].getLiquidDensity()
        * sections[0].getLiquidVelocity() * area;
    double outletMassFlow = sections[LEGS - 1].getLiquidHoldup() * sections[LEGS - 1].getLiquidDensity()
        * sections[LEGS - 1].getLiquidVelocity() * area;
    // Before the fix the end cells dropped to 31.7 and 30.6 kg/s in the first step (half pressure force).
    Assertions.assertEquals(40.0, inletMassFlow, 1.0, "inlet cell mass flow after one step");
    Assertions.assertEquals(40.0, outletMassFlow, 1.0, "outlet cell mass flow after one step");
  }
}
