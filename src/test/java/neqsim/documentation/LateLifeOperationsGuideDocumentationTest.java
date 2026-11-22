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

/** Compiles and executes the late-life field screening guide. */
public class LateLifeOperationsGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/fielddevelopment/LATE_LIFE_OPERATIONS.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Executable late-life screen.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideStatesUnitsSemanticsAndDecisionBoundaries() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(guide.startsWith("---\ntitle: Late-Life Field Screening\n"));
    assertTrue(guide.contains("Initial plateau rate | 8.0 million Sm³/d"));
    assertTrue(guide.contains("Total CAPEX | 1,384.5 MUSD"));
    assertTrue(guide.contains("Gas price / tariff | 0.30 / 0.02 USD/Sm³"));
    assertTrue(prose.contains("returns annual production volumes"));
    assertTrue(prose.contains("multiplying by days per year again would overstate revenue"));
    assertTrue(prose.contains("do not determine a safe operating limit or an abandonment date"));
    assertTrue(prose.contains("does not simulate water cut, GOR evolution, artificial lift"));
    assertTrue(prose.contains("A positive NPV is not permission to operate"));
    assertTrue(guide.contains("java -ea"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("ScenarioAnalyzer"));
    assertFalse(guide.contains("DecommissioningEstimator"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable late-life example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class LateLifeScreeningExample"));
    assertTrue(source.contains("generateFullProfile("));
    assertTrue(source.contains("DeclineType.EXPONENTIAL"));
    assertTrue(source.contains("new CashFlowEngine(\"NO\")"));
    assertTrue(source.contains("engine.addAnnualProduction("));
    assertTrue(source.contains("calculateBreakevenGasPrice(0.08)"));
    assertTrue(source.contains("assert finalRateSm3PerDay < firstRateSm3PerDay"));
    assertTrue(source.contains("assert lowerRate.getNpv() < base.getNpv()"));
    assertTrue(source.contains("assert higherCapex.getNpv() < base.getNpv()"));
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
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
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
