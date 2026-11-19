package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies every Java API pattern in the thermal-conductivity documentation. */
public class ThermalConductivityDocumentationTest {
  private static final Path GUIDE =
      Paths.get("docs", "physical_properties", "thermal_conductivity_models.md");
  private static final String EXAMPLE_MARKER = "<!-- thermal-conductivity-example: basic -->";
  private static final String EXAMPLE_CLASS = "ThermalConductivityQuickStart";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  private static void flashAndInitialize(SystemInterface fluid) {
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();
  }

  private static void assertPositiveFinite(double value) {
    assertTrue(Double.isFinite(value), "Conductivity must be finite");
    assertTrue(value > 0.0, "Conductivity must be positive: " + value);
  }

  /**
   * Compiles and executes the exact marked quick start published in the guide.
   *
   * @throws Exception if the source cannot be read, compiled, loaded, or invoked
   */
  @Test
  void publishedBasicExampleCompilesAndRuns() throws Exception {
    String source = markedPublishedExample();
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));

    try (URLClassLoader loader = compilePublishedExample(source)) {
      Class<?> example = loader.loadClass(EXAMPLE_CLASS);
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java source from the thermal-conductivity guide.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedPublishedExample() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(EXAMPLE_MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + EXAMPLE_MARKER);
    assertTrue(markerPosition == markdown.lastIndexOf(EXAMPLE_MARKER),
        "Marker must be unique: " + EXAMPLE_MARKER);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker");
    String source = fence.group(1);
    assertTrue(source.contains("public class " + EXAMPLE_CLASS));
    return source;
  }

  /**
   * Compiles the published Java 8 source and returns a class loader for it.
   *
   * @param source exact published Java source
   * @return class loader containing the compiled example
   * @throws Exception if the source or class output cannot be created
   */
  private URLClassLoader compilePublishedExample(String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(EXAMPLE_CLASS));
    Path javaFile = classes.resolve(EXAMPLE_CLASS + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  diagnostics,
                  manager,
                  null,
                  Arrays.asList(
                      "-source",
                      "8",
                      "-target",
                      "8",
                      "-proc:none",
                      "-classpath",
                      classpath,
                      "-d",
                      classes.toString()),
                  null,
                  manager.getJavaFileObjects(javaFile.toFile()))
              .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }

  /** Verifies the basic natural-gas example, including its formerly missing interface import. */
  @Test
  void basicConductivityExample() {
    SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    flashAndInitialize(fluid);

    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
  }

  /** Verifies the self-contained model-comparison example. */
  @Test
  void compareConductivityModelsExample() {
    String[] models = {"PFCT", "Chung-dense"};
    double[] conductivities = new double[models.length];

    for (int i = 0; i < models.length; i++) {
      SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
      fluid.addComponent("methane", 0.85);
      fluid.addComponent("ethane", 0.10);
      fluid.addComponent("propane", 0.05);
      fluid.setMixingRule("classic");
      flashAndInitialize(fluid);

      fluid.getPhase("gas").getPhysicalProperties().setConductivityModel(models[i]);
      fluid.getPhase("gas").initPhysicalProperties();
      conductivities[i] = fluid.getPhase("gas").getThermalConductivity("W/mK");
      assertPositiveFinite(conductivities[i]);
    }
  }

  /** Verifies cloning, pressure units, flashing, and property access in the pressure sweep. */
  @Test
  void conductivityPressureSweepExample() {
    SystemInterface baseFluid = new SystemSrkEos(350.0, 10.0);
    baseFluid.addComponent("methane", 1.0);
    baseFluid.setMixingRule("classic");

    double[] pressuresBara = {10, 50, 100, 150, 200};
    for (double pressureBara : pressuresBara) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setPressure(pressureBara, "bara");
      flashAndInitialize(fluid);
      assertPositiveFinite(fluid.getPhase(0).getThermalConductivity("W/mK"));
    }
  }

  /** Verifies phase-name guards and conductivity access for the multiphase example. */
  @Test
  void twoPhaseConductivityExample() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-pentane", 0.5);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    flashAndInitialize(fluid);

    assertTrue(fluid.hasPhaseType("gas"), "Expected a gas phase");
    assertTrue(fluid.hasPhaseType("oil"), "Expected an oil phase");
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertPositiveFinite(fluid.getPhase("oil").getThermalConductivity("W/mK"));
  }

  /** Verifies the case-sensitive model keys used by the page's model-specific snippets. */
  @Test
  void documentedConductivityModelKeys() {
    SystemInterface gas = new SystemSrkEos(300.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    flashAndInitialize(gas);
    for (String model : new String[] {"PFCT", "Chung", "Chung-dense"}) {
      gas.getPhase("gas").getPhysicalProperties().setConductivityModel(model);
      gas.getPhase("gas").initPhysicalProperties();
      assertPositiveFinite(gas.getPhase("gas").getThermalConductivity("W/mK"));
    }
    gas.getPhase("gas").getPhysicalProperties().setConductivityModel("friction theory");
    gas.getPhase("gas").initPhysicalProperties();
    assertPositiveFinite(gas.getPhase("gas").getThermalConductivity("W/mK"));

    SystemInterface liquid = new SystemSrkEos(300.0, 10.0);
    liquid.addComponent("n-heptane", 1.0);
    liquid.setMixingRule("classic");
    flashAndInitialize(liquid);
    liquid.getPhase("oil").getPhysicalProperties().setConductivityModel("polynom");
    liquid.getPhase("oil").initPhysicalProperties();
    assertPositiveFinite(liquid.getPhase("oil").getThermalConductivity("W/mK"));

    SystemInterface carbonDioxide = new SystemSpanWagnerEos(300.0, 10.0);
    flashAndInitialize(carbonDioxide);
    carbonDioxide.getPhase(0).getPhysicalProperties().setConductivityModel("CO2Model");
    carbonDioxide.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(carbonDioxide.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface water = new SystemSrkEos(400.0, 1.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    flashAndInitialize(water);
    water.getPhase(0).getPhysicalProperties().setConductivityModel("WaterModel");
    water.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(water.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface hydrogen = new SystemSrkEos(300.0, 1.0);
    hydrogen.addComponent("hydrogen", 1.0);
    hydrogen.setMixingRule("classic");
    flashAndInitialize(hydrogen);
    hydrogen.getPhase(0).getPhysicalProperties().setConductivityModel("H2Model");
    hydrogen.getPhase(0).initPhysicalProperties();
    assertPositiveFinite(hydrogen.getPhase(0).getThermalConductivity("W/mK"));

    SystemInterface aqueous = new SystemSrkCPAstatoil(298.15, 1.013);
    aqueous.addComponent("water", 0.7);
    aqueous.addComponent("MEG", 0.3);
    aqueous.setMixingRule(10);
    aqueous.setMultiPhaseCheck(true);
    flashAndInitialize(aqueous);
    int aqueousPhaseIndex = 0;
    for (int i = 0; i < aqueous.getNumberOfPhases(); i++) {
      String phaseType = aqueous.getPhase(i).getType().toString();
      if (phaseType.contains("LIQUID") || phaseType.contains("AQUEOUS")) {
        aqueousPhaseIndex = i;
        break;
      }
    }
    aqueous.getPhase(aqueousPhaseIndex).getPhysicalProperties().setConductivityModel("Filippov");
    aqueous.getPhase(aqueousPhaseIndex).initPhysicalProperties();
    assertPositiveFinite(aqueous.getPhase(aqueousPhaseIndex).getThermalConductivity("W/mK"));
  }
}
