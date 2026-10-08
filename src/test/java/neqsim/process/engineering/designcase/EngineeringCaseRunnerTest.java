package neqsim.process.engineering.designcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.engineering.numerics.EngineeringNumericalHealthCriteria;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests deterministic isolated engineering-case execution. */
class EngineeringCaseRunnerTest {
  private static final Path GUIDE = Paths.get("docs", "engineering", "design-cases-and-envelopes.md");
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\R(.*?)\\R```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  @Test
  void sequentialAndParallelRunsProduceTheSameFingerprintWithoutMutatingBaseProcess() {
    ProcessSystem process = process();
    double basePressure = ((Stream) process.getUnit("FEED")).getPressure("bara");
    EngineeringCaseSet cases = new EngineeringCaseSet("pressure-envelope").addCase(caseAtPressure("normal", 50.0, 10))
        .addCase(caseAtPressure("maximum", 80.0, 20)).addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringCaseRunReport sequential = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.sequential());
    EngineeringCaseRunReport parallel = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.builder().parallelism(2).build());

    assertEquals(sequential.getDefinitionFingerprint(), parallel.getDefinitionFingerprint());
    assertEquals(sequential.getResultFingerprint(), parallel.getResultFingerprint());
    assertEquals(80.0, sequential.getEnvelope().getGoverningValues().get("FEED.pressure").getValue(), 1.0e-10);
    assertEquals(basePressure, ((Stream) process.getUnit("FEED")).getPressure("bara"), 1.0e-10);
    assertEquals(2, sequential.getEnvelope().getSuccessfulCaseCount());
    assertTrue(sequential.isComplete());
    assertFalse(sequential.isAccepted());
    assertEquals(1, sequential.getEnvelope().getUnassessedGoverningMetricIds().size());
    assertTrue(sequential.toJson().contains("isolatedProcessCopies"));
    assertNotEquals(sequential.getDefinitionFingerprint(), sequential.getResultFingerprint());
  }

  @Test
  void caseRunnerCanEmbedNumericalHealthReports() {
    ProcessSystem process = process();
    EngineeringCaseSet cases = new EngineeringCaseSet("health-report").addCase(caseAtPressure("normal", 50.0, 10))
        .addMetric(EngineeringMetric.equipmentPressure("FEED"));

    EngineeringCaseRunReport report = EngineeringCaseRunner.run(process, cases, EngineeringCaseRunOptions.builder()
        .numericalHealthCriteria(EngineeringNumericalHealthCriteria.defaults()).build());

    assertTrue(report.toJson().contains("numericalHealth"));
    assertEquals("HEALTHY", report.getEnvelope().getCaseResults().get(0).getNumericalHealthReport().getStatus().name());
  }

  @Test
  void incompleteEnvelopeCanReturnOrThrowWithTheSamePartialEvidence() {
    ProcessSystem process = process();
    EngineeringMetric nonFinite = new EngineeringMetric("FEED.invalid", "FEED", "Invalid metric", "fraction",
        EngineeringMetric.GoverningDirection.MAXIMUM, new EngineeringMetric.Extractor() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double extract(ProcessSystem ignored) {
            return Double.NaN;
          }
        });
    EngineeringCaseSet cases = new EngineeringCaseSet("incomplete").addCase(caseAtPressure("normal", 50.0, 10))
        .addMetric(nonFinite);

    EngineeringCaseRunReport partial = EngineeringCaseRunner.run(process, cases,
        EngineeringCaseRunOptions.sequential());

    assertFalse(partial.isComplete());
    assertEquals("FEED.invalid", partial.getEnvelope().getMissingGoverningMetricIds().get(0));
    EngineeringCaseExecutionException requireException = assertThrows(EngineeringCaseExecutionException.class,
        partial::requireComplete);
    assertSame(partial, requireException.getPartialReport());

    EngineeringCaseExecutionException executionException = assertThrows(EngineeringCaseExecutionException.class,
        () -> EngineeringCaseRunner.run(process, cases, EngineeringCaseRunOptions.builder()
            .failurePolicy(EngineeringCaseFailurePolicy.THROW_WITH_PARTIAL_RESULT).build()));
    assertFalse(executionException.getPartialReport().isComplete());
  }

  @Test
  void acceptanceRequiresConfiguredLimitsAndNoViolation() {
    ProcessSystem process = process();
    EngineeringCaseSet passing = new EngineeringCaseSet("passing-limits").addCase(caseAtPressure("normal", 50.0, 10))
        .addCase(caseAtPressure("maximum", 80.0, 20))
        .addMetric(EngineeringMetric.equipmentPressure("FEED").setAcceptanceRange(null, Double.valueOf(90.0)));
    EngineeringCaseSet failing = new EngineeringCaseSet("failing-limits").addCase(caseAtPressure("normal", 50.0, 10))
        .addCase(caseAtPressure("maximum", 80.0, 20))
        .addMetric(EngineeringMetric.equipmentPressure("FEED").setAcceptanceRange(null, Double.valueOf(70.0)));

    EngineeringCaseRunReport accepted = EngineeringCaseRunner.run(process, passing,
        EngineeringCaseRunOptions.sequential());
    EngineeringCaseRunReport violated = EngineeringCaseRunner.run(process, failing,
        EngineeringCaseRunOptions.sequential());

    assertTrue(accepted.isComplete());
    assertTrue(accepted.isAccepted());
    assertTrue(violated.isComplete());
    assertFalse(violated.isAccepted());
    assertEquals(1, violated.getEnvelope().getLimitViolationCount());
  }

  @Test
  void documentationExampleCompilesAndRunsWithAssertionsEnabled() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    Matcher matcher = JAVA_FENCE.matcher(markdown);
    assertTrue(matcher.find(), "Expected one Java fence in " + GUIDE);
    String source = matcher.group(1);
    assertFalse(matcher.find(), "Expected exactly one Java fence in " + GUIDE);
    assertFalse(markdown.contains("```python"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("PROCESS-DESIGN-BASIS-REV-A"));
    assertTrue(source.contains("feed pressure\", pressureBara, \"bara\""));
    assertTrue(source.contains("assert report.isComplete()"));
    assertTrue(source.contains("assert report.isAccepted()"));
    assertTrue(source.contains("sourcePressureBara"));

    Path sourceFile = temporaryDirectory.resolve("DesignCaseEnvelopeExample.java");
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A full JDK is required to compile the documentation example");
    int exitCode = compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"), "-source", "8",
        "-target", "8", "-d", temporaryDirectory.toString(), sourceFile.toString());
    assertEquals(0, exitCode, "The exact documentation fence must compile as Java 8");

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("DesignCaseEnvelopeExample", true, loader);
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException ex) {
        throw new AssertionError("The exact documentation fence must run with assertions enabled", ex.getCause());
      }
    }
  }

  private EngineeringDesignCase caseAtPressure(String id, final double pressureBara, int priority) {
    return new EngineeringDesignCase(id, id, EngineeringDesignCase.Type.CUSTOM,
        new EngineeringDesignCase.Configurator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void configure(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(pressureBara, "bara");
          }
        }).setPriority(priority)
        .addInput(new EngineeringDesignCase.Input("feedPressure", pressureBara, "bara", "DESIGN-BASIS-A"));
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    return process;
  }
}
