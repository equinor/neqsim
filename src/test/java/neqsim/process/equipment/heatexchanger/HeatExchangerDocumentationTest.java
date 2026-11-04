package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable regression coverage for {@code docs/process/equipment/heat_exchangers.md}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class HeatExchangerDocumentationTest extends neqsim.NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "process", "equipment", "heat_exchangers.md");
  private static final String QUICK_START_MARKER = "<!-- heat-exchanger-example: two-stream-quick-start -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Compiles and executes the exact complete Java quick start published in the guide.
   *
   * @throws Exception if guide source cannot be compiled or executed
   */
  @Test
  public void literalQuickStartCompilesAndRuns() throws Exception {
    String source = markedExampleSource(QUICK_START_MARKER, "HeatExchangerGuideExample");
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));

    try (URLClassLoader loader = compile("HeatExchangerGuideExample", source)) {
      Class<?> exampleClass = loader.loadClass("HeatExchangerGuideExample");
      exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /** Verifies the complete two-stream quick start and its result semantics. */
  @Test
  public void testTwoStreamQuickStart() {
    SystemInterface gas = new SystemSrkEos(303.15, 30.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    Stream hot = new Stream("hot feed", gas);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(10000.0, "kg/hr");
    hot.run();

    Stream cold = new Stream("cold feed", gas.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(8000.0, "kg/hr");
    cold.run();

    HeatExchanger exchanger = new HeatExchanger("E-100", hot, cold);
    exchanger.setUAvalue(5000.0);
    exchanger.setGuessOutTemperature(70.0, "C");
    exchanger.run();

    double hotOutletC = exchanger.getOutStream(0).getTemperature("C");
    double coldOutletC = exchanger.getOutStream(1).getTemperature("C");
    double dutyKW = Math.abs(exchanger.getDuty()) / 1000.0;
    double effectiveness = exchanger.getThermalEffectiveness();
    double minimumApproachK = exchanger.getApproachTemperature();

    assertTrue(hotOutletC < 100.0);
    assertTrue(coldOutletC > 20.0);
    assertTrue(dutyKW > 0.0);
    assertTrue(Double.isFinite(effectiveness));
    assertTrue(effectiveness > 0.0 && effectiveness <= 1.0);
    assertTrue(Double.isFinite(minimumApproachK));
    assertTrue(minimumApproachK > 0.0);
  }

  /** Verifies unit-bearing heater/cooler temperature specifications and duty reporting. */
  @Test
  public void testHeaterAndCoolerTemperatureSpecifications() {
    SystemInterface gas = new SystemSrkEos(298.15, 20.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream feed = new Stream("heater feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Heater heater = new Heater("E-200", feed);
    heater.setOutletTemperature(80.0, "C");
    heater.run();
    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1.0e-6);
    assertTrue(heater.getDuty("kW") > 0.0);

    Cooler cooler = new Cooler("E-201", heater.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");
    cooler.run();
    assertEquals(30.0, cooler.getOutletStream().getTemperature("C"), 1.0e-6);
    assertTrue(cooler.getDuty("kW") < 0.0);

    heater.autoSize(1.2);
    assertTrue(heater.isAutoSized());
    assertFalse(heater.getSizingReportJson().isEmpty());
  }

  /** Verifies the fixed-outlet-temperature mode and side ownership. */
  @Test
  public void testFixedOutletTemperatureMode() {
    SystemInterface gas = new SystemSrkEos(303.15, 30.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream hot = new Stream("specified hot", gas);
    hot.setTemperature(100.0, "C");
    hot.setFlowRate(1000.0, "kg/hr");
    hot.run();

    Stream cold = new Stream("balanced cold", gas.clone());
    cold.setTemperature(20.0, "C");
    cold.setFlowRate(1000.0, "kg/hr");
    cold.run();

    HeatExchanger exchanger = new HeatExchanger("E-300", hot, cold);
    exchanger.setOutStreamSpecificationNumber(0);
    exchanger.setOutTemperature(55.0, "C");
    exchanger.run();

    assertEquals(55.0, exchanger.getOutStream(0).getTemperature("C"), 1.0e-3);
    assertTrue(exchanger.getOutStream(1).getTemperature("C") > 20.0);
    assertEquals(hot.getPressure("bara"), exchanger.getOutStream(0).getPressure("bara"), 1.0e-8);
  }

  /**
   * Extracts one uniquely marked Java fence from the guide.
   *
   * @param marker unique marker immediately before the fence
   * @param className expected public class name
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource(String marker, String className) throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(marker);
    assertTrue(markerPosition >= 0, "Missing marker: " + marker);
    assertEquals(markerPosition, markdown.lastIndexOf(marker), "Marker must be unique: " + marker);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + marker);
    assertTrue(fence.group(1).contains("public final class " + className));
    return fence.group(1);
  }

  /**
   * Compiles one Java 8 documentation source and returns its class loader.
   *
   * @param className public class name
   * @param source exact Java source
   * @return class loader for the compiled source
   * @throws Exception if the source or class output cannot be created
   */
  private URLClassLoader compile(String className, String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(className));
    Path javaFile = classes.resolve(className + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(diagnostics, manager, null, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classpath, "-d", classes.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }

}
