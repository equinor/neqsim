package neqsim.process.equipment.absorber;

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
import neqsim.NeqSimTest;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executes and protects the conservative equilibrium-contact example in the TEG tutorial.
 *
 * @author NeqSim
 * @version 1.0
 */
class TEGDehydrationTutorialTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TEGDehydrationTutorialTest.class);
  private static final String EXAMPLE_MARKER =
      "<!-- teg-equilibrium-example: conservative-contact -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile(
          "(?s)<!-- teg-equilibrium-example: conservative-contact -->\\s*"
              + "```java\\s*\\r?\\n([\\s\\S]*?)\\r?\\n```");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /**
   * Sum one component's mass flow across all phases.
   *
   * @param stream process stream
   * @param componentName NeqSim component name
   * @return component mass flow in kg/hr
   */
  private static double componentFlow(StreamInterface stream, String componentName) {
    double flow = 0.0;
    for (int phaseNumber = 0; phaseNumber < stream.getFluid().getNumberOfPhases(); phaseNumber++) {
      ComponentInterface component =
          stream.getFluid().getPhase(phaseNumber).getComponent(componentName);
      if (component != null) {
        flow += component.getFlowRate("kg/hr");
      }
    }
    return flow;
  }

  /** Validate the quantitative engineering envelopes and mass balances independently. */
  @Test
  void equilibriumContactExampleConservesMass() {
    SystemSrkCPAstatoil gasFluid = new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("ethane", 0.05);
    gasFluid.addComponent("propane", 0.02);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.setMixingRule(10);

    Stream gasFeed = new Stream("dry gas basis", gasFluid);
    gasFeed.setFlowRate(1.0, "MSm3/day");
    gasFeed.setTemperature(30.0, "C");
    gasFeed.setPressure(70.0, "bara");

    StreamSaturatorUtil saturator = new StreamSaturatorUtil("water saturator", gasFeed);

    SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
    tegFluid.addComponent("TEG", 99.5, "kg/hr");
    tegFluid.addComponent("water", 0.5, "kg/hr");
    tegFluid.setMixingRule(10);

    Stream leanTeg = new Stream("lean TEG", tegFluid);
    leanTeg.setFlowRate(3000.0, "kg/hr");
    leanTeg.setTemperature(30.0, "C");
    leanTeg.setPressure(70.0, "bara");

    Mixer equilibriumContact = new Mixer("equilibrium contact");
    equilibriumContact.addStream(saturator.getOutletStream());
    equilibriumContact.addStream(leanTeg);

    Separator phaseSplitter =
        new Separator("gas and rich TEG separator", equilibriumContact.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(gasFeed);
    process.add(saturator);
    process.add(leanTeg);
    process.add(equilibriumContact);
    process.add(phaseSplitter);
    process.run();

    StreamInterface wetGas = saturator.getOutletStream();
    StreamInterface mixedContact = equilibriumContact.getOutletStream();
    StreamInterface productGas = phaseSplitter.getGasOutStream();
    StreamInterface richTeg = phaseSplitter.getLiquidOutStream();

    double wetWater = wetGas.getFluid().getPhase("gas").getComponent("water").getx();
    double productWater = productGas.getFluid().getPhase("gas").getComponent("water").getx();

    double wetWaterFlow = componentFlow(wetGas, "water");
    double leanWaterFlow = componentFlow(leanTeg, "water");
    double productWaterFlow = componentFlow(productGas, "water");
    double richWaterFlow = componentFlow(richTeg, "water");
    double waterTransferred = wetWaterFlow - productWaterFlow;
    double richTegFlow = richTeg.getFlowRate("kg/hr");
    double waterResidual = wetWaterFlow + leanWaterFlow - productWaterFlow - richWaterFlow;
    double totalMassResidual =
        wetGas.getFlowRate("kg/hr")
            + leanTeg.getFlowRate("kg/hr")
            - productGas.getFlowRate("kg/hr")
            - richTeg.getFlowRate("kg/hr");

    logger.info("Wet gas water {} mol-ppm", wetWater * 1.0e6);
    logger.info("Product gas water {} mol-ppm", productWater * 1.0e6);

    double wetWaterPpm = wetWater * 1.0e6;
    double productWaterPpm = productWater * 1.0e6;

    assertTrue(
        wetWaterPpm > 700.0 && wetWaterPpm < 900.0,
        "Saturated-gas water must remain in the validated screening range: " + wetWaterPpm);
    assertTrue(
        productWaterPpm > 40.0 && productWaterPpm < 55.0,
        "Product-gas water must remain in the validated screening range: " + productWaterPpm);
    assertTrue(
        productWater < 0.1 * wetWater,
        "The equilibrium contact must remove at least 90% of the gas-phase water");
    assertTrue(
        waterTransferred > 22.0 && waterTransferred < 25.0,
        "Water transfer must remain in the validated screening range: " + waterTransferred);
    assertTrue(
        richTegFlow > 3040.0 && richTegFlow < 3050.0,
        "Rich-liquid flow must remain in the validated screening range: " + richTegFlow);
    assertTrue(richTegFlow > leanTeg.getFlowRate("kg/hr"));
    assertTrue(mixedContact.getFluid().getNumberOfPhases() > 1);
    assertEquals(wetWaterFlow + leanWaterFlow, componentFlow(mixedContact, "water"), 1.0e-8);
    assertEquals(0.0, waterResidual, 1.0e-8);
    assertEquals(0.0, totalMassResidual, 1.0e-8);
  }

  /** Extract, compile, and execute the literal complete Java program published in the tutorial. */
  @Test
  void literalTegEquilibriumScreeningCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/tutorials/teg_dehydration_tutorial.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);

    assertEquals(1, countOccurrences(guide, EXAMPLE_MARKER));
    Matcher fence = JAVA_FENCE.matcher(guide);
    assertTrue(fence.find(), "The marked TEG equilibrium-contact Java fence is missing");
    String exampleSource = fence.group(1);
    assertFalse(fence.find(), "The marked TEG Java fence must be unique");
    assertFalse(exampleSource.contains("System.out"));
    assertFalse(exampleSource.contains("System.err"));

    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The TEG tutorial example must declare a complete public class");
    assertEquals("TegEquilibriumScreening", className.group(1));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("TegEquilibriumScreening.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {temporaryDirectory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("TegEquilibriumScreening", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("TegEquilibriumScreening failed", exception.getCause());
      }
    }
  }

  /**
   * Compile the extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options =
        Arrays.asList(
            "-source",
            "8",
            "-target",
            "8",
            "-proc:none",
            "-classpath",
            classPath,
            "-d",
            temporaryDirectory.toString());
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
                  manager.getJavaFileObjects(source.toFile()))
              .call();
      assertTrue(
          Boolean.TRUE.equals(successful),
          "docs/tutorials/teg_dehydration_tutorial.md: " + diagnostics.getDiagnostics());
    }
  }

  /**
   * Count exact non-overlapping occurrences of a marker.
   *
   * @param text text to search
   * @param token marker token
   * @return occurrence count
   */
  private static int countOccurrences(String text, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }
}
