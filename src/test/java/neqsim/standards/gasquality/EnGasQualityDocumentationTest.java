package neqsim.standards.gasquality;

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

/** Compiles and executes the maintained EN 16726 / EN 16723 documentation example. */
public class EnGasQualityDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/standards/en16723_en16726_gas_quality.md";
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^# ");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesCurrentApisAndImplementationBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve(GUIDE);
    String guide = read(guidePath);
    String body = guide.split("---", 3)[2];
    String bodyWithoutFences = JAVA_FENCE.matcher(body).replaceAll("");
    String normalizedGuide = guide.replaceAll("\\s+", " ");

    assertTrue(guide.startsWith("---\ntitle:"));
    assertFalse(DUPLICATE_H1.matcher(bodyWithoutFences).find());
    assertTrue(normalizedGuide.contains("does **not** calculate or check water dew point"));
    assertTrue(normalizedGuide.contains("It does not calculate those contaminants"));
    assertTrue(normalizedGuide.contains("Unknown result keys currently fall back to the Wobbe index"));
    assertTrue(normalizedGuide.contains("Do not report a passing boolean as EN conformity"));

    assertNotNull(Standard_EN16726.class.getMethod("setNetworkType", String.class));
    assertNotNull(Standard_EN16726.class.getMethod("setH2Limit", double.class));
    assertNotNull(Standard_EN16726.class.getMethod("getWobbeIndexMin"));
    assertNotNull(Standard_EN16726.class.getMethod("getWobbeIndexMax"));
    assertNotNull(Standard_EN16723.class.getConstructor(SystemInterface.class, int.class));
    assertNotNull(Standard_EN16723.class.getMethod("setPart", int.class));
    assertEquals(Standard_EN16726.class, Standard_EN16723.class.getMethod("getEN16726").getReturnType());

    String standardsIndex = read(repositoryRoot.resolve("docs/standards/README.md"));
    String referenceIndex = read(repositoryRoot.resolve("docs/REFERENCE_MANUAL_INDEX.md"));
    assertTrue(standardsIndex.contains("en16723_en16726_gas_quality"));
    assertTrue(referenceIndex.contains("standards/en16723_en16726_gas_quality.md"));

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
    assertTrue(source.contains("assert networkGas.isOnSpec()"));
    assertTrue(source.contains("assert gridInjection.isOnSpec()"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "Guide must contain exactly one Java program");
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
