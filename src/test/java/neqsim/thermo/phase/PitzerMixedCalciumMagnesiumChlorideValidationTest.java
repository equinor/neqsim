package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemPitzer;

/** Held-out mixed CaCl2-MgCl2 validation for the public-domain PHREEQC Pitzer catalog. */
class PitzerMixedCalciumMagnesiumChlorideValidationTest extends neqsim.NeqSimTest {
  private static final String REFERENCE_RESOURCE =
      "/data/chemistry_benchmarks/pitzer_cacl2_mgcl2_osmotic_298k.csv";

  @Test
  void mixedChlorideOsmoticPropertiesMatchIndependentNbsEvidence() throws IOException {
    List<ReferenceState> states = readReferenceStates();
    assertEquals(9, states.size());

    double maximumOsmoticResidual = 0.0;
    double maximumWaterActivityResidual = 0.0;
    for (ReferenceState state : states) {
      assertSourcePreprocessing(state);
      SystemPitzer system = createSystem(state.calciumChlorideMolality, state.magnesiumChlorideMolality);
      PhasePitzer phase = (PhasePitzer) system.getPhase(1);
      phase.requireCompletePitzerParameterCoverage();

      assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, phase.getParameterDatasetId());
      assertTrue(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideMixtureValidationRange(298.15,
          state.calciumChlorideMolality, state.magnesiumChlorideMolality));
      assertMaterialAndPhaseState(phase);

      double calculatedOsmoticCoefficient = phase.getOsmoticCoefficientOfWater();
      double calculatedWaterActivity = waterActivity(phase);
      assertEquals(calculatedOsmoticCoefficient, phase.getOsmoticCoefficientOfWater(), 0.0,
          "Repeated evaluation changed the osmotic coefficient");
      assertEquals(calculatedWaterActivity, waterActivity(phase), 0.0,
          "Repeated evaluation changed water activity");
      assertTrue(Double.isFinite(calculatedOsmoticCoefficient) && calculatedOsmoticCoefficient > 0.0);
      assertTrue(Double.isFinite(calculatedWaterActivity) && calculatedWaterActivity > 0.0
          && calculatedWaterActivity <= 1.0);
      assertWaterActivityOsmoticConsistency(phase, calculatedWaterActivity);

      maximumOsmoticResidual =
          Math.max(maximumOsmoticResidual, Math.abs(calculatedOsmoticCoefficient - state.osmoticCoefficient));
      maximumWaterActivityResidual =
          Math.max(maximumWaterActivityResidual, Math.abs(calculatedWaterActivity - state.waterActivity));
    }

