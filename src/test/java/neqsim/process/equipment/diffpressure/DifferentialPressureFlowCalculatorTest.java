package neqsim.process.equipment.diffpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.diffpressure.DifferentialPressureFlowCalculator.FlowCalculationResult;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DifferentialPressureFlowCalculator}.
 */
class DifferentialPressureFlowCalculatorTest {

  private static SystemInterface createNitrogenSystem() {
    SystemInterface system = new SystemSrkEos(273.15 + 15.0, 1.0125);
    system.addComponent("nitrogen", 1.0);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);
    system.init(1);
    system.initPhysicalProperties();
    return system;
  }

  @Test
  void testVenturiFlowMatchesPythonReference() throws Exception {
    double[] pressureBarg = {10.0};
    double[] temperatureC = {20.0};
    double[] differentialPressureMbar = {100.0};
    double[] flowData = {300.0, 200.0, 0.9};
    List<String> components = Arrays.asList("nitrogen");
    double[] fractions = {1.0};

    FlowCalculationResult result = DifferentialPressureFlowCalculator.calculate(pressureBarg,
        temperatureC, differentialPressureMbar, "Venturi", flowData, components, fractions, true);

    double[] massFlow = result.getMassFlowKgPerHour();
    double[] volFlow = result.getVolumetricFlowM3PerHour();
    double[] stdFlow = result.getStandardFlowMSm3PerDay();
    double[] molecularWeight = result.getMolecularWeightGPerMol();

    SystemInterface base = createNitrogenSystem();
    SystemInterface actual = (SystemInterface) base.clone();
    actual.setTemperature(temperatureC[0] + 273.15);
    actual.setPressure(pressureBarg[0] + 1.0125, "bara");
    actual.init(0);
    actual.init(1);
    actual.initPhysicalProperties();
    double density = actual.getDensity("kg/m3");

    SystemInterface zero = (SystemInterface) base.clone();
    zero.setTemperature(temperatureC[0] + 273.15);
    zero.setPressure(1.0125, "bara");
    zero.init(0);
    zero.init(1);
    zero.initPhysicalProperties();
    double kappa = zero.getGamma();

    SystemInterface standard = (SystemInterface) base.clone();
    standard.setTemperature(273.15 + 15.0);
    standard.setPressure(1.0125, "bara");
    standard.init(0);
    standard.init(1);
    standard.initPhysicalProperties();
    double densityStd = standard.getDensity("kg/m3");

    double dpPa = differentialPressureMbar[0] * 100.0;
    double pressurePa = (pressureBarg[0] + 1.0125) * 1.0e5;
    double D = flowData[0] / 1000.0;
    double d = flowData[1] / 1000.0;
    double dischargeCoefficient = flowData.length > 2 ? flowData[2] : 0.985;
    double expectedMassFlow =
        runPythonVenturi(dpPa, pressurePa, density, kappa, D, d, dischargeCoefficient);

    assertEquals(expectedMassFlow, massFlow[0], expectedMassFlow * 1e-8);
    assertEquals(expectedMassFlow / density, volFlow[0], 1e-8);
    double expectedStdFlow = expectedMassFlow / densityStd * 24.0 / 1.0e6;
    assertEquals(expectedStdFlow, stdFlow[0], expectedStdFlow * 1e-8);
    assertEquals(base.getMolarMass() * 1000.0, molecularWeight[0], 1e-10);
  }

  @Test
  void testSimplifiedFlowUsesCv() {
    double[] pressureBarg = {5.0};
    double[] temperatureC = {25.0};
    double[] differentialPressureMbar = {50.0};
    double[] flowData = {2.5};
    List<String> components = Arrays.asList("nitrogen");
    double[] fractions = {1.0};

    FlowCalculationResult result = DifferentialPressureFlowCalculator.calculate(pressureBarg,
        temperatureC, differentialPressureMbar, "Simplified", flowData, components, fractions,
        true);

    double[] massFlow = result.getMassFlowKgPerHour();

    SystemInterface base = createNitrogenSystem();
    SystemInterface actual = (SystemInterface) base.clone();
    actual.setTemperature(temperatureC[0] + 273.15);
    actual.setPressure(pressureBarg[0] + 1.0125, "bara");
    actual.init(0);
    actual.init(1);
    actual.initPhysicalProperties();
    double density = actual.getDensity("kg/m3");

    double expectedMassFlow = flowData[0]
        * Math.sqrt(Math.max(differentialPressureMbar[0] * 100.0 * density, 0.0));
    assertEquals(expectedMassFlow, massFlow[0], expectedMassFlow * 1e-8);
  }

  @Test
  void testStreamFlowRateMatchesCalculatorResult() {
    double[] pressureBarg = {8.0};
    double[] temperatureC = {18.0};
    double[] differentialPressureMbar = {120.0};
    double[] flowData = {300.0, 200.0, 0.9};

    List<String> components = Arrays.asList("nitrogen");
    double[] fractions = {1.0};

    FlowCalculationResult result = DifferentialPressureFlowCalculator.calculate(pressureBarg,
        temperatureC, differentialPressureMbar, "Venturi", flowData, components, fractions, true);

    double massFlowKgPerHour = result.getMassFlowKgPerHour()[0];

    SystemInterface base = createNitrogenSystem();
    SystemInterface streamFluid = (SystemInterface) base.clone();
    streamFluid.setTemperature(temperatureC[0] + 273.15);
    streamFluid.setPressure(pressureBarg[0] + 1.0125, "bara");
    streamFluid.init(0);
    streamFluid.init(1);
    streamFluid.initPhysicalProperties();

    StreamInterface stream = new Stream("venturi stream", streamFluid);
    stream.setFlowRate(massFlowKgPerHour, "kg/hr");

    assertEquals(massFlowKgPerHour, stream.getFluid().getFlowRate("kg/hr"),
        Math.max(massFlowKgPerHour * 1e-10, 1e-10));
    stream.runTPflash();
    assertEquals(massFlowKgPerHour, stream.getFluid().getFlowRate("kg/hr"),
        Math.max(massFlowKgPerHour * 1e-10, 1e-10));
  }

  @Test
  void testSimplifiedRequiresPositiveCv() {
    double[] pressureBarg = {5.0};
    double[] temperatureC = {25.0};
    double[] differentialPressureMbar = {50.0};
    double[] flowData = {0.0};

    assertThrows(IllegalArgumentException.class,
        () -> DifferentialPressureFlowCalculator.calculate(pressureBarg, temperatureC,
            differentialPressureMbar, "Simplified", flowData, null, null, true));
  }

  private static double runPythonVenturi(double dpPa, double pressurePa, double density, double kappa,
      double D, double d, double dischargeCoefficient) throws IOException, InterruptedException {
    Path script = Paths.get("src", "test", "resources", "diffpressure_reference.py").toAbsolutePath();
    ProcessBuilder processBuilder = new ProcessBuilder("python3", script.toString(), "venturi", "--dp",
        Double.toString(dpPa), "--p", Double.toString(pressurePa), "--rho", Double.toString(density),
        "--kappa", Double.toString(kappa), "--D", Double.toString(D), "--d", Double.toString(d), "--C",
        Double.toString(dischargeCoefficient));
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();

    String output;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      output = reader.lines().collect(Collectors.joining()).trim();
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException(
          "Python reference calculation failed with exit code " + exitCode + ": " + output);
    }

    return Double.parseDouble(output);
  }
}
