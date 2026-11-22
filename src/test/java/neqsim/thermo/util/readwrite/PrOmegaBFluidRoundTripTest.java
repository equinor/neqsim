package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrCPA;
import neqsim.thermo.system.SystemUMRCPAEoS;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * PR OmegaB text persistence and documented Java usage regressions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PrOmegaBFluidRoundTripTest {
  /** Temporary directory for isolated E300 files. */
  @TempDir
  Path directory;

  /**
   * Verifies E300 import, replicated fluids, and flash preserve component-specific covolumes.
   *
   * @throws Exception if the temporary fluid file cannot be written or read
   */
  @Test
  void eclipseRoundTripAndReplication() throws Exception {
    SystemInterface fluid = tunedFluid();
    Path file = directory.resolve("omega-b.e300");
    EclipseFluidReadWrite.write(fluid, file.toString(), 76.85);
    SystemInterface legacy = EclipseFluidReadWrite.read(file.toString());
    assertFalse(((ComponentPR) legacy.getComponent(0)).hasOmegaBOverride());
    SystemInterface restored = EclipseFluidReadWrite.readWithPrOmegaB(file.toString());
    verifyCoefficients(restored);
    SystemInterface repeated = EclipseFluidReadWrite.readWithPrOmegaB(file.toString(),
        new String[] {"first", "second"});
    verifyCoefficients(repeated);
    new ThermodynamicOperations(restored).TPflash();
    verifyCoefficients(restored);
    assertFalse(((ComponentPR) EclipseFluidReadWrite.read(file.toString()).getComponent(0)).hasOmegaBOverride());
  }

  /**
   * Verifies untuned E300 output and legacy imports retain their historical default behavior.
   *
   * @throws Exception if the temporary fluid file cannot be written or read
   */
  @Test
  void eclipseDefaultOutputAndReadStayUnchanged() throws Exception {
    SystemInterface fluid = new SystemPrEos(350.0, 100.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    Path file = directory.resolve("default.e300");
    EclipseFluidReadWrite.write(fluid, file.toString(), 76.85);
    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(content.contains("OMEGAB\n     0.07780\n"));
    SystemInterface restored = EclipseFluidReadWrite.read(file.toString());
    assertEquals(((ComponentPR) fluid.getComponent(0)).getOmegaB(),
        ((ComponentPR) restored.getComponent(0)).getOmegaB(), 0.0);
  }

  /**
   * Verifies tuning one component does not round the untuned component's coefficient on an opt-in round trip.
   *
   * @throws Exception if the temporary fluid file cannot be written or read
   */
  @Test
  void eclipseMixedTuningKeepsUntunedCoefficient() throws Exception {
    SystemInterface fluid = tunedFluid();
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      ((ComponentPR) fluid.getPhase(phase).getComponent(1)).clearOmegaBOverride();
    }
    fluid.init(0);
    Path file = directory.resolve("mixed.e300");
    EclipseFluidReadWrite.write(fluid, file.toString(), 76.85);
    SystemInterface restored = EclipseFluidReadWrite.readWithPrOmegaB(file.toString());
    assertEquals(0.081, ((ComponentPR) restored.getComponent(0)).getOmegaB(), 0.0);
    assertEquals(0.077803333, ((ComponentPR) restored.getComponent(1)).getOmegaB(), 0.0);
  }

  /**
   * Verifies malformed OMEGAB coefficients fail rather than silently restoring the PR default.
   *
   * @throws Exception if the temporary fluid file cannot be written
   */
  @Test
  void eclipseRejectsInvalidCovolume() throws Exception {
    String content = EclipseFluidReadWrite.toE300String(tunedFluid(), 76.85);
    String invalid = content.replaceFirst("OMEGAB\\s+[0-9.]+", "OMEGAB\n  -0.1");
    Path file = directory.resolve("invalid.e300");
    Files.write(file, invalid.getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> EclipseFluidReadWrite.readWithPrOmegaB(file.toString()));
  }

  /**
   * Verifies the existing forced-CPA import continues to ignore PR OMEGAB values.
   *
   * @throws Exception if the temporary fluid file cannot be written or read
   */
  @Test
  void forcedCpaLegacyImportIsUnchanged() throws Exception {
    Path file = directory.resolve("forced-cpa.e300");
    EclipseFluidReadWrite.write(tunedFluid(), file.toString(), 76.85);
    SystemInterface cpa = EclipseFluidReadWrite.read(file.toString(), new SystemPrCPA(350.0, 100.0));
    assertTrue(cpa.getComponent(0) instanceof neqsim.thermo.component.ComponentCPAInterface);
    SystemInterface umr = EclipseFluidReadWrite.read(file.toString(), new SystemUMRCPAEoS(350.0, 100.0));
    assertFalse(((ComponentPR) umr.getComponent(0)).hasOmegaBOverride());
    assertThrows(UnsupportedOperationException.class, () -> ((ComponentPR) umr.getComponent(0)).setOmegaB(0.081));
  }

  /** Verifies optional JSON tuning survives reload and is rejected on an incompatible EOS. */
  @Test
  void jsonRoundTripAndValidation() {
    String content = JsonFluidReadWrite.toJsonString(tunedFluid());
    assertTrue(content.contains("omegaB"));
    SystemInterface restored = JsonFluidReadWrite.readString(content);
    verifyCoefficients(restored);
    new ThermodynamicOperations(restored).TPflash();
    verifyCoefficients(restored);
    assertThrows(IllegalArgumentException.class,
        () -> JsonFluidReadWrite.readString(content.replace("\"eos\": \"PR\"", "\"eos\": \"SRK\"")));
    assertThrows(IllegalArgumentException.class,
        () -> JsonFluidReadWrite.readString(content.replace("\"omegaB\": 0.081", "\"omegaB\": 0.0")));
  }

  /**
   * Constructs the synthetic fluid used by the documented per-phase tuning recipe.
   *
   * @return tuned initialized PR fluid
   */
  private SystemInterface tunedFluid() {
    SystemInterface fluid = new SystemPrEos(350.0, 100.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("n-decane", 0.6);
    fluid.setMixingRule("classic");
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      ((ComponentPR) fluid.getPhase(phase).getComponent("methane")).setOmegaB(0.081);
      ((ComponentPR) fluid.getPhase(phase).getComponent("n-decane")).setOmegaB(0.079);
    }
    fluid.init(0);
    return fluid;
  }

  /**
   * Verifies coefficient identity and the analytical covolume in each fluid block and allocated phase.
   *
   * @param fluid imported or calculated fluid
   */
  private void verifyCoefficients(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
        ComponentPR component = (ComponentPR) fluid.getPhase(phase).getComponent(index);
        double expected = index % 2 == 0 ? 0.081 : 0.079;
        assertEquals(expected, component.getOmegaBOverride(), 0.0);
        assertEquals(expected * component.R * component.getTC() / component.getPC(), component.getb(), 1e-14);
      }
    }
  }
}
