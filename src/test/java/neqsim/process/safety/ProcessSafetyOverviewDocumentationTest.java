package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.hipps.HIPPSLogic;
import neqsim.process.logic.sis.VotingLogic;

/**
 * Compiles and executes the exact complete Java program in the process safety overview.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessSafetyOverviewDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "safety", "README.md");
  private static final String MARKER =
      "<!-- process-safety-overview-example: esd-sequence -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir Path temporaryDirectory;

  /**
   * Verifies that the marked pressure-protection and ESD quick start is literal executable source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void processSafetyOverviewQuickStartCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class ProcessSafetyOverviewQuickStart"));
    assertTrue(source.contains("new SafetyValve("));
    assertTrue(source.contains("new RuptureDisk("));
    assertTrue(source.contains("new TripValveAction("));
    assertTrue(source.contains("new ActivateBlowdownAction("));
    assertTrue(source.contains("esdLogic.execute(timeStepSeconds);"));
    assertTrue(source.contains("inletIsolation.runTransient(timeStepSeconds, calculationId);"));
    assertTrue(source.contains("blowdownValve.runTransient(timeStepSeconds, calculationId);"));

    try (URLClassLoader loader = compile("ProcessSafetyOverviewQuickStart", source)) {
      Class<?> example = loader.loadClass("ProcessSafetyOverviewQuickStart");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Verifies that the overview names the current HIPPS logic types.
   *
   * @throws Exception if a documented constructor or method is absent
   */
  @Test
  void hippsDocumentationNamesTheCurrentLogicTypes() throws Exception {
    assertSame(
        VotingLogic.class,
        HIPPSLogic.class.getConstructor(String.class, VotingLogic.class).getParameterTypes()[1]);
    assertSame(
        void.class,
        HIPPSLogic.class
            .getMethod("setIsolationValve", ThrottlingValve.class)
            .getReturnType());
    assertSame(
        void.class, HIPPSLogic.class.getMethod("update", double[].class).getReturnType());
  }

  /**
   * Extracts the uniquely marked Java fence.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(
        markerPosition, markdown.lastIndexOf(MARKER), "Marker must be unique: " + MARKER);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(fence.group(1).contains("public final class ProcessSafetyOverviewQuickStart"));
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
    String classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
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
}
