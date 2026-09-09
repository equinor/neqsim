package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the advanced overview fragments with connected two-area and two-well process fixtures. */
public class ProcessModelOptimizationOverviewDocumentationTest extends NeqSimTest {
  @TempDir
  Path output;

  /** Public fixture fields allow the extracted documentation to compile in an external client package. */
  public static final class Fixture {
    public ProcessModel model;
    public Stream feed;
    public Stream producerA;
    public Stream producerB;
    public StreamInterface export;
    public ProcessModelSimulationEvaluator simulation;
    public ProcessModelOperatingAction wellAAction;
    public ProcessModelOperatingAction wellBAction;
  }

  private Stream gasStream(String name, double rate, double pressure) {
    SystemSrkEos gas = new SystemSrkEos(298.15, pressure);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    Stream stream = new Stream(name, gas);
    stream.setFlowRate(rate, "kg/hr");
    return stream;
  }

  private Fixture throughputFixture() {
    Fixture fixture = new Fixture();
    fixture.feed = gasStream("feed", 10000.0, 50.0);
    ThrottlingValve choke = new ThrottlingValve("choke", fixture.feed);
    choke.setOutletPressure(30.0, "bara");
    Separator separator = new Separator("separator", choke.getOutletStream());
    separator.clearCapacityConstraints();
    separator.addCapacityConstraint(new CapacityConstraint("installedGasCapacity", "kg/hr", ConstraintType.HARD)
        .setDesignValue(15000.0).setMaxValue(16500.0).setSeverity(ConstraintSeverity.HARD)
        .setValueSupplier(() -> fixture.feed.getFlowRate("kg/hr")));
    ProcessSystem wells = new ProcessSystem("wells");
    wells.add(fixture.feed);
    wells.add(choke);
    ProcessSystem separation = new ProcessSystem("separation");
    separation.add(separator);
    fixture.model = new ProcessModel();
    fixture.model.add("wells", wells);
    fixture.model.add("separation", separation);
    fixture.export = separator.getGasOutStream();
    fixture.model.run();
    return fixture;
  }

  private WellFlow well(String name, Stream producer, double rateLimit) {
    double baselineRate = producer.getFlowRate("kg/hr");
    WellFlow well = new WellFlow(name);
    well.setInletStream(producer);
    well.setWellProductionIndex(5.0e-4);
    producer.setFlowRate(rateLimit, "kg/hr");
    producer.run();
    well.run();
    well.setMaxDrawdown(well.getDrawdown(), "bara");
    well.useWellConstraints();
    well.getCapacityConstraints().get("well drawdown").setSeverity(ConstraintSeverity.HARD);
    producer.setFlowRate(baselineRate, "kg/hr");
    producer.run();
    well.run();
    return well;
  }

  private Fixture hydraulicFixture() {
    Fixture fixture = new Fixture();
    fixture.feed = gasStream("producer", 30000.0, 100.0);
    fixture.feed.setFlowRate(1.0, "MSm3/day");
    WellFlow well = well("well", fixture.feed, 1.35 * fixture.feed.getFlowRate("kg/hr"));
    ProcessSystem subsurface = new ProcessSystem("Subsurface");
    subsurface.add(fixture.feed);
    subsurface.add(well);
    fixture.model = new ProcessModel();
    fixture.model.add("Subsurface", subsurface);
    fixture.export = well.getOutletStream();
    fixture.model.run();
    return fixture;
  }

