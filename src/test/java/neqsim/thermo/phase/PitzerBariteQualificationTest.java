package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import neqsim.thermo.phase.PitzerParameterQualification.ValidationTarget;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Frozen public barite qualification blockers; no missing interaction is filled by a test.
 *
 * @author esol
 * @version 1.0
 */
class PitzerBariteQualificationTest extends neqsim.NeqSimTest {
  private static final List<String> MISSING = Arrays.asList("B0:[Ba++, SO4--]", "B1:[Ba++, SO4--]", "C0:[Ba++, SO4--]",
      "PSI:[Ba++, Cl-, Na+]", "PSI:[Ba++, Cl-, SO4--]", "PSI:[Ba++, Na+, SO4--]");

  /**
   * Freeze unsupported public feeds and preserve inventory on repeated rejected precipitation attempts.
   *
   * @param id case identity
   * @param temperature temperature in K
   * @param pressure absolute pressure in bar
   * @param waterKg solvent mass in kg
   * @param sodium sodium molality
   * @param barium barium molality
   * @param chloride chloride molality
   * @param sulfate sulfate molality
   */
  @ParameterizedTest
  @CsvFileSource(resources = "/neqsim/thermo/phase/barite-qualification-cases.csv", numLinesToSkip = 1)
  void frozenFeedsRemainUnqualified(String id, double temperature, double pressure, double waterKg, double sodium,
      double barium, double chloride, double sulfate) {
    SystemPitzer system = createSystem(temperature, pressure, waterKg, sodium, barium, chloride, sulfate);
    assertEquals(0.0, sodium + 2.0 * barium - chloride - 2.0 * sulfate, 1.0e-14, id);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    String originalDataset = phase.getParameterDatasetId();
    double[] initial = inventory(system);
    assertEquals(MISSING, system.getMissingPhreeqcPitzerCatalogRows(), id);
    assertEquals(originalDataset, phase.getParameterDatasetId());
    assertArrayEquals(initial, inventory(system), 0.0);
    assertFalse(PitzerParameterDatasets.tryApplyCompletePhreeqcPitzerCatalog(phase));
    assertEquals(originalDataset, phase.getParameterDatasetId());
    assertThrows(IllegalStateException.class,
        () -> system.requirePitzerDatasetValidationFor(ValidationTarget.MINERAL_SATURATION_AND_PRECIPITATION));
    for (int repeat = 0; repeat < 2; repeat++) {
      assertThrows(IllegalStateException.class, () -> new ThermodynamicOperations(system).precipitateScale("BaSO4"),
          id);
      assertEquals(MISSING, system.getMissingPhreeqcPitzerCatalogRows());
      assertArrayEquals(initial, inventory(system), 1.0e-12);
    }
  }

  /** Check source presence, not just fitted values: an explicit zero psi is present. */
  @Test
  void catalogEvidenceDistinguishesAbsentRowsFromExplicitZeros() {
    PhreeqcPitzerParameterCatalog catalog = PhreeqcPitzerParameterCatalog.getInstance();
    assertEquals(0.07, catalog.require(PhreeqcPitzerParameterCatalog.Family.THETA, "Ba++", "Na+")[0], 0.0);
    assertEquals(0.03, catalog.require(PhreeqcPitzerParameterCatalog.Family.THETA, "Cl-", "SO4--")[0], 0.0);
    assertEquals(0.0, catalog.require(PhreeqcPitzerParameterCatalog.Family.PSI, "Na+", "Cl-", "SO4--")[0], 0.0);
    SystemPitzer sulfateBrine = createSystem(298.15, 1.01325, 1.0, 1.002, 0.0, 1.0, 0.001);
    assertTrue(sulfateBrine.getMissingPhreeqcPitzerCatalogRows().isEmpty());
    assertFalse(PitzerParameterDatasets.getQualification(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID)
        .isValidatedFor(ValidationTarget.MINERAL_SATURATION_AND_PRECIPITATION));
  }

