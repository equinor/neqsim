package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.util.agentic.AgentBenchmarkSuite.BenchmarkProblem;
import neqsim.util.agentic.AgentBenchmarkSuite.BenchmarkReport;
import neqsim.util.agentic.AgentBenchmarkSuite.ProblemCategory;
import neqsim.util.agentic.AgentBenchmarkSuite.ProblemResult;
import neqsim.util.agentic.AgentBenchmarkSuite.Difficulty;

/**
 * Tests for {@link AgentBenchmarkSuite}.
 */
class AgentBenchmarkSuiteTest {

  private AgentBenchmarkSuite suite;

  @BeforeEach
  void setUp() {
    suite = new AgentBenchmarkSuite("Test Suite");
  }

  @Test
  void testCreateStandardSuiteHasProblems() {
    AgentBenchmarkSuite standard = AgentBenchmarkSuite.createStandardSuite();
    assertFalse(standard.getProblems().isEmpty());
    assertTrue(standard.getProblems().size() >= 10,
        "Standard suite should have at least 10 problems");
  }

  @Test
  void testStandardSuiteCoversAllCategories() {
    AgentBenchmarkSuite standard = AgentBenchmarkSuite.createStandardSuite();
    boolean hasThermo = false;
    boolean hasFlash = false;
    boolean hasProcess = false;
    boolean hasPipeline = false;
    boolean hasEconomics = false;
    boolean hasSafety = false;
    for (BenchmarkProblem p : standard.getProblems()) {
      switch (p.getCategory()) {
        case THERMO:
          hasThermo = true;
          break;
        case FLASH:
          hasFlash = true;
          break;
        case PROCESS:
          hasProcess = true;
          break;
        case PIPELINE:
          hasPipeline = true;
          break;
        case ECONOMICS:
          hasEconomics = true;
          break;
        case SAFETY:
          hasSafety = true;
          break;
      }
    }
    assertTrue(hasThermo, "Suite should have THERMO problems");
    assertTrue(hasFlash, "Suite should have FLASH problems");
    assertTrue(hasProcess, "Suite should have PROCESS problems");
    assertTrue(hasPipeline, "Suite should have PIPELINE problems");
    assertTrue(hasEconomics, "Suite should have ECONOMICS problems");
    assertTrue(hasSafety, "Suite should have SAFETY problems");
  }

