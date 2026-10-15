package neqsim.chemicalreactions.chemicalequilibrium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.SaltSaturationResult;

/**
 * Checks the chemical Newton equations against stationary and conservative analytical states.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ChemicalEquilibriumTraceInventoryTest {
  /** Atom counts for water, CO2 and methanol, with hydrogen followed by oxygen. */
  private static final double[][] ATOMS = {{2.0, 0.0, 4.0}, {1.0, 2.0, 1.0}};

  /** A stationary chemical potential has no reaction step despite sub-tolerance inventory drift. */
  @Test
  void stationaryPotentialDoesNotCreateTraceSpeciesStep() {
    ChemicalEquilibrium solver = analyticalSolver(1.0, 1.0e-9);
    solver.chemSolve();
    for (int species = 0; species < 3; species++) {
      assertEquals(0.0, solver.dn_matrix.get(species, 0) / solver.n_mol[species], 1.0e-10,
          "A stationary potential must not drive a relative trace-species step");
    }
  }

  /**
   * The linear Newton constraints restore a resolved atom deficit at every material scale.
   *
   * @param scale overall material scale
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.001, 1.0, 1000.0})
  void conservationCorrectionScalesWithInventory(double scale) {
    ChemicalEquilibrium solver = analyticalSolver(scale, scale * 1.0e-7);
    solver.chemSolve();
    for (int element = 0; element < ATOMS.length; element++) {
      double updated = 0.0;
      for (int species = 0; species < 3; species++) {
        updated += ATOMS[element][species] * (solver.n_mol[species] + solver.dn_matrix.get(species, 0));
      }
      assertEquals(solver.b_element[element], updated, scale * 1.0e-12);
    }
  }

  /**
   * The unchanged public FeCO3 saturation case must finish with a qualified chemical state.
   *
   * @throws Exception if salt saturation cannot be qualified
   */
  @Test
  void traceCarbonateSeedAllowsIronCarbonateSaturation() throws Exception {
    SystemInterface system = new SystemElectrolyteCPAstatoil(293.15, 1.01325);
    system.addComponent("water", 1.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    SaltSaturationResult result = new ThermodynamicOperations(system).calcSaltSaturationWithDiagnostics("FeCO3");
    assertTrue(result.isConverged());
    assertEquals(1.0, result.getFinalSaturationRatio(), 1.0e-6);
    assertTrue(system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
    assertTrue(system.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8);
    assertEquals(0.0, system.getChemicalReactionOperations().getReactivePhaseChargeMoles(), 1.0e-8);
  }

  /**
   * Builds a known stationary potential mu=A-transpose*lambda independently of equilibrium constants.
   *
   * @param scale overall material scale
   * @param hydrogenDeficit hydrogen inventory missing from the current iterate, in mol
   * @return configured ideal-curvature chemical Newton system
   */
  private static ChemicalEquilibrium analyticalSolver(double scale, double hydrogenDeficit) {
    SystemInterface system = new SystemSrkEos(300.0, 1.0);
    system.addComponent("water", scale);
    system.addComponent("CO2", scale * 1.0e-4);
    system.addComponent("methanol", scale * 2.0e-4);
    system.init(0);
    system.setNumberOfPhases(1);
    system.setPhaseType(0, PhaseType.GAS);
    system.setBeta(0, 1.0);
    system.init(1);
    ComponentInterface[] components = new ComponentInterface[3];
    double[] target = new double[2];
    for (int species = 0; species < 3; species++) {
      components[species] = system.getPhase(0).getComponent(species);
      for (int element = 0; element < ATOMS.length; element++) {
        target[element] += ATOMS[element][species] * components[species].getNumberOfMolesInPhase();
      }
    }
    target[0] += hydrogenDeficit;
    ChemicalEquilibrium solver = new ChemicalEquilibrium(ATOMS, target, system, components, 0);
    for (int species = 0; species < 3; species++) {
      double potential = 2.0 * ATOMS[0][species] - 0.5 * ATOMS[1][species];
      solver.logactivityVec[species] = 0.0;
      solver.chem_ref[species] = potential
          - Math.log(components[species].getNumberOfMolesInPhase() / system.getPhase(0).getNumberOfMolesInPhase());
    }
    return solver;
  }
}
