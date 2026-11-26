package neqsim.documentation;

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
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Compiles the root Java quickstart and executes the primary Python API paths. */
public class EntryPointExamplesDocumentationTest extends NeqSimTest {
  private static final String README = "README.md";
  private static final String PYTHON_GUIDE = "docs/quickstart/python-quickstart.md";
  private static final Pattern README_JAVA = Pattern
      .compile("(?ms)^### Java - add to your project.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern UNITLESS_PRESSURE = Pattern.compile("setOutletPressure\\(\\s*80\\.0\\s*\\)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void primaryEntryPointsUseExplicitUnitsAndTruthfulCorrectionGuidance() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String readme = read(repositoryRoot.resolve(README));
    String pythonGuide = read(repositoryRoot.resolve(PYTHON_GUIDE));

    assertFalse(readme.contains("System.out"));
    assertTrue(readme.contains("java -ea ReadmeQuickstart"));
    assertTrue(pythonGuide.contains("compressor.setOutletPressure(80.0, \"bara\")"));
    assertFalse(UNITLESS_PRESSURE.matcher(pythonGuide).find());
    assertTrue(pythonGuide.contains("Selecting a density unit does not enable Peneloux volume correction"));
    assertTrue(pythonGuide.contains("correction is a separate model choice"));
    assertFalse(pythonGuide.contains("with unit for Peneloux correction"));
    assertFalse(pythonGuide.contains("includes Peneloux correction"));
    assertTrue(pythonGuide.contains("./mvnw -Dtest=EntryPointExamplesDocumentationTest test"));
    assertTrue(pythonGuide.contains(
        "[`EntryPointExamplesDocumentationTest`](../../src/test/java/neqsim/documentation/EntryPointExamplesDocumentationTest.java)"));
  }

  @Test
  void pythonFlashQuickstartApiExecutes() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(PYTHON_GUIDE));
    String source = markedPythonSource(guide, "<!-- python-quickstart-example: flash -->");

    for (String token : Arrays.asList("from neqsim import jneqsim", "SystemSrkEos(273.15 + 25, 50.0)",
        "fluid.setMixingRule(\"classic\")", "ops.TPflash()", "fluid.initProperties()",
        "fluid.getDensity('kg/m3')", "fluid.getZ()", "fluid.getMolarMass('kg/mol')")) {
      assertTrue(source.contains(token), "Missing flash example token: " + token);
    }

    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double density = fluid.getDensity("kg/m3");
    double compressibility = fluid.getZ();
    double molarMass = fluid.getMolarMass("kg/mol");
    assertTrue(fluid.getNumberOfPhases() >= 1);
    assertTrue(Double.isFinite(density) && density > 0.0);
    assertTrue(Double.isFinite(compressibility) && compressibility > 0.0);
    assertTrue(Double.isFinite(molarMass) && molarMass > 0.01 && molarMass < 0.10);
  }

  @Test
  void pythonProcessQuickstartApiExecutes() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(PYTHON_GUIDE));
    String source = markedPythonSource(guide, "<!-- python-quickstart-example: process -->");

    for (String token : Arrays.asList("from neqsim import jneqsim", "SystemSrkEos(273.15 + 30, 50.0)",
        "feed.setFlowRate(10000, \"kg/hr\")", "Separator(\"HP Separator\", feed)",
        "Compressor(\"Gas Compressor\", separator.getGasOutStream())",
        "compressor.setOutletPressure(80.0, \"bara\")", "process.run()",
        "compressor.getPower('kW')")) {
      assertTrue(source.contains(token), "Missing process example token: " + token);
    }

    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    process.add(feed);
    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);
    Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);
    process.run();

    double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidFlow = separator.getLiquidOutStream().getFlowRate("kg/hr");
    double power = compressor.getPower("kW");
    double outletTemperature = compressor.getOutletStream().getTemperature("C");
    assertTrue(Double.isFinite(gasFlow) && gasFlow > 0.0);
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow >= 0.0);
    assertEquals(10000.0, gasFlow + liquidFlow, 1.0e-6);
    assertTrue(Double.isFinite(power) && power > 0.0);
    assertEquals(80.0, compressor.getOutletStream().getPressure("bara"), 1.0e-8);
    assertTrue(Double.isFinite(outletTemperature) && outletTemperature > 30.0);
  }

  @Test
  void publishedReadmeProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String readme = read(repositoryRoot.resolve(README));
    Matcher fences = README_JAVA.matcher(readme);

    assertTrue(fences.find(), "README Java quickstart must be a complete program");
    String source = fences.group(1);
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("assert fluid.getNumberOfPhases() >= 1"));
    assertTrue(source.contains("assert Double.isFinite(densityKgPerCubicMetre)"));
    assertTrue(source.contains("assert densityKgPerCubicMetre > 0.0"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "README Java quickstart section must contain one Java program");
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Extract the unique Python fence following a stable documentation marker.
   *
   * @param guide complete guide source
   * @param marker unique marker immediately before the fence
   * @return literal Python source inside the marked fence
   */
  private String markedPythonSource(String guide, String marker) {
    assertEquals(guide.indexOf(marker), guide.lastIndexOf(marker), "Marker must be unique: " + marker);
    assertTrue(guide.indexOf(marker) >= 0, "Missing marker: " + marker);
    Matcher fence = Pattern.compile("```python\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(guide.substring(guide.indexOf(marker) + marker.length()));
    assertTrue(fence.find(), "Missing Python fence after " + marker);
    return fence.group(1);
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
