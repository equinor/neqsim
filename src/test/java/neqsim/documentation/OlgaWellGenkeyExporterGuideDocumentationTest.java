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

/** Compiles and executes the complete OLGA well GENKEY export example published in the guide. */
public class OlgaWellGenkeyExporterGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void olgaWellGenkeyExportExampleCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/fielddevelopment/OLGA_WELL_GENKEY_EXPORT.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);
    String exporterSource = new String(
        Files.readAllBytes(repositoryRoot
            .resolve("src/main/java/neqsim/process/fielddevelopment/olga/" + "OlgaWellGenkeyExporter.java")),
        StandardCharsets.UTF_8);
    String trajectorySource = new String(
        Files.readAllBytes(repositoryRoot
            .resolve("src/main/java/neqsim/process/fielddevelopment/olga/" + "SyntheticWellTrajectory.java")),
        StandardCharsets.UTF_8);

    assertTrue(guide.startsWith("---\ntitle: Export a well as OLGA GENKEY text"));
    assertTrue(guide.contains("format generator, not an OLGA simulator"));
    assertTrue(guide.contains("licensed OLGA installation"));
    assertTrue(guide.contains("not field data or recommended defaults"));
    assertTrue(guide.contains("synthetic profile is screening evidence only"));
    assertTrue(guide.contains("does not generate PVT data"));
    assertTrue(guide.contains("does not invent a choke or Cv table"));
    assertTrue(exporterSource.contains("public String toGenkeyText()"));
    assertTrue(exporterSource.contains("public void setTrajectory("));
    assertTrue(trajectorySource.contains("public static Result generateSyntheticTrajectory("));
    assertTrue(trajectorySource.contains("public static String syntheticTrajectoryWarning("));

    Matcher fences = JAVA_FENCE.matcher(guide);
    assertTrue(fences.find(), "The OLGA well guide must contain one complete Java example");
    String exampleSource = fences.group(1);
    assertFalse(fences.find(), "The OLGA well guide should expose one maintained Java program");
    Matcher className = PUBLIC_CLASS.matcher(exampleSource);
    assertTrue(className.find(), "The OLGA well example must be a complete public class");
    assertEquals("OlgaWellGenkeyExportExample", className.group(1));
    assertTrue(exampleSource.contains("SyntheticWellTrajectory.Statistics"));
    assertTrue(exampleSource.contains("horizontalCoordinates(trajectory)"));
    assertTrue(exampleSource.contains("OlgaWellGenkeyExporter exporter"));
    assertTrue(exampleSource.contains("assert genkeyText.contains"));
    assertFalse(exampleSource.contains("System.out"));
    assertFalse(exampleSource.contains("System.err"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("OlgaWellGenkeyExportExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName("OlgaWellGenkeyExportExample", true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("OlgaWellGenkeyExportExample failed", exception.getCause());
      }
    }
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
      assertTrue(Boolean.TRUE.equals(successful),
          "docs/fielddevelopment/OLGA_WELL_GENKEY_EXPORT.md: " + diagnostics.getDiagnostics());
    }
  }
}
