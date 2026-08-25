package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;

/** Scientific and provenance regressions for versioned Pitzer parameter datasets. */
class PitzerParameterDatasetsTest extends neqsim.NeqSimTest {
  private static final double REFERENCE_TEMPERATURE = 298.15;

  @Test
  void exactPhreeqcRowsAndCoverageAreAppliedAsOneDataset() {
    PhasePitzer phase = createPhase(REFERENCE_TEMPERATURE);
    assertFalse(phase.isPhreeqcCommonIonTermsActive());
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);

    int carbonDioxide = index(phase, "CO2");
    int sodium = index(phase, "Na+");
    int sulfate = index(phase, "SO4--");
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_NA2SO4_ID, phase.getParameterDatasetId());
    assertTrue(phase.isPhreeqcCommonIonTermsActive());
    assertEquals(0.0273, phase.getBeta0ij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.956, phase.getBeta1ij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.003418, phase.getCphiij(sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.0134, phase.getLambda(carbonDioxide, carbonDioxide, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.085, phase.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.075, phase.getLambda(carbonDioxide, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.015, phase.getZeta(carbonDioxide, sodium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertTrue(phase.getPitzerParameterCoverage().isComplete());
    assertTrue(phase.auditNeutralPitzerParameterCoverage().isComplete());
  }

  @Test
  void co2ActivityMatchesIndependentIphreeqc373ReferenceStates() {
    // Generated with IPhreeqc 3.7.3, exact public-domain pitzer.dat blob
    // 324f852784be84650b77bd7f07f8316aafd8188b, MacInnes scaling disabled.
    assertPhreeqcCo2LogGamma(298.15, 0.009335381788874181, 0.2, 0.09997223611354554, 0.048445563893430764);
    assertPhreeqcCo2LogGamma(298.15, 0.08226801278545755, 2.0, 0.9999294802590848, 0.457786331769995);
    assertPhreeqcCo2LogGamma(373.15, 0.08136555608701694, 2.0, 0.9995278707441787, 0.44890462098965683);
    assertPhreeqcCo2LogGamma(423.15, 0.14946601518796535, 4.0, 1.9980632335950677, 0.836784371391815);
  }

  @Test
  void sodiumSulfateActivityAndOsmoticCoefficientMatchIphreeqcAt298K() {
    PhasePitzer phase = createPhase(REFERENCE_TEMPERATURE);
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);
    setMolality(phase, "CO2", 0.0);
    setMolality(phase, "Na+", 2.0);
    setMolality(phase, "SO4--", 0.9999992982438045);

    int sodium = index(phase, "Na+");
    int sulfate = index(phase, "SO4--");
    ComponentGePitzer sodiumComponent = (ComponentGePitzer) phase.getComponent(sodium);
    ComponentGePitzer sulfateComponent = (ComponentGePitzer) phase.getComponent(sulfate);
    double gammaSodium = sodiumComponent.getGamma(phase, phase.getNumberOfComponents(), REFERENCE_TEMPERATURE,
        phase.getPressure(), phase.getType());
    double gammaSulfate = sulfateComponent.getGamma(phase, phase.getNumberOfComponents(), REFERENCE_TEMPERATURE,
        phase.getPressure(), phase.getType());
    double meanGamma = Math.pow(gammaSodium * gammaSodium * gammaSulfate, 1.0 / 3.0);

    assertEquals(0.20137297119267375, meanGamma, 1.3e-3);
    assertEquals(0.6422470176005581, phase.getOsmoticCoefficientOfWater(), 7.0e-4);
  }

  @Test
  void carbonDioxideShowsHeldOutSodiumSulfateSaltingOutTrend() {
    // Dos Santos et al. (2020), DOI 10.1021/acs.jced.0c00230, independently reports
    // lower CO2 solubility as Na2SO4 molality increases. At fixed aqueous CO2 molality,
    // this parameter subset must therefore increase the CO2 activity coefficient.
    double oneMolalLogGamma = carbonDioxideLogGamma(373.15, 0.08, 2.0, 1.0);
    double twoMolalLogGamma = carbonDioxideLogGamma(373.15, 0.08, 4.0, 2.0);

    assertTrue(twoMolalLogGamma > oneMolalLogGamma, "CO2 activity coefficient must increase from 1 to 2 molal Na2SO4: "
        + oneMolalLogGamma + " -> " + twoMolalLogGamma);
  }

  @Test
  void validationEnvelopeAndUnsupportedTopologyAreExplicit() {
    assertTrue(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(303.15, 1.0));
    assertTrue(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(423.15, 2.0));
    assertFalse(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(298.15, 1.0));
    assertFalse(PitzerParameterDatasets.isWithinCo2SodiumSulfateValidationRange(373.15, 2.1));

    SystemInterface incomplete = new SystemPitzer(REFERENCE_TEMPERATURE, 1.01325);
    incomplete.addComponent("water", 55.508);
    incomplete.addComponent("CO2", 0.1);
    incomplete.addComponent("Na+", 2.0);
    incomplete.init(0);
    PhasePitzer phase = (PhasePitzer) incomplete.getPhase(1);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase));
    assertTrue(error.getMessage().contains("SO4--"));
  }

  @Test
  void systemApiRetainsQualifiedDatasetAcrossInitializationAndClone() {
    SystemPitzer system = createSystem(REFERENCE_TEMPERATURE);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    system.init(0);
    PhasePitzer original = (PhasePitzer) system.getPhase(1);
    int carbonDioxide = index(original, "CO2");
    int sodium = index(original, "Na+");
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_NA2SO4_ID, original.getParameterDatasetId());

    SystemPitzer clonedSystem = system.clone();
    PhasePitzer cloned = (PhasePitzer) clonedSystem.getPhase(1);
    cloned.setLambda(carbonDioxide, sodium, 0.25);
    assertNotEquals(original.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE),
        cloned.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE));
    assertEquals(0.085, original.getLambda(carbonDioxide, sodium, REFERENCE_TEMPERATURE), 0.0);
  }

  @Test
  void qualifiedSubsetFailsClosedForAnAdditionalActiveNeutral() {
    SystemPitzer system = createSystem(REFERENCE_TEMPERATURE);
    system.addComponent("methane", 0.1);
    system.init(0);
    system.applyPhreeqcCo2SodiumSulfateParameters();
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);

    PitzerNeutralParameterCoverage coverage = phase.auditNeutralPitzerParameterCoverage();
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getMissingLambdaPairs().toString().contains("methane"));
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> phase.getNeutralPitzerLogGammaContribution(index(phase, "CO2"), REFERENCE_TEMPERATURE));
    assertTrue(error.getMessage().contains("methane"));
  }

  @Test
  void exactPhreeqcNaKClFamilyIsAppliedWithoutLegacyMixing() {
    PhasePitzer phase = createNaKClPhase(REFERENCE_TEMPERATURE, 0.5, 0.5, 1.0);
    assertFalse(phase.isPhreeqcCommonIonTermsActive());
    PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride(phase);

    int sodium = index(phase, "Na+");
    int potassium = index(phase, "K+");
    int chloride = index(phase, "Cl-");
    assertEquals(PitzerParameterDatasets.PHREEQC_NA_K_CL_ID, phase.getParameterDatasetId());
    assertTrue(phase.isPhreeqcCommonIonTermsActive());
    assertEquals(0.07534, phase.getBeta0ij(sodium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.2769, phase.getBeta1ij(sodium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.00148, phase.getCphiij(sodium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.04808, phase.getBeta0ij(potassium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.2168, phase.getBeta1ij(potassium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.000788, phase.getCphiij(potassium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.012, phase.getThetaij(potassium, sodium, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.0015, phase.getPsiijk(chloride, potassium, sodium, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.00075, phase.getPsiijk(chloride, potassium, sodium, 423.15), 1.0e-16);
    assertTrue(phase.getPitzerParameterCoverage().isComplete());
  }

  @Test
  void naKClActivitiesAndWaterMatchIndependentIphreeqcStates() {
    // IPhreeqc 3.7.3, MacInnes scaling disabled. The selected Na/K/Cl rows were
    // independently checked against public-domain pitzer.dat blob 324f852784be8465.
    assertNaKClIphreeqc(298.15, 0.05, 0.05, 0.1, -0.109558383571162, -0.115399611824963, -0.111956216732935,
        0.996657457563853, 0.929248316594131);
    assertNaKClIphreeqc(298.15, 0.5, 0.5, 1.0, -0.185963066379783, -0.226129675290685, -0.200671984110192,
        0.967599049696263, 0.914152855746013);
    assertNaKClIphreeqc(298.15, 0.2, 0.8, 1.0, -0.188167301944823, -0.221689205692125, -0.211545219840229,
        0.967950656246827, 0.904069363351145);
    assertNaKClIphreeqc(298.15, 1.5, 1.5, 3.0, -0.162636431144403, -0.264988407898330, -0.196712087625404,
        0.899604248327128, 0.978800685940389);
    assertNaKClIphreeqc(373.15, 0.5, 0.5, 1.0, -0.210812206972725, -0.244391407261678, -0.222376193262467,
        0.967662299278456, 0.912334399235720);
    assertNaKClIphreeqc(423.15, 0.5, 0.5, 1.0, -0.257376190299029, -0.284989821795124, -0.266063564510975,
        0.968513818532068, 0.887907342187288);
  }

  @Test
  void naKClBinaryBoundariesMatchIndependentIphreeqcStates() {
    assertNaKClBinaryIphreeqc("Na+", 1.0, 0.0, -0.182289344126129, 0.966825193700153, 0.936358675023342);
    assertNaKClBinaryIphreeqc("K+", 0.0, 1.0, -0.218728896047286, 0.968138038944173, 0.898697034760478);
  }

  @Test
  void sodiumChlorideMatchesIndependentTraceableExperimentSynthesis() {
    // Partanen and Partanen, J. Chem. Eng. Data 65 (2020) 5226-5239,
    // DOI 10.1021/acs.jced.0c00402, Tables 6 and 10 (CC BY 4.0). Their
    // extended-Huckel recommendations synthesize vapor-pressure,
    // electrochemical, cryoscopic, and solubility evidence independently of
    // the PHREEQC implementation used for the equation-convention regressions.
    double[][] reference = { { 0.2, 0.7344, 0.9242 }, { 0.5, 0.684, 0.924 }, { 1.0, 0.662, 0.940 },
        { 2.0, 0.677, 0.989 } };
    for (double[] state : reference) {
      double molality = state[0];
      PhasePitzer phase = createNaKClPhase(REFERENCE_TEMPERATURE, molality, 0.0, molality);
      PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride(phase);
      double actualMeanGamma = Math.exp(0.5 * (componentLogGamma(phase, "Na+") + componentLogGamma(phase, "Cl-")));
      double relativeGammaResidual = Math.abs(actualMeanGamma / state[1] - 1.0);
      assertTrue(relativeGammaResidual <= 0.015,
          "NaCl mean activity residual at " + molality + " mol/kg: " + relativeGammaResidual);
      assertEquals(state[2], phase.getOsmoticCoefficientOfWater(), 0.006);
      double referenceWaterActivity = Math.exp(-2.0 * molality * 18.015 * state[2] / 1000.0);
      assertEquals(referenceWaterActivity, waterActivity(phase), 5.0e-4);
    }
  }

  @Test
  void potassiumChlorideMatchesIndependentNistEvaluation() {
    // Hamer and Wu, J. Phys. Chem. Ref. Data 1 (1972) 1047-1100,
    // DOI 10.1063/1.3253108. These NBS/NIST critically evaluated KCl values
    // are reproduced in Table 2 of Dash et al. (2012), DOI
    // 10.5402/2012/730154 (CC BY), independently of PHREEQC. Values are on
    // the molality scale at 298.15 K and rounded to 0.001.
    double[][] reference = { { 0.0982, 0.768 }, { 0.5001, 0.649 }, { 0.9926, 0.604 }, { 1.9895, 0.573 } };
    for (double[] state : reference) {
      double molality = state[0];
      PhasePitzer phase = createNaKClPhase(REFERENCE_TEMPERATURE, 0.0, molality, molality);
      PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride(phase);
      double actualMeanGamma = Math.exp(0.5 * (componentLogGamma(phase, "K+") + componentLogGamma(phase, "Cl-")));
      double relativeGammaResidual = Math.abs(actualMeanGamma / state[1] - 1.0);
      assertTrue(relativeGammaResidual <= 0.002,
          "KCl mean activity residual at " + molality + " mol/kg: " + relativeGammaResidual);
    }
  }

  @Test
  void naKClValidationRangeChargeAndExtraSpeciesFailClosed() {
    assertTrue(PitzerParameterDatasets.isWithinSodiumPotassiumChlorideValidationRange(298.15, 0.05, 0.05, 0.1));
    assertTrue(PitzerParameterDatasets.isWithinSodiumPotassiumChlorideValidationRange(423.15, 1.5, 1.5, 3.0));
    assertFalse(PitzerParameterDatasets.isWithinSodiumPotassiumChlorideValidationRange(298.15, 0.5, 0.4, 1.0));
    assertFalse(PitzerParameterDatasets.isWithinSodiumPotassiumChlorideValidationRange(423.16, 0.5, 0.5, 1.0));

    SystemPitzer incomplete = createNaKClSystem(REFERENCE_TEMPERATURE, 0.5, 0.5, 1.0);
    incomplete.addComponent("Mg++", 0.1);
    incomplete.addComponent("Cl-", 0.2);
    incomplete.init(0);
    incomplete.applyPhreeqcSodiumPotassiumChlorideParameters();
    PhasePitzer phase = (PhasePitzer) incomplete.getPhase(1);
    PitzerParameterCoverage coverage = phase.getPitzerParameterCoverage();
    assertFalse(coverage.isComplete());
    assertTrue(coverage.getMissingBinaryPairs().contains("Cl-|Mg++"));
    assertThrows(IllegalStateException.class, phase::requireCompletePitzerParameterCoverage);
  }

  @Test
  void naKClDatasetSurvivesChangedStateCloneSerializationAndParallelConstruction() throws Exception {
    SystemPitzer system = createNaKClSystem(REFERENCE_TEMPERATURE, 0.5, 0.5, 1.0);
    system.applyPhreeqcSodiumPotassiumChlorideParameters();
    double originalChecksum = naKClChecksum((PhasePitzer) system.getPhase(1));

    SystemPitzer clone = system.clone();
    PhasePitzer clonedPhase = (PhasePitzer) clone.getPhase(1);
    int sodium = index(clonedPhase, "Na+");
    int potassium = index(clonedPhase, "K+");
    clonedPhase.setTheta(sodium, potassium, 0.25);
    assertEquals(-0.012, ((PhasePitzer) system.getPhase(1)).getThetaij(sodium, potassium), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    SystemPitzer restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemPitzer) input.readObject();
    }
    assertEquals(PitzerParameterDatasets.PHREEQC_NA_K_CL_ID,
        ((PhasePitzer) restored.getPhase(1)).getParameterDatasetId());
    assertEquals(originalChecksum, naKClChecksum((PhasePitzer) restored.getPhase(1)), 1.0e-12);

    system.addComponent("Na+", -0.3);
    system.addComponent("K+", 0.3);
    system.init(0);
    SystemPitzer freshChangedState = createNaKClSystem(REFERENCE_TEMPERATURE, 0.2, 0.8, 1.0);
    freshChangedState.applyPhreeqcSodiumPotassiumChlorideParameters();
    assertEquals(naKClChecksum((PhasePitzer) freshChangedState.getPhase(1)),
        naKClChecksum((PhasePitzer) system.getPhase(1)), 1.0e-12);

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
      for (int task = 0; task < 8; task++) {
        tasks.add(() -> {
          SystemPitzer independent = createNaKClSystem(REFERENCE_TEMPERATURE, 0.5, 0.5, 1.0);
          independent.applyPhreeqcSodiumPotassiumChlorideParameters();
          return naKClChecksum((PhasePitzer) independent.getPhase(1));
        });
      }
      for (Future<Double> result : executor.invokeAll(tasks)) {
        assertEquals(originalChecksum, result.get(), 1.0e-12);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void naKClCompositionSweepIsContinuousAndRepeatable() {
    double previousWaterActivity = Double.NaN;
    for (int point = 0; point <= 20; point++) {
      double potassium = point / 20.0;
      double sodium = 1.0 - potassium;
      SystemPitzer system = createNaKClSystem(REFERENCE_TEMPERATURE, sodium, potassium, 1.0);
      system.applyPhreeqcSodiumPotassiumChlorideParameters();
      PhasePitzer phase = (PhasePitzer) system.getPhase(1);
      double currentWaterActivity = waterActivity(phase);
      assertTrue(Double.isFinite(currentWaterActivity) && currentWaterActivity > 0.0 && currentWaterActivity <= 1.0);
      assertEquals(naKClChecksum(phase), naKClChecksum(phase), 0.0);
      if (Double.isFinite(previousWaterActivity)) {
        assertTrue(Math.abs(currentWaterActivity - previousWaterActivity) < 2.0e-4);
      }
      previousWaterActivity = currentWaterActivity;
    }
  }

  @Test
  void bundledCatalogHasBroadFamiliesAndExactScaleRows() {
    PhreeqcPitzerParameterCatalog catalog = PhreeqcPitzerParameterCatalog.getInstance();
    assertTrue(catalog.size(PhreeqcPitzerParameterCatalog.Family.B0) > 50);
    assertTrue(catalog.size(PhreeqcPitzerParameterCatalog.Family.B1) > 40);
    assertTrue(catalog.size(PhreeqcPitzerParameterCatalog.Family.THETA) > 20);
    assertTrue(catalog.size(PhreeqcPitzerParameterCatalog.Family.PSI) > 40);
    assertEquals(0.3159, catalog.require(PhreeqcPitzerParameterCatalog.Family.B0, "Ca++", "Cl-")[0], 0.0);
    assertEquals(-1.13, catalog.require(PhreeqcPitzerParameterCatalog.Family.B2, "Ca++", "Cl-")[0], 0.0);
    assertEquals(-0.122, catalog.require(PhreeqcPitzerParameterCatalog.Family.PSI, "Ca++", "Cl-", "SO4--")[0], 0.0);
  }

  @Test
  void completeCalciumMagnesiumChlorideSulfateFamilyIsApplied() {
    SystemPitzer system = createCalciumMagnesiumChlorideSulfateSystem(REFERENCE_TEMPERATURE, 0.2, 0.3, 0.4, 0.3);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    int calcium = index(phase, "Ca++");
    int magnesium = index(phase, "Mg++");
    int chloride = index(phase, "Cl-");
    int sulfate = index(phase, "SO4--");

    assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, phase.getParameterDatasetId());
    assertEquals(0.3159, phase.getBeta0ij(calcium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-1.13, phase.getBeta2ij(calcium, chloride, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(12.0, phase.getPitzerAlpha2(calcium, chloride), 0.0);
    assertEquals(1.4, phase.getPitzerAlpha1(calcium, sulfate), 0.0);
    assertEquals(0.007, phase.getThetaij(calcium, magnesium, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(0.03, phase.getThetaij(chloride, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.122, phase.getPsiijk(calcium, chloride, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertEquals(-0.008, phase.getPsiijk(chloride, magnesium, sulfate, REFERENCE_TEMPERATURE), 0.0);
    assertTrue(phase.getPitzerParameterCoverage().isComplete());

    double chargeResidual = 0.0;
    double compositionSum = 0.0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      double moleFraction = phase.getComponent(component).getx();
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0);
      compositionSum += moleFraction;
      chargeResidual += phase.getComponent(component).getMolality(phase)
          * phase.getComponent(component).getIonicCharge();
    }
    assertEquals(1.0, compositionSum, 1.0e-12);
    assertEquals(0.0, chargeResidual, 1.0e-12);
    assertTrue(Double.isFinite(componentLogGamma(phase, "Ca++")));
    assertTrue(Double.isFinite(componentLogGamma(phase, "Mg++")));
    assertTrue(Double.isFinite(componentLogGamma(phase, "Cl-")));
    assertTrue(Double.isFinite(componentLogGamma(phase, "SO4--")));
    assertTrue(waterActivity(phase) > 0.0 && waterActivity(phase) <= 1.0);

    system.init(3);
    system.initPhysicalProperties();
    assertTrue(system.getPhase(0) instanceof PhaseSrkEos);
    assertTrue(system.getPhase(1) instanceof PhasePitzer);
    assertTrue(system.getPhase(2) instanceof PhaseSrkEos);
    assertTrue(Double.isFinite(system.getPhase(1).getDensity()) && system.getPhase(1).getDensity() > 0.0);
    assertTrue(Double.isFinite(system.getPhase(1).getEnthalpy()));
    assertTrue(Double.isFinite(system.getPhase(1).getCp()) && system.getPhase(1).getCp() > 0.0);
  }

  @Test
  void calciumChlorideMatchesIndependentNistThermoMlValues() {
    // Partanen, J. Chem. Eng. Data 58 (2013) 132-144, DOI 10.1021/je300852v.
    // NIST ThermoML archive values are molality-scale mean ionic activity
    // coefficients at 298.15 K and 101 kPa, uncertainty 0.001. No parameter was
    // fitted to these held-out values.
    double[][] reference = { { 0.1, 0.517 }, { 0.5, 0.449 }, { 1.0, 0.502 }, { 2.0, 0.790 } };
    for (double[] state : reference) {
      double molality = state[0];
      PhasePitzer phase = createCalciumMagnesiumChlorideSulfatePhase(REFERENCE_TEMPERATURE, molality, 0.0,
          2.0 * molality, 0.0);
      PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog(phase);
      double meanLogGamma = (componentLogGamma(phase, "Ca++") + 2.0 * componentLogGamma(phase, "Cl-")) / 3.0;
      double relativeResidual = Math.abs(Math.exp(meanLogGamma) / state[1] - 1.0);
      assertTrue(relativeResidual <= 0.012,
          "CaCl2 mean activity residual at " + molality + " mol/kg: " + relativeResidual);
    }
  }

  @Test
  void magnesiumChlorideMatchesIndependentTraceableValues() {
    // Partanen, J. Chem. Thermodynamics 66 (2013) 65-70,
    // DOI 10.1016/j.jct.2013.06.016. The publisher-permitted NIST ThermoML
    // archive records the paper's molality-scale quantities at 298.15 K and
    // 101.325 kPa. No PHREEQC parameter was fitted to these held-out values.
    double[][] reference = { { 0.1667, 0.5018 }, { 0.3333, 0.4796 }, { 1.0, 0.5776 }, { 2.0, 1.0695 } };
    for (double[] state : reference) {
      double molality = state[0];
      PhasePitzer phase = createCalciumMagnesiumChlorideSulfatePhase(REFERENCE_TEMPERATURE, 0.0, molality,
          2.0 * molality, 0.0);
      PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog(phase);
      double meanLogGamma = (componentLogGamma(phase, "Mg++") + 2.0 * componentLogGamma(phase, "Cl-")) / 3.0;
      double relativeResidual = Math.abs(Math.exp(meanLogGamma) / state[1] - 1.0);
      assertTrue(relativeResidual <= 0.025,
          "MgCl2 mean activity residual at " + molality + " mol/kg: " + relativeResidual);
    }
  }

  @Test
  void qualificationSeparatesCoverageFromIndependentEvidence() {
    PitzerParameterQualification scaleFamily = PitzerParameterDatasets
        .getQualification(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID);
    assertEquals(PitzerParameterQualification.Level.PARTIALLY_EXPERIMENTALLY_VALIDATED, scaleFamily.getLevel());
    assertFalse(scaleFamily.isValidatedWithinDeclaredEnvelope());
    assertTrue(scaleFamily.getValidatedSystems().toString().contains("MgCl2"));
    assertTrue(scaleFamily.getLimitations().toString().contains("Mixed Ca-Mg-Cl-SO4"));
    assertThrows(UnsupportedOperationException.class, () -> scaleFamily.getLimitations().add("unsafe"));

    PitzerParameterQualification unknown = PitzerParameterDatasets.getQualification("private-project-dataset");
    assertEquals(PitzerParameterQualification.Level.UNQUALIFIED, unknown.getLevel());
    assertFalse(unknown.isValidatedWithinDeclaredEnvelope());
    assertTrue(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideBinaryValidationRange(298.15, 0.1));
    assertTrue(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideBinaryValidationRange(298.15, 2.0));
    assertFalse(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideBinaryValidationRange(298.15, 2.1));
    assertFalse(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideBinaryValidationRange(323.15, 1.0));
  }

  @Test
  void catalogFailsBeforeMutationForMissingCarbonateFamily() {
    SystemPitzer system = createCalciumMagnesiumChlorideSulfateSystem(REFERENCE_TEMPERATURE, 0.2, 0.3, 0.6, 0.2);
    system.addComponent("CO3--", 0.1);
    system.init(0);
    PhasePitzer phase = (PhasePitzer) system.getPhase(1);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> PitzerParameterDatasets.applyCompletePhreeqcPitzerCatalog(phase));
    assertTrue(error.getMessage().contains("no explicit"));
    assertNotEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, phase.getParameterDatasetId());
  }

  @Test
  void changedIonTopologyCannotReuseStaleCatalogCoverage() {
    SystemPitzer system = createCalciumMagnesiumChlorideSulfateSystem(REFERENCE_TEMPERATURE, 0.2, 0.3, 0.4, 0.3);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    system.addComponent("Ba++", 0.05);
    system.addComponent("Cl-", 0.10);
    system.init(0);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> ((PhasePitzer) system.getPhase(1)).requireCompletePitzerParameterCoverage());
    assertTrue(error.getMessage().contains("Ba++"));
  }

  @Test
  void scaleFamilySurvivesCloneSerializationAndParallelConstruction() throws Exception {
    SystemPitzer system = createCalciumMagnesiumChlorideSulfateSystem(REFERENCE_TEMPERATURE, 0.2, 0.3, 0.4, 0.3);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    double checksum = scaleFamilyChecksum((PhasePitzer) system.getPhase(1));
    assertEquals(checksum, scaleFamilyChecksum((PhasePitzer) system.clone().getPhase(1)), 0.0);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(system);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      SystemPitzer restored = (SystemPitzer) input.readObject();
      assertEquals(checksum, scaleFamilyChecksum((PhasePitzer) restored.getPhase(1)), 1.0e-12);
    }

    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Double>> tasks = new ArrayList<Callable<Double>>();
      for (int task = 0; task < 8; task++) {
        tasks.add(() -> {
          SystemPitzer independent = createCalciumMagnesiumChlorideSulfateSystem(REFERENCE_TEMPERATURE, 0.2, 0.3, 0.4,
              0.3);
          independent.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
          return scaleFamilyChecksum((PhasePitzer) independent.getPhase(1));
        });
      }
      for (Future<Double> result : executor.invokeAll(tasks)) {
        assertEquals(checksum, result.get(), 1.0e-12);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void assertNaKClBinaryIphreeqc(String activeCation, double sodiumMolality, double potassiumMolality,
      double expectedMeanLog10Gamma, double expectedWaterActivity, double expectedOsmoticCoefficient) {
    PhasePitzer phase = createNaKClPhase(REFERENCE_TEMPERATURE, sodiumMolality, potassiumMolality, 1.0);
    PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride(phase);
    phase.requireCompletePitzerParameterCoverage();
    double meanNaturalLogGamma = 0.5 * (componentLogGamma(phase, activeCation) + componentLogGamma(phase, "Cl-"));
    assertEquals(expectedMeanLog10Gamma * Math.log(10.0), meanNaturalLogGamma, 0.002);
    assertEquals(expectedWaterActivity, waterActivity(phase), 7.0e-4);
    assertEquals(expectedOsmoticCoefficient, phase.getOsmoticCoefficientOfWater(), 7.0e-3);
  }

  private static void assertNaKClIphreeqc(double temperature, double sodiumMolality, double potassiumMolality,
      double chlorideMolality, double expectedLog10GammaNa, double expectedLog10GammaK, double expectedLog10GammaCl,
      double expectedWaterActivity, double expectedOsmoticCoefficient) {
    PhasePitzer phase = createNaKClPhase(temperature, sodiumMolality, potassiumMolality, chlorideMolality);
    PitzerParameterDatasets.applyPhreeqcSodiumPotassiumChloride(phase);
    phase.requireCompletePitzerParameterCoverage();

    double naturalLog10 = Math.log(10.0);
    double maximumLogGammaResidual = temperature <= 298.15 ? 0.002 : (temperature <= 373.15 ? 0.008 : 0.019);
    assertEquals(expectedLog10GammaNa * naturalLog10, componentLogGamma(phase, "Na+"), maximumLogGammaResidual);
    assertEquals(expectedLog10GammaK * naturalLog10, componentLogGamma(phase, "K+"), maximumLogGammaResidual);
    assertEquals(expectedLog10GammaCl * naturalLog10, componentLogGamma(phase, "Cl-"), maximumLogGammaResidual);
    assertEquals(expectedWaterActivity, waterActivity(phase), 7.0e-4);
    assertEquals(expectedOsmoticCoefficient, phase.getOsmoticCoefficientOfWater(), 7.0e-3);

    double chargeResidual = 0.0;
    double compositionSum = 0.0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      double moleFraction = phase.getComponent(component).getx();
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0);
      compositionSum += moleFraction;
      chargeResidual += phase.getComponent(component).getMolality(phase)
          * phase.getComponent(component).getIonicCharge();
    }
    assertEquals(1.0, compositionSum, 1.0e-12);
    assertEquals(0.0, chargeResidual, 1.0e-12);

    double actualIonMolalitySum = phase.getComponent("Na+").getMolality(phase)
        + phase.getComponent("K+").getMolality(phase) + phase.getComponent("Cl-").getMolality(phase);
    double calculatedFromWaterActivity = -1000.0 * Math.log(waterActivity(phase)) / (18.015 * actualIonMolalitySum);
    assertEquals(calculatedFromWaterActivity, phase.getOsmoticCoefficientOfWater(), 2.0e-10);
  }

  private static double componentLogGamma(PhasePitzer phase, String componentName) {
    int component = index(phase, componentName);
    ComponentGePitzer pitzerComponent = (ComponentGePitzer) phase.getComponent(component);
    return Math.log(pitzerComponent.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure(), phase.getType()));
  }

  private static double waterActivity(PhasePitzer phase) {
    int water = index(phase, "water");
    ComponentGePitzer waterComponent = (ComponentGePitzer) phase.getComponent(water);
    double waterGamma = waterComponent.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure(), phase.getType());
    return waterGamma * phase.getComponent(water).getx();
  }

  private static double naKClChecksum(PhasePitzer phase) {
    return componentLogGamma(phase, "Na+") + componentLogGamma(phase, "K+") + componentLogGamma(phase, "Cl-")
        + waterActivity(phase) + phase.getOsmoticCoefficientOfWater();
  }

  private static double scaleFamilyChecksum(PhasePitzer phase) {
    return componentLogGamma(phase, "Ca++") + componentLogGamma(phase, "Mg++") + componentLogGamma(phase, "Cl-")
        + componentLogGamma(phase, "SO4--") + waterActivity(phase) + phase.getOsmoticCoefficientOfWater();
  }

  private static void assertPhreeqcCo2LogGamma(double temperature, double carbonDioxideMolality, double sodiumMolality,
      double sulfateMolality, double expectedNaturalLogGamma) {
    assertEquals(expectedNaturalLogGamma,
        carbonDioxideLogGamma(temperature, carbonDioxideMolality, sodiumMolality, sulfateMolality), 1.5e-5);
  }

  private static double carbonDioxideLogGamma(double temperature, double carbonDioxideMolality, double sodiumMolality,
      double sulfateMolality) {
    PhasePitzer phase = createPhase(temperature);
    PitzerParameterDatasets.applyPhreeqcCo2SodiumSulfate(phase);
    setMolality(phase, "CO2", carbonDioxideMolality);
    setMolality(phase, "Na+", sodiumMolality);
    setMolality(phase, "SO4--", sulfateMolality);
    int carbonDioxide = index(phase, "CO2");
    return phase.getNeutralPitzerLogGammaContribution(carbonDioxide, temperature);
  }

  private static PhasePitzer createPhase(double temperature) {
    return (PhasePitzer) createSystem(temperature).getPhase(1);
  }

  private static SystemPitzer createSystem(double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("CO2", 0.1);
    system.addComponent("Na+", 2.0);
    system.addComponent("SO4--", 1.0);
    system.init(0);
    return system;
  }

  private static PhasePitzer createNaKClPhase(double temperature, double sodiumMolality, double potassiumMolality,
      double chlorideMolality) {
    return (PhasePitzer) createNaKClSystem(temperature, sodiumMolality, potassiumMolality, chlorideMolality)
        .getPhase(1);
  }

  private static SystemPitzer createNaKClSystem(double temperature, double sodiumMolality, double potassiumMolality,
      double chlorideMolality) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", sodiumMolality);
    system.addComponent("K+", potassiumMolality);
    system.addComponent("Cl-", chlorideMolality);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  private static PhasePitzer createCalciumMagnesiumChlorideSulfatePhase(double temperature, double calciumMolality,
      double magnesiumMolality, double chlorideMolality, double sulfateMolality) {
    return (PhasePitzer) createCalciumMagnesiumChlorideSulfateSystem(temperature, calciumMolality, magnesiumMolality,
        chlorideMolality, sulfateMolality).getPhase(1);
  }

  private static SystemPitzer createCalciumMagnesiumChlorideSulfateSystem(double temperature, double calciumMolality,
      double magnesiumMolality, double chlorideMolality, double sulfateMolality) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Ca++", calciumMolality);
    system.addComponent("Mg++", magnesiumMolality);
    system.addComponent("Cl-", chlorideMolality);
    system.addComponent("SO4--", sulfateMolality);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  private static void setMolality(PhasePitzer phase, String componentName, double molality) {
    double targetMoles = molality * phase.getSolventWeight();
    double moleFraction = phase.getComponent(componentName).getx();
    phase.getComponent(componentName).setNumberOfMolesInPhase(targetMoles == 0.0 ? 0.0 : targetMoles / moleFraction);
  }

  private static int index(PhasePitzer phase, String componentName) {
    return phase.getComponent(componentName).getComponentNumber();
  }
}
