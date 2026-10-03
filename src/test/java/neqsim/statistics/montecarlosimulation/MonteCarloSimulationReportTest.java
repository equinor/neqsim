package neqsim.statistics.montecarlosimulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.StatisticsBaseClass;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * Regression coverage for public Monte Carlo report access and the documented matrix layout.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
class MonteCarloSimulationReportTest {
  /** Temporary output for compilation of the actual documentation snippets. */
  @TempDir
  Path output;

  /**
   * Compile and execute the report-access and analysis snippets against deterministic parameter samples.
   *
   * @throws Exception if a documented snippet cannot be compiled or executed
   */
  @Test
  void documentedReportAnalysisCompilesAndRuns() throws Exception {
    String guide = new String(Files.readAllBytes(Paths.get("docs/statistics/monte_carlo_simulation.md")),
        StandardCharsets.UTF_8);
    String workflow = snippet(guide, "### Method 2: Direct MonteCarloSimulation");
    String layout = snippet(guide, "### Report Matrix Structure").replace("double[][] results = mc.getReportMatrix();",
        "");
    String calculations = workflow + layout + snippet(guide, "### Computing Statistics")
        + "check(50.5, means[0]); check(505.0, means[1]); check(Math.sqrt(833.25), stds[0]);" + "{ int j = 0; "
        + snippet(guide, "### Percentile-Based Intervals") + "check(3.0, lower); check(98.0, upper); }"
        + "{ int i = 0; int j = 1; " + snippet(guide, "### Parameter Correlation") + "check(1.0, correlation); }";
    compileAndRun(calculations, "ReportAnalysis");
    String histogram = snippet(guide, "### Example 3: Analyzing Parameter Distribution");
    histogram = histogram.substring(0, histogram.indexOf("// Print histogram"));
    compileAndRun(histogram + "check(1000.0, java.util.Arrays.stream(histogram).sum());"
        + "check(1.0, minVal); check(1000.0, maxVal);", "ReportHistogram");
  }

  /**
   * Read the first Java block following a guide heading.
   *
   * @param guide full guide text
   * @param heading heading owning the snippet
   * @return Java source without import declarations
   */
  private String snippet(String guide, String heading) {
    int start = guide.indexOf("```java\n", guide.indexOf(heading)) + "```java\n".length();
    return guide.substring(start, guide.indexOf("```", start)).replaceAll("(?m)^import [^;]+;\\s*", "");
  }

  /**
   * Compile and invoke a documentation example, preserving its original calculation statements.
   *
   * @param snippet example statements and independent numerical assertions
   * @param name generated class name
   * @throws Exception if compilation or execution fails
   */
  private void compileAndRun(String snippet, String name) throws Exception {
    String source = "import java.util.Arrays; import neqsim.statistics.montecarlosimulation.MonteCarloSimulation;"
        + "import neqsim.statistics.parameterfitting.StatisticsBaseClass; public class " + name
        + " { public static void run(StatisticsBaseClass optimizer) { " + snippet + " }"
        + "private static void check(double expected, double actual) {"
        + "if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9)"
        + " throw new AssertionError(expected + \" != \" + actual); } }";
    Path file = output.resolve(name + ".java");
    Files.write(file, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation validation requires a JDK");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean success = compiler.getTask(diagnostics, manager, null,
          Arrays.asList("-source", "8", "-target", "8", "-classpath",
              System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), "-d",
              output.toString()),
          null, manager.getJavaFileObjects(file.toFile())).call();
      assertEquals(Boolean.TRUE, success, diagnostics.toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, getClass().getClassLoader())) {
      loader.loadClass(name).getMethod("run", StatisticsBaseClass.class).invoke(null, new DeterministicFit(0));
    }
  }

  /** Verify callers get a clear error before a report is available. */
  @Test
  void rejectsReadingBeforeSimulation() {
    assertThrows(IllegalStateException.class, () -> new MonteCarloSimulation().getReportMatrix());
  }

  /** Verify the documented public workflow and that snapshots cannot corrupt later reads. */
  @Test
  void readsIndependentParameterByRunSnapshots() {
    DeterministicFit optimizer = new DeterministicFit(0);
    MonteCarloSimulation mc = new MonteCarloSimulation(optimizer);
    mc.setNumberOfRuns(3);
    mc.runSimulation();
    double[][] results = mc.getReportMatrix();
    int numRuns = results[0].length;
    int numParams = optimizer.getSampleSet().getSample(0).getFunction().getNumberOfFittingParams();

    assertEquals(3, numRuns);
    assertEquals(2, numParams);
    assertEquals(10, results.length);
    assertArrayEquals(new double[] {0.0, 1.0, 2.0}, results[0], 0.0);
    assertArrayEquals(new double[] {1.0, 2.0, 3.0}, results[1], 0.0);
    assertArrayEquals(new double[] {10.0, 20.0, 30.0}, results[2], 0.0);
    assertArrayEquals(new double[3], results[3], 0.0);

    // The guide uses row j + 1 for parameter j and column i for run i.
    double[] means = new double[numParams];
    for (int j = 0; j < numParams; j++) {
      for (int i = 0; i < numRuns; i++) {
        means[j] += results[j + 1][i] / numRuns;
      }
    }
    assertArrayEquals(new double[] {2.0, 20.0}, means, 1.0e-12);

    results[1][0] = -100.0;
    results[2] = new double[] {-1.0};
    double[][] reread = mc.getReportMatrix();
    assertArrayEquals(new double[] {1.0, 2.0, 3.0}, reread[1], 0.0);
    assertArrayEquals(new double[] {10.0, 20.0, 30.0}, reread[2], 0.0);

    mc.runSimulation();
    assertArrayEquals(new double[] {4.0, 5.0, 6.0}, mc.getReportMatrix()[1], 0.0);
    assertArrayEquals(new double[] {1.0, 2.0, 3.0}, reread[1], 0.0);
  }

  /** Deterministic completed fits isolate report access from random sampling and optimizer convergence. */
  private static class DeterministicFit extends LevenbergMarquardt {
    private int nextRun;

    /**
     * Create a fit with two known parameters.
     *
     * @param run run index used to set the parameter values
     */
    DeterministicFit(int run) {
      LevenbergMarquardtFunction function = new LevenbergMarquardtFunction();
      function.setInitialGuess(new double[] {run, 10.0 * run});
      SampleValue sample = new SampleValue(1.0, 0.1, new double[] {1.0});
      sample.setFunction(function);
      setSampleSet(new SampleSet(new SampleValue[] {sample}));
    }

    /** {@inheritDoc} */
    @Override
    public StatisticsBaseClass createNewRandomClass() {
      return new DeterministicFit(++nextRun);
    }

    /** The fixture already holds its completed fit. */
    @Override
    public void init() {
    }

    /** The fixture already holds its completed fit. */
    @Override
    public void solve() {
    }
  }
}
