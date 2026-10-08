package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Compiles and executes the maintained ISO 15403 documentation example. */
class StandardISO15403DocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/standards/iso15403_cng_quality.md";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^# ");

  @TempDir Path temporaryDirectory;

  @Test
  void guideMatchesCurrentApiAndEngineeringBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve(GUIDE);
    String guide = read(guidePath);
    String body = guide.split("---", 3)[2];
    String bodyWithoutFences = JAVA_FENCE.matcher(body).replaceAll("");
    String normalizedGuide = guide.replaceAll("\\s+", " ");

    assertTrue(guide.startsWith("---\ntitle:"));
    assertFalse(DUPLICATE_H1.matcher(bodyWithoutFences).find());
    assertTrue(normalizedGuide.contains("same one-mole composition basis at 200 bara absolute"));
    assertTrue(normalizedGuide.contains("returns an empty string"));
    assertTrue(normalizedGuide.contains("Hydrogen, C5+ hydrocarbons"));
    assertTrue(normalizedGuide.contains("Do not use `isOnSpec()` as evidence"));
    assertTrue(normalizedGuide.contains("not ISO acceptance limits"));

    Matcher links = MARKDOWN_LINK.matcher(guide);
    while (links.find()) {
      String target = links.group(1).split("#", 2)[0];
      if (target.isEmpty() || target.contains("://")) {
        continue;
      }
      assertFalse(target.endsWith(".md"), "Published links must be extensionless");
      Path resolved = guidePath.getParent().resolve(target + ".md").normalize();
      assertTrue(Files.isRegularFile(resolved), resolved.toString());
    }
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(GUIDE));
    Matcher fences = JAVA_FENCE.matcher(guide);

    assertTrue(fences.find(), "Guide must contain one complete Java program");
    String source = fences.group(1);
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("PRESSURE_BARA_ABSOLUTE = 200.0"));
    assertTrue(source.contains("assert Math.abs(totalMoles - 1.0)"));
    assertTrue(source.contains("assert Math.abs(baseMon - 128.18474)"));
    assertTrue(source.contains("assert carbonDioxideNm > baseNm"));
    assertTrue(source.contains("assert nitrogenNm < baseNm"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "Guide must contain exactly one Java program");
    assertFalse(guide.contains("```python"), "Guide must not contain Python fences");
  }

  @Test
  void pureMethaneAnchorAndUnsupportedAliasMatchCurrentImplementation() {
    SystemInterface methane = new SystemSrkEos(288.15, 200.0);
    methane.addComponent("methane", 1.0);
    methane.init(0);

    Standard_ISO15403 standard = new Standard_ISO15403(methane);
    standard.calculate();

    assertEquals(137.78, standard.getValue("MON"), 1.0e-12);
    assertEquals(95.6721, standard.getValue("NM"), 1.0e-12);
    assertThrows(RuntimeException.class, () -> standard.getValue("MN"));
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options =
        Arrays.asList(
            "-source",
            "8",
            "-target",
            "8",
            "-classpath",
            classPath,
            "-d",
            outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  options,
                  null,
                  manager.getJavaFileObjects(javaSource.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
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
