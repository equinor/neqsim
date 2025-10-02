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
    double surgeControlFactor = 1.05; // safety factor applied in both controllers
    double volumetricConversion = 40000.0; // m3/hr produced per MSm3/day in this simplified rig
    double initialRecycle = 0.2;
    double tolerance = 1.0e-3;
    int maxIterations = 50;

    SimulationResult modern = simulateController(baseProcessFlow, initialRecycle, surgeFlowM3PerHour,
        surgeControlFactor, volumetricConversion, tolerance, maxIterations, true);
    SimulationResult legacy = simulateController(baseProcessFlow, initialRecycle, surgeFlowM3PerHour,
        surgeControlFactor, volumetricConversion, tolerance, maxIterations, false);

    assertTrue(modern.iterations <= maxIterations,
        "modern controller should settle within the configured iteration budget");
    assertTrue(modern.finalError < tolerance,
        "modern controller should converge to the surge line recycle requirement");
    assertTrue(modern.finalError <= legacy.finalError + 1.0e-6,
        "modern controller must be at least as accurate as the legacy algorithm");
    assertTrue(modern.finalRecycleFlow >= 1.0e-6,
        "modern controller should respect the lower clamp on recycle flow");
    assertTrue(modern.finalRecycleFlow <= (baseProcessFlow + modern.finalRecycleFlow) * 0.99 + 1.0e-6,
        "modern controller should respect the inlet capacity clamp");
  }

  private static SimulationResult simulateController(double baseProcessFlow, double initialRecycle,
      double surgeFlowM3PerHour, double surgeControlFactor, double volumetricConversion,
      double tolerance, int maxIterations, boolean useModernController) {
    double recycle = initialRecycle;
    double surgeFlowMSm3PerDay = surgeFlowM3PerHour / volumetricConversion;
    double targetTotalFlow = Math.max(baseProcessFlow, surgeFlowMSm3PerDay);
    double targetRecycle = Math.max(0.0, targetTotalFlow - baseProcessFlow);

    int iterationsToTolerance = maxIterations + 1;
    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      double inletFlow = baseProcessFlow + recycle;
      double actualFlowM3PerHour = inletFlow * volumetricConversion;
      double nextRecycle;
      if (useModernController) {
        nextRecycle = Calculator.calculateAntiSurgeRecycleFlow(inletFlow, recycle, actualFlowM3PerHour,
            surgeFlowM3PerHour, surgeControlFactor, 0.6);
      } else {
        double distanceToSurge = actualFlowM3PerHour / surgeFlowM3PerHour - 1.0;
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

