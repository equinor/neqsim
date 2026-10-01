package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/**
 * Verifies the indexed Gibbs reactor guide and executes its exact Java program.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class GibbsReactorGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/wiki/gibbs_reactor.md";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\s*(.*?)```", Pattern.DOTALL)

  @TempDir
  Path temporaryDirectory;

  /** Verifies metadata, topology, units, safeguards, and engineering boundaries. */
  @Test
  void guidePublishesOneBoundedCurrentApiProgram() throws Exception {
    String source = readGuide();
    List<String> programs = javaPrograms(source);

    assertTrue(source.startsWith("---\ntitle: \"Gibbs Reactor\"\ndescription: \"Run and qualify"));
    assertFalse(source.contains("\n# Gibbs Reactor\n"));
    assertEquals(1, programs.size());
    assertTrue(programs.get(0).contains("public final class GibbsReactorGuideExample"));
    assertTrue(programs.get(0).contains("setUseAllDatabaseSpecies(false)"));
    assertTrue(programs.get(0).contains("GibbsReactor.EnergyMode.ISOTHERMAL"));
    assertTrue(programs.get(0).contains("setPressure(1.0, \"bara\")"));
    assertTrue(programs.get(0).contains("setTemperature(1300.0, \"K\")"));
    assertTrue(programs.get(0).contains("getElementMassBalanceConverged()"));
    assertTrue(programs.get(0).contains("IllegalStateException"));
    assertTrue(programs.get(0).contains("assert reactor.hasConverged()"));
    assertTrue(source.contains("Qualification boundary"));
    assertTrue(source.contains("not kinetic, residence-time, catalyst, emissions"));
    assertTrue(source.contains("../process/gibbs-reactor-documentation.md"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("thermodynam..."));
  }

  /** Compiles the exact Java fence for Java 8 and runs it with assertions enabled. */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  void exactGuideProgramCompilesForJava8AndRuns() throws Exception {
    List<String> programs = javaPrograms(readGuide());
    assertEquals(1, programs.size());

    Path sourceDirectory = temporaryDirectory.resolve("source").resolve("examples");
    Path outputDirectory = temporaryDirectory.resolve("compiled");
    Files.createDirectories(sourceDirectory);
    Files.createDirectories(outputDirectory);
    Path sourceFile = sourceDirectory.resolve("GibbsReactorGuideExample.java");
    Files.write(sourceFile, programs.get(0).getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());

    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(sourceFile.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("examples.GibbsReactorGuideExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("Gibbs reactor guide example failed", exception.getCause());
      }
    }
  }

  /**
   * Read the maintained guide.
   *
   * @return guide text
   * @throws Exception if the guide cannot be read
   */
  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  /**
   * Extract Java fences from Markdown.
   *
   * @param markdown Markdown source
   * @return Java program bodies in source order
   */
  private List<String> javaPrograms(String markdown) {
    Matcher matcher = JAVA_FENCE.matcher(markdown);
    List<String> programs = new ArrayList<String>();
    while (matcher.find()) {
      programs.add(matcher.group(1).trim() + "\n");
    }
    return programs;
  }
}
