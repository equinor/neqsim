package neqsim.physicalproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.FrictionTheoryViscosityMethod;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LBCViscosityMethod;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Executable coverage for {@code docs/physical_properties/README.md}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class PhysicalPropertiesPackageDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "physical_properties", "README.md");
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Compiles and executes all three exact complete Java programs published in the guide.
   *
   * @throws Exception if guide source cannot be compiled or executed
   */
  @Test
  void literalGuideProgramsCompileAndRun() throws Exception {
    String[][] examples = {{"<!-- physical-properties-example: overview -->", "PhysicalPropertiesOverview"},
        {"<!-- physical-properties-example: phase-specific -->", "PhaseSpecificPhysicalProperties"},
        {"<!-- physical-properties-example: temperature-sweep -->", "PhysicalPropertyTemperatureSweep"}};

    for (String[] example : examples) {
      String source = markedExampleSource(example[0], example[1]);
      assertFalse(source.contains("System.out"));
      assertFalse(source.contains("System.err"));

      try (URLClassLoader loader = compile(example[1], source)) {
        Class<?> exampleClass = loader.loadClass(example[1]);
        exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      }
    }
  }

  @Test
  void basicOverviewExampleCalculatesPositiveGasProperties() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.DEFAULT);
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertPositiveFinite(fluid.getPhase("gas").getDensity("kg/m3"));
    assertPositiveFinite(fluid.getPhase("gas").getPhysicalProperties().getKinematicViscosity());
  }

  @Test
  void diffusivityGuideUsesPublicBinaryAndEffectiveAccessors() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("nitrogen", 0.50);
    fluid.setMixingRule("classic");

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    PhysicalProperties properties = fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    fluid.getPhase("gas").initPhysicalProperties();

    assertPositiveFinite(properties.getDiffusionCoefficient("methane", "nitrogen"));

    properties.calcEffectiveDiffusionCoefficients();
    assertPositiveFinite(properties.getEffectiveDiffusionCoefficient("methane"));

    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
    fluid.initPhysicalProperties();
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
  }

  @Test
  void modelSetSelectionUsesPhysicalPropertyModelApi() {
    SystemInterface fluid = createNaturalGas(298.15, 50.0);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.GLYCOL);
    fluid.initPhysicalProperties();

    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    fluid.initPhysicalProperties("DENSITY");
    assertPositiveFinite(fluid.getPhase("gas").getDensity("kg/m3"));
    fluid.initPhysicalProperties("VISCOSITY");
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    fluid.initPhysicalProperties("CONDUCTIVITY");
    assertPositiveFinite(fluid.getPhase("gas").getThermalConductivity("W/mK"));
    assertThrows(RuntimeException.class, () -> fluid.initPhysicalProperties("GLYCOL"));
  }

  @Test
  void phaseSpecificModelExampleRecalculatesBothPhases() {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("n-pentane", 0.50);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    assertTrue(fluid.hasPhaseType("gas"));
    assertTrue(fluid.hasPhaseType("oil"));

    fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
    fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");

    fluid.getPhase("gas").initPhysicalProperties();
    fluid.getPhase("oil").initPhysicalProperties();

    assertInstanceOf(FrictionTheoryViscosityMethod.class,
        fluid.getPhase("gas").getPhysicalProperties().getViscosityModel());
    assertInstanceOf(LBCViscosityMethod.class, fluid.getPhase("oil").getPhysicalProperties().getViscosityModel());
    assertPositiveFinite(fluid.getPhase("gas").getViscosity("cP"));
    assertPositiveFinite(fluid.getPhase("oil").getViscosity("cP"));
  }

  @Test
  void clonedTemperatureSweepFlashesEachClone() {
    SystemInterface baseFluid = createNaturalGas(298.15, 50.0);
    double[] temperaturesK = {280.0, 300.0, 320.0};

    for (double temperatureK : temperaturesK) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setTemperature(temperatureK, "K");

      new ThermodynamicOperations(fluid).TPflash();
      fluid.initPhysicalProperties();

      assertPositiveFinite(fluid.getPhase("gas").getViscosity("kg/msec"));
    }
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
    assertTrue(fence.group(1).contains("public class " + className));
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

  /**
   * Creates the natural-gas control used by several API-level tests.
   *
   * @param temperatureK temperature in kelvin
   * @param pressureBara absolute pressure in bara
   * @return configured SRK fluid
   */
  private static SystemInterface createNaturalGas(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Asserts that a calculated physical property is finite and positive.
   *
   * @param value calculated property value
   */
  private static void assertPositiveFinite(double value) {
    assertTrue(value > 0.0 && Double.isFinite(value));
  }
}
