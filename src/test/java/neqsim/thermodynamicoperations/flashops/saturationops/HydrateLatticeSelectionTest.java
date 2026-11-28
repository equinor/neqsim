package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Fixed-lattice and unconstrained incipient-temperature regressions. */
class HydrateLatticeSelectionTest {
  /**
   * Independently solve both roots, then repeat on the same fluid to detect a lingering constraint.
   * 
   * @throws Exception if a hydrate flash fails
   */
  @Test
  void autoSelectsWarmerDissociationRoot() throws Exception {
    for (boolean cpa : new boolean[] {true, false}) {
      for (double propane : new double[] {0.0, 0.06}) {
        SystemInterface fluid = cpa ? new SystemSrkCPAstatoil(280, 55) : new SystemSrkEos(280, 55);
        fluid.addComponent("methane", 1.0 - propane);
        if (propane > 0) {
          fluid.addComponent("propane", propane);
        }
        fluid.addComponent("water", 1.0);
        fluid.setMixingRule(cpa ? 10 : 2);
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        double[] roots = new double[2];
        for (int structure = 1; structure <= 2; structure++) {
          ops.hydrateFormationTemperature(structure);
          assertEquals(structure, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
          roots[structure - 1] = fluid.getTemperature();
        }
        ops.hydrateFormationTemperature();
        int winner = propane == 0.0 ? 1 : 2;
        assertEquals(winner, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
        assertTrue(roots[winner - 1] > roots[2 - winner] + 0.01);
        assertEquals(Math.max(roots[0], roots[1]), fluid.getTemperature(), 2e-4);
        assertThrows(IllegalArgumentException.class, () -> ops.hydrateFormationTemperature(3));
      }
    }
  }

  /**
   * Three experimental controls guard the unfitted temperature baseline and phase selection.
   * 
   * @throws Exception if the incipient hydrate calculation fails
   */
  @Test
  void reproducesSemenovBaselineAtLowPressure() throws Exception {
    String[] solutes = {"water", "methanol", "MEG"};
    double[] methane = {0.6115353, 0.6455098, 0.6517957};
    double[] pressure = {47.7, 49.7, 49.9};
    double[] calculated = {279.1192, 274.9189, 277.0063};
    double[] waterG = {350.0, 315.100, 315.0};
    double[] soluteG = {0.0, 35.063, 35.0};
    double[] molarMass = {18.01528, 32.04186, 62.06784};
    for (int i = 0; i < solutes.length; i++) {
      SystemInterface fluid = new SystemSrkCPAstatoil(280, pressure[i]);
      fluid.addComponent("methane", methane[i]);
      fluid.addComponent("water", waterG[i] / 18.01528);
      if (i > 0) {
        fluid.addComponent(solutes[i], soluteG[i] / molarMass[i]);
      }
      fluid.setMixingRule(10);
      fluid.setMultiPhaseCheck(true);
      fluid.setHydrateCheck(true);
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      ops.hydrateFormationTemperature();
      assertEquals(calculated[i], fluid.getTemperature(), 0.01);
      assertEquals(1, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
      HydrateEquilibriumDiagnostics diag = ((HydrateFormationTemperatureFlash) ops.getOperation()).getDiagnostics();
      assertTrue(diag.isConverged());
      assertTrue(Math.abs(diag.getHydrateResidual()) < 1e-6);
      assertTrue(diag.getComponentBalanceResidual() < 1e-8);
    }
  }
}
