package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Null test of {@link TwoFluidPipe#runTransient(double, java.util.UUID)} on an undulating route that uses the explicit
 * cell-face elevation convention.
 *
 * <p>
 * A converged steady state must be a fixed point of the transient. On an undulating liquid-full line with explicit
 * cell-face elevations the first transient step instead moves the inlet pressure by about 0.4 bar (6 % of the 7.1 bar
 * steady drop) with constant boundary conditions, and the following cells show an odd-even pressure pattern whose
 * amplitude follows the local gravity head. Two causes were separated during the 2026-10 OLGA comparison:
 * </p>
 * <ul>
 * <li>the transient imposes the outlet pressure on the last cell centre, whereas the steady solve imposes it on the
 * outlet face (half-cell friction plus gravity head, 0.43 bar here);</li>
 * <li>the coupled pressure-momentum solve on the collocated grid does not balance the cell-centred gravity source on
 * steep slopes, so the total transient pressure drop is about 12 % above the steady drop, and correcting the outlet
 * placement alone raises the inlet pressure further from the OLGA value instead of closing the gap.</li>
 * </ul>
 * <p>
 * The test pins the acceptance criterion and stays disabled until both causes are removed together.
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
  @Disabled("Known defect: terrain transient moves the inlet pressure by about 6 % of the steady drop at constant"
      + " boundary conditions (outlet-face placement plus unbalanced gravity in the coupled pressure solve).")
  void testSteadyTerrainStateIsAFixedPointOfTheTransientPressureLevel() {
    TwoFluidPipe pipe = buildTerrainPipe(40.0);
    Assertions.assertTrue(pipe.isSteadyStateConverged(), "steady solve must converge");
    double[] steady = pipe.getPressureProfile();
    double steadyDrop = (steady[0] - steady[steady.length - 1]) / 1.0e5;
    double inletBefore = steady[0] / 1.0e5;

    for (int i = 0; i < 15; i++) {
      pipe.runTransient(2.0, null);
    }
    double inletAfter = pipe.getPressureProfile()[0] / 1.0e5;

    Assertions.assertTrue(Math.abs(inletAfter - inletBefore) < 0.03 * steadyDrop, "constant boundaries moved the inlet"
        + " pressure from " + inletBefore + " to " + inletAfter + " bara (steady drop " + steadyDrop + " bar)");
  }
}
