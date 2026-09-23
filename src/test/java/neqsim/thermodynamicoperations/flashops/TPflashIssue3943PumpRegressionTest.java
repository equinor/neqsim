package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.serialization.SerializationManager;

/**
 * Regression for the serialized pump inlet supplied with issue #3943 (NeqSim 3.22.0).
 *
 * <p>
 * The saved GAS/OIL inlet is metastable. Reflashing it at the scrubber pressure finds the equilibrium aqueous branch
 * and gives the physically expected small pump temperature rise.
 * </p>
 */
class TPflashIssue3943PumpRegressionTest {
  private SystemInterface inlet() throws Exception {
    String resource = "/neqsim/thermodynamicoperations/flashops/issue3943/pump1_inlet_fluid.ser.gz.b64";
    Path serialized = Files.createTempFile("neqsim-3943-pump-inlet", ".ser");
    try {
      try (InputStream base64 = getClass().getResourceAsStream(resource);
          InputStream decoded = Base64.getMimeDecoder().wrap(base64);
          GZIPInputStream decompressed = new GZIPInputStream(decoded)) {
        Files.copy(decompressed, serialized, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return (SystemInterface) SerializationManager.open(serialized.toString());
    } finally {
      Files.deleteIfExists(serialized);
    }
  }

  @Test
  void waterRichTpFlashStaysOnTheLowerGibbsAqueousBranch() throws Exception {
    SystemInterface inlet = inlet();
    double previousEntropy = Double.NEGATIVE_INFINITY;
    for (double temperature : new double[] {374.0, 374.97, 375.0, 376.0, 377.501, 377.5011, 377.502, 377.6, 378.2}) {
      SystemInterface state = inlet.clone();
      state.setPressure(19.0, "bara");
      state.setTemperature(temperature);
      new ThermodynamicOperations(state).TPflash();
      assertEquals(2, state.getNumberOfPhases(), "T=" + temperature);
      assertTrue(state.hasPhaseType(PhaseType.OIL), "T=" + temperature);
      assertTrue(state.hasPhaseType(PhaseType.AQUEOUS), "T=" + temperature);
      assertTrue(state.getGibbsEnergy() < -50000.0, "T=" + temperature);
      assertComponentBalance(state);
      state.init(3);
      assertTrue(state.getEntropy() > previousEntropy, "non-monotonic equilibrium entropy at T=" + temperature);
      previousEntropy = state.getEntropy();
    }
  }

  @Test
  void pumpOutletPsFlashClosesTheInletEntropy() throws Exception {
    SystemInterface inlet = inlet();
    inlet.init(3);
    double targetEntropy = inlet.getEntropy();
    SystemInterface outlet = inlet.clone();
    outlet.setPressure(19.0, "bara");
    new ThermodynamicOperations(outlet).PSflash(targetEntropy);
    assertEquals(targetEntropy, outlet.getEntropy(), PSFlash.entropyTolerance(outlet, targetEntropy));
    assertComponentBalance(outlet);
  }

  @Test
  void equilibriumScrubberInletGivesSmallIsentropicPumpTemperatureRise() throws Exception {
    SystemInterface inlet = inlet();
    new ThermodynamicOperations(inlet).TPflash();
    inlet.init(3);
    assertTrue(inlet.hasPhaseType(PhaseType.AQUEOUS));
    double targetEntropy = inlet.getEntropy();

    SystemInterface outlet = inlet.clone();
    outlet.setPressure(19.0, "bara");
    new ThermodynamicOperations(outlet).PSflash(targetEntropy);
    assertEquals(targetEntropy, outlet.getEntropy(), PSFlash.entropyTolerance(outlet, targetEntropy));
    assertEquals(298.61, outlet.getTemperature(), 0.1);
    assertComponentBalance(outlet);
  }

  private void assertComponentBalance(SystemInterface state) {
    for (int component = 0; component < state.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
        recovered += state.getBeta(phase) * state.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(state.getPhase(0).getComponent(component).getz(), recovered, 1.0e-8);
    }
  }
}
