package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Pipeline branch regressions for independent transport-property state on sibling fluid clones.
 *
 * @author NeqSim
 * @version 1.0
 */
class PipeBeggsAndBrillsParallelCloneTest {
  /**
   * Runs a short pipe using a distinct inlet fluid and default transport-property models.
   *
   * @param fluid fluid assigned exclusively to this calculation
   * @return outlet pressure in bara and final liquid holdup
   */
  private double[] calculatePipe(SystemInterface fluid) {
    Stream inlet = new Stream("inlet", fluid);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", inlet);
    pipe.setLength(100.0);
    pipe.setDiameter(0.706);
    pipe.setPipeWallRoughness(1e-5);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(2);
    pipe.setRunIsothermal(true);
    pipe.run();
    return new double[] {pipe.getOutletStream().getPressure(), pipe.getSegmentLiquidHoldup(2)};
  }

  /**
   * Differing parallel branches match sequential pressure and holdup, with no global execution lock.
   *
   * @throws Exception if a worker calculation fails
   */
  @Test
  void parallelBranchesMatchSequentialPressureAndHoldup() throws Exception {
    SystemInterface template = new SystemPrEos(288.15, 36.0);
    template.addComponent("methane", 0.92);
    template.addComponent("ethane", 0.05);
    template.addComponent("n-decane", 0.02);
    template.addComponent("water", 0.01);
    template.setMixingRule("classic");
    template.setMultiPhaseCheck(true);
    template.setTotalFlowRate(15.0, "MSm3/day");
    new ThermodynamicOperations(template).TPflash();
    template.initProperties();
    List<SystemInterface> branchTemplates = new ArrayList<>();
    List<double[]> expected = new ArrayList<>();
    for (int branch = 0; branch < 4; branch++) {
      SystemInterface fluid = template.clone();
      fluid.setTemperature(278.15 + 25.0 * branch);
      fluid.setPressure(36.0 + 10.0 * branch);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initProperties();
      branchTemplates.add(fluid);
      expected.add(calculatePipe(fluid.clone()));
    }
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Void>> calculations = new ArrayList<>();
      for (int branch = 0; branch < branchTemplates.size(); branch++) {
        final SystemInterface fluid = branchTemplates.get(branch);
        final double[] baseline = expected.get(branch);
        calculations.add(() -> {
          for (int repeat = 0; repeat < 12; repeat++) {
            double[] actual = calculatePipe(fluid.clone());
            assertEquals(baseline[0], actual[0], 1e-10, "parallel outlet pressure in bara");
            assertEquals(baseline[1], actual[1], 1e-12, "parallel liquid holdup");
          }
          return null;
        });
      }
      for (Future<Void> result : executor.invokeAll(calculations)) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
