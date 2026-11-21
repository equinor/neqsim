package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Line packing behind a closed outlet and coldest-section cooldown for {@link TwoFluidPipe}.
 *
 * <p>
 * From a subsea tie-back HIPPS study: with the default pressure split the pressure stays flat behind a closed outlet
 * while mass accumulates, whereas the coupled pressure-momentum option gives the compressible pressure rise that
 * overpressure-protection studies need.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TwoFluidPipeLinePackingTest {
  /**
   * Builds a short wet-gas/oil line fed at 300 t/h.
   *
   * @param coupled true to enable coupled pressure-momentum and interfacial pressure
   * @return the pipe after a steady-state run
   */
  private TwoFluidPipe pipe(boolean coupled) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 111.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("n-decane", 6.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream("well", fluid);
    stream.setFlowRate(300000.0, "kg/hr");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("jumper", stream);
    pipe.setLength(400.0);
    pipe.setDiameter(0.3048);
    pipe.setNumberOfSections(10);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setHeatTransferCoefficient(4.0);
    pipe.setOutletPressure(110.0, "bara");
    if (coupled) {
      pipe.setEnableCoupledPressureMomentum(true);
      pipe.setEnableInterfacialPressure(true);
    }
    pipe.run();
    return pipe;
  }

  /** With coupled pressure-momentum, pressure rises behind a closed outlet as mass accumulates. */
  @Test
  void closedOutletPacksLineWithCoupledPressure() {
    TwoFluidPipe pipe = pipe(true);
    double p0 = pipe.getPressureProfile()[0] / 1e5;
    double m0 = pipe.getTotalMassInventory();
    pipe.closeOutlet();
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 10; i++) {
      pipe.runTransient(0.5, id);
    }
    double p1 = pipe.getPressureProfile()[0] / 1e5;
    double m1 = pipe.getTotalMassInventory();
    assertTrue(m1 > m0 * 1.03, "mass must accumulate: " + m0 + " -> " + m1);
    assertTrue(p1 > p0 + 10.0, "pressure must rise with accumulated mass: " + p0 + " -> " + p1);
  }

  /**
   * Packing a line through the saturation pressure of the feed keeps the pressure profile intact.
   *
   * <p>
   * Above the saturation pressure the equilibrium flash of the feed has a single hydrocarbon phase while every cell
   * still holds gas and oil. Resetting the cell phase densities from that merged phase used to leave the cell volume
   * far from closed: the coupled pressure correction stopped converging at about 120 s in this case. The same happens
   * at rare isolated conditions inside the two-phase region where the flash misses the gas-oil split.
   * </p>
   */
  @Test
  void packingThroughSaturationPressureKeepsCellPressures() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 240.0);
    fluid.addComponent("methane", 70.0);
    fluid.addComponent("ethane", 7.0);
    fluid.addComponent("n-heptane", 10.0);
    fluid.addComponent("n-decane", 6.0);
    fluid.addComponent("water", 20.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream stream = new Stream("well", fluid);
    stream.setFlowRate(37500.0, "kg/hr");
    stream.run();

    TwoFluidPipe pipe = new TwoFluidPipe("flowline", stream);
    pipe.setLength(1950.0);
    pipe.setDiameter(0.3048);
    pipe.setNumberOfSections(10);
    pipe.setSurfaceTemperature(4.0, "C");
    pipe.setHeatTransferCoefficient(4.0);
    pipe.setOutletPressure(220.0, "bara");
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.run();
    pipe.closeOutlet();

    UUID id = UUID.randomUUID();
    double lowestCellPressure = Double.POSITIVE_INFINITY;
    double largestRiseAlongFlow = 0.0;
    for (int step = 0; step < 60; step++) {
      pipe.runTransient(5.0, id);
      double[] pressure = pipe.getPressureProfile();
      for (int cell = 0; cell < pressure.length; cell++) {
        lowestCellPressure = Math.min(lowestCellPressure, pressure[cell] / 1e5);
        if (cell > 0) {
          largestRiseAlongFlow = Math.max(largestRiseAlongFlow, (pressure[cell] - pressure[cell - 1]) / 1e5);
        }
      }
    }
    double inletPressure = pipe.getPressureProfile()[0] / 1e5;
    assertEquals(0, pipe.getTransientCoupledPressureMomentumRejectedSubsteps(), "no rejected coupled substeps");
    assertTrue(inletPressure > 280.0, "line must pack past the saturation pressure: " + inletPressure);
    assertTrue(lowestCellPressure > 200.0, "no cell pressure may collapse: " + lowestCellPressure);
    assertTrue(largestRiseAlongFlow < 30.0, "no pressure spike between neighbour cells: " + largestRiseAlongFlow);
  }

  /** The coldest-section cooldown time is no longer than the inlet-based cooldown time. */
  @Test
  void coldestSectionCooldownIsShorterThanInletBased() {
    TwoFluidPipe pipe = pipe(false);
    double inletBased = pipe.calculateCooldownTime(20.0, "C");
    double coldest = pipe.calculateColdestSectionCooldownTime(20.0, "C");
    assertTrue(coldest > 0.0 && coldest <= inletBased + 1e-9, "coldest " + coldest + " inlet " + inletBased);
  }
}
