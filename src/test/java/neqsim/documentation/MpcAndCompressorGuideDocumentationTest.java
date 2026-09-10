package neqsim.documentation;

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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mpc.ProcessLinkedMPC;

/** Compiles and executes the actual Java blocks published in the MPC and compressor guides. */
class MpcAndCompressorGuideDocumentationTest {
  private static final String MPC_GUIDE = "docs/integration/neqsim_industrial_mpc_integration.md";
  private static final String COMPRESSOR_GUIDE = "docs/process/equipment/compressor_curves.md";

  @TempDir
  Path temporaryDirectory;

  @Test
  void completeMpcExampleExportsFiniteGainsAndPreservesMassBalance() throws Exception {
    String source = codeAfter(MPC_GUIDE, "### Complete Separator Control Example", "java");
    Path output = temporaryDirectory.resolve("mpc-output");
    try (URLClassLoader loader = compile("SeparatorMPCIntegration", source)) {
      ProcessLinkedMPC mpc = (ProcessLinkedMPC) loader.loadClass("SeparatorMPCIntegration")
          .getMethod("configureAndExport", String.class).invoke(null, output.toString());
      assertTrue(mpc.getLinearizationResult().isSuccessful());
      assertEquals(2, mpc.getManipulatedVariables().size());
      assertEquals(2, mpc.getControlledVariables().size());
      assertEquals(1, mpc.getDisturbanceVariables().size());
      for (double[] disturbanceRow : mpc.getLinearizationResult().getDisturbanceGainMatrix()) {
        for (double gain : disturbanceRow) {
          assertTrue(Double.isFinite(gain));
        }
      }
      double[][] gains = mpc.getLinearizationResult().getGainMatrix();
      for (double[] row : gains) {
        for (double gain : row) {
          assertTrue(Double.isFinite(gain));
        }
      }

      StreamInterface feed = (StreamInterface) mpc.getProcessSystem().getUnit("Feed");
      Separator separator = (Separator) mpc.getProcessSystem().getUnit("HP Separator");
      double gasFlow = separator.getGasOutStream().getFlowRate("kg/hr");
      double liquidFlow = separator.getLiquidOutStream().getFlowRate("kg/hr");
      assertEquals(500.0, feed.getFlowRate("kg/hr"), 1.0e-6);
      assertEquals(50.0, feed.getPressure("bara"), 1.0e-8);
      assertEquals(298.15, feed.getTemperature("K"), 1.0e-8);
      assertTrue(gasFlow > 0.0);
      assertEquals(feed.getFlowRate("kg/hr"), gasFlow + liquidFlow, 1.0e-5);
      assertEquals(gasFlow / feed.getFlowRate("kg/hr"), gains[0][0], 1.0e-5);
      assertEquals(1.0, gains[1][1], 1.0e-8);

      JsonObject model = readJson(output.resolve("hp_sep_model.json"));
      assertEquals("step_response_model", model.get("format").getAsString());
      assertEquals(10.0, model.get("sampleTime").getAsDouble(), 0.0);
      JsonArray responses = model.getAsJsonArray("stepResponses");
      assertEquals(4, responses.size());
      for (int index = 0; index < responses.size(); index++) {
        JsonObject response = responses.get(index).getAsJsonObject();
        assertEquals(60.0, response.get("timeConstant").getAsDouble(), 0.0);
        assertEquals(60, response.getAsJsonArray("coefficients").size());
      }
      assertTrue(read(output.resolve("hp_sep_model.csv")).startsWith("Step,Time,"));
      assertEquals(2, readJson(output.resolve("hp_sep_sensors.json")).getAsJsonArray("softSensors").size());
      assertTrue(Files.size(output.resolve("hp_sep_config.json")) > 0);
      assertTrue(Files.size(output.resolve("hp_sep_subrmodl.cnf")) > 0);
    }
  }

