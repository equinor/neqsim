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
 * and gives the physically expected small pump temperature rise. The complete lifecycle qualification enforces phase
 * and composition normalization, component balance, fugacity equality, poor initialization recovery, state reuse,
 * return continuity, and deterministic repeat.
 * </p>
 */
class TPflashIssue3943PumpRegressionTest {
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

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
      assertQualifiedState(state);
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
    assertQualifiedState(outlet);
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
    assertQualifiedState(outlet);
  }

  @Test
  void poorInitializationRecoversTheQualifiedAqueousBranch() throws Exception {
    SystemInterface reference = flashAt(375.0);
    SystemInterface poorGuess = inlet();
    poorGuess.setPressure(19.0, "bara");
    poorGuess.setTemperature(375.0);
    poorGuess.setBeta(0, 1.0e-12);
    poorGuess.setBeta(1, 1.0 - 1.0e-12);
    new ThermodynamicOperations(poorGuess).TPflash();

    assertEquivalentState(reference, poorGuess, "poor initialization");
  }

  @Test
  void changedReturnedAndRepeatedStatesRemainEquivalent() throws Exception {
    SystemInterface reference = flashAt(375.0);
    SystemInterface changedReference = flashAt(376.0);
    SystemInterface reused = flashAt(375.0);

    reused.setTemperature(376.0);
    new ThermodynamicOperations(reused).TPflash();
    assertEquivalentState(changedReference, reused, "changed state");

    reused.setTemperature(375.0);
    new ThermodynamicOperations(reused).TPflash();
    assertEquivalentState(reference, reused, "returned state");

    SystemInterface settled = reused.clone();
    new ThermodynamicOperations(reused).TPflash();
    assertEquivalentState(settled, reused, "deterministic repeat");
  }

  private SystemInterface flashAt(double temperature) throws Exception {
    SystemInterface state = inlet();
    state.setPressure(19.0, "bara");
    state.setTemperature(temperature);
    new ThermodynamicOperations(state).TPflash();
    return state;
  }

  private void assertEquivalentState(SystemInterface expected, SystemInterface actual, String label) {
    assertQualifiedState(expected);
    assertQualifiedState(actual);
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases(), label + " phase count");

    for (PhaseType type : new PhaseType[] {PhaseType.OIL, PhaseType.AQUEOUS}) {
      int expectedPhase = phaseIndex(expected, type);
      int actualPhase = phaseIndex(actual, type);
      assertTrue(expectedPhase >= 0 && actualPhase >= 0, label + " " + type + " phase");
      assertEquals(expected.getBeta(expectedPhase), actual.getBeta(actualPhase), 1.0e-10,
          label + " " + type + " phase fraction");
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(expectedPhase).getComponent(component).getx(),
            actual.getPhase(actualPhase).getComponent(component).getx(), 1.0e-10, label + " " + type + " composition");
      }
    }
    assertEquals(expected.getGibbsEnergy(), actual.getGibbsEnergy(),
        Math.max(1.0e-6, Math.abs(expected.getGibbsEnergy()) * 1.0e-10), label + " Gibbs energy");
  }

  private int phaseIndex(SystemInterface state, PhaseType type) {
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      if (state.getPhase(phase).getType() == type) {
        return phase;
      }
    }
    return -1;
  }

  private void assertQualifiedState(SystemInterface state) {
    double betaSum = 0.0;
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      betaSum += state.getBeta(phase);
      double compositionSum = 0.0;
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        compositionSum += state.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(1.0, compositionSum, NORMALIZATION_TOLERANCE);
    }
    assertEquals(1.0, betaSum, NORMALIZATION_TOLERANCE);

    double maximumMaterialResidual = 0.0;
    for (int component = 0; component < state.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
        recovered += state.getBeta(phase) * state.getPhase(phase).getComponent(component).getx();
      }
      maximumMaterialResidual = Math.max(maximumMaterialResidual,
          Math.abs(state.getPhase(0).getComponent(component).getz() - recovered));
    }
    assertTrue(maximumMaterialResidual < MATERIAL_BALANCE_TOLERANCE,
        "maximum component material-balance residual was " + maximumMaterialResidual);

    if (state.getNumberOfPhases() == 2) {
      double maximumLogFugacityResidual = 0.0;
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        double firstComposition = state.getPhase(0).getComponent(component).getx();
        double secondComposition = state.getPhase(1).getComponent(component).getx();
        if (firstComposition > 1.0e-12 && secondComposition > 1.0e-12) {
          double firstFugacity = firstComposition * state.getPhase(0).getComponent(component).getFugacityCoefficient();
          double secondFugacity = secondComposition
              * state.getPhase(1).getComponent(component).getFugacityCoefficient();
          maximumLogFugacityResidual = Math.max(maximumLogFugacityResidual,
              Math.abs(Math.log(firstFugacity / secondFugacity)));
        }
      }
      assertTrue(maximumLogFugacityResidual < FUGACITY_TOLERANCE,
          "maximum log fugacity residual was " + maximumLogFugacityResidual);
    }
  }
}
