package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles and executes the actual complete Java examples published in the optimization guides. */
class OptimizationGuideExamplesTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void batchStudyExampleExecutesEveryCase() throws Exception {
    runExample("batch-studies.md", "### Basic Usage", "BatchGuideExample",
        "if (result.getSuccessCount() != 30 || result.getFailureCount() != 0) {"
            + " throw new AssertionError(\"Expected 30 successful documented batch cases\"); }");
  }

  @Test
  void multiObjectiveExampleProducesFeasiblePowerThroughputTradeoffs() throws Exception {
    runExample("multi-objective-optimization.md", "### Example 1: Basic Throughput vs Power Optimization",
        "ParetoGuideExample",
        "if (front.size() < 2) { throw new AssertionError(\"No tradeoff front\"); }"
            + "for (ParetoSolution solution : front) { if (!solution.isFeasible()"
            + " || solution.getRawValue(0) <= 0 || solution.getRawValue(1) <= 0) {"
            + " throw new AssertionError(\"Invalid Pareto operating point\"); } }");
  }

  @Test
  void pressureBoundaryExampleFindsAFeasibleOperatingPoint() throws Exception {
    runExample("flow-rate-optimization.md", "### Basic Flow Rate Calculation", "FlowGuideExample",
        "if (result == null || !result.isFeasible() || result.getFlowRate() <= 0"
            + " || result.getTotalPower() <= 0) { throw new AssertionError(\"No feasible capacity\"); }");
  }

  @Test
  void algebraicSqpExampleReachesTheAnalyticalSolution() throws Exception {
    runExample("sqp_optimizer.md", "## Basic Usage", "SqpAlgebraGuideExample",
        "if (!result.isConverged() || Math.abs(result.getOptimalPoint()[0] - 1.5) > 1e-5"
            + " || Math.abs(result.getOptimalPoint()[1] - 2.5) > 1e-5) {"
            + " throw new AssertionError(\"Incorrect analytical constrained optimum\"); }");
  }

  @Test
  void compressorSqpExampleRestoresTheOptimalProcess() throws Exception {
    runExample("sqp_optimizer.md", "## Process Optimization Example", "SqpProcessGuideExample",
        "if (!result.isConverged() || result.getOptimalPoint()[0] <= 8.0" + " || result.getOptimalPoint()[0] >= 60.0) {"
            + " throw new AssertionError(\"Compressor optimum did not converge\"); }"
            + "double restoredPower = comp1.getPower(\"kW\") + comp2.getPower(\"kW\");"
            + "if (Math.abs(restoredPower - result.getOptimalValue()) > 0.1) {"
            + " throw new AssertionError(\"Process was not restored to the optimum\"); }");
  }

  @Test
  void synthesisExampleProducesAFeasibleProcess() throws Exception {
    runExample("process-researcher.md", "## Java Example", "ResearchGuideExample",
        "if (best.getProcessSystem() == null) { throw new AssertionError(\"No simulated candidate\"); }");
  }

  @Test
  void steadyStateExampleReachesReconciliation() throws Exception {
    runExample("data-reconciliation.md", "### SSD Java Example", "SteadyStateGuideExample",
        "if (!ssResult.isAtSteadyState()) { throw new AssertionError(\"SSD example never became steady\"); }");
  }

  @Test
  void completeReconciliationExampleRuns() throws Exception {
    runExample("data-reconciliation.md", "## Complete Java Example", "SeparatorReconciliation", "");
  }

  @Test
  void designFrameworkExampleStartsInsideTheMapAndRetainsFeasibleCapacity() throws Exception {
    runExample("../DESIGN_FRAMEWORK.md", "### Optimization with Multiple Equipment Types", "DesignCapacityGuideExample",
        "if (!result.isFeasible() || result.getBottleneckUtilization() > 0.95001"
            + " || !(comp.getPower(\"kW\") > 0.0)) {"
            + " throw new AssertionError(\"Invalid accepted compressor operating point\"); }"
            + "if (Math.abs(feed.getFlowRate(\"kg/hr\") - pipe.getOutletStream().getFlowRate(\"kg/hr\")) > 1e-6) {"
            + " throw new AssertionError(\"Accepted process does not conserve mass: \" + feed.getFlowRate(\"kg/hr\") + \" vs \" + pipe.getOutletStream().getFlowRate(\"kg/hr\")); }");
  }

  private void runExample(String document, String heading, String className, String checks) throws Exception {
    Path guide = Paths.get("docs", "process", "optimization", document);
    String markdown = new String(Files.readAllBytes(guide), StandardCharsets.UTF_8);
    int headingStart = markdown.indexOf(heading + "\n");
    assertTrue(headingStart >= 0, "Missing example heading: " + heading);
    Matcher fence = Pattern.compile("```java\\R(.*?)\\R```", Pattern.DOTALL).matcher(markdown.substring(headingStart));
    assertTrue(fence.find(), "Missing Java example: " + heading);
    String code = fence.group(1);
    Path sourceDirectory = temporaryDirectory.resolve(className);
    Files.createDirectories(sourceDirectory);
    code = code.replace("\"batch_results.csv\"",
        "\"" + sourceDirectory.resolve("batch_results.csv").toString().replace("\\", "\\\\") + "\"");

    String source;
    if (code.contains("public class " + className)) {
      source = code;
    } else {
      Matcher imports = Pattern.compile("^import .*?;", Pattern.MULTILINE).matcher(code);
      StringBuilder importLines = new StringBuilder();
      while (imports.find()) {
        importLines.append(imports.group()).append('\n');
      }
      String body = imports.replaceAll("");
      source = importLines + "\npublic class " + className + " {\n"
          + "private static final org.apache.logging.log4j.Logger logger = "
          + "org.apache.logging.log4j.LogManager.getLogger(" + className + ".class);\n"
          + "public static void main(String[] args) throws Exception {\n" + body + "\n" + checks + "\n}\n}";
    }
    Path sourceFile = sourceDirectory.resolve(className + ".java");
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "The documentation execution gate requires a JDK");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units = manager.getJavaFileObjects(sourceFile.toFile());
      boolean compiled = compiler.getTask(null, manager, diagnostics,
          Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d", sourceDirectory.toString()),
          null, units).call();
      assertTrue(compiled, document + " / " + heading + ": " + diagnostics.getDiagnostics());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] { sourceDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      loader.loadClass(className).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }
}
