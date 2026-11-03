package neqsim.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
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
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable coverage for the public APIs used by the process-recipes cookbook.
 *
 * @author ESOL
 * @version 1.0
 */
class ProcessRecipesDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "cookbook", "process-recipes.md");
  private static final String MARKER = "<!-- process-recipes-example: reference-process -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Creates the shared rich-gas feed used by focused cookbook API tests.
   *
   * @param name stream name
   * @return configured feed stream
   */
  private Stream createFeed(String name) {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    return feed;
  }

  /**
   * Compiles and executes the literal marked Java reference process.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void executableReferenceProcessRunsExactGuideSource() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public class ProcessCookbookScreen"));
    assertTrue(source.contains("new Separator(\"HP separator\", feed)"));
    assertTrue(source.contains("new Compressor(\"Gas compressor\", separator.getGasOutStream())"));
    assertTrue(source.contains("new Cooler(\"Aftercooler\", compressor.getOutletStream())"));
    assertTrue(source.contains("process.run()"));

    try (URLClassLoader loader = compile("ProcessCookbookScreen", source)) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = loader.loadClass("ProcessCookbookScreen");
      assertTrue(example.desiredAssertionStatus(), "Guide assertions must be enabled");
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("ProcessCookbookScreen failed", exception.getCause());
      }
    }
  }

  @Test
  void streamSeparatorCompressorAndCoolerRecipesExecute() {
    Stream feed = createFeed("Feed");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("K-100", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    process.add(compressor);

    Cooler cooler = new Cooler("E-100", compressor.getOutletStream());
    cooler.setOutletTemperature(40.0, "C");
    process.add(cooler);
    process.run();

    assertTrue(compressor.getPower("kW") > 0.0);
    assertEquals(100.0, compressor.getOutletStream().getPressure("bara"), 1.0e-6);
    assertEquals(40.0, cooler.getOutletStream().getTemperature("C"), 1.0e-6);
  }

  @Test
  void polytropicHeaterExpanderAndHeatExchangerRecipesExecute() {
    Stream feed = createFeed("Feed");
    feed.run();

    Compressor compressor = new Compressor("Polytropic compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.80);
    compressor.run();
    assertTrue(compressor.getPower("kW") > 0.0);

    Heater heater = new Heater("Heater", feed);
    heater.setOutletTemperature(80.0, "C");
    heater.run();
    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1.0e-6);

    Expander expander = new Expander("Turbo-Expander", feed);
    expander.setOutletPressure(20.0, "bara");
    expander.setIsentropicEfficiency(0.85);
    expander.run();
    assertTrue(expander.getPower("kW") < 0.0);

    Stream hotStream = createFeed("Hot feed");
    hotStream.setTemperature(120.0, "C");
    Stream coldStream = createFeed("Cold feed");
    coldStream.setTemperature(20.0, "C");
    HeatExchanger heatExchanger = new HeatExchanger("E-102", hotStream, coldStream);
    heatExchanger.setUAvalue(5000.0);
    heatExchanger.run();

    assertTrue(Double.isFinite(heatExchanger.getDuty()));
    assertTrue(heatExchanger.getOutStream(0).getTemperature("C") < 120.0);
    assertTrue(heatExchanger.getOutStream(1).getTemperature("C") > 20.0);
  }

  @Test
  void valveRecycleAndAdjusterConfigurationUsesCurrentApis() {
    Stream feed = createFeed("Feed");
    feed.run();

    ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
    valve.setOutletPressure(25.0, "bara");
    valve.setPercentValveOpening(100.0);
    ValveMechanicalDesign design = (ValveMechanicalDesign) valve.getMechanicalDesign();
    design.setValveSizingStandard("IEC 60534");
    design.getValveSizingMethod().setxT(0.75);
    valve.run();
    valve.calcKv();
    assertTrue(valve.getKv() > 0.0);

    for (String standard : new String[] {"IEC 60534", "IEC 60534 full", "prod choke", "Sachdeva", "Gilbert",
        "Baxendell", "Ros", "Achong"}) {
      design.setValveSizingStandard(standard);
      assertEquals(standard, design.getValveSizingStandard());
    }

    Stream recycleSource = createFeed("Recycle source");
    Stream recycleTear = createFeed("Recycle tear");
    Recycle recycle = new Recycle("Recycle");
    recycle.addStream(recycleSource);
    recycle.setOutletStream(recycleTear);
    recycle.setTolerance(1.0e-6);
    assertNotNull(recycle.getOutletStream());

    Adjuster adjuster = new Adjuster("Adjust feed flow");
    adjuster.setAdjustedVariable(feed, "flow", "kg/hr");
    adjuster.setTargetVariable(feed, "gasVolumeFlow", feed.getFlowRate("Am3/hr"), "Am3/hr");
    adjuster.setMinAdjustedValue(1.0);
    adjuster.setMaxAdjustedValue(100000.0);
    adjuster.setTolerance(1.0e-4);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(adjuster);
    process.run();
    assertTrue(adjuster.solved());
  }

  /**
   * Extracts the uniquely marked Java fence from the process cookbook.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique: " + MARKER);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(fence.group(1).contains("public class ProcessCookbookScreen"));
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
