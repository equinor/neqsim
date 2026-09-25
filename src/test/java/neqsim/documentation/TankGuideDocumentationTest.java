package neqsim.documentation;

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

/** Compiles and executes the storage-tank guide. */
public class TankGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/process/equipment/tanks.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Executable LNG boil-off example.*?^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^\`\`\`java\\r?\\n([\\s\\S]*?)^\`\`\`[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideStatesCurrentApisUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("There is no unit-string overload"));
    assertTrue(guide.contains("does not provide \`setLiquidLevel\` or \`setPressure\` methods"));
    assertTrue(guide.contains("Pressure and temperature enter through the connected stream state"));
    assertTrue(guide.contains("W/m²/K"));
    assertTrue(guide.contains("storage pressure in bara"));
    assertTrue(guide.contains("getBOGMassFlowRate()"));
    assertTrue(guide.contains("getBoilOffRatePctPerDay()"));
    assertTrue(guide.contains("already returns percent/day"));
    assertTrue(guide.contains("not a detailed storage-tank design or operations simulator"));
    assertTrue(guide.contains("does not replace a vendor thermal design"));
    assertTrue(guide.contains("java -ea"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable LNG tank example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class LNGTankGuideExample"));
    assertTrue(source.contains("new SystemSrkEos(273.15 + lngTemperatureC, storagePressureBara)"));
    assertTrue(source.contains("tank.setOverallHeatTransferCoefficient"));
    assertTrue(source.contains("tank.setTankSurfaceArea(surfaceAreaM2)"));
    assertTrue(source.contains("tank.setLNGInventory(inventoryKg)"));
    assertTrue(source.contains("tank.setStoragePressure(storagePressureBara)"));
    assertTrue(source.contains("tank.getBOGMassFlowRate()"));
    assertTrue(source.contains("tank.getBoilOffRatePctPerDay()"));
    assertTrue(source.contains("tank.getBOGStream().getFlowRate(\"kg/hr\")"));
    assertTrue(source.contains("expectedHeatIngressW"));
    assertTrue(source.contains("assert bogMassFlowKgPerHr > 0.0"));
    assertTrue(source.contains("LogManager.getLogger"));
    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("setLiquidLevel"));
    assertFalse(source.contains("setHeatInput"));
    assertFalse(source.contains("getBoilOffGasRate"));
    assertFalse(source.contains("getBoilOffGasStream"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
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
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath",
        classPath, "-d", outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null,
              manager.getJavaFileObjects(javaSource.toFile()))
          .call();
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
