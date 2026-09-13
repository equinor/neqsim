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
 * Compiles and executes the published GERG-2008 and EOS-CG examples with assertions enabled.
 */
public class GergEoscgDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^\x60\x60\x60java\\r?\\n([\\s\\S]*?)^\x60\x60\x60[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void gergAndEoscgExamplesCompileAndRunWithPhysicalAssertions() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path document = repositoryRoot.resolve("docs/thermo/gerg2008_eoscg.md");
    String markdown = new String(Files.readAllBytes(document), StandardCharsets.UTF_8);
    assertTrue(markdown.contains("do not establish custody-transfer accuracy"),
        "The guide must retain its implementation-validation boundary");

    Matcher fences = JAVA_FENCE.matcher(markdown);
    List<String> classNames = new ArrayList<String>();
    List<Path> sourceFiles = new ArrayList<Path>();
    while (fences.find()) {
      String source = fences.group(1);
      Matcher className = PUBLIC_CLASS.matcher(source);
      assertTrue(className.find(), "Each Java example must be a complete public class");
      assertTrue(!source.contains("System.out") && !source.contains("System.err"),
          "GERG/EOS-CG examples must use the project logger");
      assertTrue(source.contains("LogManager.getLogger"), "Each example must declare a project logger");
      assertTrue(source.contains("assert "), "Each example must check its physical or model-selection result");
      String name = className.group(1);
      assertTrue(!classNames.contains(name), "Duplicate GERG/EOS-CG example class " + name);
      classNames.add(name);
      Path sourceFile = temporaryDirectory.resolve(name + ".java");
      Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
      sourceFiles.add(sourceFile);
    }
    assertEquals(Arrays.asList("GergExample", "Gerg2008H2Example", "Gerg2008NH3Example", "EosCgExample"),
        classNames, "GERG/EOS-CG example coverage changed");
    compile(compiler, sourceFiles);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      for (String className : classNames) {
        Class<?> example = Class.forName(className, true, loader);
        assertTrue(example.desiredAssertionStatus(), "Physical assertions must be enabled for " + className);
        try {
          example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
          throw new AssertionError(className + " failed", exception.getCause());
        }
      }
    }
  }

  /**
   * Compile with Java 8 syntax and the repository test classpath.
   *
   * @param compiler current JDK compiler
   * @param sourceFiles extracted Markdown sources
   * @throws IOException if a source cannot be read
   */
  private void compile(JavaCompiler compiler, List<Path> sourceFiles) throws IOException {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    List<java.io.File> files = new ArrayList<java.io.File>();
    for (Path sourceFile : sourceFiles) {
      files.add(sourceFile.toFile());
    }
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    List<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        temporaryDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files)).call();
      assertTrue(Boolean.TRUE.equals(successful),
          "docs/thermo/gerg2008_eoscg.md: " + diagnostics.getDiagnostics());
    }
  }
}
