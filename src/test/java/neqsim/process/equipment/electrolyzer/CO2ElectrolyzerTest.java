package neqsim.process.equipment.electrolyzer;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

class CO2ElectrolyzerTest extends neqsim.NeqSimTest {
  private static final String GUIDE = "docs/pvtsimulation/CO2ElectrolyzerExample.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Executable Java workflow.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void testSelectivityBasedConversionAndEnergyDemand() {
    SystemInterface feedFluid = new Fluid().create2(new String[] {"CO2", "water"}, new double[] {0.95, 0.05},
        "mole/sec");
    feedFluid.setTemperature(298.15);
    feedFluid.setPressure(20.0);

    Stream feedStream = new Stream("CO2 feed", feedFluid);
    feedStream.setTemperature(298.15, "K");
    feedStream.setPressure(20.0, "bara");
    feedStream.run();

    CO2Electrolyzer electrolyzer = new CO2Electrolyzer("CO2 electrolyzer", feedStream);
    double coSelectivity = 0.7;
    double hydrogenSelectivity = 0.3;
    double conversion = 0.55;

    electrolyzer.setCO2Conversion(conversion);
    electrolyzer.setGasProductSelectivity("CO", coSelectivity);
    electrolyzer.setGasProductSelectivity("H2", hydrogenSelectivity);
    electrolyzer.setProductFaradaicEfficiency("CO", 0.9);
    electrolyzer.setElectronsPerMoleProduct("H2", 2.0);

    electrolyzer.run();

    double inletCo2 = feedStream.getThermoSystem().getComponent("CO2").getFlowRate("mole/sec");
    double convertedCo2 = inletCo2 * conversion;
    double expectedCo2 = inletCo2 - convertedCo2;
    double expectedCo = convertedCo2 * coSelectivity;
    double expectedHydrogen = convertedCo2 * hydrogenSelectivity;

    SystemInterface gasProduct = electrolyzer.getGasProductStream().getThermoSystem();
    String co2Name = ComponentInterface.getComponentNameFromAlias("CO2");
    String coName = ComponentInterface.getComponentNameFromAlias("CO");
    String hydrogenName = ComponentInterface.getComponentNameFromAlias("H2");

    assertEquals(expectedCo2, gasProduct.getComponent(co2Name).getFlowRate("mole/sec"), 1e-6);
    assertEquals(expectedCo, gasProduct.getComponent(coName).getFlowRate("mole/sec"), 1e-6);
    assertEquals(expectedHydrogen, gasProduct.getComponent(hydrogenName).getFlowRate("mole/sec"), 1e-6);
    double electronMoles = expectedCo * 2.0 / 0.9 + expectedHydrogen * 2.0;
    double expectedPower = electronMoles / 0.95 * 96485.3329 * 2.7;
    assertEquals(expectedPower, electrolyzer.getEnergyStream().getDuty(), 1e-3);
  }

  /** Verifies the units, engineering limits, and single executable-program contract. */
  @Test
  void guideStatesUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("Feed rates are in mol/s, temperature is in K, and pressure is absolute bara"));
    assertTrue(guide.contains("Electrical duty and delivered power are in W"));
    assertTrue(guide.contains("capacity and state of charge are in Wh"));
    assertTrue(guide.contains("does not close an electrochemical oxygen balance"));
    assertTrue(guide.contains("does not qualify dispatch"));
    assertTrue(guide.contains("not a product-purification design"));
    assertTrue(guide.contains("accountable engineering review"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("```python"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not publish unverified Java fragments");
  }

  /** Compiles the exact Markdown fence for Java 8 and executes it with assertions enabled. */
  @Test
  void publishedProgramCompilesAndExecutes() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());
    assertTrue(fence.find(), "Executable CO2-electrolyzer workflow is missing");
    String source = fence.group(1);

    assertTrue(source.contains("new Fluid()"));
    assertTrue(source.contains("setCO2Conversion(conversion)"));
    assertTrue(source.contains("setProductFaradaicEfficiency(\"CO\", 0.90)"));
    assertTrue(source.contains("setStateOfCharge(batteryCapacityWh)"));
    assertTrue(source.contains("battery.discharge(electricalDutyW, 1.0 / 3600.0)"));
    assertTrue(source.contains("assert Double.isFinite(electricalDutyW)"));
    assertTrue(source.contains("logger.info("));
    assertFalse(source.contains("System.out"));

    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  /** Reads the CO2-electrolyzer guide from the repository root. */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  /** Compiles one extracted Java source and invokes its main method with assertions enabled. */
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
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(javaSource.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
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
