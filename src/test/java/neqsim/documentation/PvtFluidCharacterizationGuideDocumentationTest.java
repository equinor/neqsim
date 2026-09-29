package neqsim.documentation;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/** Compiles and executes the PVT fluid-characterization guide. */
public class PvtFluidCharacterizationGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/thermo/pvt_fluid_characterization.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern.compile(
      "(?ms)^## Executable characterized-fluid example.*?^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideStatesUnitsRoutesAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(guide.contains("Temperature in K and absolute pressure in bara"));
    assertTrue(guide.contains("Molar mass in kg/mol"));
    assertTrue(guide.contains("specific gravity/relative density"));
    assertTrue(guide.contains("select the TBP correlation before adding TBP or plus fractions"));
    assertTrue(guide.contains(".model(\"PVTlumpingModel\").plusFractionGroups(n)"));
    assertTrue(guide.contains(".model(\"standard\").totalPseudoComponents(n)"));
    assertTrue(guide.contains("Do not use \`plusFractionGroups\` and \`totalPseudoComponents\` interchangeably"));
    assertTrue(prose.contains("Characterization does not tune an equation of state"));
    assertTrue(prose.contains("require named stock-tank and separator conditions"));
    assertTrue(prose.contains("correlations, not measurements"));
    assertTrue(guide.contains("java -ea"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable PVT characterization example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class PvtFluidCharacterizationExample"));
    assertTrue(source.contains("new SystemSrkEos(temperatureK, pressureBara)"));
    assertTrue(source.contains("setTBPModel(\"PedersenSRK\")"));
    assertTrue(source.contains("addPlusFraction(\"C10\", 11.0, 0.290, 0.82)"));
    assertTrue(source.contains("setPlusFractionModel(\"Pedersen\")"));
    assertTrue(source.contains(".model(\"PVTlumpingModel\")"));
    assertTrue(source.contains(".plusFractionGroups(9)"));
    assertTrue(source.contains("characterisePlusFraction()"));
    assertTrue(source.contains("operations.TPflash()"));
    assertTrue(source.contains("assert fluid.getNumberOfComponents() == 17"));
    assertTrue(source.contains("assert Double.isFinite(vaporMoleFraction)"));
    assertTrue(source.contains("LogManager.getLogger"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  private void compileAndRun(String source) throws Exception {
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
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList(
        "-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null,
              manager.getJavaFileObjects(javaSource.toFile()))
          .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
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
