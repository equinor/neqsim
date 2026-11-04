package neqsim.process.processmodel.dexpi;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executes the literal Java program in {@code docs/engineering/dexpi-guide.md}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DexpiEngineeringGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "engineering", "dexpi-guide.md");
  private static final String NATIVE_EXPORT_MARKER = "<!-- dexpi-example: native-2.0-export -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Compiles and runs the exact native DEXPI export example and verifies its four deliverables.
   *
   * @throws Exception if the guide source cannot be compiled, executed, or inspected
   */
  @Test
  public void literalNativeExportCompilesRunsAndWritesSidecars() throws Exception {
    String source = markedExampleSource(NATIVE_EXPORT_MARKER, "Dexpi20ExportExample");
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));

    Path outputDirectory = temporaryDirectory.resolve("dexpi-output");
    try (URLClassLoader loader = compile("Dexpi20ExportExample", source)) {
      Class<?> exampleClass = loader.loadClass("Dexpi20ExportExample");
      String[] arguments = new String[] {outputDirectory.toString()};
      exampleClass.getMethod("main", String[].class).invoke(null, (Object) arguments);
    }

    assertNonEmpty(outputDirectory.resolve("plant.dexpi.xml"));
    assertNonEmpty(outputDirectory.resolve("process.dexpi.xml"));
    assertNonEmpty(outputDirectory.resolve("plant.dexpi.conformance.json"));
    assertNonEmpty(outputDirectory.resolve("process.dexpi.conformance.json"));
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
    assertTrue(fence.group(1).contains("public final class " + className));
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

  /**
   * Verifies that one generated deliverable exists and is non-empty.
   *
   * @param path generated deliverable
   * @throws Exception if the file cannot be inspected
   */
  private static void assertNonEmpty(Path path) throws Exception {
    assertTrue(Files.isRegularFile(path), "Missing generated file: " + path);
    assertTrue(Files.size(path) > 0L, "Empty generated file: " + path);
  }
}
