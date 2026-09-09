package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;

/** Compiles guide fragments in a client package and executes the complete process examples. */
class ProductionOptimizationGuideExamplesTest extends NeqSimTest {
  @TempDir
  Path output;

  private static final String[] GUIDES = { "docs/examples/PRODUCTION_OPTIMIZATION_GUIDE.md",
      "docs/process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md",
      "docs/process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE.md", "docs/wiki/bottleneck_analysis.md" };

  // These declarations represent the explicitly required existing plant, ratings and observations in advanced
  // fragments. They allow compilation to check public APIs without inventing missing engineering evidence.
  private static final String IMPORTS = "import java.util.*;\n" + "import java.util.function.*;\n"
      + "import java.nio.file.*;\n" + "import org.apache.logging.log4j.LogManager;\n"
      + "import org.apache.logging.log4j.Logger;\n" + "import neqsim.thermo.system.*;\n"
      + "import neqsim.process.equipment.*;\n" + "import neqsim.process.equipment.stream.*;\n"
      + "import neqsim.process.equipment.separator.*;\n" + "import neqsim.process.equipment.compressor.*;\n"
      + "import neqsim.process.equipment.heatexchanger.*;\n" + "import neqsim.process.equipment.mixer.*;\n"
      + "import neqsim.process.equipment.splitter.*;\n" + "import neqsim.process.equipment.pump.*;\n"
      + "import neqsim.process.equipment.valve.*;\n" + "import neqsim.process.equipment.pipeline.*;\n"
      + "import neqsim.process.equipment.capacity.*;\n"
      + "import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;\n"
      + "import neqsim.process.processmodel.*;\n" + "import neqsim.process.util.optimizer.*;\n"
      + "import neqsim.process.util.optimizer.ProductionOptimizer.*;\n"
      + "import neqsim.process.util.optimizer.CompressorOptimizationHelper.CompressorBounds;\n"
      + "import neqsim.process.util.optimizer.CompressorOptimizationHelper.TwoStageResult;\n";

  private static final String FIELDS = "static final Logger logger = LogManager.getLogger(\"OptimizationDocumentation\");\n"
      + "static ProcessSystem process, processSystem, processWinter, separationArea, compressionArea,\n"
      + " baseProcess, upgradedProcess;\n" + "static ProcessModel plant, processModel, model, plantA, plantB;\n"
      + "static ProcessModule processModule;\n"
      + "static Stream feed, feedStream, inletStream, stream, gasStream, feedWinter, feedA, feedB,\n"
      + " northStream, southStream, northInlet, southInlet, baseFeed, upgradedFeed, export;\n"
      + "static Compressor compressor, comp1, comp2, comp3, casingA, casingB;\n"
      + "static Separator separator, gasScrubber;\n" + "static PipeBeggsAndBrills pipeline, exportPipeline;\n"
      + "static ThrottlingValve valve;\n" + "static Heater heater;\n" + "static Mixer manifold;\n"
      + "static CapacityConstrainedEquipment equipment;\n" + "static Splitter splitter;\n"
      + "static OptimizationConfig config, baseConfig;\n" + "static ProductionOptimizer optimizer;\n"
      + "static OptimizationResult result, lastResult;\n" + "static List<OptimizationObjective> objectives;\n"
      + "static List<OptimizationConstraint> constraints;\n"
      + "static OptimizationObjective objective, throughputObjective;\n"
      + "static OptimizationConstraint keepPowerLow;\n" + "static ManipulatedVariable split1Var, split2Var;\n"
      + "static CompressorBounds bounds;\n"
      + "static double currentFlow, minFlow, maxFlow, minRate, maxRate, flowRate, originalFlow,\n"
      + " currentRate, proposedFeedRate;\n" + "static SystemInterface reservoirFluid;\n"
      + "static PlantConstraintRegistry registry;\n" + "static String calculationId;\n"
      + "static boolean fullModelConverged;\n" + "static PlantConstraintSample powerSample;\n"
      + "static ProcessModelSimulationEvaluator evaluator;\n"
      + "static ProcessModelDebottleneckStudy.StudyResult study1100, study1150, study1200;\n"
      + "static neqsim.process.equipment.stream.MechanicalShaft shaft;\n"
      + "static neqsim.process.equipment.stream.EnergyPort casingAPort, casingBPort, driverPort;\n"
      + "static CompressorDriver driver;\n" + "static neqsim.process.equipment.energy.Gearbox gearbox;\n"
      + "static neqsim.process.equipment.network.NetworkNomination nomination;\n" + "static int periodIndex;\n";

