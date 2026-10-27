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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the exact complete Java examples published in the process-energy guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessEnergyExactSourceDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "energy_streams.md");
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);
  private static final List<String> MARKERS = Arrays.asList("<!-- energy-streams-example: pump -->",
      "<!-- energy-streams-example: bus -->", "<!-- energy-streams-example: shaft -->",
      "<!-- energy-streams-example: allocation -->", "<!-- energy-streams-example: thermal-utility -->");
  private static final List<String> CLASS_NAMES = Arrays.asList("PumpEnergyExample", "EnergyBusExample",
      "MechanicalShaftExample", "EnergyAllocationExample", "ThermalUtilityExample");
  private static final List<String> EXAMPLE_IMPORTS = Arrays.asList(
      "import neqsim.process.equipment.energy.ThermalUtilityConsumer;",
      "import neqsim.process.equipment.energy.ThermalUtilitySource;",
      "import neqsim.process.equipment.energy.ThermalUtilityState;",
      "import neqsim.process.equipment.energy.UtilityEnergyBus;", "import neqsim.process.equipment.pump.Pump;",
      "import neqsim.process.equipment.stream.EnergyBus;",
      "import neqsim.process.equipment.stream.EnergyNetworkReport;",
      "import neqsim.process.equipment.stream.EnergyPort;",
      "import neqsim.process.equipment.stream.EnergyPortDirection;",
      "import neqsim.process.equipment.stream.EnergyPortMode;", "import neqsim.process.equipment.stream.EnergyStream;",
      "import neqsim.process.equipment.stream.EnergyType;", "import neqsim.process.equipment.stream.MechanicalShaft;",
      "import neqsim.process.equipment.stream.Stream;", "import neqsim.process.equipment.stream.UtilityLevel;",
      "import neqsim.thermo.system.SystemInterface;", "import neqsim.thermo.system.SystemSrkEos;");

  @TempDir\n  Path temporaryDirectory;

  /** Verifies that every marked complete process-energy example is exact executable source. */
  @Test
  void processEnergyExamplesCompileAndRun() throws Exception {
    assertEquals(MARKERS.size(), CLASS_NAMES.size());
    for (int exampleIndex = 0; exampleIndex < MARKERS.size(); exampleIndex++) {
      String className = CLASS_NAMES.get(exampleIndex);
      String exactFence = markedExampleSource(MARKERS.get(exampleIndex));
      assertFalse(exactFence.contains("System.out"));
      assertFalse(exactFence.contains("System.err"));

      String source = String.join("\n", EXAMPLE_IMPORTS) + "\n\npublic class " + className
          + " {\n  public static void main(String[] args) {\n" + exactFence + "\n  }\n}\n";
      try (URLClassLoader loader = compile(className, source)) {
        Class<?> example = loader.loadClass(className);
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      }
    }
  }

  /**
   * Extracts one uniquely marked Java fence from the process-energy guide.
   *
   * @param marker unique marker immediately before the fence
   * @return exact published Java statements
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource(String marker) throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(marker);
    assertTrue(markerPosition >= 0, "Missing marker: " + marker);
    assertEquals(markerPosition, markdown.lastIndexOf(marker), "Marker must be unique: " + marker);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + marker);
    return fence.group(1);
  }

  /**
   * Compiles one Java 8 documentation source and returns its class loader.
   *
   * @param className generated public class name
   * @param source exact fence wrapped only with imports, class, and main method
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

