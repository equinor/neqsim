package neqsim.process.equipment.splitter;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class ComponentSplitterTest {
  private static final Logger logger = LogManager.getLogger(ComponentSplitterTest.class);
  private static final String MIXER_SPLITTER_GUIDE = "docs/process/equipment/mixers_splitters.md";
  private static final Pattern JAVA_FENCE = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  /** Logger object for class. */

  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    ComponentSplitter splitter = new ComponentSplitter("splitter", inletStream);
    splitter.setSplitFactors(new double[] {1.00, 0.0, 0.0});

    StreamInterface stream1 = new Stream("stream 1", splitter.getSplitStream(0));
    StreamInterface stream2 = new Stream("stream 2", splitter.getSplitStream(1));

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);
  }

  @Test
  public void configSplitter() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();
    Splitter splitter = new Splitter("splitter", inletStream, 3);
    splitter.setSplitFactors(new double[] {0.8, 0.2, 0.0});
    splitter.run();
    assertEquals(0.815104472498348, splitter.getSplitStream(0).getFluid().getPhase(0).getZ(), 0.01);
    assertEquals(0.815104472498348, splitter.getSplitStream(1).getFluid().getPhase(0).getZ(), 0.01);
    assertEquals(0.815104472498348, splitter.getSplitStream(2).getFluid().getPhase(0).getZ(), 0.01);
  }

  @Test
  public void testRun() {
    processOps.run();
    // ((StreamInterface)processOps.getUnit("stream 1")).displayResult();
    // ((StreamInterface)processOps.getUnit("stream 2")).displayResult();
    assertEquals(((StreamInterface) processOps.getUnit("stream 1")).getFluid().getComponent("methane").getx(), 1.0,
        1e-6);
    assertEquals(((StreamInterface) processOps.getUnit("stream 2")).getFluid().getComponent("methane").getx(), 0.0,
        1e-6);
  }

  @Test
  public void testSplitBasisMolarAndMass() {
    // A UniSim-style Component Splitter that routes water to the bottoms and
    // everything else to the overhead. For a per-component "fraction of feed"
    // split the molar and mass bases give the SAME mole split (a single
    // component's mass fraction equals its mole fraction), so both bases must
    // produce identical outlet streams.
    double[] factors = new double[] {1.0, 1.0, 0.0}; // methane, ethane -> overhead; water -> bottoms

    SystemSrkEos sys = new SystemSrkEos(298.0, 10.0);
    sys.addComponent("methane", 100.0);
    sys.addComponent("ethane", 10.0);
    sys.addComponent("water", 5.0);
    Stream feed = new Stream("feed", sys);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(30.0, "C");
    feed.setFlowRate(1000.0, "kg/hr");

    ComponentSplitter molar = new ComponentSplitter("molar", feed);
    molar.setSplitFactors(factors, "molar");
    molar.run();

    ComponentSplitter mass = new ComponentSplitter("mass", feed);
    mass.setSplitFactors(factors, "mass");
    mass.run();

    assertEquals("molar", molar.getSplitBasis());
    assertEquals("mass", mass.getSplitBasis());

    double feedWater = feed.getThermoSystem().getComponent("water").getNumberOfmoles();

    // Overhead (split0): no water.
    assertEquals(0.0, molar.getSplitStream(0).getFluid().getComponent("water").getNumberOfmoles(), 1e-9);
    assertEquals(0.0, mass.getSplitStream(0).getFluid().getComponent("water").getNumberOfmoles(), 1e-9);
    // Bottoms (split1): all the feed water, no methane.
    assertEquals(feedWater, mass.getSplitStream(1).getFluid().getComponent("water").getNumberOfmoles(), 1e-6);
    assertEquals(0.0, mass.getSplitStream(1).getFluid().getComponent("methane").getNumberOfmoles(), 1e-9);

    // Molar and mass bases coincide for a per-component feed fraction.
    assertEquals(molar.getSplitStream(0).getFluid().getComponent("methane").getNumberOfmoles(),
        mass.getSplitStream(0).getFluid().getComponent("methane").getNumberOfmoles(), 1e-9);
    assertEquals(molar.getSplitStream(1).getFluid().getComponent("water").getNumberOfmoles(),
        mass.getSplitStream(1).getFluid().getComponent("water").getNumberOfmoles(), 1e-9);
  }

  @Test
  public void testRunSplitter() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    splitter.setFlowRates(new double[] {4.0, 1.0}, "MSm3/day");
    // splitter.setFlowRates(new double[] {-1.0, 1.0}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    ThrottlingValve valve1 = new ThrottlingValve("valve", stream1);
    valve1.setCv(500.0);
    valve1.setOutletPressure(5.0);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);
    processOps.add(valve1);

    processOps.run();

    assertEquals(stream1.getFlowRate("MSm3/day"), 4.0, 1e-6);
    assertEquals(stream2.getFlowRate("MSm3/day"), 1.0, 1e-6);
    logger.info("valve opening " + valve1.getPercentValveOpening());

    splitter.setFlowRates(new double[] {-1, 4.9}, "MSm3/day");
    processOps.run();

    logger.info("valve opening " + valve1.getPercentValveOpening());
    assertEquals(0.1, splitter.getSplitStream(0).getFlowRate("MSm3/day"), 1e-6);
    assertEquals(4.9, splitter.getSplitStream(1).getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testRunSplitter2() {
    testSystem = new SystemSrkEos(298.0, 55.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);

    processOps = new ProcessSystem();

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(55.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");

    Stream streamresycl = inletStream.clone("recycle stream");

    Mixer mixer1 = new Mixer("mixer 1");
    mixer1.addStream(inletStream);
    mixer1.addStream(streamresycl);

    Compressor compressor1 = new Compressor("compressor 1", mixer1.getOutletStream());
    compressor1.setOutletPressure(100.0);

    Stream compressedStream = (Stream) compressor1.getOutletStream();

    Splitter splitter = new Splitter("splitter 1", compressedStream);
    splitter.setFlowRates(new double[] {5.0, 0.1}, "MSm3/day");

    StreamInterface resycStream1 = splitter.getSplitStream(1);

    ThrottlingValve valve1 = new ThrottlingValve("valve 1", resycStream1);
    valve1.setOutletPressure(55.0);
    valve1.setCv(500.0);

    Recycle recycle1 = new Recycle("recycle 1");
    recycle1.addStream(valve1.getOutletStream());
    recycle1.setOutletStream(streamresycl);
    recycle1.setTolerance(1e-6);

    StreamInterface exportStream = splitter.getSplitStream(0);

    processOps.add(inletStream);
    processOps.add(streamresycl);
    processOps.add(mixer1);
    processOps.add(compressor1);
    processOps.add(compressedStream);
    processOps.add(splitter);
    processOps.add(resycStream1);
    processOps.add(valve1);
    processOps.add(recycle1);
    processOps.add(exportStream);

    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.1, resycStream1.getFlowRate("MSm3/day"), 1e-6);
    // assertEquals(8.43553108874272, valve1.getPercentValveOpening(), 1e-2);

    splitter.setFlowRates(new double[] {5.0, 0.5}, "MSm3/day");
    processOps.run();

    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(0.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);
    // assertEquals(41.9139926125338, valve1.getPercentValveOpening(), 1e-2);

    splitter.setFlowRates(new double[] {-1, 2.5}, "MSm3/day");
    processOps.run();
    assertEquals(5.00000000, exportStream.getFlowRate("MSm3/day"), 1e-4);
    assertEquals(2.5, resycStream1.getFlowRate("MSm3/day"), 1e-4);

    splitter.setFlowRates(new double[] {5.0, 0.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);

    splitter.setFlowRates(new double[] {5.0, 3.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(3.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);

    splitter.setFlowRates(new double[] {-1, 0.0}, "MSm3/day");
    processOps.run();
    assertEquals(5.0, exportStream.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(0.0, resycStream1.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOnePositionFirst() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    // -1 in first position
    splitter.setFlowRates(new double[] {-1, 1.0}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);

    processOps.run();

    // stream1 should get 4.0 MSm3/day (5.0 - 1.0)
    assertEquals(4.0, stream1.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(1.0, stream2.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOnePositionLast() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter = new Splitter("splitter", inletStream);
    splitter.setSplitNumber(2);
    // -1 in last position
    splitter.setFlowRates(new double[] {1.0, -1}, "MSm3/day");

    StreamInterface stream1 = splitter.getSplitStream(0);
    StreamInterface stream2 = splitter.getSplitStream(1);

    processOps.add(inletStream);
    processOps.add(splitter);
    processOps.add(stream1);
    processOps.add(stream2);

    processOps.run();

    // stream1 should get 1.0 MSm3/day, stream2 should get 4.0 (5.0 - 1.0)
    assertEquals(1.0, stream1.getFlowRate("MSm3/day"), 1e-6);
    assertEquals(4.0, stream2.getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  public void testSplitterNegativeOneArbitraryPosition() {
    // Test that -1 produces identical results regardless of position
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);

    // Test with -1 first
    ProcessSystem processOps1 = new ProcessSystem();
    neqsim.thermo.system.SystemInterface testSystem1 = new SystemSrkEos(298.0, 10.0);
    testSystem1.addComponent("methane", 100.0);
    testSystem1.addComponent("ethane", 10.0);
    testSystem1.addComponent("propane", 10.0);
    Stream inletStream1 = new Stream("inlet stream", testSystem1);
    inletStream1.setPressure(pressure_inlet, "bara");
    inletStream1.setTemperature(temperature_inlet, "C");
    inletStream1.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter1 = new Splitter("splitter", inletStream1);
    splitter1.setSplitNumber(2);
    splitter1.setFlowRates(new double[] {-1, 2.5}, "MSm3/day");

    processOps1.add(inletStream1);
    processOps1.add(splitter1);
    processOps1.run();

    double result1Stream0 = splitter1.getSplitStream(0).getFlowRate("MSm3/day");
    double result1Stream1 = splitter1.getSplitStream(1).getFlowRate("MSm3/day");

    // Test with -1 last
    ProcessSystem processOps2 = new ProcessSystem();
    neqsim.thermo.system.SystemInterface testSystem2 = new SystemSrkEos(298.0, 10.0);
    testSystem2.addComponent("methane", 100.0);
    testSystem2.addComponent("ethane", 10.0);
    testSystem2.addComponent("propane", 10.0);
    Stream inletStream2 = new Stream("inlet stream", testSystem2);
    inletStream2.setPressure(pressure_inlet, "bara");
    inletStream2.setTemperature(temperature_inlet, "C");
    inletStream2.setFlowRate(gasFlowRate, "MSm3/day");

    Splitter splitter2 = new Splitter("splitter", inletStream2);
    splitter2.setSplitNumber(2);
    splitter2.setFlowRates(new double[] {2.5, -1}, "MSm3/day");

    processOps2.add(inletStream2);
    processOps2.add(splitter2);
    processOps2.run();

    double result2Stream0 = splitter2.getSplitStream(0).getFlowRate("MSm3/day");
    double result2Stream1 = splitter2.getSplitStream(1).getFlowRate("MSm3/day");

    // Both configurations should produce the same results
    assertEquals(result1Stream0, result2Stream0, 1e-6);
    assertEquals(result1Stream1, result2Stream1, 1e-6);
    // Verify the actual values
    assertEquals(2.5, result1Stream0, 1e-6);
    assertEquals(2.5, result2Stream0, 1e-6);
    assertEquals(2.5, result1Stream1, 1e-6);
    assertEquals(2.5, result2Stream1, 1e-6);
  }

  /** Compiles the exact mixer/splitter guide fence for Java 8 and executes its assertions. */
  @Test
  public void mixersAndSplittersGuideCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = new String(Files.readAllBytes(repositoryRoot.resolve(MIXER_SPLITTER_GUIDE)), StandardCharsets.UTF_8)
        .replace("\r\n", "\n");

    assertTrue(guide.startsWith("---\n"));
    assertTrue(guide.contains("pressure is absolute bara"));
    assertTrue(guide.contains("issue #4073"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("System.err"));
    assertFalse(guide.contains("```python"));

    Matcher fence = JAVA_FENCE.matcher(guide);
    assertTrue(fence.find(), "Mixer/splitter guide must publish one Java program");
    String source = fence.group(1);
    assertFalse(fence.find(), "Mixer/splitter guide must not publish dependent Java fragments");

    assertTrue(source.contains("public final class MixerSplitterExample"));
    assertTrue(source.contains("mixer.isPressureMismatch()"));
    assertTrue(source.contains("new Splitter(\"SP-100\", mixedGas, 2)"));
    assertTrue(source.contains("new ComponentSplitter(\"CS-100\", mixedGas)"));
    assertTrue(source.contains("new double[] {7.0, 3.0}"));
    assertTrue(source.contains("new double[] {1.0, 0.0, 0.0}"));
    assertTrue(source.contains("assert Math.abs(mixedFlowKgPerHour - 8000.0) < 1.0e-6"));
    assertTrue(source.contains("assert Double.isFinite(product.getFluid().getEnthalpy())"));

    compileAndRunGuideExample(source);
  }

  /** Compiles one extracted Java source and invokes its main method with assertions enabled. */
  private void compileAndRunGuideExample(String source) throws Exception {
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
    Iterable<String> options = java.util.Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
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