  private Fixture allocationFixture() {
    Fixture fixture = new Fixture();
    fixture.producerA = gasStream("producer A", 15000.0, 100.0);
    fixture.producerB = gasStream("producer B", 15000.0, 100.0);
    WellFlow wellA = well("well A", fixture.producerA, 21000.0);
    WellFlow wellB = well("well B", fixture.producerB, 24000.0);
    ThrottlingValve chokeA = new ThrottlingValve("choke A", wellA.getOutletStream());
    ThrottlingValve chokeB = new ThrottlingValve("choke B", wellB.getOutletStream());
    chokeA.setOutletPressure(50.0, "bara");
    chokeB.setOutletPressure(50.0, "bara");
    Mixer mixer = new Mixer("manifold");
    mixer.addStream(chokeA.getOutletStream());
    mixer.addStream(chokeB.getOutletStream());
    Separator separator = new Separator("inlet separator", mixer.getOutletStream());
    separator.clearCapacityConstraints();
    separator.addCapacityConstraint(
        new CapacityConstraint("installed gathering rate", "kg/hr", ConstraintType.HARD).setDesignValue(33000.0)
            .setSeverity(ConstraintSeverity.HARD).setValueSupplier(() -> mixer.getOutletStream().getFlowRate("kg/hr")));
    ProcessSystem subsurface = new ProcessSystem("Subsurface");
    subsurface.add(fixture.producerA);
    subsurface.add(fixture.producerB);
    subsurface.add(wellA);
    subsurface.add(wellB);
    subsurface.add(chokeA);
    subsurface.add(chokeB);
    ProcessSystem gathering = new ProcessSystem("Gathering");
    gathering.add(mixer);
    gathering.add(separator);
    fixture.model = new ProcessModel();
    fixture.model.add("Subsurface", subsurface);
    fixture.model.add("Gathering", gathering);
    fixture.export = separator.getGasOutStream();
    fixture.model.run();
    fixture.simulation = new ProcessModelSimulationEvaluator(fixture.model);
    fixture.simulation.setIncludeStrategyCapacityConstraints(false);
    fixture.simulation.addObjective("allocation value proxy",
        model -> 2.0 * fixture.producerA.getFlowRate("kg/hr") + fixture.producerB.getFlowRate("kg/hr"),
        ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    fixture.wellAAction = ProcessModelOperatingAction.continuous("well-a-rate", "Well A rate",
        "Subsurface::producer A.flowRate", 6000.0, 30000.0, "kg/hr", "synthetic well A envelope");
    fixture.wellBAction = ProcessModelOperatingAction.continuous("well-b-rate", "Well B rate",
        "Subsurface::producer B.flowRate", 6000.0, 30000.0, "kg/hr", "synthetic well B envelope");
    return fixture;
  }

  @Test
  void advancedOverviewFragmentsCompileAndExecuteWithPhysicalFixtures() throws Exception {
    String document = new String(Files.readAllBytes(Paths.get("docs/process/optimization/OPTIMIZATION_OVERVIEW.md")),
        StandardCharsets.UTF_8);
    String imports = "package optimization.documentation;\nimport java.util.*;\n"
        + "import java.util.function.*;\nimport java.nio.file.*;\n"
        + "import neqsim.process.processmodel.*;\nimport neqsim.process.equipment.stream.*;\n"
        + "import neqsim.process.util.optimizer.*;\n"
        + "import neqsim.process.util.optimizer.ProcessModelOptimizationOverviewDocumentationTest.Fixture;\n";
    String setup = "ProcessModel model = fixture.model; StreamInterface export = fixture.export;\n";
    String allocationSetup = setup + "ProcessModelSimulationEvaluator simulation = fixture.simulation;\n"
        + "ProcessModelOperatingAction wellAAction = fixture.wellAAction, wellBAction = fixture.wellBAction;\n"
        + "double wellARate = 18000.0, wellBRate = 12000.0, totalRate = 30000.0;\n"
        + "double initialWellARate = 15000.0, initialWellBRate = 15000.0;\n";
    // Only relocate CSV filenames; the extracted API calls and control flow remain unchanged.
    String throughput = snippet(document, 2)
        .replace("\"installed_capacity.csv\"", "output.resolve(\"installed_capacity.csv\").toString()")
        .replace("\"throughput_trace.csv\"", "output.resolve(\"throughput_trace.csv\").toString()");
    String source = imports + "public class AdvancedOverviewExamples {\n"
        + "public static ProcessModelThroughputResult throughput(Fixture fixture, Path output) throws Exception {\n"
        + setup + throughput + "\nreturn result; }\n"
        + "public static ProcessModelSimulationEvaluator.EvaluationResult evaluate(Fixture fixture) {\n" + setup
        + snippet(document, 3) + "\nreturn result; }\n"
        + "public static ProcessModelOperatingActionEvaluator.CandidateEvaluationResult hydraulic(Fixture fixture) {\n"
        + setup + snippet(document, 5) + "\nreturn candidate; }\n"
        + "public static Object[] allocation(Fixture fixture) {\n" + allocationSetup + snippet(document, 6) + "\n"
        + snippet(document, 7) + "\n" + snippet(document, 8) + "\nreturn new Object[] {result, search, relief}; }\n}";
    Path javaFile = output.resolve("AdvancedOverviewExamples.java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
    compile(javaFile);
    Matcher csv = Pattern.compile("```text\\n(area,equipment,constraint,currentValueAddress,.*?)```", Pattern.DOTALL)
        .matcher(document);
    assertTrue(csv.find(), "The installed-capacity CSV shown below the snippet must be exercised");
    Files.write(output.resolve("installed_capacity.csv"), csv.group(1).getBytes(StandardCharsets.UTF_8));
    try (
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, getClass().getClassLoader())) {
      Class<?> examples = loader.loadClass("optimization.documentation.AdvancedOverviewExamples");
      Fixture fixture = throughputFixture();
      ProcessModelThroughputResult throughputResult = (ProcessModelThroughputResult) examples
          .getMethod("throughput", Fixture.class, Path.class).invoke(null, fixture, output);
      assertNotNull(throughputResult.getBestFeasibleCase());
      assertNotNull(throughputResult.getFirstInfeasibleCase());
      assertTrue(throughputResult.getBestFeasibleCase().isFeasible());
      assertFalse(throughputResult.getFirstInfeasibleCase().isFeasible());
      assertEquals(1.5, throughputResult.getOptimalMultiplier(), 0.02);
      assertTrue(Files.size(output.resolve("throughput_trace.csv")) > 100);

      fixture = throughputFixture();
      ProcessModelSimulationEvaluator.EvaluationResult evaluated = (ProcessModelSimulationEvaluator.EvaluationResult) examples
          .getMethod("evaluate", Fixture.class).invoke(null, fixture);
      assertTrue(evaluated.isSimulationConverged());
      assertTrue(evaluated.isFeasible());
      assertEquals(12000.0, evaluated.getObjectivesRaw()[0], 1.0e-6);
      assertEquals(fixture.feed.getFlowRate("kg/hr"), fixture.export.getFlowRate("kg/hr"), 1.0e-6);
      assertFalse(evaluated.getInstalledEquipmentCapacityEvidence().isEmpty());

      fixture = hydraulicFixture();
      ProcessModelOperatingActionEvaluator.CandidateEvaluationResult hydraulic = (ProcessModelOperatingActionEvaluator.CandidateEvaluationResult) examples
          .getMethod("hydraulic", Fixture.class).invoke(null, fixture);
      assertTrue(hydraulic.isFeasible(), hydraulic.getDiagnostics().toString());
      assertTrue(hydraulic.isBaselineRestored());
      assertTrue(hydraulic.isBaselineSimulationConverged());
      assertEquals(1.0, fixture.feed.getFlowRate("MSm3/day"), 1.0e-5);
      assertEquals(fixture.feed.getFlowRate("kg/hr"), fixture.export.getFlowRate("kg/hr"), 1.0e-6);

      fixture = allocationFixture();
      Object[] allocation = (Object[]) examples.getMethod("allocation", Fixture.class).invoke(null, fixture);
      ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult candidate = (ProcessModelOperatingActionSetEvaluator.CandidateSetEvaluationResult) allocation[0];
      ProcessModelAllocationOptimizer.AllocationSearchResult search = (ProcessModelAllocationOptimizer.AllocationSearchResult) allocation[1];
      ProcessModelAllocationBottleneckAnalyzer.BottleneckAnalysisResult relief = (ProcessModelAllocationBottleneckAnalyzer.BottleneckAnalysisResult) allocation[2];
      assertTrue(candidate.isFeasible(), candidate.getDiagnostics().toString());
      assertTrue(search.isModelRecovered(), search.getDiagnostics().toString());
      assertTrue(search.isConverged(), search.getDiagnostics().toString());
      assertNotNull(search.getBestFeasibleCandidate());
      assertFalse(relief.getOpportunities().isEmpty(), "Higher-value infeasible samples should expose well relief");
      for (ProcessModelAllocationOptimizer.CandidateRecord record : search.getCandidates()) {
        double[] values = record.getCandidateValues();
        assertEquals(30000.0, values[0] + values[1], 1.0e-6);
        assertTrue(record.getEvaluation().isBaselineRestored());
      }
      assertEquals(15000.0, fixture.producerA.getFlowRate("kg/hr"), 1.0e-6);
      assertEquals(15000.0, fixture.producerB.getFlowRate("kg/hr"), 1.0e-6);
      assertEquals(30000.0, fixture.export.getFlowRate("kg/hr"), 1.0e-6);
    }
  }

  private String snippet(String document, int index) {
    Matcher block = Pattern.compile("<!-- overview-example: " + index + " -->\\s*```java\\n(.*?)```", Pattern.DOTALL)
        .matcher(document);
    assertTrue(block.find(), "Missing overview example " + index);
    return block.group(1);
  }

  private void compile(Path file) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Run documentation tests with a full JDK");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<>(Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d",
          output.toString(), "-encoding", "UTF-8", "-proc:none"));
      if ("1.8".equals(System.getProperty("java.specification.version"))) {
        options.addAll(Arrays.asList("-source", "8", "-target", "8"));
      } else {
        options.addAll(Arrays.asList("--release", "8"));
      }
      assertTrue(
          compiler.getTask(null, manager, diagnostics, options, null,
              manager.getJavaFileObjectsFromFiles(Arrays.asList(file.toFile()))).call(),
          diagnostics.getDiagnostics().toString());
    }
  }
}
