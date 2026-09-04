package neqsim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles every standalone Java source linked from the documentation examples catalog.
 *
 * @author esol
 * @version 1.0
 */
public class StandaloneJavaDocumentationCompilationTest {
  private static final int EXPECTED_EXAMPLE_COUNT = 14;

  @TempDir
  Path compilationOutput;

  /** Verifies that the complete standalone documentation-example corpus matches the current API. */
  @Test
  void testStandaloneDocumentationExamplesCompile() throws IOException {
    Path examplesDirectory = Paths.get(System.getProperty("user.dir"), "docs", "examples").toAbsolutePath();
    List<File> sourceFiles;
    try (Stream<Path> paths = Files.list(examplesDirectory)) {
      sourceFiles = paths.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().map(Path::toFile)
          .collect(Collectors.toList());
    }

    assertEquals(EXPECTED_EXAMPLE_COUNT, sourceFiles.size(),
        "The compilation contract must track every catalogued standalone Java example");

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A full JDK is required to verify documentation examples");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null,
        StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
      List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d",
          compilationOutput.toString(), "-encoding", StandardCharsets.UTF_8.name(), "-proc:none");

      Boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
      assertTrue(Boolean.TRUE.equals(compiled), formatDiagnostics(diagnostics));
    }
  }

  private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
    return diagnostics.getDiagnostics().stream()
        .map(diagnostic -> (diagnostic.getSource() == null ? "<compiler>" : diagnostic.getSource().getName()) + ":"
            + diagnostic.getLineNumber() + ": " + diagnostic.getKind() + ": " + diagnostic.getMessage(null))
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
