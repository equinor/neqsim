package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalculatorAntiSurgeTest {
  private static final class SimulationResult {
    private final int iterations;
    private final double finalRecycleFlow;
    private final double finalError;

    private SimulationResult(int iterations, double finalRecycleFlow, double finalError) {
      this.iterations = iterations;
      this.finalRecycleFlow = finalRecycleFlow;
      this.finalError = finalError;
    }
  }

  @Test
  void newControllerConvergesFasterAndCloserToTargetThanLegacyAlgorithm() {
    double baseProcessFlow = 4.5; // MSm3/day handled by the process without recycle
    double surgeFlowM3PerHour = 260000.0; // compressor surge line reference
    double surgeControlFactor = 1.07; // safety factor applied in both controllers
    double initialRecycle = 0.2;
    double tolerance = 1.0e-3;
    int maxIterations = 50;

    SimulationResult modern = simulateController(baseProcessFlow, initialRecycle, surgeFlowM3PerHour,
        surgeControlFactor, tolerance, maxIterations, true);
    SimulationResult legacy = simulateController(baseProcessFlow, initialRecycle, surgeFlowM3PerHour,
        surgeControlFactor, tolerance, maxIterations, false);

    assertTrue(modern.iterations <= 10,
        "modern controller should settle on the target recycle flow within a handful of iterations");
    assertTrue(modern.iterations < legacy.iterations,
        "modern controller must reach the target recycle flow faster than the legacy algorithm");
    assertTrue(modern.finalError < tolerance,
        "modern controller should converge to the surge line recycle requirement");
    assertTrue(legacy.finalError > 0.3,
        "legacy controller remains far from the surge line target, demonstrating poor stability");
  }

  private static SimulationResult simulateController(double baseProcessFlow, double initialRecycle,
      double surgeFlowM3PerHour, double surgeControlFactor, double tolerance, int maxIterations,
      boolean useModernController) {
    double recycle = initialRecycle;
    double surgeFlowMSm3PerDay = surgeFlowM3PerHour * 24.0 / 1.0e6;
    double targetTotalFlow = surgeFlowMSm3PerDay * surgeControlFactor;
    double targetRecycle = Math.max(0.0, targetTotalFlow - baseProcessFlow);

    int iterationsToTolerance = maxIterations + 1;
    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      double inletFlow = baseProcessFlow + recycle;
      double nextRecycle;
      if (useModernController) {
        nextRecycle = Calculator.calculateAntiSurgeRecycleFlow(inletFlow, recycle, surgeFlowM3PerHour,
            surgeControlFactor, 0.6);
      } else {
        double distanceToSurge = inletFlow / surgeFlowMSm3PerDay - 1.0;
        nextRecycle = legacyUpdate(recycle, inletFlow, distanceToSurge);
      }

      recycle = nextRecycle;
      if (iterationsToTolerance == maxIterations + 1
          && Math.abs(recycle - targetRecycle) < tolerance) {
        iterationsToTolerance = iteration;
      }
    }

    double finalError = Math.abs(recycle - targetRecycle);
    return new SimulationResult(iterationsToTolerance, recycle, finalError);
  }

  private static double legacyUpdate(double currentRecycle, double inletFlow, double distanceToSurge) {
    double flow = currentRecycle - inletFlow * distanceToSurge * 0.5;
    double inletCapacity = Math.max(1e-6, inletFlow);
    if (flow > inletCapacity) {
      flow = inletCapacity * 0.99;
    }
    if (flow < 1e-6) {
      flow = 1e-6;
    }
    return flow;
  }
}

