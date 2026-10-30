package neqsim.process.processmodel;

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
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Compiles and executes the exact complete Java program in the process overview.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessSystemDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "process", "README.md");
  private static final String MARKER = "<!-- process-overview-example: valve-separator -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the marked valve-and-separator quick start is the literal executable guide source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void processOverviewQuickStartCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class ProcessSystemQuickStart"));
    assertTrue(source.contains("process.run();"));
    assertTrue(source.contains("process.hasRecycleLoops()"));
    assertTrue(source.contains("process.getExecutionPartitionInfo()"));
    assertTrue(source.contains("process.getReport_json()"));
    assertTrue(source.contains("process.getStreamSummaryTable()"));

    try (URLClassLoader loader = compile("ProcessSystemQuickStart", source)) {
      Class<?> example = loader.loadClass("ProcessSystemQuickStart");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Verifies the stream-topology ownership described by the equipment index.
   *
   * @throws NoSuchMethodException if a documented stream accessor is absent
   */
  @Test
  void equipmentIndexDocumentsStreamTopologyOwnership() throws NoSuchMethodException {
    assertTrue(ProcessEquipmentInterface.class.isAssignableFrom(ProcessEquipmentBaseClass.class));
    assertTrue(ProcessEquipmentBaseClass.class.isAssignableFrom(TwoPortEquipment.class));
    assertTrue(TwoPortEquipment.class.isAssignableFrom(ThrottlingValve.class));
    assertFalse(TwoPortEquipment.class.isAssignableFrom(Separator.class));

    assertSame(
        StreamInterface.class,
        TwoPortEquipment.class.getMethod("getInletStream").getReturnType());
    assertSame(
        StreamInterface.class,
        TwoPortEquipment.class.getMethod("getOutletStream").getReturnType());
    assertSame(
        StreamInterface.class, Separator.class.getMethod("getGasOutStream").getReturnType());
    assertSame(
        StreamInterface.class, Separator.class.getMethod("getLiquidOutStream").getReturnType());
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
    assertTrue(fence.group(1).contains("public final class ProcessSystemQuickStart"));
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
