package neqsim.thermo.util.readwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies that writing a characterised fluid to Eclipse E300 and reading it back reproduces the liquid density.
 *
 * <p>
 * Characterised TBP and plus fractions take their Peneloux volume translation from the Rackett compressibility of the
 * characterisation rather than from an explicitly set volume-shift constant. Exporting the constant therefore wrote
 * SSHIFT = 0 and dropped the translation, which left the condensate density of the exported model roughly 14 % too
 * high.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EclipseFluidReadWriteVolumeShiftTest {
  /**
   * Build a gas condensate with characterised heavy ends.
   *
   * @return an initialised fluid
   */
  private SystemInterface createGasCondensate() {
    SystemInterface fluid = new SystemPrEos1978(273.15 + 80.0, 100.0);
    fluid.getCharacterization().setTBPModel("PedersenPR");
    fluid.addComponent("CO2", 7.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 4.0);
    fluid.addComponent("propane", 1.5);
    fluid.addTBPfraction("C7", 1.0, 96.0 / 1000.0, 0.727);
    fluid.addTBPfraction("C10", 0.9, 134.0 / 1000.0, 0.782);
    fluid.addTBPfraction("C20", 0.6, 275.0 / 1000.0, 0.866);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * The written SSHIFT must reproduce the volume translation, so both phase densities survive the round trip.
   *
   * @param tempDir temporary directory supplied by JUnit
   * @throws IOException if the E300 file cannot be written
   */
  @Test
  void testVolumeShiftSurvivesE300RoundTrip(@TempDir Path tempDir) throws IOException {
    SystemInterface fluid = createGasCondensate();
    Path file = tempDir.resolve("gascondensate.e300");
    EclipseFluidReadWrite.write(fluid, file.toString(), 80.0);
    assertTrue(Files.exists(file));

    SystemInterface back = EclipseFluidReadWrite.read(file.toString());
    back.setMultiPhaseCheck(true);

    // The heavy pseudo-components must carry a non-zero translation on both sides.
    // The file stores SSHIFT with six decimals, so the tolerance is file precision.
    double shift = fluid.getComponent(6).getVolumeCorrection();
    assertTrue(Math.abs(shift) > 1.0e-6, "characterisation must produce a volume translation");
    assertEquals(shift, back.getComponent(6).getVolumeCorrection(), 1.0e-4 * Math.abs(shift));

    flashToStandardConditions(fluid);
    flashToStandardConditions(back);
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(fluid.getNumberOfPhases(), back.getNumberOfPhases());

    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      double expected = fluid.getPhase(i).getDensity("kg/m3");
      assertEquals(expected, back.getPhase(i).getDensity("kg/m3"), 0.005 * expected,
          "phase " + fluid.getPhase(i).getPhaseTypeName() + " density must survive the round trip");
    }
  }

  /**
   * Flash a fluid to 15 C and 1.01325 bara and initialise its physical properties.
   *
   * @param fluid the fluid to flash
   */
  private void flashToStandardConditions(SystemInterface fluid) {
    fluid.setTemperature(288.15);
    fluid.setPressure(1.01325);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
  }
}
