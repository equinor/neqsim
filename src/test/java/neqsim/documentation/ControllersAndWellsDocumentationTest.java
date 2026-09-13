package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the actual controller and well documentation examples, including their physical assertions.
 */
public class ControllersAndWellsDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern.compile("public class ([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void controllerExamplesCompileAndRunWithPhysicalAssertions() throws Exception {
    compileAndRun("docs/process/controllers.md", 7);
  }

  @Test
  void wellExamplesCompileAndRunWithPhysicalAssertions() throws Exception {
    compileAndRun("docs/process/equipment/wells.md", 3);
  }

  /**
   * Extract each complete example rather than maintaining a second copy that can drift from the documentation.
   *
   * @param document path relative to the repository root
   * @param expectedExamples expected number of Java examples, preventing silent loss of coverage
   * @throws Exception if compilation or an example's physical assertions fail
   */
  private void compileAndRun(String document, int expectedExamples) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String markdown = new String(Files.readAllBytes(repositoryRoot.resolve(document)), StandardCharsets.UTF_8);
    Matcher fences = JAVA_FENCE.matcher(markdown);
    List<String> classNames = new ArrayList<String>();
    List<Path> sourceFiles = new ArrayList<Path>();
    while (fences.find()) {
      String source = fences.group(1);
      Matcher className = PUBLIC_CLASS.matcher(source);
      assertTrue(className.find(), document + ": each Java example must be a complete public class");
      assertTrue(source.contains("    assert "), document + ": examples must check their physical results");
      String name = className.group(1);
      assertTrue(!classNames.contains(name), document + ": duplicate example class " + name);
      classNames.add(name);
      Path sourceFile = temporaryDirectory.resolve(name + ".java");
      Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
      sourceFiles.add(sourceFile);
    }
    assertEquals(expectedExamples, classNames.size(), document + ": Java-example coverage changed");
    compile(compiler, sourceFiles, document);

    try (URLClassLoader loader = new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      for (String className : classNames) {
        Class<?> example = Class.forName(className, true, loader);
        assertTrue(example.desiredAssertionStatus(), "Physical assertions must be enabled for " + className);
        try {
          example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
          throw new AssertionError(document + ": " + className + " failed", exception.getCause());
        }
      }
    }
  }

  /**
   * Compile with Java 8 syntax and the same dependency classpath used by the repository tests.
   *
   * @param compiler current JDK compiler
   * @param sourceFiles extracted Markdown sources
   * @param document source document, included in failure diagnostics
   * @throws IOException if a source cannot be read
   */
  private void compile(JavaCompiler compiler, List<Path> sourceFiles, String document) throws IOException {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    List<java.io.File> files = new ArrayList<java.io.File>();
    for (Path sourceFile : sourceFiles) {
      files.add(sourceFile.toFile());
    }
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    List<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        temporaryDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files)).call();
      assertTrue(Boolean.TRUE.equals(successful), document + ": " + diagnostics.getDiagnostics());
    }
  }
}
