package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.gerg.NeqSimEOSCG;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Pressure and material-balance regressions for the CO2/SO2 documentation mixture. */
class SystemEOSCGEosCO2SO2Test extends neqsim.NeqSimTest {
  @ParameterizedTest
  @CsvSource({"298.15, 50.0, 2", "300.0, 50.0, 1", "298.15, 48.0, 1", "350.0, 10.0, 1"})
  void flashPreservesPressureCompositionAndPhaseProperties(double temperature, double pressure, int phaseCount) {
    SystemInterface fluid = new SystemEOSCGEos(temperature, pressure);
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("SO2", 0.05);
    fluid.createDatabase(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);

    assertEquals(phaseCount, fluid.getNumberOfPhases());
    double[] recoveredMoles = new double[2];
    double totalBeta = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      NeqSimEOSCG eos = new NeqSimEOSCG(phase);
      double density = phase.getDensity("kg/m3");
      double molarDensity = density / (phase.getMolarMass() * 1000.0);
      assertTrue(Double.isFinite(density) && density > 0.0);
      // Use the existing PhaseEOSCGEos relative pressure acceptance contract.
      assertEquals(pressure * 100.0, eos.getPressure(molarDensity), pressure * 100.0 * 1.0e-6);
      assertEquals(density, phase.getDensity_EOSCG(), density * 1.0e-8);
      assertEquals(100.0 / molarDensity, phase.getMolarVolume(), 1.0e-8);
      assertTrue(Double.isFinite(phase.getCp()) && phase.getCp() > 0.0);
      assertTrue(Double.isFinite(phase.getCv()) && phase.getCv() > 0.0);
      double sumX = 0.0;
      for (int component = 0; component < 2; component++) {
        double x = phase.getComponent(component).getx();
        assertTrue(x >= 0.0 && x <= 1.0);
        sumX += x;
        recoveredMoles[component] += x * phase.getNumberOfMolesInPhase();
      }
      assertEquals(1.0, sumX, 1.0e-10);
      totalBeta += phase.getBeta();
    }
    assertEquals(1.0, totalBeta, 1.0e-10);
    assertEquals(0.95, recoveredMoles[0], 1.0e-9);
    assertEquals(0.05, recoveredMoles[1], 1.0e-9);
    if (phaseCount == 2) {
      PhaseInterface gas = fluid.getPhase("gas");
      PhaseInterface liquid = fluid.getPhase("liquid");
      assertTrue(gas.getDensity() < liquid.getDensity());
      assertTrue(gas.getComponent("CO2").getx() > liquid.getComponent("CO2").getx());
      for (int component = 0; component < 2; component++) {
        double gasActivity = gas.getComponent(component).getx() * gas.getComponent(component).getFugacityCoefficient();
        double liquidActivity = liquid.getComponent(component).getx()
            * liquid.getComponent(component).getFugacityCoefficient();
        assertEquals(Math.log(gasActivity), Math.log(liquidActivity), 1.0e-5);
      }
    }
  }

  @Test
  void bothRequestedDensityRootsReproducePressureAtTheFeedComposition() {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0);
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("SO2", 0.05);
    fluid.init(0);
    NeqSimEOSCG eos = new NeqSimEOSCG(fluid.getPhase(0));
    double gasDensity = eos.getMolarDensity(PhaseType.GAS);
    double liquidDensity = eos.getMolarDensity(PhaseType.LIQUID);

    assertTrue(gasDensity > 0.0 && liquidDensity > gasDensity);
    assertEquals(5000.0, eos.getPressure(gasDensity), 0.005);
    assertEquals(5000.0, eos.getPressure(liquidDensity), 0.005);
    assertEquals(0.95, fluid.getPhase(0).getComponent("CO2").getx(), 1.0e-12);
    assertEquals(0.05, fluid.getPhase(0).getComponent("SO2").getx(), 1.0e-12);
  }

  @Test
  void documentedMixtureProgramCompilesAndRuns(@TempDir Path directory) throws Exception {
    Path guide = Paths.get(System.getProperty("basedir", "."), "docs/thermo/eoscg_co2_so2.md");
    String markdown = new String(Files.readAllBytes(guide), StandardCharsets.UTF_8);
    Matcher example = Pattern.compile("(?s)```java\\R(.*?)```").matcher(markdown);
    assertTrue(example.find(), "The guide must contain its executable Java example");
    Path source = directory.resolve("EosCgCo2So2Example.java");
    Files.write(source, example.group(1).getBytes(StandardCharsets.UTF_8));
    assertTrue(!example.find(), "Every Java example in this guide must be executed");
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation validation requires a JDK compiler");
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean compiled = compiler.getTask(diagnostics, files, null,
          Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d", directory.toString()), null,
          files.getJavaFileObjects(source.toFile())).call();
      assertTrue(Boolean.TRUE.equals(compiled), diagnostics.toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {directory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> program = Class.forName("EosCgCo2So2Example", true, loader);
      assertTrue(program.desiredAssertionStatus());
      program.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }
}