  /** Auditing a manually defined phase neither overwrites it nor hides catalog deficiencies. */
  @Test
  void auditIsImmutableAndIndependentOfOrderAndManualParameters() {
    SystemPitzer system = createSystem(298.15, 1.01325, 1.0, 1.0, 0.001, 1.0, 0.001);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    phase.setParameterDatasetId("manual-test-dataset");
    int ba = phase.getComponent("Ba++").getComponentNumber();
    int na = phase.getComponent("Na+").getComponentNumber();
    phase.setTheta(ba, na, 0.123);
    List<String> missing = system.getMissingPhreeqcPitzerCatalogRows();
    assertThrows(UnsupportedOperationException.class, () -> missing.clear());
    assertEquals(MISSING, missing);
    assertEquals("manual-test-dataset", phase.getParameterDatasetId());
    assertEquals(0.123, phase.getThetaij(ba, na), 0.0);
    SystemPitzer reverse = new SystemPitzer(298.15, 1.01325);
    for (int i = system.getNumberOfComponents() - 1; i >= 0; i--) {
      reverse.addComponent(system.getComponent(i).getComponentName(), system.getComponent(i).getNumberOfmoles());
    }
    reverse.setMixingRule("classic");
    reverse.init(0);
    assertEquals(missing, reverse.getMissingPhreeqcPitzerCatalogRows());
    assertEquals(missing, system.clone().getMissingPhreeqcPitzerCatalogRows());
    assertThrows(IllegalArgumentException.class,
        () -> PitzerParameterDatasets.getMissingPhreeqcPitzerCatalogRows(null));
  }

  /**
   * Build a molality-defined feed on an exact solvent-mass basis using the current water molar mass.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param waterKg water mass in kg
   * @param sodium sodium molality
   * @param barium barium molality
   * @param chloride chloride molality
   * @param sulfate sulfate molality
   * @return initialized system
   */
  private static SystemPitzer createSystem(double temperature, double pressure, double waterKg, double sodium,
      double barium, double chloride, double sulfate) {
    SystemPitzer system = new SystemPitzer(temperature, pressure);
    system.addComponent("water", 1.0);
    system.addComponent("water", waterKg / system.getComponent("water").getMolarMass() - 1.0);
    system.addComponent("Na+", sodium * waterKg);
    system.addComponent("Ba++", barium * waterKg);
    system.addComponent("Cl-", chloride * waterKg);
    system.addComponent("SO4--", sulfate * waterKg);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  /**
   * Capture system-wide component amounts before or after a rejected operation.
   *
   * @param system fluid
   * @return component moles in registration order
   */
  private static double[] inventory(SystemPitzer system) {
    double[] result = new double[system.getNumberOfComponents()];
    for (int i = 0; i < result.length; i++) {
      result[i] = system.getComponent(i).getNumberOfmoles();
    }
    return result;
  }

  /** Reuse the selector's neutral policy, and recompute the audit after composition changes. */
  @Test
  void auditIncludesNeutralGapsAndExcludesEosHydrocarbons() {
    SystemPitzer system = createSystem(298.15, 1.01325, 1.0, 1.002, 0.0, 1.0, 0.001);
    system.addComponent("methane", 0.01);
    system.setMixingRule("classic");
    system.init(0);
    assertTrue(system.getMissingPhreeqcPitzerCatalogRows().isEmpty());
    assertTrue(system.refreshDefaultPitzerParameterSelection());

    system.addComponent("CO2", 0.01);
    system.setMixingRule("classic");
    system.init(0);
    List<String> missing = system.getMissingPhreeqcPitzerCatalogRows();
    assertEquals(Arrays.asList("ZETA:[CO2, Cl-, Na+]"), missing);
    assertFalse(system.refreshDefaultPitzerParameterSelection());

    system.addComponent("H2S", 0.01);
    system.setMixingRule("classic");
    system.init(0);
    List<String> expanded = system.getMissingPhreeqcPitzerCatalogRows();
    assertTrue(expanded.contains("LAMBDA:[CO2, H2S]"));
    assertTrue(expanded.contains("LAMBDA:[H2S, H2S]"));
    assertTrue(expanded.contains("LAMBDA:[H2S, Na+]"));
    assertTrue(expanded.contains("ZETA:[Cl-, H2S, Na+]"));
    assertEquals(Arrays.asList("ZETA:[CO2, Cl-, Na+]"), missing);
    assertFalse(expanded.toString().contains("methane"));
  }

  /** Reproduce issue 4351 without replacing missing coefficients or the flash. */
  @Test
  void reportedMixtureFailsClosed() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ba++", 0.001);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", 0.001);
    system.setMixingRule("classic");
    system.init(0);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(system).precipitateScale("BaSO4"));
    assertTrue(error.getMessage().contains("Failed running precipitation TPflash for BaSO4"));
    assertTrue(error.getCause().getMessage().contains("Pitzer parameter coverage incomplete"));
    PitzerParameterCoverage coverage = ((PhasePitzer) system.getPhase(1)).getPitzerParameterCoverage();
    assertFalse(coverage.isComplete());
    assertEquals(2, coverage.getMissingThetaPairs().size());
    assertEquals(4, coverage.getMissingPsiTuples().size());
  }
}
