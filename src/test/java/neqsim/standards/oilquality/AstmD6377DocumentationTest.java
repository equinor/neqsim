package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpMethod;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executes and verifies the ASTM D6377 vapor-pressure documentation workflow.
 *
 * @author ESOL
 * @version 1.0
 */
class AstmD6377DocumentationTest extends NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");

  @TempDir
  Path temporaryDirectory;

  @Test
  void exactPublishedProgramCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/standards/astm_d6377_rvp.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The ASTM D6377 guide must contain one complete Java program");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The ASTM D6377 guide should expose one maintained Java program");
    for (String required : Arrays.asList("public final class AstmD6377Example",
        "RvpMethod.RVP_ASTM_D6377_ADD_WATER_SATURATED", "Standard_ASTM_D6377.getWaterSaturationPressure(310.95)",
        "sourceStream.getRVP(", "manualWaterSaturatedBara", "assert waterSaturated.isValid()")) {
      assertTrue(exampleSource.contains(required), required);
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("AstmD6377Example.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("AstmD6377Example", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("AstmD6377Example failed", exception.getCause());
      }
    }
  }

  @Test
  void documentedTypedWorkflowProducesTraceableResultsAndPreservesSourceState() {
    SystemInterface sourceOil = createOil();
    double sourceTemperatureK = sourceOil.getTemperature();
    double sourcePressureBara = sourceOil.getPressure();

    SystemInterface workingFluid = sourceOil.clone();
    Standard_ASTM_D6377 vaporPressure = new Standard_ASTM_D6377(workingFluid);
    vaporPressure.setReferenceTemperature(37.8, "C");
    vaporPressure.setMethodRVP(RvpMethod.RVP_ASTM_D6377);
    vaporPressure.calculate();

    RvpResult selected = vaporPressure.getRvpResult();
    RvpResult vpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4);
    RvpResult dryVpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER);
    double tvpBara = vaporPressure.getValue("TVP", "bara");
    double selectedRvpKPa = vaporPressure.getValue("RVP", "kPa");

    assertTrue(selected.isValid());
    assertTrue(vpcr4.isValid());
    assertTrue(dryVpcr4.isValid());
    assertEquals(RvpMethod.RVP_ASTM_D6377, selected.getMethod());
    assertEquals("RVP_ASTM_D6377", vaporPressure.getMethodRVP());
    assertEquals(37.8, selected.getReferenceTemperatureC(), 1.0e-12);
    assertEquals(0.9653068384, selected.getValue(), 1.0e-3);
    assertEquals(1.1574422523, vpcr4.getValue(), 1.0e-3);
    assertEquals(vpcr4.getValue(), dryVpcr4.getValue(), 1.0e-6);
    assertEquals(1.6662983670, tvpBara, 1.0e-3);
    assertEquals(selected.getValue() * 100.0, selectedRvpKPa, 1.0e-6);
    assertTrue(tvpBara > vpcr4.getValue());
    assertTrue(vpcr4.getValue() > selected.getValue());

    JsonObject json = JsonParser.parseString(selected.toJson()).getAsJsonObject();
    assertEquals("RVP_ASTM_D6377", json.get("method").getAsString());
    assertEquals("bara", json.get("unit").getAsString());
    assertTrue(json.get("valid").getAsBoolean());

    assertEquals(sourceTemperatureK, sourceOil.getTemperature(), 0.0);
    assertEquals(sourcePressureBara, sourceOil.getPressure(), 0.0);
  }

  @Test
  void documentedWaterFreeComparisonRespondsToWater() {
    SystemInterface wetOil = createOil();
    wetOil.addComponent("water", 0.00545);
    // Rebuild binary interaction matrices after changing the component list.
    wetOil.setMixingRule(2);
    wetOil.init(0);

    Standard_ASTM_D6377 vaporPressure = new Standard_ASTM_D6377(wetOil.clone());
    vaporPressure.setReferenceTemperature(37.8, "C");
    vaporPressure.setMethodRVP(RvpMethod.VPCR4);
    vaporPressure.calculate();

    RvpResult wetVpcr4 = vaporPressure.getRvpResult();
    RvpResult dryVpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER);

    assertTrue(wetVpcr4.isValid());
    assertTrue(dryVpcr4.isValid());
    assertNotEquals(wetVpcr4.getValue(), dryVpcr4.getValue(), 1.0e-6);
  }

  /**
   * Compile the extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        temporaryDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(source.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), "docs/standards/astm_d6377_rvp.md: " + diagnostics.getDiagnostics());
    }
  }

  private SystemInterface createOil() {
    SystemInterface oil = new SystemSrkEos(275.15, 1.0);
    oil.addComponent("methane", 0.0006538);
    oil.addComponent("ethane", 0.006538);
    oil.addComponent("propane", 0.065380);
    oil.addComponent("n-pentane", 0.154500);
    oil.addComponent("nC10", 0.545000);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }
}
