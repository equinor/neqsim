package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.thermo.phase.PhasePitzer;
import neqsim.util.database.NeqSimDataBase;

/**
 * Verifies that the extended component database preserves the standard Pitzer electrolyte identities and results.
 */
public class PitzerComponentDatabaseCompatibilityTest extends neqsim.NeqSimTest {
  private static final String[] VALUE_NAMES = { "Na+ ionic charge", "Ca++ ionic charge", "Cl- ionic charge",
      "Na+ molar mass", "Ca++ molar mass", "Cl- molar mass", "NaCl beta0", "NaCl beta1", "CaCl2 beta0", "CaCl2 beta1",
      "Na+ activity coefficient", "Ca++ activity coefficient", "Cl- activity coefficient", "water osmotic coefficient",
      "water activity", "aqueous density", "aqueous molar volume", "aqueous enthalpy", "aqueous entropy",
      "aqueous Gibbs energy" };

  /**
   * The standard and extended component databases must produce the same mixed-brine Pitzer state.
   */
  @Test
  public void standardAndExtendedDatabasesPreservePitzerElectrolyteResults() {
    PitzerSnapshot standard;
    PitzerSnapshot extended;

    synchronized (NeqSimDataBase.class) {
      try {
        NeqSimDataBase.useExtendedComponentDatabase(false);
        standard = createSnapshot();

        NeqSimDataBase.useExtendedComponentDatabase(true);
        extended = createSnapshot();
      } finally {
        NeqSimDataBase.useExtendedComponentDatabase(false);
      }
    }

    assertEquals(standard.parameterDatasetId, extended.parameterDatasetId,
        "Switching component database must not change the Pitzer parameter dataset");
    assertEquals(standard.values.length, VALUE_NAMES.length);
    assertEquals(extended.values.length, VALUE_NAMES.length);

    for (int i = 0; i < VALUE_NAMES.length; i++) {
      double tolerance = Math.max(1.0, Math.abs(standard.values[i])) * 1.0e-12;
      assertEquals(standard.values[i], extended.values[i], tolerance,
          VALUE_NAMES[i] + " must be invariant across component database modes");
    }
  }

  private static PitzerSnapshot createSnapshot() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.useLegacyPitzerParameters();
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 0.5);
    system.addComponent("Ca++", 0.1);
    system.addComponent("Cl-", 0.7);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    system.initPhysicalProperties();

    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    phase.loadParametersFromDatabase();

    int water = phase.getComponent("water").getComponentNumber();
    int sodium = phase.getComponent("Na+").getComponentNumber();
    int calcium = phase.getComponent("Ca++").getComponentNumber();
    int chloride = phase.getComponent("Cl-").getComponentNumber();

    assertEquals(1.0, phase.getComponent(sodium).getIonicCharge(), 0.0);
    assertEquals(2.0, phase.getComponent(calcium).getIonicCharge(), 0.0);
    assertEquals(-1.0, phase.getComponent(chloride).getIonicCharge(), 0.0);
    assertTrue(Math.abs(phase.getBeta0ij(sodium, chloride)) > 0.0,
        "NaCl beta0 must be loaded from the legacy Pitzer database");
    assertTrue(Math.abs(phase.getBeta1ij(sodium, chloride)) > 0.0,
        "NaCl beta1 must be loaded from the legacy Pitzer database");
    assertTrue(Math.abs(phase.getBeta0ij(calcium, chloride)) > 0.0,
        "CaCl2 beta0 must be loaded from the legacy Pitzer database");
    assertTrue(Math.abs(phase.getBeta1ij(calcium, chloride)) > 0.0,
        "CaCl2 beta1 must be loaded from the legacy Pitzer database");

    double[] values = { phase.getComponent(sodium).getIonicCharge(), phase.getComponent(calcium).getIonicCharge(),
        phase.getComponent(chloride).getIonicCharge(), phase.getComponent(sodium).getMolarMass(),
        phase.getComponent(calcium).getMolarMass(), phase.getComponent(chloride).getMolarMass(),
        phase.getBeta0ij(sodium, chloride), phase.getBeta1ij(sodium, chloride), phase.getBeta0ij(calcium, chloride),
        phase.getBeta1ij(calcium, chloride), phase.getActivityCoefficient(sodium, water),
        phase.getActivityCoefficient(calcium, water), phase.getActivityCoefficient(chloride, water),
        phase.getOsmoticCoefficientOfWater(),
        phase.getActivityCoefficient(water, water) * phase.getComponent(water).getx(), phase.getDensity(),
        phase.getMolarVolume(), phase.getEnthalpy(), phase.getEntropy(), phase.getGibbsEnergy() };

    for (int i = 0; i < values.length; i++) {
      assertTrue(Double.isFinite(values[i]), VALUE_NAMES[i] + " must be finite");
    }
    assertTrue(values[10] > 0.0, "Na+ activity coefficient must be positive");
    assertTrue(values[11] > 0.0, "Ca++ activity coefficient must be positive");
    assertTrue(values[12] > 0.0, "Cl- activity coefficient must be positive");
    assertTrue(values[13] > 0.0, "water osmotic coefficient must be positive");
    assertTrue(values[14] > 0.0, "water activity must be positive");
    assertTrue(values[15] > 0.0, "aqueous density must be positive");
    assertTrue(values[16] > 0.0, "aqueous molar volume must be positive");

    return new PitzerSnapshot(phase.getParameterDatasetId(), values);
  }

  private static final class PitzerSnapshot {
    private final String parameterDatasetId;
    private final double[] values;

    private PitzerSnapshot(String parameterDatasetId, double[] values) {
      this.parameterDatasetId = parameterDatasetId;
      this.values = values;
    }
  }
}
