package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression tests for stable reaction-equilibrium diagnostics. */
class ChemicalReactionUnderflowDiagnosticTest extends neqsim.NeqSimTest {
  @Test
  void traceWaterDissociationQuotientUnderflows() {
    SystemInterface fluid = createTraceWaterSystem(1.0e-200);

    ChemicalReaction reaction = new ChemicalReaction("trace water dissociation",
        new String[] { "H3O+", "OH-", "water" }, new double[] { 1.0, 1.0, -2.0 }, new double[] { 0.0, 0.0, 0.0, 0.0 },
        0.0, 0.0, 298.15);

    assertEquals(0.0, reaction.calcK(fluid, 0),
        "The legacy product-space quotient demonstrates the current-master underflow");
    double logReactionQuotient = reaction.calcLogReactionQuotient(fluid, 0);
    assertTrue(Double.isFinite(logReactionQuotient));
    assertTrue(logReactionQuotient < -700.0, "The trace-species logarithmic quotient must retain its magnitude");
    assertEquals(logReactionQuotient, reaction.calcLogReactionQuotient(fluid, 0), 0.0,
        "Repeated diagnostics must be deterministic");
    assertEquals(logReactionQuotient, reaction.calcLogReactionResidual(fluid, 0), 1.0e-12,
        "The synthetic reaction uses K=1");

    SystemInterface nearbyFluid = createTraceWaterSystem(1.0e-190);
    double nearbyLogReactionQuotient = reaction.calcLogReactionQuotient(nearbyFluid, 0);
    assertTrue(Double.isFinite(nearbyLogReactionQuotient));
    assertTrue(nearbyLogReactionQuotient > logReactionQuotient,
        "Increasing both ionic trace activities must increase the reaction quotient");
  }

  @Test
  void aqueousReactionOperationsExposeImmutableResidualDiagnostics() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(303.15, 14.0);
    fluid.addComponent("water", 0.98);
    fluid.addComponent("Na+", 0.01);
    fluid.addComponent("Cl-", 0.01);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();

    Map<String, Double> residuals = fluid.getChemicalReactionOperations().getReactionLogResiduals();
    assertFalse(residuals.isEmpty(), "The database-selected aqueous reaction set must be diagnosed");
    double expectedMaximum = 0.0;
    for (double residual : residuals.values()) {
      assertTrue(Double.isFinite(residual), "Every aqueous reaction residual must be finite");
      expectedMaximum = Math.max(expectedMaximum, Math.abs(residual));
    }
    assertEquals(expectedMaximum, fluid.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual(), 0.0);
    assertTrue(expectedMaximum <= 2.0e-6, "Maximum absolute ln(Q/K) was " + expectedMaximum);

    Map<String, Double> elementResiduals = fluid.getChemicalReactionOperations().getElementBalanceResiduals();
    assertFalse(elementResiduals.isEmpty(), "The chemical-equilibrium basis must expose element balances");
    for (double residual : elementResiduals.values()) {
      assertTrue(Double.isFinite(residual), "Every elemental balance residual must be finite");
    }
    assertTrue(fluid.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8);
    assertTrue(Math.abs(fluid.getChemicalReactionOperations().getReactivePhaseChargeMoles()) <= 1.0e-8);
    assertTrue(fluid.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual() <= 1.0e-6);

    assertThrows(UnsupportedOperationException.class, () -> residuals.put("mutation", 0.0),
        "Callers must not mutate reaction-operation diagnostics");
    assertThrows(UnsupportedOperationException.class, () -> elementResiduals.put("mutation", 0.0),
        "Callers must not mutate element-balance diagnostics");
  }

  @Test
  void pitzerDiagnosticsDetectElementAndChargePerturbation() {
    SystemInterface fluid = new SystemPitzer(298.15, 1.01325);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);

    ChemicalReactionOperations diagnostics = fluid.getChemicalReactionOperations();
    Map<String, Double> elementResidualsBefore = diagnostics.getElementBalanceResiduals();
    assertFalse(elementResidualsBefore.isEmpty());
    double chargeBefore = diagnostics.getReactivePhaseChargeMoles();
    double normalizedChargeBefore = diagnostics.getNormalizedReactivePhaseChargeResidual();

    int aqueousPhase = -1;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      if ("aqueous".equalsIgnoreCase(fluid.getPhase(phaseIndex).getPhaseTypeName())) {
        aqueousPhase = phaseIndex;
        break;
      }
    }
    assertTrue(aqueousPhase >= 0, "Pitzer system must expose its aqueous phase");

    fluid.getPhase(aqueousPhase).getComponent("Na+").addMolesChemReac(1.0e-6, 0.0);

    Map<String, Double> elementResidualsAfter = diagnostics.getElementBalanceResiduals();
    double largestElementResidualChange = 0.0;
    for (Map.Entry<String, Double> entry : elementResidualsAfter.entrySet()) {
      largestElementResidualChange = Math.max(largestElementResidualChange,
          Math.abs(entry.getValue() - elementResidualsBefore.get(entry.getKey())));
    }

    assertEquals(1.0e-6, largestElementResidualChange, 1.0e-12);
    assertEquals(chargeBefore + 1.0e-6, diagnostics.getReactivePhaseChargeMoles(), 1.0e-12);
    assertTrue(diagnostics.getNormalizedReactivePhaseChargeResidual() > normalizedChargeBefore);
  }

  private SystemInterface createTraceWaterSystem(double ionicMoles) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("H3O+", ionicMoles);
    fluid.addComponent("OH-", ionicMoles);
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.init(1);
    return fluid;
  }
}
