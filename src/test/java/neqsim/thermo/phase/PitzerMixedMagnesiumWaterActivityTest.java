package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemPitzer;

/** Held-out mixed MgCl2-MgSO4 water-activity validation for the PHREEQC Pitzer catalog. */
class PitzerMixedMagnesiumWaterActivityTest extends neqsim.NeqSimTest {
  private static final String REFERENCE_RESOURCE = "/data/chemistry_benchmarks/pitzer_mgcl2_mgso4_water_activity_298k.csv";

  @Test
  void mixedMagnesiumWaterActivityMatchesIndependentThermoMlData() throws IOException {
    List<ReferenceState> states = readReferenceStates();
    assertEquals(28, states.size());

    double maximumAbsoluteResidual = 0.0;
    double squaredResidualSum = 0.0;
    int withinReportedExpandedUncertainty = 0;
    Map<Integer, Double> previousCalculatedActivity = new HashMap<Integer, Double>();
    Map<Integer, Double> previousExperimentalActivity = new HashMap<Integer, Double>();

    for (ReferenceState state : states) {
      SystemPitzer system = createSystem(state.magnesiumChlorideMolality, state.magnesiumSulfateMolality);
      PhasePitzer phase = (PhasePitzer) system.getPhase(1);
      phase.requireCompletePitzerParameterCoverage();

      assertEquals(PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID, phase.getParameterDatasetId());
      assertTrue(PitzerParameterDatasets.isWithinMagnesiumChlorideSulfateWaterActivityValidationRange(298.15,
          state.magnesiumChlorideMolality, state.magnesiumSulfateMolality));
      assertMaterialAndPhaseState(phase);

      double calculatedActivity = waterActivity(phase);
      assertEquals(calculatedActivity, waterActivity(phase), 0.0, "Repeated evaluation changed water activity");
      assertTrue(Double.isFinite(calculatedActivity) && calculatedActivity > 0.0 && calculatedActivity <= 1.0);
      assertWaterActivityOsmoticConsistency(phase, calculatedActivity);

      int compositionLine = compositionLine(state);
      if (previousCalculatedActivity.containsKey(compositionLine)) {
        assertTrue(calculatedActivity < previousCalculatedActivity.get(compositionLine),
            "Calculated water activity must decrease with molality on composition line " + compositionLine);
        assertTrue(state.waterActivity < previousExperimentalActivity.get(compositionLine),
            "Experimental water activity must decrease with molality on composition line " + compositionLine);
      }
      previousCalculatedActivity.put(compositionLine, calculatedActivity);
      previousExperimentalActivity.put(compositionLine, state.waterActivity);

      double residual = calculatedActivity - state.waterActivity;
      maximumAbsoluteResidual = Math.max(maximumAbsoluteResidual, Math.abs(residual));
      squaredResidualSum += residual * residual;
      if (Math.abs(residual) <= state.expandedUncertainty) {
        withinReportedExpandedUncertainty++;
      }
    }

    double rootMeanSquareResidual = Math.sqrt(squaredResidualSum / states.size());
    assertTrue(maximumAbsoluteResidual <= 0.004,
        "Maximum absolute water-activity residual: " + maximumAbsoluteResidual);
    assertTrue(rootMeanSquareResidual <= 0.0015, "Water-activity root-mean-square residual: " + rootMeanSquareResidual);
    assertEquals(26, withinReportedExpandedUncertainty,
        "Number of states within the reported 95% expanded uncertainty");
  }

  @Test
  void changedStateMatchesFreshConstructionWithoutStaleParameters() {
    SystemPitzer changed = createSystem(0.20, 0.15);
    double initialActivity = waterActivity((PhasePitzer) changed.getPhase(1));
    changed.addComponent("Mg++", 0.35);
    changed.addComponent("Cl-", 0.40);
    changed.addComponent("SO4--", 0.15);
    changed.init(0);

    PhasePitzer changedPhase = (PhasePitzer) changed.getPhase(1);
    changedPhase.requireCompletePitzerParameterCoverage();
    double changedActivity = waterActivity(changedPhase);
    double freshActivity = waterActivity((PhasePitzer) createSystem(0.40, 0.30).getPhase(1));
    assertTrue(changedActivity < initialActivity);
    assertEquals(freshActivity, changedActivity, 1.0e-12);
  }

  private static SystemPitzer createSystem(double magnesiumChlorideMolality, double magnesiumSulfateMolality) {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Ca++", 0.0);
    system.addComponent("Mg++", magnesiumChlorideMolality + magnesiumSulfateMolality);
    system.addComponent("Cl-", 2.0 * magnesiumChlorideMolality);
    system.addComponent("SO4--", magnesiumSulfateMolality);
    system.setMixingRule("classic");
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    return system;
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

    double magnesiumMolality = phase.getComponent("Mg++").getMolality(phase);
    double chlorideFormulaMolality = 0.5 * phase.getComponent("Cl-").getMolality(phase);
    double sulfateFormulaMolality = phase.getComponent("SO4--").getMolality(phase);
    assertEquals(magnesiumMolality, chlorideFormulaMolality + sulfateFormulaMolality, 1.0e-12,
        "Formula-unit Mg material balance");
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
        if (line.trim().isEmpty() || line.startsWith("#") || line.startsWith("mgcl2_")) {
          continue;
        }
        String[] fields = line.split(",", -1);
        assertEquals(4, fields.length, "Unexpected validation row: " + line);
        states.add(new ReferenceState(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]),
            Double.parseDouble(fields[2]), Double.parseDouble(fields[3])));
      }
    }
    return states;
  }

  private static int compositionLine(ReferenceState state) {
    double fraction = 3.0 * state.magnesiumChlorideMolality
        / (3.0 * state.magnesiumChlorideMolality + 4.0 * state.magnesiumSulfateMolality);
    if (fraction < 0.35) {
      return 20;
    }
    return fraction < 0.65 ? 50 : 80;
  }

  private static final class ReferenceState {
    private final double magnesiumChlorideMolality;
    private final double magnesiumSulfateMolality;
    private final double waterActivity;
    private final double expandedUncertainty;

    private ReferenceState(double magnesiumChlorideMolality, double magnesiumSulfateMolality, double waterActivity,
        double expandedUncertainty) {
      this.magnesiumChlorideMolality = magnesiumChlorideMolality;
      this.magnesiumSulfateMolality = magnesiumSulfateMolality;
      this.waterActivity = waterActivity;
      this.expandedUncertainty = expandedUncertainty;
    }
  }
}
