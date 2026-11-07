package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent activity and conservation checks for automatic calcium/sulfur acid-base selection.
 *
 * @author esol
 * @version 1.0
 */
class PitzerAutomaticCalciumSulfurTest {
  /** Native NeqSim identities corresponding to the independently generated PHREEQC columns. */
  private static final String[] SPECIES = {"Na+", "K+", "Ca++", "Cl-", "H3O+", "OH-", "HCO3-", "CO3--", "SO4--",
      "HSO4-", "CO2", "H2S", "HS-"};

  /**
   * Replays reference free species without silently comparing different equilibrium reaction sets.
   *
   * @throws Exception when the frozen reference cannot be read
   */
  @Test
  void calciumSulfateAndSulfideActivitiesMatchIndependentReference() throws Exception {
    String resource = "/neqsim/thermo/phase/reactive-carbonate/iphreeqc-calcium-sulfur-states.csv";
    int states = 0;
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
        for (int index = 0; index < SPECIES.length; index++) {
          fluid.addComponent(SPECIES[index], state[index + 2]);
        }
        fluid.chemicalReactionInit();
        fluid.setMixingRule("classic");
        fluid.init(0);
        fluid.init(1);
        PhasePitzer aqueous = (PhasePitzer) fluid.getGeLiquidPhase();
        assertEquals(state[15], aqueous.getPitzerPH(), 1.0e-3, "Native pH at row " + states);
        assertEquals(state[16], aqueous.getPitzerActivity(aqueous.getComponent("water").getComponentNumber()), 6.0e-5,
            "Water activity at row " + states);
        for (int index = 0; index < SPECIES.length; index++) {
          if (Double.isFinite(state[18 + index])) {
            int component = aqueous.getComponent(SPECIES[index]).getComponentNumber();
            assertEquals(state[18 + index], Math.log10(aqueous.getActivityCoefficient(component)), 1.0e-2,
                SPECIES[index] + " log10 gamma at row " + states);
          }
        }
        assertTrue(aqueous.getPitzerParameterCoverage().isComplete());
        assertTrue(aqueous.getPitzerReactionParameterCoverage().isComplete());
        assertTrue(aqueous.auditNeutralPitzerParameterCoverage().isComplete());
        assertTrue(state[31] < 1.0e-6, "Source-only H2S dimer must remain dilute in the activity comparison");
        states++;
      }
    }
    assertEquals(135, states);
  }

  /** Resolves the ordinary sour gas/oil/brine setup with both sulfate and sulfide conserved separately. */
  @Test
  void sourGasOilCalciumSulfateBrineClosesWithoutRedoxOrMinerals() {
    SystemPitzer fluid = new SystemPitzer(313.15, 50.0);
    fluid.addComponent("methane", 5.0);
    fluid.addComponent("n-heptane", 2.0);
    fluid.addComponent("CO2", 0.5);
    fluid.addComponent("H2S", 0.02);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.5);
    fluid.addComponent("Ca++", 0.05);
    fluid.addComponent("Cl-", 1.08);
    fluid.addComponent("SO4--", 0.01);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);

    PhasePitzer aqueous = (PhasePitzer) fluid.getPhase("aqueous");
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(aqueous.getPitzerPH() > 3.0 && aqueous.getPitzerPH() < 5.0);
    assertTrue(aqueous.getPitzerReactionParameterCoverage().isComplete());
    assertTrue(aqueous.auditNeutralPitzerParameterCoverage().isComplete());
    assertTrue(aqueous.getComponent("HSO4-").getMolality(aqueous) > 0.0);
    assertTrue(aqueous.getComponent("HS-").getMolality(aqueous) > 0.0);
    assertFalse(aqueous.hasComponent("S--"));
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() < 3.0e-6);
    assertTrue(fluid.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual() < 1.0e-10);
    Map<String, Double> expected = new TreeMap<String, Double>();
    expected.put("C", 19.5);
    expected.put("H", 163.04);
    expected.put("O", 56.54);
    expected.put("Na", 0.5);
    expected.put("K", 0.5);
    expected.put("Ca", 0.05);
    expected.put("Cl", 1.08);
    expected.put("S", 0.03);
    Map<String, Double> actual = new TreeMap<String, Double>();
    double sulfate = 0.0;
    double sulfide = 0.0;
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      betaSum += fluid.getBeta(phaseIndex);
      sulfate += phase.getComponent("SO4--").getNumberOfMolesInPhase()
          + phase.getComponent("HSO4-").getNumberOfMolesInPhase();
      sulfide += phase.getComponent("H2S").getNumberOfMolesInPhase()
          + phase.getComponent("HS-").getNumberOfMolesInPhase();
      for (int index = 0; index < phase.getNumberOfComponents(); index++) {
        ComponentInterface component = phase.getComponent(index);
        if (phase != aqueous && Math.abs(component.getIonicCharge()) > 0.5) {
          assertTrue(component.getNumberOfMolesInPhase() < 1.0e-15);
        }
        for (String element : component.getElements().getElementNames()) {
          double previous = actual.containsKey(element) ? actual.get(element) : 0.0;
          actual.put(element,
              previous + component.getNumberOfMolesInPhase() * component.getElements().getNumberOfElements(element));
        }
      }
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    assertEquals(0.01, sulfate, 1.0e-8, "S(VI) inventory");
    assertEquals(0.02, sulfide, 1.0e-8, "S(-II) inventory");
    assertEquals(expected.keySet(), actual.keySet());
    for (String element : expected.keySet()) {
      assertEquals(expected.get(element), actual.get(element), 1.0e-8, element + " inventory");
    }
    assertThrows(IllegalStateException.class, () -> fluid
        .requirePitzerDatasetValidationFor(PitzerParameterQualification.ValidationTarget.REACTIVE_SPECIATION));
  }

  /** A calcium-only sulfate feed needs neither CO2 nor an alkali-metal chloride to select the reviewed family. */
  @Test
  void sulfateOnlyAndH2sOnlyFeedsSelectAutomatically() {
    for (String solute : new String[] {"SO4--", "H2S"}) {
      SystemPitzer fluid = new SystemPitzer(298.15, 1.01325);
      fluid.addComponent("water", 55.508);
      fluid.addComponent("Ca++", 0.01);
      fluid.addComponent(solute, 0.01);
      if ("H2S".equals(solute)) {
        fluid.addComponent("Cl-", 0.02);
      }
      fluid.chemicalReactionInit();
      fluid.setMixingRule("classic");
      fluid.init(0);
      fluid.init(1);
      PhasePitzer phase = (PhasePitzer) fluid.getGeLiquidPhase();
      assertEquals(PitzerParameterDatasets.PHREEQC_AUTOMATIC_REACTIVE_CARBONATE_ID, phase.getParameterDatasetId());
      phase.requireCompletePitzerParameterCoverage();
      phase.requireCompletePitzerReactionParameterCoverage();
      phase.requireCompleteNeutralPitzerParameterCoverage();
      assertFalse(phase.hasComponent("CO2"));
      assertFalse(phase.hasComponent("S--"));
    }
  }

  /** Checks the exact analytic constant mapping independently across the source temperature range. */
  @Test
  void bisulfateReactionUsesTheExactSourceAnalyticConstant() {
    SystemPitzer fluid = new SystemPitzer(298.15, 1.01325);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 0.2);
    fluid.addComponent("SO4--", 0.1);
    fluid.chemicalReactionInit();
    fluid.setMixingRule("classic");
    neqsim.chemicalreactions.chemicalreaction.ChemicalReaction reaction = fluid.getChemicalReactionOperations()
        .getReactionList().getReaction("bisulfate");
    double bisulfateCoefficient = 0.0;
    for (int index = 0; index < reaction.getNames().length; index++) {
      if ("HSO4-".equals(reaction.getNames()[index])) {
        bisulfateCoefficient = reaction.getStocCoefs()[index];
      }
    }
    assertEquals(1.0, Math.abs(bisulfateCoefficient), 0.0);
    for (double temperature : new double[] {273.15, 298.15, 313.15, 323.15, 363.15}) {
      fluid.setTemperature(temperature);
      double sourceLogAssociation = -56.889 + 0.006473 * temperature + 2307.9 / temperature
          + 19.8858 * Math.log10(temperature);
      assertEquals(bisulfateCoefficient * sourceLogAssociation, Math.log10(reaction.getK(fluid.getGeLiquidPhase())),
          1.0e-12);
    }
  }
}
