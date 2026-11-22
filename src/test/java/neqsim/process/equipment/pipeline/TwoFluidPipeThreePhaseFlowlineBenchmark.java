package neqsim.process.equipment.pipeline;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Replays the three-phase 5.4 km flowline and riser used to profile the steady solver.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TwoFluidPipeThreePhaseFlowlineBenchmark {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeThreePhaseFlowlineBenchmark.class);

  /** Prevents instantiation of the benchmark launcher. */
  private TwoFluidPipeThreePhaseFlowlineBenchmark() {
  }

  /**
   * Runs two warm-up and five measured fresh-pipe steady solves on each mesh.
   *
   * @param args unused command-line arguments
   */
  public static void main(String[] args) {
    Stream inlet = createInlet();
    for (int refinement : new int[] {1, 2}) {
      double[] seconds = new double[5];
      for (int sample = -2; sample < seconds.length; sample++) {
        TwoFluidPipe pipe = createPipe(inlet, refinement);
        long start = System.nanoTime();
        pipe.run();
        double elapsed = (System.nanoTime() - start) / 1.0e9;
        if (!pipe.isSteadyStateConverged()) {
          throw new IllegalStateException(pipe.getSteadyStateConvergenceReport().toString());
        }
        if (sample >= 0) {
          seconds[sample] = elapsed;
        }
        logger.info(
            "cells={} sample={} seconds={} iterations={} arrivalBara={} oilHoldup={} waterHoldup={} outletKgS={}",
            60 * refinement, sample, elapsed, pipe.getSteadyStateIterationsUsed(), pipe.getOutletPressure("bara"),
            Arrays.stream(pipe.getOilHoldupProfile()).average().getAsDouble(),
            Arrays.stream(pipe.getWaterHoldupProfile()).average().getAsDouble(),
            pipe.getOutletStream().getFlowRate("kg/sec"));
        TwoFluidSection[] sections = pipe.getSectionSnapshots();
        TwoFluidSection outletCell = sections[sections.length - 1];
        SteadyStateConvergenceReport residual = pipe.getSteadyStateConvergenceReport();
        logger.info("lastCellKgS gas={} oil={} water={} residual momentum={} holdup={} split={} thermo={} mass={}",
            outletCell.getGasMassPerLength() * outletCell.getGasVelocity(),
            outletCell.getOilMassPerLength() * outletCell.getOilVelocity(),
            outletCell.getWaterMassPerLength() * outletCell.getWaterVelocity(), residual.getPressureMomentumResidual(),
            residual.getLiquidHoldupResidual(), residual.getLiquidSplitResidual(), residual.getThermodynamicResidual(),
            residual.getMassFluxResidual());
      }
      Arrays.sort(seconds);
      logger.info("cells={} medianSeconds={}", 60 * refinement, seconds[seconds.length / 2]);
    }
  }

  /**
   * Builds the flashed synthetic feed at 70 bara, 35 degrees Celsius and 20 kg/s.
   *
   * @return initialized SRK feed stream
   */
  static Stream createInlet() {
    SystemInterface fluid = new SystemSrkEos(308.15, 70.0);
    String[] names = {"methane", "ethane", "propane", "n-pentane", "n-heptane", "n-decane", "water"};
    double[] fractions = {0.42, 0.04, 0.03, 0.07, 0.07, 0.05, 0.32};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], fractions[i]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("Wellhead", fluid);
    inlet.setFlowRate(20.0, "kg/sec");
    inlet.run();
    return inlet;
  }

  /**
   * Builds the isothermal undulating flowline with explicit physical face elevations.
   *
   * @param inlet feed stream
   * @param refinement cells per coarse-mesh cell
   * @return uninitialized pipe
   */
  static TwoFluidPipe createPipe(Stream inlet, int refinement) {
    double[] legLengths = {800, 900, 850, 1100, 1350, 400};
    double[] elevations = {-260, -278, -252, -289, -270, -280, 25};
    int[] legCells = {8, 9, 9, 11, 13, 10};
    double[] lengths = new double[60 * refinement];
    double[] faces = new double[lengths.length + 1];
    faces[0] = elevations[0];
    int index = 0;
    for (int leg = 0; leg < legCells.length; leg++) {
      int count = legCells[leg] * refinement;
      for (int cell = 1; cell <= count; cell++) {
        lengths[index] = legLengths[leg] / count;
        faces[++index] = elevations[leg] + (double) cell / count * (elevations[leg + 1] - elevations[leg]);
      }
    }
    TwoFluidPipe pipe = new TwoFluidPipe("Flowline and riser", inlet);
    pipe.setLength(5400.0);
    pipe.setDiameter(0.2032);
    pipe.setRoughness(4.5e-5);
    pipe.setSectionLengths(lengths);
    pipe.setCellFaceElevationProfile(faces);
    pipe.setEnableJouleThomson(false);
    pipe.setEnableWaterOilSlip(true);
    return pipe;
  }
}
