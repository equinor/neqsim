package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NetworkValidationBenchmarks}.
 *
 * <p>
 * Runs all analytical benchmarks and verifies pass/fail status.
 * </p>
 */
class NetworkValidationBenchmarkTest {

  @Test
  void testSinglePipeBenchmark() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runSinglePipeBenchmark();
    assertNotNull(result);
    assertTrue(result.converged, "Single pipe benchmark should converge. " + result.getSummary());
  }

  @Test
  void testParallelPipeBenchmark() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runParallelPipeBenchmark();
    assertNotNull(result);
    assertTrue(result.converged, "Parallel pipe benchmark should converge. " + result.getSummary());
  }

  @Test
  void testTriangleMassBalance() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runTriangleMassBalance();
    assertNotNull(result);
    assertTrue(result.converged, "Triangle mass balance should converge. " + result.getSummary());
  }

  @Test
  void testSolverCrossVerification() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runSolverCrossVerification();
    assertNotNull(result);
    assertTrue(result.converged,
        "Solver cross-verification should converge. " + result.getSummary());
  }

  @Test
  void testPressureMonotonicity() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runPressureMonotonicity();
    assertNotNull(result);
    assertTrue(result.converged, "Pressure monotonicity should converge. " + result.getSummary());
  }

  @Test
  void testSparseVsDenseBenchmark() {
    NetworkValidationBenchmarks.BenchmarkResult result =
        NetworkValidationBenchmarks.runSparseVsDenseBenchmark();
    assertNotNull(result);
    assertTrue(result.converged, "Sparse vs Dense should agree. " + result.getSummary());
  }

  @Test
  void testRunAllBenchmarks() {
    List<NetworkValidationBenchmarks.BenchmarkResult> results =
        NetworkValidationBenchmarks.runAllBenchmarks();
    assertFalse(results.isEmpty(), "Should have benchmark results");
    assertEquals(6, results.size(), "Should have 6 benchmarks");

    for (NetworkValidationBenchmarks.BenchmarkResult r : results) {
      assertNotNull(r.name);
      assertFalse(r.metrics.isEmpty(), "Benchmark " + r.name + " should have metrics");
    }
  }

  @Test
  void testRunValidationBenchmarksFromNetwork() {
    // Verify the static convenience method on LoopedPipeNetwork
    List<NetworkValidationBenchmarks.BenchmarkResult> results =
        LoopedPipeNetwork.runValidationBenchmarks();
    assertNotNull(results);
    assertFalse(results.isEmpty());
  }
}