  @Test
  void mpcWorkflowFragmentsProducePropertyTablesAndGasQualityResults() throws Exception {
    String complete = codeAfter(MPC_GUIDE, "### Complete Separator Control Example", "java");
    String imports = complete.substring(0, complete.indexOf("public class"));
    StringBuilder source = new StringBuilder(imports);
    source.append("import java.io.BufferedWriter;\n").append("import java.nio.charset.StandardCharsets;\n")
        .append("import java.util.Locale;\n").append("import neqsim.standards.gasquality.Standard_ISO6976_2016;\n")
        .append("public class MpcGuideFragments {\n")
        .append("private static final Logger logger = LogManager.getLogger(MpcGuideFragments.class);\n")
        .append("public static double[] run(String outputDirectory) throws Exception {\n");
    for (String heading : Arrays.asList("### Step 1: Build NeqSim Process Model", "### Step 2: Configure MPC Variables",
        "### Step 3: Generate Step Response Models", "### Pattern 2: Property Table Lookup",
        "### Pattern 3: Gain Scheduling", "### Pattern 4: Nonlinear MPC with Steady-State Solver",
        "### Example: Separator Train Optimization", "### Phase Properties", "### Molecular Weight Estimation",
        "### Heating Value Calculation", "### Operating Point Identification", "### Bias Detection",
        "### NeqSim's Derivative Calculator", "### Derivative Methods", "### Single Derivative",
        "### Gradient (One Output, All Inputs)", "### Export for External Systems")) {
      source.append(codeAfter(MPC_GUIDE, heading, "java").replaceAll("(?m)^import [^;]+;\\s*", "")).append('\n');
    }
    source.append("return new double[] {gcv, ncv, wobbeIndex, gasFlowBias.getBias(),")
        .append("feed.getFlowRate(\"kg/hr\"), feed.getPressure(\"bara\")};\n}\n}\n");
    Path output = temporaryDirectory.resolve("fragment-output");
    try (URLClassLoader loader = compile("MpcGuideFragments", source.toString())) {
      double[] values = (double[]) loader.loadClass("MpcGuideFragments").getMethod("run", String.class).invoke(null,
          output.toString());
      assertTrue(values[0] > values[1]);
      assertTrue(values[1] > 0.0);
      assertTrue(Double.isFinite(values[2]) && values[2] > 0.0);
      assertEquals(5.0, values[3], 1.0e-10);
      assertEquals(500.0, values[4], 1.0e-6);
      assertEquals(50.0, values[5], 1.0e-6);
      assertEquals(10, Files.readAllLines(output.resolve("property_table.csv"), StandardCharsets.UTF_8).size());
      assertEquals(2, readJson(output.resolve("gas_soft_sensors.json")).getAsJsonArray("softSensors").size());
      assertTrue(Files.size(output.resolve("jacobian.csv")) > 0);
    }
  }

  @Test
  void compressorJsonExampleLoadsThePublishedChartAndRunsWithinItsRange() throws Exception {
    Path chart = temporaryDirectory.resolve("compressor_curve.json");
    Files.write(chart, codeAfter(COMPRESSOR_GUIDE, "#### JSON File Format", "json").getBytes(StandardCharsets.UTF_8));
    String source = codeAfter(COMPRESSOR_GUIDE, "#### Java Usage", "java");
    try (URLClassLoader loader = compile("CompressorJsonExample", source)) {
      Compressor compressor = (Compressor) loader.loadClass("CompressorJsonExample").getMethod("run", String.class)
          .invoke(null, chart.toString());
      assertEquals(6327.9, compressor.getSpeed(), 1.0e-8);
      assertEquals(3, compressor.getCompressorChart().getSpeeds().length);
      double actualFlow = compressor.getInletStream().getFlowRate("m3/hr");
      assertEquals(20000.0, actualFlow, 1.0e-5);
      assertTrue(actualFlow > compressor.getCompressorChart().getSurgeFlowAtSpeed(compressor.getSpeed()));
      assertTrue(actualFlow < compressor.getCompressorChart().getStoneWallFlowAtSpeed(compressor.getSpeed()));
      assertTrue(Double.isFinite(compressor.getPower("kW")) && compressor.getPower("kW") > 0.0);
      assertTrue(compressor.getOutletPressure() > compressor.getInletStream().getPressure("bara"));
      assertTrue(compressor.getPolytropicEfficiency() > 0.0 && compressor.getPolytropicEfficiency() <= 1.0);
      assertTrue(Double.isFinite(compressor.getPolytropicHead("kJ/kg")));
      assertEquals(compressor.getInletStream().getFlowRate("kg/hr"), compressor.getOutletStream().getFlowRate("kg/hr"),
          1.0e-5);
    }
  }

  @Test
  void modelValidationHelperRejectsInvalidPredictions() throws Exception {
    try (URLClassLoader loader = compile("ModelValidator",
        codeAfter(MPC_GUIDE, "### Continuous Model Monitoring", "java"))) {
      Class<?> validator = loader.loadClass("ModelValidator");
      assertTrue((Boolean) validator.getMethod("withinTolerance", double[].class, double[].class, double[].class)
          .invoke(null, new double[] { 100.0 }, new double[] { 100.5 }, new double[] { 1.0 }));
      assertFalse((Boolean) validator.getMethod("withinTolerance", double[].class, double[].class, double[].class)
          .invoke(null, new double[] { 100.0 }, new double[] { Double.NaN }, new double[] { 1.0 }));
    }
  }

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
    return new URLClassLoader(new URL[] { classes.toUri().toURL() }, getClass().getClassLoader());
  }

  private static String codeAfter(String document, String heading, String language) throws Exception {
    String markdown = read(Paths.get(document));
    int start = markdown.indexOf(heading + "\n");
    assertTrue(start >= 0, "Missing documentation heading: " + heading);
    Matcher code = Pattern.compile("```" + language + "\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(markdown.substring(start));
    assertTrue(code.find(), "Missing code block below " + heading);
    return code.group(1);
  }

  private static String read(Path file) throws Exception {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  private static JsonObject readJson(Path file) throws Exception {
    return JsonParser.parseString(read(file)).getAsJsonObject();
  }
}
