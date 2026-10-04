package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * Accuracy gate for the agentic benchmark suite.
 *
 * <p>
 * The suite only declared problems until now; nothing compared NeqSim against the reference data, so an accuracy
 * regression could reach an engineering task unnoticed. This test executes the suite.
 * </p>
 *
 * <p>
 * Only problems with an independently sourced reference value are asserted on. Reference values come from CoolProp HEOS
 * via devtools/source_benchmark_references.py. The two problems still marked UNVERIFIED are underdetermined - the
 * problem statement omits an input that determines the answer - so they are reported but not asserted on, because
 * failing them would report a defect in the reference data as a defect in NeqSim.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AgentBenchmarkRunnerTest {
  private static final Logger logger = LogManager.getLogger(AgentBenchmarkRunnerTest.class);

  /** Minimum number of verified-reference problems that must execute. */
  private static final int MIN_VERIFIED_ATTEMPTED = 8;

  /** Where the summary is written for the CI job summary to publish. */
  private static final String SUMMARY_FILE = "target/agent_benchmark_summary.txt";

  /** Every problem with a sourced reference value must reproduce it. */
  @Test
  public void verifiedReferenceProblemsPass() {
    AgentBenchmarkSuite.BenchmarkReport report = AgentBenchmarkRunner.runStandardSuite();
    assertNotNull(report);
    String summary = AgentBenchmarkRunner.summarize(report);
    logger.info("Agent benchmark: {}", summary);
    writeSummary(summary);

    List<String> failures = new ArrayList<String>();
    int attempted = 0;
    for (AgentBenchmarkSuite.ProblemResult result : report.getResults()) {
      if (!AgentBenchmarkRunner.isVerifiedReference(result.getProblem())) {
        continue;
      }
      if ("NOT_ATTEMPTED".equals(result.getVerdict())) {
        continue;
      }
      attempted++;
      if (!result.isPassed()) {
        failures.add(result.getProblem().getId() + ": " + result.getDetail());
      }
    }

    assertTrue(attempted >= MIN_VERIFIED_ATTEMPTED,
        "only " + attempted + " verified-reference problems executed, expected at least " + MIN_VERIFIED_ATTEMPTED);
    assertTrue(failures.isEmpty(), "verified benchmark problems failed: " + failures);
  }

  /**
   * Writes the summary where CI can read it.
   *
   * <p>
   * The logger output does not reach Maven's console under the test logging configuration, so the CI job summary reads
   * this file instead of grepping the build log.
   * </p>
   *
   * @param summary the text to write
   */
  private static void writeSummary(String summary) {
    File file = new File(SUMMARY_FILE);
    File parent = file.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      logger.warn("could not create {}", parent);
      return;
    }
    Writer writer = null;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
      writer.write(summary);
      writer.write("\n");
    } catch (IOException ex) {
      logger.warn("could not write {}: {}", SUMMARY_FILE, ex.getMessage());
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException ex) {
          logger.warn("could not close {}: {}", SUMMARY_FILE, ex.getMessage());
        }
      }
    }
  }

  /** Unverified reference values must stay visible so they get re-sourced, not silently dropped. */
  @Test
  public void unverifiedReferencesAreDeclared() {
    AgentBenchmarkSuite suite = AgentBenchmarkSuite.createStandardSuite();
    int unverified = 0;
    for (AgentBenchmarkSuite.BenchmarkProblem problem : suite.getProblems()) {
      if (!AgentBenchmarkRunner.isVerifiedReference(problem)) {
        unverified++;
        logger.info("UNVERIFIED benchmark reference: {} ({})", problem.getId(), problem.getReferenceSource());
      }
    }
    assertTrue(unverified > 0, "expected the suite to declare which reference values are still unsourced");
  }

  /** A zero expected value must be comparable, otherwise conservation checks can never pass. */
  @Test
  public void zeroExpectedValueUsesAbsoluteTolerance() {
    AgentBenchmarkSuite suite = new AgentBenchmarkSuite("zero-expected");
    suite.addProblem(new AgentBenchmarkSuite.BenchmarkProblem("closure", AgentBenchmarkSuite.ProblemCategory.PROCESS,
        AgentBenchmarkSuite.Difficulty.BASIC, "Mass balance closure", "%", 0.0, 0.1, "Conservation of mass"));
    suite.addResult("closure", 1.0e-11);

    AgentBenchmarkSuite.BenchmarkReport report = suite.evaluate();
    assertTrue(report.getResults().get(0).isPassed(),
        "a residual of 1e-11 against an expected 0 must pass: " + report.getResults().get(0).getDetail());
  }

  /** The report must serialize so CI and agents can consume it. */
  @Test
  public void reportSerializesToJson() {
    AgentBenchmarkSuite.BenchmarkReport report = AgentBenchmarkRunner.runStandardSuite();
    String json = report.toJson();
    assertNotNull(json);
    assertTrue(json.contains("passRate"), "report JSON should expose passRate: " + json);
  }
}
