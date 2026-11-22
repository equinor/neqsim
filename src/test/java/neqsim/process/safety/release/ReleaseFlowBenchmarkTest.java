package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Reproducible analytical limits and physical-invariant coverage, not experimental qualification. */
class ReleaseFlowBenchmarkTest extends neqsim.NeqSimTest {
  private SystemInterface fluid(String component, double temperature, double pressureBar) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressureBar);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReleaseFlowResult calculate(SystemInterface fluid, double backPressurePa) {
    ReleaseFlowResult result = new HomogeneousEquilibriumReleaseModel()
        .calculate(new ReleaseFlowRequest(fluid, 0.01, 0.62, backPressurePa));
    assertTrue(result.isUsable(), () -> result.getDiagnostics().get(0).getMessage());
    ReleaseState initial = result.getStations().get(Station.UPSTREAM_STAGNATION);
    for (ReleaseState state : result.getStations().values()) {
      assertEquals(initial.getEntropyJkgK(), state.getEntropyJkgK(), 1e-5);
      assertEquals(initial.getEnthalpyJkg(), state.getEnthalpyJkg() + 0.5 * Math.pow(state.getVelocityMs(), 2), 1e-5);
      assertEquals(1.0, state.getPhaseMassFractions().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
      assertEquals(initial.getComponentMoleFractions(), state.getComponentMoleFractions());
    }
    return result;
  }

  @Test
  void diluteGasBackpressureSweepMatchesIndependentPerfectGasEquation() throws Exception {
    SystemInterface gas = fluid("methane", 300.0, 0.1);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    double gamma = gas.getGamma();
    double specificGasConstant = 8.314462618 / gas.getMolarMass();
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    List<String> rows = new ArrayList<String>();
    rows.add("backpressure_ratio,reference_kg_s,hem_kg_s,relative_error,choked");
    for (double ratio : new double[] {0.05, 0.30, 0.70, 0.90, 0.98}) {
      double throatRatio = Math.max(ratio, criticalRatio);
      double expected = 0.62 * Math.PI * 0.01 * 0.01 / 4.0 * 10000.0
          * Math.sqrt(2.0 * gamma / (specificGasConstant * 300.0 * (gamma - 1.0))
              * (Math.pow(throatRatio, 2.0 / gamma) - Math.pow(throatRatio, (gamma + 1.0) / gamma)));
      ReleaseFlowResult result = calculate(gas, ratio * 10000.0);
      assertEquals(ratio < criticalRatio, result.isChoked());
      assertEquals(expected, result.getMassFlowRateKgS(), expected * 0.015);
      double error = (result.getMassFlowRateKgS() - expected) / expected;
      rows.add(ratio + "," + expected + "," + result.getMassFlowRateKgS() + "," + error + "," + result.isChoked());
    }
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    Files.write(directory.resolve("dilute-gas.csv"), rows, StandardCharsets.UTF_8);
  }

  @Test
  void denseFluidHighBackpressureAndCo2SolidRiskAreExplicit() throws Exception {
    SystemInterface dense = fluid("CO2", 310.0, 120.0);
    ReleaseFlowResult result = calculate(dense, 110e5);
    assertFalse(result.isChoked());
    assertTrue(result.getStations().get(Station.UPSTREAM_STAGNATION).getDensityKgM3() > 200.0);
    assertEquals(110e5, result.getStations().get(Station.THROAT_CRITICAL).getPressurePa(), 1e-5);
    SystemInterface cold = fluid("CO2", 210.0, 20.0);
    ReleaseFlowResult unsupported = new HomogeneousEquilibriumReleaseModel()
        .calculate(new ReleaseFlowRequest(cold, 0.01, 0.62, 1e5));
    assertEquals(ReleaseFlowResult.Status.UNSUPPORTED, unsupported.getStatus());
    assertThrows(IllegalStateException.class, unsupported::getMassFlowRateKgS);
    Path directory = Paths.get("target", "source-term-benchmarks");
    Files.createDirectories(directory);
    List<String> rows = new ArrayList<String>();
    rows.add("case,temperature_K,upstream_Pa,backpressure_Pa,status,rate_kg_s,evidence");
    rows.add("dense-co2,310.0,12000000.0,11000000.0," + result.getStatus() + "," + result.getMassFlowRateKgS()
        + ",conservation-only");
    rows.add("cold-co2,210.0,2000000.0,100000.0," + unsupported.getStatus() + ",,applicability-guard");
    Files.write(directory.resolve("dense-fluid.csv"), rows, StandardCharsets.UTF_8);
  }
}