  @Test
  void testProblemPassesWithinTolerance() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Test density", "kg/m3", 100.0, 5.0, "NIST"));
    suite.addResult("test1", 103.0); // 3% deviation, within 5%
    BenchmarkReport report = suite.evaluate();
    assertEquals(1, report.getPassed());
    assertEquals(0, report.getFailed());
    assertEquals(1.0, report.getPassRate(), 0.001);
  }

  @Test
  void testProblemFailsOutsideTolerance() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Test density", "kg/m3", 100.0, 5.0, "NIST"));
    suite.addResult("test1", 110.0); // 10% deviation, outside 5%
    BenchmarkReport report = suite.evaluate();
    assertEquals(0, report.getPassed());
    assertEquals(1, report.getFailed());
    assertEquals(0.0, report.getPassRate(), 0.001);
  }

  @Test
  void testNotAttemptedProblems() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Test density", "kg/m3", 100.0, 5.0, "NIST"));
    // No result submitted
    BenchmarkReport report = suite.evaluate();
    assertEquals(0, report.getPassed());
    assertEquals(0, report.getFailed());
    assertEquals(1, report.getNotAttempted());
  }

  @Test
  void testConvergenceFailure() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.FLASH, Difficulty.INTERMEDIATE,
        "Flash calc", "phases", 2.0, 0.1, "ref"));
    suite.addConvergenceResult("test1", false);
    BenchmarkReport report = suite.evaluate();
    assertEquals(0, report.getPassed());
    assertEquals(1, report.getFailed());
    List<ProblemResult> failedList = report.getFailedProblems();
    assertEquals(1, failedList.size());
    assertTrue(failedList.get(0).getDetail().contains("converge"));
  }

  @Test
  void testConvergenceOnlyPass() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.PROCESS, Difficulty.BASIC,
        "Sep mass balance", "%", 0.0, 0.1, "conservation"));
    suite.addConvergenceResult("test1", true);
    // No numerical result submitted, but converged
    BenchmarkReport report = suite.evaluate();
    assertEquals(1, report.getPassed());
    assertEquals("PASS_CONVERGENCE", report.getResults().get(0).getVerdict());
  }

  @Test
  void testMixedResults() {
    suite.addProblem(new BenchmarkProblem("p1", ProblemCategory.THERMO, Difficulty.BASIC, "Prob 1",
        "K", 373.15, 0.5, "NIST"));
    suite.addProblem(new BenchmarkProblem("p2", ProblemCategory.FLASH, Difficulty.INTERMEDIATE,
        "Prob 2", "phases", 2.0, 0.1, "ref"));
    suite.addProblem(new BenchmarkProblem("p3", ProblemCategory.PROCESS, Difficulty.ADVANCED,
        "Prob 3", "kW", 22.0, 10.0, "textbook"));

    suite.addResult("p1", 374.0); // ~0.23% deviation, passes at 0.5%
    suite.addResult("p2", 3.0); // 50% deviation, fails at 0.1%
    // p3 not attempted

    BenchmarkReport report = suite.evaluate();
    assertEquals(1, report.getPassed());
    assertEquals(1, report.getFailed());
    assertEquals(1, report.getNotAttempted());
    assertEquals(0.5, report.getPassRate(), 0.001);
  }

  @Test
  void testGetResultsByCategory() {
    suite.addProblem(new BenchmarkProblem("t1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Thermo 1", "K", 300.0, 1.0, "ref"));
    suite.addProblem(new BenchmarkProblem("f1", ProblemCategory.FLASH, Difficulty.BASIC, "Flash 1",
        "phases", 1.0, 0.1, "ref"));
    suite.addResult("t1", 300.5);
    suite.addResult("f1", 1.0);
    BenchmarkReport report = suite.evaluate();

    List<ProblemResult> thermoResults = report.getResultsByCategory(ProblemCategory.THERMO);
    assertEquals(1, thermoResults.size());
    assertEquals("t1", thermoResults.get(0).getProblem().getId());
  }

  @Test
  void testSuiteJsonSerialization() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Test density", "kg/m3", 100.0, 5.0, "NIST"));
    String json = suite.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Test Suite"));
    assertTrue(json.contains("test1"));
    assertTrue(json.contains("THERMO"));
  }

  @Test
  void testReportJsonSerialization() {
    suite.addProblem(new BenchmarkProblem("test1", ProblemCategory.THERMO, Difficulty.BASIC,
        "Test density", "kg/m3", 100.0, 5.0, "NIST"));
    suite.addResult("test1", 101.0);
    BenchmarkReport report = suite.evaluate();
    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("passRate"));
    assertTrue(json.contains("PASS"));
    assertTrue(json.contains("101.0"));
  }

  @Test
  void testProblemToMap() {
    BenchmarkProblem p = new BenchmarkProblem("id1", ProblemCategory.PIPELINE,
        Difficulty.INTERMEDIATE, "Pipe flow", "bar", 8.0, 15.0, "Beggs-Brill");
    java.util.Map<String, Object> map = p.toMap();
    assertEquals("id1", map.get("id"));
    assertEquals("PIPELINE", map.get("category"));
    assertEquals("INTERMEDIATE", map.get("difficulty"));
    assertEquals(8.0, map.get("expectedValue"));
  }

  @Test
  void testSuiteNameGetter() {
    assertEquals("Test Suite", suite.getSuiteName());
  }

  @Test
  void testBenchmarkProblemGetters() {
    BenchmarkProblem p = new BenchmarkProblem("x", ProblemCategory.ECONOMICS, Difficulty.ADVANCED,
        "NPV test", "MNOK", 614.5, 1.0, "DCF textbook");
    assertEquals("x", p.getId());
    assertEquals(ProblemCategory.ECONOMICS, p.getCategory());
    assertEquals(Difficulty.ADVANCED, p.getDifficulty());
    assertEquals("NPV test", p.getDescription());
    assertEquals("MNOK", p.getUnit());
    assertEquals(614.5, p.getExpectedValue(), 0.01);
    assertEquals(1.0, p.getTolerancePct(), 0.01);
    assertEquals("DCF textbook", p.getReferenceSource());
  }

  @Test
  void testConvergenceRate() {
    suite.addProblem(new BenchmarkProblem("p1", ProblemCategory.THERMO, Difficulty.BASIC, "Prob 1",
        "K", 300.0, 1.0, "ref"));
    suite.addProblem(new BenchmarkProblem("p2", ProblemCategory.FLASH, Difficulty.BASIC, "Prob 2",
        "phases", 1.0, 0.1, "ref"));
    suite.addResult("p1", 300.0);
    // p2 not attempted
    BenchmarkReport report = suite.evaluate();
    assertEquals(0.5, report.getConvergenceRate(), 0.001);
  }
}