    assertTrue(maximumOsmoticResidual <= 0.04,
        "Maximum absolute osmotic-coefficient residual: " + maximumOsmoticResidual);
    assertTrue(maximumWaterActivityResidual <= 0.004,
        "Maximum absolute water-activity residual: " + maximumWaterActivityResidual);
  }

  @Test
  void rangeAndChangedStateDiagnosticsFailClosedAndRemainFresh() {
    assertFalse(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideMixtureValidationRange(298.15, -0.1, 1.0));
    assertFalse(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideMixtureValidationRange(298.15, 0.05, 2.0));
    assertFalse(PitzerParameterDatasets.isWithinCalciumMagnesiumChlorideMixtureValidationRange(308.15, 0.5, 0.5));

    SystemPitzer changed = createSystem(0.25, 0.75);
    double initialActivity = waterActivity((PhasePitzer) changed.getPhase(1));
    changed.addComponent("Ca++", 0.25);
    changed.addComponent("Mg++", 0.25);
    changed.addComponent("Cl-", 1.0);
    changed.init(0);

    PhasePitzer changedPhase = (PhasePitzer) changed.getPhase(1);
    changedPhase.requireCompletePitzerParameterCoverage();
    double changedActivity = waterActivity(changedPhase);
    double freshActivity = waterActivity((PhasePitzer) createSystem(0.50, 1.00).getPhase(1));
    assertTrue(changedActivity < initialActivity);
    assertEquals(freshActivity, changedActivity, 1.0e-12);
  }

  private static SystemPitzer createSystem(double calciumChlorideMolality, double magnesiumChlorideMolality) {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Ca++", calciumChlorideMolality);
    system.addComponent("Mg++", magnesiumChlorideMolality);
    system.addComponent("Cl-", 2.0 * (calciumChlorideMolality + magnesiumChlorideMolality));
    system.addComponent("SO4--", 0.0);
    system.setMixingRule("classic");
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    return system;
  }

  private static void assertSourcePreprocessing(ReferenceState state) {
    double ratio = 1.0 - state.a * state.calciumFraction
        - state.b * state.calciumFraction * state.calciumFraction;
    double totalMolality = state.mbMolality / ratio;
    assertEquals((1.0 - state.calciumFraction) * totalMolality, state.magnesiumChlorideMolality, 5.0e-12);
    assertEquals(state.calciumFraction * totalMolality, state.calciumChlorideMolality, 5.0e-12);
    assertEquals(state.mbPhi * ratio / state.mbMolality, state.osmoticCoefficient, 5.0e-11);
    double derivedWaterActivity =
        Math.exp(-0.01801528 * 3.0 * totalMolality * state.osmoticCoefficient);
    assertEquals(derivedWaterActivity, state.waterActivity, 5.0e-12);
  }

  private static void assertMaterialAndPhaseState(PhasePitzer phase) {
    double compositionSum = 0.0;
    double chargeResidual = 0.0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      double moleFraction = phase.getComponent(component).getx();
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0);
      compositionSum += moleFraction;
      chargeResidual += phase.getComponent(component).getMolality(phase)
          * phase.getComponent(component).getIonicCharge();
    }
    assertEquals(1.0, compositionSum, 1.0e-12);
    assertEquals(0.0, chargeResidual, 1.0e-12);

    double totalDivalentCationMolality =
        phase.getComponent("Ca++").getMolality(phase) + phase.getComponent("Mg++").getMolality(phase);
    assertEquals(2.0 * totalDivalentCationMolality, phase.getComponent("Cl-").getMolality(phase), 1.0e-12,
        "Ca/Mg/Cl formula-unit material balance");
  }

  private static void assertWaterActivityOsmoticConsistency(PhasePitzer phase, double activity) {
    double totalIonMolality = 0.0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      if (Math.abs(phase.getComponent(component).getIonicCharge()) >= 0.5) {
        totalIonMolality += phase.getComponent(component).getMolality(phase);
      }
    }
    double osmoticCoefficientFromActivity = -1000.0 * Math.log(activity) / (18.015 * totalIonMolality);
    assertEquals(osmoticCoefficientFromActivity, phase.getOsmoticCoefficientOfWater(), 2.0e-10);
  }

  private static double waterActivity(PhasePitzer phase) {
    int water = phase.getComponent("water").getComponentNumber();
    ComponentGePitzer waterComponent = (ComponentGePitzer) phase.getComponent(water);
    return waterComponent.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
        phase.getType()) * phase.getComponent(water).getx();
  }

  private List<ReferenceState> readReferenceStates() throws IOException {
    InputStream input = getClass().getResourceAsStream(REFERENCE_RESOURCE);
    assertNotNull(input, "Missing validation resource " + REFERENCE_RESOURCE);
    List<ReferenceState> states = new ArrayList<ReferenceState>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("mb_")) {
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(9, fields.length, "Unexpected validation row: " + line);
        states.add(new ReferenceState(fields));
      }
    }
    return states;
  }

  private static final class ReferenceState {
    private final double mbMolality;
    private final double mbPhi;
    private final double a;
    private final double b;
    private final double calciumFraction;
    private final double magnesiumChlorideMolality;
    private final double calciumChlorideMolality;
    private final double osmoticCoefficient;
    private final double waterActivity;

    private ReferenceState(String[] fields) {
      mbMolality = Double.parseDouble(fields[0]);
      mbPhi = Double.parseDouble(fields[1]);
      a = Double.parseDouble(fields[2]);
      b = Double.parseDouble(fields[3]);
      calciumFraction = Double.parseDouble(fields[4]);
      magnesiumChlorideMolality = Double.parseDouble(fields[5]);
      calciumChlorideMolality = Double.parseDouble(fields[6]);
      osmoticCoefficient = Double.parseDouble(fields[7]);
      waterActivity = Double.parseDouble(fields[8]);
    }
  }
}