  @Test
  void everyJavaFragmentCompilesAndCompleteProcessExamplesRun() throws Exception {
    List<File> files = new ArrayList<>();
    String[] documents = new String[GUIDES.length];
    String standalone = null;
    int count = 0;
    for (int page = 0; page < GUIDES.length; page++) {
      documents[page] = new String(Files.readAllBytes(Paths.get(GUIDES[page])), StandardCharsets.UTF_8);
      Matcher blocks = Pattern.compile("^```java\\n(.*?)^```", Pattern.MULTILINE | Pattern.DOTALL)
          .matcher(documents[page]);
      int block = 0;
      while (blocks.find()) {
        String name = "Guide" + page + "Block" + block++;
        String code = blocks.group(1);
        String imports = extractImports(code);
        code = code.replaceAll("(?m)^import .*;\\n?", "");
        String source;
        if (code.contains("public class BottleneckExample")) {
          source = imports + code.replace("BottleneckExample", name);
          standalone = name;
        } else {
          source = imports + "public class " + name + " {\n" + FIELDS + "public static void run() throws Exception {\n"
              + code + "\n}\n}";
        }
        writeSource(files, name, source);
        count++;
      }
    }
    assertTrue(count >= 90, "The complete Java fragment inventory must remain covered");
    String[] headings = { "### Basic Production Rate Optimization",
        "### Full Process Example: Finding Active Constraint", "### Maximum Throughput" };
    for (int index = 0; index < headings.length; index++) {
      String doc = index < 2 ? documents[0] : documents[1];
      Matcher block = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL)
          .matcher(doc.substring(doc.indexOf(headings[index])));
      assertTrue(block.find());
      String code = block.group(1);
      String imports = extractImports(code);
      code = code.replaceAll("(?m)^import .*;\\n?", "");
      String feedName = index == 1 ? "wellFeed" : "feed";
      String name = "CompleteGuide" + index;
      writeSource(files, name,
          imports + "public class " + name + " {\n"
              + "private static final Logger logger = LogManager.getLogger(\"Documentation\");\n"
              + "public static Object[] run() throws Exception {\n" + code + "\nreturn new Object[] {result, process, "
              + feedName + "};\n}\n}");
    }
    compile(files);
    try (
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, getClass().getClassLoader())) {
      for (int index = 0; index < headings.length; index++) {
        Object[] values = (Object[]) loader.loadClass("optimization.documentation.CompleteGuide" + index)
            .getMethod("run").invoke(null);
        OptimizationResult result = (OptimizationResult) values[0];
        ProcessSystem process = (ProcessSystem) values[1];
        Stream feed = (Stream) values[2];
        assertTrue(result.isFeasible(), headings[index] + ": " + result.getInfeasibilityDiagnosis());
        assertEquals(result.getOptimalRate(), feed.getFlowRate("kg/hr"), 1.0e-6);
        assertTrue(result.getOptimalRate() > 1000.0);
        Compressor compressor = (Compressor) process.getUnit(index == 2 ? "Export Compressor" : "Gas Compressor");
        assertTrue(Double.isFinite(compressor.getPower("kW")) && compressor.getPower("kW") > 0.0);
        assertEquals(compressor.getMaxUtilization(), result.getBottleneckUtilization(), 1.0e-4);
        if (index == 1) {
          ThreePhaseSeparator separator = (ThreePhaseSeparator) process.getUnit("HP Separator");
          Separator oilFlash = (Separator) process.getUnit("Oil Flash");
          Pump pump = (Pump) process.getUnit("Oil Pump");
          double products = compressor.getOutletStream().getFlowRate("kg/hr")
              + separator.getWaterOutStream().getFlowRate("kg/hr") + oilFlash.getGasOutStream().getFlowRate("kg/hr")
              + pump.getOutletStream().getFlowRate("kg/hr");
          assertEquals(feed.getFlowRate("kg/hr"), products, 1.0e-3);
          assertTrue(pump.getPower("kW") > 0.0, "The oil export pump must increase pressure");
        }
      }
      assertNotNull(standalone);
      loader.loadClass("optimization.documentation." + standalone).getMethod("main", String[].class).invoke(null,
          (Object) new String[0]);
    }
  }

  private String extractImports(String code) {
    StringBuilder imports = new StringBuilder(IMPORTS);
    Matcher matcher = Pattern.compile("(?m)^import .*;").matcher(code);
    while (matcher.find()) {
      imports.append(matcher.group()).append("\n");
    }
    return imports.toString();
  }

  @Test
  void compressorSingleMultiAndTwoStageExamplesRunOnAThreeTrainProcess() throws Exception {
    String document = new String(Files.readAllBytes(Paths.get(GUIDES[2])), StandardCharsets.UTF_8);
    String setup = "processSystem = new ProcessSystem();\n"
        + "SystemInterface fluid = new SystemSrkEos(298.15, 50.0);\n"
        + "fluid.addComponent(\"methane\", 0.9); fluid.addComponent(\"ethane\", 0.1);\n"
        + "fluid.setMixingRule(\"classic\");\n" + "inletStream = new Stream(\"Inlet Stream\", fluid);\n"
        + "inletStream.setFlowRate(100000.0, \"kg/hr\"); processSystem.add(inletStream);\n"
        + "feedStream = inletStream; process = processSystem;\n"
        + "splitter = new Splitter(\"Compressor Splitter\", inletStream, 3);\n"
        + "splitter.setSplitFactors(new double[] {1.0/3.0, 1.0/3.0, 1.0/3.0});\n" + "processSystem.add(splitter);\n"
        + "Compressor[] trains = new Compressor[3];\n" + "for (int i = 0; i < 3; i++) {\n"
        + "trains[i] = new Compressor(\"Train \" + i, splitter.getSplitStream(i));\n"
        + "trains[i].setOutletPressure(110.0, \"bara\");\n"
        + "trains[i].setUsePolytropicCalc(true); trains[i].setPolytropicEfficiency(0.78);\n"
        + "trains[i].getMechanicalDesign().setMaxDesignPower(2000.0); processSystem.add(trains[i]);\n}\n"
        + "processSystem.run();\nfor (Compressor train : trains) {\n"
        + "train.generateCompressorChart(\"normal curves\", 5); train.setSolveSpeed(true);\n"
        + "train.reinitializeCapacityConstraints();\n}\nprocessSystem.run();\n"
        + "comp1 = trains[0]; comp2 = trains[1]; comp3 = trains[2]; compressor = comp1;\n"
        + "optimizer = new ProductionOptimizer(); currentFlow = 100000.0; originalFlow = currentFlow;\n"
        + "minFlow = 90000.0; maxFlow = 110000.0;\n"
        + "throughputObjective = new OptimizationObjective(\"throughput\",\n"
        + "ps -> ((StreamInterface) ps.getUnit(\"Inlet Stream\")).getFlowRate(\"kg/hr\"), 1.0);\n";
    String single = sectionCode(document, "## Single-Variable Optimization");
    String multi = sectionCode(document, "## Multi-Variable Optimization");
    String stages = sectionCode(document, "## Two-Stage Optimization (Recommended)");
    String helper = sectionCode(document, "### Two-Stage Helper Method (Simplified)");
    String source = IMPORTS + "public class CompressorGuideExecution {\n" + FIELDS + "public static void setup() {\n"
        + setup + "}\n" + "public static OptimizationResult single() { setup();\n" + single + "\nreturn result; }\n"
        + "public static OptimizationResult multi() { setup();\n" + multi
        + "\nCompressorGuideExecution.split1Var = split1Var;\n"
        + "CompressorGuideExecution.split2Var = split2Var;\nreturn result; }\n"
        + "public static OptimizationResult stages() { multi();\n" + stages + "\nreturn stage2Result; }\n"
        + "public static TwoStageResult helper() { setup();\n" + helper + "\nreturn result; }\n} ";
    List<File> files = new ArrayList<>();
    writeSource(files, "CompressorGuideExecution", source);
    compile(files);
    try (
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, getClass().getClassLoader())) {
      Class<?> example = loader.loadClass("optimization.documentation.CompressorGuideExecution");
      for (String method : Arrays.asList("single", "multi", "stages")) {
        OptimizationResult result = (OptimizationResult) example.getMethod(method).invoke(null);
        assertTrue(result.isFeasible(), method + ": " + result.getInfeasibilityDiagnosis());
        assertTrue(result.getOptimalRate() >= 90000.0 && result.getOptimalRate() <= 115000.0);
      }
      CompressorOptimizationHelper.TwoStageResult result = (CompressorOptimizationHelper.TwoStageResult) example
          .getMethod("helper").invoke(null);
      assertTrue(result.getStage1Result().isFeasible());
      assertTrue(result.getStage2Result().isFeasible());
      assertEquals(1.0, result.getTrainSplits().values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
      assertEquals(result.getTotalFlow(),
          result.getTrainFlows().values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-5);
      for (double utilization : result.getTrainUtilizations().values()) {
        assertTrue(utilization >= 0.0 && utilization <= 1.0, "Driver utilization must be a fraction");
      }
    }
  }

  private String sectionCode(String document, String heading) {
    Matcher block = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL)
        .matcher(document.substring(document.indexOf(heading)));
    assertTrue(block.find(), heading);
    return block.group(1).replaceAll("(?m)^import .*;\\n?", "");
  }

  private void writeSource(List<File> files, String name, String source) throws Exception {
    Path file = output.resolve(name + ".java");
    // Use a package outside neqsim.process.util.optimizer so inaccessible nested types cannot compile accidentally.
    Files.write(file, ("package optimization.documentation;\n" + source).getBytes(StandardCharsets.UTF_8));
    files.add(file.toFile());
  }

  private void compile(List<File> files) throws Exception {
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
      assertTrue(compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files))
          .call(), diagnostics.getDiagnostics().toString());
    }
  }
}
