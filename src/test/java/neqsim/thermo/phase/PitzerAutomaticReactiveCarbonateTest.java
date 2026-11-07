package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/**
 * Automatic parameter selection for the public gas-oil-reactive mixed-brine reproducer.
 *
 * @author esol
 * @version 1.0
 */
class PitzerAutomaticReactiveCarbonateTest {
  /** Verifies that the ordinary setup needs no external parameter setter or preloading order. */
  @Test
  void ordinaryReactiveMixedBrineSetupSelectsCompleteParameters() {
    SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("CO2", 0.5);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.5);
    fluid.addComponent("Cl-", 1.0);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    new ThermodynamicOperations(fluid).TPflash();

    PhasePitzer aqueous = (PhasePitzer) fluid.getPhase("aqueous");
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(aqueous.getPitzerParameterCoverage().isComplete());
    assertTrue(aqueous.getPitzerReactionParameterCoverage().isComplete());
    assertTrue(aqueous.auditNeutralPitzerParameterCoverage().isComplete());
    assertTrue(aqueous.getParameterDatasetId().contains("reactive-nakcl-carbonate"));
    assertWholeFluidElements(fluid);
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      double beta = fluid.getBeta(phaseIndex);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      if (phase != aqueous) {
        for (int index = 0; index < phase.getNumberOfComponents(); index++) {
          if (Math.abs(phase.getComponent(index).getIonicCharge()) > 0.5) {
            assertTrue(phase.getComponent(index).getNumberOfMolesInPhase() < 1.0e-15);
          }
        }
      }
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    int water = aqueous.getComponent("water").getComponentNumber();
    int hydrogen = aqueous.getComponent("H3O+").getComponentNumber();
    double activity = aqueous.getComponent(hydrogen).getMolality(aqueous)
        * aqueous.getActivityCoefficient(hydrogen, water);
    assertTrue(-Math.log10(activity) > 3.0 && -Math.log10(activity) < 4.0);
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 3.0e-6);
    assertTrue(fluid.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual() < 1.0e-12);
  }

  /** Refreshes a carrier-brine catalog selected before CO2 and reaction species were inserted. */
  @Test
  void previouslyEvaluatedCarrierBrineSelectsTheReactiveDataset() {
    SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.5);
    fluid.addComponent("Cl-", 1.0);
    fluid.init(0);
    PhasePitzer phase = (PhasePitzer) fluid.getGeLiquidPhase();
    phase.requireCompletePitzerParameterCoverage();
    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, phase.getParameterDatasetId());
    assertThrows(IllegalStateException.class, phase::getPitzerPH);
    fluid.addComponent("CO2", 0.5);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(PitzerParameterDatasets.PHREEQC_AUTOMATIC_REACTIVE_CARBONATE_ID, phase.getParameterDatasetId());
    assertEquals(54, phase.getReactiveCarbonateSourceZeroDefinitions().size());
    assertWholeFluidElements(fluid);
  }

  /**
   * Independently checks total elemental inventories for the public synthetic feed.
   *
   * @param fluid flashed gas/oil/reactive-brine system
   */
  private static void assertWholeFluidElements(SystemPitzer fluid) {
    java.util.Map<String, Double> expected = new java.util.TreeMap<String, Double>();
    expected.put("C", 19.5);
    expected.put("H", 163.0);
    expected.put("O", 56.5);
    expected.put("Na", 0.5);
    expected.put("K", 0.5);
    expected.put("Cl", 1.0);
    java.util.Map<String, Double> actual = new java.util.TreeMap<String, Double>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      for (int index = 0; index < phase.getNumberOfComponents(); index++) {
        neqsim.thermo.component.ComponentInterface component = phase.getComponent(index);
        for (String element : component.getElements().getElementNames()) {
          double previous = actual.containsKey(element) ? actual.get(element) : 0.0;
          actual.put(element,
              previous + component.getNumberOfMolesInPhase() * component.getElements().getNumberOfElements(element));
        }
      }
    }
    assertEquals(expected.keySet(), actual.keySet());
    for (String element : expected.keySet()) {
      assertEquals(expected.get(element), actual.get(element), 1.0e-8, element + " inventory");
    }
  }

  /**
   * Replays independent acidic NaCl, KCl and mixed-chloride aqueous reference states.
   *
   * @throws Exception when the frozen reference cannot be read
   */
  @Test
  void automaticCoefficientsMatchIndependentIPhreeqcStates() throws Exception {
    String resource = "/neqsim/thermo/phase/reactive-carbonate/iphreeqc-states.csv";
    int states = 0;
    String[] species = {"Na+", "K+", "Cl-", "H3O+", "OH-", "HCO3-", "CO3--", "CO2"};
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(getClass().getResourceAsStream(resource), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("temperature")) {
          continue;
        }
        String[] columns = line.split(",");
        double[] state = new double[columns.length];
        for (int index = 0; index < columns.length; index++) {
          state[index] = "nan".equals(columns[index]) ? Double.NaN : Double.parseDouble(columns[index]);
        }
        SystemPitzer fluid = new SystemPitzer(state[0], state[1]);
        fluid.addComponent("water", 1.0 / 0.01801528);
        for (int index = 0; index < species.length; index++) {
          fluid.addComponent(species[index], state[index + 2]);
        }
        fluid.chemicalReactionInit();
        fluid.setMixingRule("classic");
        fluid.init(0);
        fluid.init(1);
        PhasePitzer aqueous = (PhasePitzer) fluid.getGeLiquidPhase();
        int water = aqueous.getComponent("water").getComponentNumber();
        int hydrogen = aqueous.getComponent("H3O+").getComponentNumber();
        double activity = aqueous.getComponent(hydrogen).getMolality(aqueous)
            * aqueous.getActivityCoefficient(hydrogen, water);
        assertEquals(state[10], aqueous.getPitzerPH(), 1.0e-3, "Unscaled pH at fixture row " + states);
        assertEquals(aqueous.getComponent(hydrogen).getMolality(aqueous) * aqueous.getActivityCoefficient(hydrogen),
            aqueous.getPitzerActivity(hydrogen), 0.0);
        assertEquals(state[11], aqueous.getPitzerActivity(water), 5.0e-5, "Water activity at fixture row " + states);
        for (int index = 0; index < species.length; index++) {
          if (Double.isFinite(state[13 + index])) {
            int component = aqueous.getComponent(species[index]).getComponentNumber();
            assertEquals(state[13 + index], Math.log10(aqueous.getActivityCoefficient(component)), 1.0e-2,
                species[index] + " log10 gamma at fixture row " + states);
          }
        }
        assertTrue(aqueous.getPitzerReactionParameterCoverage().isComplete());
        assertTrue(aqueous.auditNeutralPitzerParameterCoverage().isComplete());
        states++;
      }
    }
    assertEquals(54, states);
  }

  /**
   * Retains parameter provenance through reinitialization, cloning and serialization.
   *
   * @throws Exception when serialization fails
   */
  @Test
  void selectionSurvivesReinitializationCloneAndSerialization() throws Exception {
    SystemPitzer fluid = reactiveSodiumChloride();
    new ThermodynamicOperations(fluid).TPflash();
    PhasePitzer original = (PhasePitzer) fluid.getGeLiquidPhase();
    assertTrue(original.getReactiveCarbonateSourceZeroDefinitions().contains("B0:H+|HCO3-"));
    assertThrows(UnsupportedOperationException.class,
        () -> original.getReactiveCarbonateSourceZeroDefinitions().add("invalid"));
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    SystemPitzer clone = fluid.clone();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(clone);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    new ThermodynamicOperations(restored).TPflash();
    PhasePitzer copy = (PhasePitzer) restored.getGeLiquidPhase();
    assertEquals(original.getParameterDatasetId(), copy.getParameterDatasetId());
    assertEquals(original.getReactiveCarbonateSourceZeroDefinitions(),
        copy.getReactiveCarbonateSourceZeroDefinitions());
    assertTrue(copy.getPitzerReactionParameterCoverage().isComplete());
  }

  /** Preserves explicit legacy policy and the established named manual parameter mode. */
  @Test
  void explicitSelectionsAreNotOverwritten() {
    SystemPitzer legacy = new SystemPitzer(293.15, 7.0);
    legacy.useLegacyPitzerParameters();
    addReactiveFeed(legacy);
    legacy.chemicalReactionInit();
    legacy.setMixingRule("classic");
    legacy.init(0);
    PhasePitzer legacyPhase = (PhasePitzer) legacy.getGeLiquidPhase();
    legacyPhase.getPitzerParameterCoverage();
    assertEquals(PhasePitzer.DEFAULT_PARAMETER_DATASET_ID, legacyPhase.getParameterDatasetId());
    assertTrue(legacyPhase.getReactiveCarbonateSourceZeroDefinitions().isEmpty());

    SystemPitzer manual = reactiveSodiumChloride();
    manual.applyPhreeqcReactiveSodiumChlorideCarbonateParameters();
    manual.chemicalReactionInit();
    manual.setMixingRule("classic");
    new ThermodynamicOperations(manual).TPflash();
    PhasePitzer selected = (PhasePitzer) manual.getGeLiquidPhase();
    assertEquals(PitzerParameterDatasets.PHREEQC_REACTIVE_NACL_CARBONATE_ID, selected.getParameterDatasetId());
  }

  /** Keeps generic missing-row lookup strict and bounds the source-zero list to reviewed carbonate tuples. */
  @Test
  void sourceDefinedZerosDoNotChangeTheGenericCatalog() {
    PhreeqcPitzerParameterCatalog catalog = PhreeqcPitzerParameterCatalog.getInstance();
    assertThrows(IllegalArgumentException.class,
        () -> catalog.require(PhreeqcPitzerParameterCatalog.Family.B0, "H+", "HCO3-"));
    assertEquals(0.0, catalog.requireReactiveCarbonate(PhreeqcPitzerParameterCatalog.Family.B0, "H+", "HCO3-")[0], 0.0);
    assertThrows(IllegalArgumentException.class,
        () -> catalog.requireReactiveCarbonate(PhreeqcPitzerParameterCatalog.Family.B0, "Xx+", "Yy-"));
  }

  /** Requires a new parameter selection when unsupported component identities replace an automatic topology. */
  @Test
  void unsupportedExtensionDoesNotReuseAnAutomaticDataset() {
    SystemPitzer fluid = reactiveSodiumChloride();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.addComponent("Ca++", 0.01);
    fluid.addComponent("Cl-", 0.02);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    assertThrows(IllegalStateException.class,
        () -> ((PhasePitzer) fluid.getGeLiquidPhase()).getPitzerParameterCoverage());
  }

  /** Does not promote complete automatic parameter coverage to full scientific qualification. */
  @Test
  void fullReactiveQualificationRemainsClosed() {
    SystemPitzer fluid = reactiveSodiumChloride();
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(PitzerParameterQualification.Level.PARTIALLY_EXPERIMENTALLY_VALIDATED,
        fluid.getPitzerParameterQualification().getLevel());
    assertThrows(IllegalStateException.class, () -> fluid
        .requirePitzerDatasetValidationFor(PitzerParameterQualification.ValidationTarget.REACTIVE_SPECIATION));
    assertThrows(IllegalStateException.class, fluid::requireCompletePitzerDatasetQualification);
  }

  /**
   * Retains the independent published conditional-dissociation comparison without promoting failed ranges.
   *
   * @throws Exception when the published correlation fixture cannot be read
   */
  @Test
  void publishedDissociationComparisonRetainsTheQualificationBoundary() throws Exception {
    String resource = "/neqsim/thermo/phase/reactive-carbonate/millero2007-dissociation.csv";
    int states = 0;
    double maximumFirst = 0.0;
    double maximumSecond = 0.0;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(getClass().getResourceAsStream(resource), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("temperature")) {
          continue;
        }
        String[] values = line.split(",");
        double temperature = Double.parseDouble(values[0]);
        double molality = Double.parseDouble(values[1]);
        SystemPitzer fluid = new SystemPitzer(temperature, 1.01325);
        fluid.addComponent("water", 1.0 / 0.01801528);
        fluid.addComponent("CO2", 1.0e-6);
        fluid.addComponent("Na+", molality);
        fluid.addComponent("Cl-", molality);
        fluid.addComponent("H3O+", 1.0e-7);
        fluid.addComponent("HCO3-", 1.0e-7);
        fluid.addComponent("OH-", 1.0e-10);
        fluid.addComponent("CO3--", 1.0e-10);
        fluid.chemicalReactionInit();
        fluid.setMixingRule("classic");
        fluid.init(0);
        fluid.init(1);
        PhasePitzer phase = (PhasePitzer) fluid.getGeLiquidPhase();
        double waterActivity = phase.getPitzerActivity(phase.getComponent("water").getComponentNumber());
        double hydrogenGamma = nativeGamma(phase, "H3O+");
        double bicarbonateGamma = nativeGamma(phase, "HCO3-");
        boolean foundFirst = false;
        boolean foundSecond = false;
        for (neqsim.chemicalreactions.chemicalreaction.ChemicalReaction reaction : fluid.getChemicalReactionOperations()
            .getReactionList().getChemicalReactionList()) {
          java.util.List<String> names = java.util.Arrays.asList(reaction.getNames());
          if (names.contains("CO2")) {
            foundFirst = true;
            double conditional = reaction.getK(phase) * waterActivity * waterActivity * nativeGamma(phase, "CO2")
                / (hydrogenGamma * bicarbonateGamma);
            maximumFirst = Math.max(maximumFirst, Math.abs(-Math.log10(conditional) - Double.parseDouble(values[2])));
          } else if (names.contains("CO3--")) {
            foundSecond = true;
            double conditional = reaction.getK(phase) * waterActivity * bicarbonateGamma
                / (hydrogenGamma * nativeGamma(phase, "CO3--"));
            maximumSecond = Math.max(maximumSecond, Math.abs(-Math.log10(conditional) - Double.parseDouble(values[3])));
          }
        }
        assertTrue(foundFirst && foundSecond, "Both published dissociation steps must be evaluated");
        assertFalse(fluid.getPitzerParameterQualification()
            .isValidatedFor(PitzerParameterQualification.ValidationTarget.REACTIVE_SPECIATION));
        states++;
      }
    }
    assertEquals(12, states);
    // Regression bounds retain the observed discrepancies, not a scientific acceptance claim.
    assertTrue(maximumFirst < 0.20);
    assertTrue(maximumSecond < 0.25);
  }

  /**
   * Reads the model's molality-standard-state coefficient without a fugacity-reference conversion.
   *
   * @param phase Pitzer phase
   * @param name species name
   * @return native Pitzer activity coefficient
   */
  private static double nativeGamma(PhasePitzer phase, String name) {
    return phase.getActivityCoefficient(phase.getComponent(name).getComponentNumber());
  }

  /**
   * Builds the independent-reference gas/NaCl-carbonate control using ordinary automatic setup.
   *
   * @return configured reactive system with no explicit parameter-loading call
   */
  private static SystemPitzer reactiveSodiumChloride() {
    SystemPitzer fluid = new SystemPitzer(293.15, 7.0);
    addReactiveFeed(fluid);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Adds the common 1 kg water, 1 mol CO2 and 1 mol NaCl feed.
   *
   * @param fluid destination system
   */
  private static void addReactiveFeed(SystemPitzer fluid) {
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
  }

}
