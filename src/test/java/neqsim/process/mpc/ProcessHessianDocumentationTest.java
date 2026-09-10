package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/** Compiles and executes the actual Hessian example in the industrial MPC guide. */
class ProcessHessianDocumentationTest extends neqsim.NeqSimTest {
  @Test
  void documentedScalarHessianExampleRuns(@TempDir Path output) throws Exception {
    String guide = new String(Files.readAllBytes(Paths.get("docs/integration/neqsim_industrial_mpc_integration.md")),
        StandardCharsets.UTF_8);
    int start = guide.indexOf("### Hessian (Second Derivatives)");
    int end = guide.indexOf("### Export for External Systems", start);
    assertTrue(start >= 0 && end > start, "Keep the documented Hessian section executable");
    Matcher block = Pattern.compile("```java\\R(.*?)\\R```", Pattern.DOTALL).matcher(guide.substring(start, end));
    assertTrue(block.find(), "The Hessian section must contain a runnable Java example");
    Path source = output.resolve("ScalarHessianExample.java");
    Files.write(source, block.group(1).getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      assertTrue(compiler.getTask(null, manager, diagnostics,
          Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d", output.toString(), "-encoding",
              "UTF-8", "-source", "8", "-target", "8", "-proc:none"),
          null, manager.getJavaFileObjects(source.toFile())).call(), diagnostics.getDiagnostics().toString());
    }
    try (
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, getClass().getClassLoader())) {
      Class<?> example = loader.loadClass("ScalarHessianExample");
      double[][] hessian = (double[][]) example.getMethod("calculate").invoke(null);
      assertEquals(3, hessian.length);
      for (double[] row : hessian) {
        assertArrayEquals(new double[3], row, 1e-6);
      }
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }
}
