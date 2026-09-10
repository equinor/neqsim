package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable API contracts used by the external optimizer, module, MPC and VFP guides.
 */
class OptimizationIntegrationDocumentationTest {
  @TempDir
  Path documentationOutput;

  @Test
  void utilityApiFragmentsCompileAndCompleteVfpExampleRuns() throws Exception {
    List<String> utility = javaBlocks("docs/util/optimizer_guide.md");
    assertEquals(34, utility.size(), "Cover every Java API example in the utility guide");
    String imports = "import java.util.*;\nimport org.apache.logging.log4j.*;\n"
        + "import neqsim.process.equipment.*;\nimport neqsim.process.equipment.capacity.*;\n"
        + "import neqsim.process.equipment.compressor.*;\nimport neqsim.process.equipment.stream.*;\n"
        + "import neqsim.process.processmodel.*;\nimport neqsim.process.util.optimizer.*;\n"
        + "import neqsim.process.util.optimizer.ProductionOptimizer.*;\n"
        + "import neqsim.process.util.optimizer.ProcessOptimizationEngine.SearchAlgorithm;\n";
    String fields = "private static final Logger logger = LogManager.getLogger(\"UtilityDocumentation\");\n"
        + "ProcessSystem process; StreamInterface feed; ProductionOptimizer optimizer;\n"
        + "OptimizationConfig config; OptimizationResult result; ProcessOptimizationEngine engine;\n"
        + "OptimizationConstraint powerLimit; OptimizationObjective throughputObj;\n"
        + "MultiObjectiveOptimizer moOptimizer; ParetoFront pareto; ProcessSimulationEvaluator evaluator;\n"
        + "double optimalFlow, inletPressure, outletPressure, rawObjective;\n";
    List<File> files = new ArrayList<>();
    for (int i = 0; i < utility.size(); i++) {
      String block = utility.get(i).replaceAll("(?m)^import .*;\\n", "");
      String name;
      String source;
      if (block.contains("public class CustomCapacityStrategy")) {
        name = "CustomCapacityStrategy";
        source = imports + block;
      } else if (block.contains("public interface ProcessConstraint")) {
        name = "ProcessConstraint";
        source = "package documentation.contract;\n" + imports + block;
      } else {
        name = "UtilityDocumentation" + i;
        source = imports + "public class " + name + " {\n";
        source += i == 0 ? block : fields + "void example() throws Exception {\n" + block + "}\n";
        source += "}\n";
      }
      Path file = documentationOutput.resolve(name + ".java");
      Files.write(file, source.getBytes(StandardCharsets.UTF_8));
      files.add(file.toFile());
    }
    List<String> vfp = javaBlocks("docs/fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION.md");
    assertEquals(10, vfp.size(), "Track the complete example and its configuration fragments");
    String vfpSource = vfp.stream().filter(block -> block.contains("public class VFPGenerationExample")).findFirst()
        .get();
    Path vfpFile = documentationOutput.resolve("VFPGenerationExample.java");
    Files.write(vfpFile, vfpSource.getBytes(StandardCharsets.UTF_8));
    files.add(vfpFile.toFile());
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Use a full JDK to compile the documentation");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<>(Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d",
          documentationOutput.toString(), "-encoding", "UTF-8", "-proc:none"));
      if ("1.8".equals(System.getProperty("java.specification.version"))) {
        options.addAll(Arrays.asList("-source", "8", "-target", "8"));
      } else {
        options.addAll(Arrays.asList("--release", "8"));
      }
      assertTrue(compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files))
          .call(), diagnostics.getDiagnostics().toString());
    }
    Path csv = documentationOutput.resolve("pressure_grid.csv");
    try (URLClassLoader loader = new URLClassLoader(new URL[] { documentationOutput.toUri().toURL() },
        getClass().getClassLoader())) {
      loader.loadClass("VFPGenerationExample").getMethod("main", String[].class).invoke(null,
          (Object) new String[] { csv.toString() });
    }
    List<String> rows = Files.readAllLines(csv, StandardCharsets.UTF_8);
    assertEquals(17, rows.size());
    for (String row : rows.subList(1, rows.size())) {
      String[] values = row.split(",");
      double requiredInlet = Double.parseDouble(values[4]);
      assertTrue(Double.isFinite(requiredInlet));
      assertTrue(requiredInlet >= Double.parseDouble(values[1]));
      assertEquals("true", values[5]);
    }
  }

  private List<String> javaBlocks(String documentPath) throws Exception {
    String source = new String(Files.readAllBytes(Paths.get(documentPath)), StandardCharsets.UTF_8);
    Matcher matcher = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL).matcher(source);
    List<String> blocks = new ArrayList<>();
    while (matcher.find()) {
      blocks.add(matcher.group(1));
    }
    return blocks;
  }

  private ProcessSystem compressionProcess() {
    SystemInterface fluid = new SystemSrkEos(303.15, 20.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(50000.0, "kg/hr");
    Stream feed = new Stream("feed", fluid);
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(80.0);
    Cooler cooler = new Cooler("cooler", compressor.getOutletStream());
    cooler.setOutletTemperature(313.15);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(cooler);
    process.run();
    return process;
  }

  @Test
  void externalEvaluatorUsesTypedPowerAndIsolatedCandidateState() {
    ProcessSystem process = compressionProcess();
    ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
    evaluator.setCloneForEvaluation(true);
    evaluator.addParameter("feed", "flowRate", 10000.0, 100000.0, "kg/hr");
    evaluator.addParameter("compressor", "outletPressure", 50.0, 120.0, "bara");
    evaluator.addObjective("power", p -> ((Compressor) p.getUnit("compressor")).getPower("kW"));
    evaluator.addConstraintLowerBound("minOutletPressure",
        p -> ((Cooler) p.getUnit("cooler")).getOutletStream().getPressure("bara"), 60.0);
    evaluator.addConstraintUpperBound("maxOutletTemp",
        p -> ((Cooler) p.getUnit("cooler")).getOutletStream().getTemperature("C"), 50.0);
    double[] target = new double[] { 10000.0, 60.0 };
    ProcessSimulationEvaluator.EvaluationResult reference = evaluator.evaluate(target);
    assertTrue(reference.isFeasible());
    assertTrue(reference.isSimulationConverged());
    assertTrue(reference.getObjective() > 400.0 && reference.getObjective() < 500.0);
    evaluator.evaluate(new double[] { 55000.0, 85.0 });
    evaluator.evaluate(new double[] { 55001.0, 85.0 });
    evaluator.evaluate(new double[] { 55000.0, 85.01 });
    ProcessSimulationEvaluator.EvaluationResult repeated = evaluator.evaluate(target);
    assertEquals(reference.getObjective(), repeated.getObjective(), 1.0e-6);
    assertEquals(50000.0, ((StreamInterface) process.getUnit("feed")).getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(80.0, ((Compressor) process.getUnit("compressor")).getOutletStream().getPressure(), 1.0e-9);
  }

  @Test
  void moduleEvaluatorOverloadsAndConstraintConversionPreserveBothBounds() {
    ProcessSystem process = compressionProcess();
    ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
    evaluator.addParameter("feed", "flowRate", 1000.0, 20000.0, "kg/hr");
    evaluator.addParameter("feed", "pressure", 30.0, 100.0, "bara");
    evaluator.addObjective("throughput", p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"),
        ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
    evaluator.addConstraintRange("feed range", p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"), 5000.0,
        15000.0);
    ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(new double[] { 10000.0, 60.0 });
    assertTrue(result.isFeasible());
    assertEquals(-10000.0, result.getObjective(), 1.0e-6);
    assertEquals(2, evaluator.getConstraints().get(0).toOptimizationConstraints().size());
    assertFalse(evaluator.evaluate(new double[] { 3000.0, 60.0 }).isFeasible());
    assertFalse(evaluator.evaluate(new double[] { 17000.0, 60.0 }).isFeasible());
  }

  @Test
  void customCapacityStrategyProvidesLivePhysicalPressure() {
    ProcessSystem process = compressionProcess();
    Compressor compressor = (Compressor) process.getUnit("compressor");
    InstalledPressureStrategy strategy = new InstalledPressureStrategy();
    CapacityConstraint pressure = strategy.getConstraints(compressor).get("maxPressure");
    assertEquals(80.0, pressure.getCurrentValue(), 1.0e-9);
    assertEquals(100.0, pressure.getMaxValue(), 1.0e-9);
    compressor.setOutletPressure(110.0);
    process.run();
    assertTrue(pressure.isHardLimitExceeded());
  }

  @Test
  void mpcTargetUsesRealProductionOptimizerAndDeclaredHardFeedLimit() {
    SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr")
        .searchMode(SearchMode.BINARY_FEASIBILITY).tolerance(10.0).maxIterations(30);
    OptimizationConstraint installedFeedLimit = OptimizationConstraint.lessThan("Installed feed capacity",
        p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"), 15000.0, ConstraintSeverity.HARD, 100.0,
        "Maximum installed feed mass rate in kg/hr");
    OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config, Collections.emptyList(),
        Collections.singletonList(installedFeedLimit));
    assertTrue(result.isFeasible());
    assertTrue(result.getOptimalRate() <= 15000.0);
    assertTrue(result.getOptimalRate() >= 14980.0);
    OptimizationObjective throughput = new OptimizationObjective("throughput",
        p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);
    OptimizationObjective lowRate = new OptimizationObjective("low rate",
        p -> ((StreamInterface) p.getUnit("feed")).getFlowRate("kg/hr"), 1.0, ObjectiveType.MINIMIZE);
    assertNotNull(new ProductionOptimizer().optimizePareto(process, feed, config, Arrays.asList(throughput, lowRate),
        Collections.singletonList(installedFeedLimit)));
  }

  @Test
  void vfpExampleRegistersOutletStreamAndRetainsTotalMassRate() {
    SystemInterface reference = new SystemSrkEos(288.15, 1.01325);
    reference.addComponent("methane", 0.5);
    reference.addComponent("n-heptane", 0.5);
    reference.setMixingRule("classic");
    reference.setMultiPhaseCheck(true);
    FluidMagicInput input = FluidMagicInput.fromFluid(reference);
    input.separateToStandardConditions();
    RecombinationFlashGenerator recombination = new RecombinationFlashGenerator(input);
    Supplier<ProcessSystem> factory = () -> {
      ProcessSystem process = new ProcessSystem();
      Stream feed = new Stream("feed", reference.clone());
      feed.setFlowRate(1000.0, "kg/hr");
      process.add(feed);
      AdiabaticPipe pipe = new AdiabaticPipe("pipe", feed);
      pipe.setLength(1000.0);
      pipe.setDiameter(0.15);
      process.add(pipe);
      pipe.getOutletStream().setName("outlet");
      process.add(pipe.getOutletStream());
      return process;
    };
    MultiScenarioVFPGenerator generator = new MultiScenarioVFPGenerator(factory, "feed", "outlet");
    generator.setFlashGenerator(recombination);
    generator.setFlowRateUnit("kg/hr");
    generator.setFlowRates(new double[] { 1000.0, 3000.0 });
    generator.setOutletPressures(new double[] { 20.0, 30.0 });
    generator.setWaterCuts(new double[] { 0.0, 0.3 });
    generator.setGORs(new double[] { 80.0, 200.0 });
    generator.setInletTemperature(353.15);
    generator.setMinInletPressure(5.0);
    generator.setMaxInletPressure(150.0);
    generator.setPressureTolerance(0.2);
    generator.setEnableParallel(false);
    MultiScenarioVFPGenerator.VFPTable table = generator.generateVFPTable();
    assertEquals(16, table.getTotalPoints());
    assertEquals(16, table.getFeasibleCount());
    for (int r = 0; r < 2; r++) {
      for (int p = 0; p < 2; p++) {
        for (int w = 0; w < 2; w++) {
          for (int g = 0; g < 2; g++) {
            assertTrue(table.getBHP(r, p, w, g) >= generator.getOutletPressures()[p]);
            assertTrue(table.getBHP(r, p, w, g) < 150.0);
          }
        }
      }
    }
    assertTrue(generator.toVFPEXPString(1).contains("Flow rates (kg/hr)"));
    assertTrue(recombination.validateGOR(200.0, 0.3, 0.05));
  }

  private static class InstalledPressureStrategy extends CompressorCapacityStrategy {
    @Override
    public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
      Map<String, CapacityConstraint> constraints = new LinkedHashMap<>(super.getConstraints(equipment));
      Compressor compressor = (Compressor) equipment;
      constraints.put("maxPressure",
          new CapacityConstraint("maxPressure", "bara", CapacityConstraint.ConstraintType.HARD).setUnit("bara")
              .setDesignValue(90.0).setMaxValue(100.0).setSeverity(CapacityConstraint.ConstraintSeverity.HARD)
              .setValueSupplier(() -> compressor.getOutletStream().getPressure("bara")));
      return constraints;
    }
  }
}
