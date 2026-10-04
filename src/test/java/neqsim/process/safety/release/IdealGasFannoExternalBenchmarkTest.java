package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reproduces the public nitrogen Fanno case from NASA NTRS 20070036728 without using model internals.
 */
class IdealGasFannoExternalBenchmarkTest extends neqsim.NeqSimTest {
  private static final double GAS_CONSTANT_J_MOL_K = 8.31446261815324;

  @Test
  void reproducesNasaGfsspChokedNitrogenCase() throws Exception {
    Benchmark benchmark = Benchmark.read();
    double gamma = 1.4;
    SystemInterface reservoir = null;
    for (int iteration = 0; iteration < 6; iteration++) {
      double stagnationFactor = 1.0 + 0.5 * (gamma - 1.0) * benchmark.inletMach * benchmark.inletMach;
      double stagnationTemperatureK = benchmark.inletTemperatureK * stagnationFactor;
      double stagnationPressurePa = benchmark.inletPressurePa * Math.pow(stagnationFactor, gamma / (gamma - 1.0));
      reservoir = nitrogen(stagnationTemperatureK, stagnationPressurePa);
      gamma = reservoir.getGamma();
    }

    ReleaseFlowRequest request = new ReleaseFlowRequest(reservoir, benchmark.diameterM, 1.0, 101325.0,
        benchmark.lengthM, benchmark.darcyFrictionFactor);
    ReleaseFlowResult result = new IdealGasFannoPipeReleaseModel().calculate(request);
    assertTrue(result.isUsable(), result.getDiagnostics().toString());
    assertTrue(result.isChoked());

    double specificGasConstant = GAS_CONSTANT_J_MOL_K / reservoir.getMolarMass();
    double expectedMassFlux = benchmark.inletPressurePa / (specificGasConstant * benchmark.inletTemperatureK)
        * benchmark.inletMach * Math.sqrt(gamma * specificGasConstant * benchmark.inletTemperatureK);
    double actualMassFlux = result.getMassFlowRateKgS() / request.getEffectiveAreaM2();
    double relativeError = Math.abs(actualMassFlux - expectedMassFlux) / expectedMassFlux;
    assertTrue(relativeError < 0.01, "NASA inlet mass-flux relative error=" + relativeError);

    ReleaseState exit = result.getStations().get(Station.ORIFICE_EXIT);
    assertEquals(benchmark.exitMach, exit.getVelocityMs() / result.getThroatSoundSpeedMs(), 1.0e-10);
    assertEquals(3207.0 * 0.0254, benchmark.lengthM, 1.0e-12);
    assertEquals(6.0 * 0.0254, benchmark.diameterM, 1.0e-12);

    Path outputDirectory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(outputDirectory);
    String receipt = "caseId,expectedMassFlux_kg_m2_s,actualMassFlux_kg_m2_s,relativeError,exitMach\n"
        + benchmark.caseId + "," + expectedMassFlux + "," + actualMassFlux + "," + relativeError + ","
        + exit.getVelocityMs() / result.getThroatSoundSpeedMs() + "\n";
    Files.write(outputDirectory.resolve("nasa-gfssp-fanno-2007-receipt.csv"), receipt.getBytes(StandardCharsets.UTF_8));
  }

  private static SystemInterface nitrogen(double temperatureK, double pressurePa) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressurePa / 1.0e5);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  private static final class Benchmark {
    private final String caseId;
    private final double inletPressurePa;
    private final double inletTemperatureK;
    private final double inletMach;
    private final double diameterM;
    private final double lengthM;
    private final double darcyFrictionFactor;
    private final double exitMach;

    private Benchmark(String[] values) {
      caseId = values[0];
      if (!"nitrogen".equals(values[1])) {
        throw new IllegalArgumentException("NASA benchmark fluid must be nitrogen");
      }
      inletPressurePa = Double.parseDouble(values[2]);
      inletTemperatureK = Double.parseDouble(values[3]);
      inletMach = Double.parseDouble(values[4]);
      diameterM = Double.parseDouble(values[5]);
      lengthM = Double.parseDouble(values[6]);
      darcyFrictionFactor = Double.parseDouble(values[7]);
      exitMach = Double.parseDouble(values[8]);
      if (!values[9].contains("20070036728")) {
        throw new IllegalArgumentException("NASA benchmark provenance is required");
      }
    }

    private static Benchmark read() throws Exception {
      Path path = Paths.get("src", "test", "resources", "neqsim", "process", "safety", "release",
          "nasa-gfssp-fanno-2007.csv");
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      assertEquals(2, lines.size());
      assertTrue(lines.get(0).contains("inletPressurePa"));
      return new Benchmark(lines.get(1).split(",", -1));
    }
  }
}
