package neqsim.process.mechanicaldesign.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the published pump documentation examples. */
public class PumpDocumentationTest {
  private static final String QUICK_REFERENCE = "docs/wiki/pump_usage_guide.md";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  public void testPumpAndApi610GuideExample() {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("n-hexane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("pump feed", fluid);
    feed.setFlowRate(100.0, "m3/hr");
    feed.run();

    Pump pump = new Pump("P-100", feed);
    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{50.0, 75.0, 100.0, 125.0, 150.0}};
    double[][] head = new double[][] {{120.0, 115.0, 105.0, 90.0, 70.0}};
    double[][] efficiency = new double[][] {{65.0, 75.0, 82.0, 78.0, 68.0}};
    double[][] npshRequired = new double[][] {{2.0, 2.4, 3.0, 4.0, 5.5}};

    pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setNPSHCurve(npshRequired);
    pump.setSpeed(1000.0);
    pump.setCheckNPSH(true);
    pump.setNPSHMargin(1.15);
    pump.run();

    double powerKw = pump.getPower("kW");
    double vendorHeadM = pump.getPumpChart().getHead(feed.getFlowRate("m3/hr"), pump.getSpeed());
    double npshAvailableM = pump.getNPSHAvailable();
    double npshRequiredM = pump.getNPSHRequired();

    assertTrue(powerKw > 0.0);
    assertEquals(100.0, feed.getFlowRate("m3/hr"), 1.0e-9);
    assertEquals(105.0, vendorHeadM, 1.0e-9);
    assertTrue(Double.isFinite(npshAvailableM));
    assertTrue(npshAvailableM > npshRequiredM);
    assertEquals(3.0, npshRequiredM, 0.02);
    assertFalse(pump.isCavitating());

    PumpMechanicalDesign design = pump.getMechanicalDesign();
    design.setApi610PumpType(PumpApi610DesignCalculator.Api610PumpType.OH2);
    design.setMaximumSuctionPressure(8.0);
    design.setFurnishedCasingMawp(25.0);
    design.calcDesign();

    PumpApi610DesignCalculator assessment = design.getApi610Assessment();
    PumpApi610DesignCalculator.AssessmentStatus status = assessment.getAssessmentStatus();
    String responseJson = design.getResponse().toJson();
    JsonObject responseObject = JsonParser.parseString(responseJson).getAsJsonObject();

    assertNotNull(status);
    assertEquals(DataSource.VENDOR_CURVE, assessment.getBepSource());
    assertEquals(DataSource.VENDOR_CURVE, assessment.getNpshrSource());
    assertEquals("POR", assessment.getOperatingRegion());
    assertEquals(1.0, assessment.getOperatingFlowRatio(), 0.05);
    assertTrue(assessment.getSelectedDriverPowerKw() > powerKw);
    assertTrue(assessment.getRequiredCasingPressureBara() <= 25.0);
    assertFalse(assessment.getChecks().isEmpty());
    assertTrue(responseObject.has("api610Screening"));
    assertTrue(responseObject.has("api610TypeCode"));
    assertEquals("OH2", responseObject.get("api610TypeCode").getAsString());
  }

  @Test
  public void testQuickReferenceProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(QUICK_REFERENCE));
    Matcher fences = JAVA_FENCE.matcher(guide);

    assertFalse(guide.contains("# Pump Usage Guide - Quick Reference"));
    assertFalse(guide.contains("```python"));
    assertTrue(fences.find(), "Quick reference must contain one complete Java program");
    String source = fences.group(1);
    assertTrue(source.contains("public final class PumpUsageGuideExample"));
    assertTrue(source.contains("LogManager.getLogger(PumpUsageGuideExample.class)"));
    assertTrue(source.contains("setFlowRate(100.0, \"m3/hr\")"));
    assertTrue(source.contains("setHeadUnit(\"meter\")"));
    assertTrue(source.contains("setNPSHCurve(npshRequiredM)"));
    assertTrue(source.contains("setNPSHMargin(1.15)"));
    assertTrue(source.contains("assert Double.isFinite(powerKw) && powerKw > 0.0"));
    assertTrue(source.contains("assert npshAvailableM > requiredNpshM"));
    assertTrue(source.contains("assert !pump.isCavitating()"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "Quick reference must contain exactly one Java program");
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private void compileAndRun(String source) throws Exception {
    Matcher className = PUBLIC_CLASS.matcher(source);
    assertTrue(className.find(), "Java fence must contain a complete public class");
    String name = className.group(1);
    assertFalse(className.find(), "Java fence must contain one public class");

    Path outputDirectory = temporaryDirectory.resolve(name);
    Files.createDirectories(outputDirectory);
    Path javaSource = outputDirectory.resolve(name + ".java");
    Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList(
        "-source",
        "8",
        "-target",
        "8",
        "-classpath",
        classPath,
        "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  options,
                  null,
                  manager.getJavaFileObjects(javaSource.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName(name, true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(name + " failed", exception.getCause());
      }
    }
  }
}
