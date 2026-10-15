package neqsim.chemicalreactions.chemicalequilibrium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Independent finite-difference checks of adaptive aqueous chemical-equilibrium curvature.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ChemicalEquilibriumDerivativeTest {
  /** The public component derivative excludes the ideal concentration term. */
  @Test
  void compositionDerivativeContainsOnlyLogFugacityCoefficient() {
    SystemInterface fluid = fluid();
    for (String name : new String[] {"CO2", "OH-"}) {
      int component = fluid.getComponent(name).getComponentNumber();
      double step = 1e-4 * fluid.getComponent(component).getNumberOfmoles();
      SystemInterface plus = perturbed(fluid, component, step);
      SystemInterface minus = perturbed(fluid, component, -step);
      double numerical = (plus.getPhase(0).getComponent(component).getLogFugacityCoefficient()
          - minus.getPhase(0).getComponent(component).getLogFugacityCoefficient()) / (2.0 * step);
      assertEquals(numerical, fluid.getPhase(0).getComponent(component).getdfugdn(component),
          2e-6 * Math.max(1.0, Math.abs(numerical)));
    }
  }

  /**
   * Both matrix conventions retain the ideal term exactly once.
   *
   * @param fullMatrix whether the total-mole coupling is included explicitly
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void adaptiveMatrixDifferentiatesActivitiesWithoutSubtractingIdealCurvature(boolean fullMatrix) {
    SystemInterface fluid = fluid();
    ChemicalReactionOperations chemistry = fluid.getChemicalReactionOperations();
    ComponentInterface[] components = chemistry.getComponents();
    double[][] elements = chemistry.getAmatrix();
    double[] amounts = chemistry.calcNVector();
    double[] totals = new double[elements.length];
    for (int row = 0; row < elements.length; row++) {
      for (int component = 0; component < amounts.length; component++) {
        totals[row] += elements[row][component] * amounts[component];
      }
    }
    ChemicalEquilibrium solver = new ChemicalEquilibrium(elements, totals, fluid, components, 0);
    solver.setUseFugacityDerivatives(true);
    solver.setUseFullMMatrix(fullMatrix);
    solver.chemSolve();
    for (int column = 0; column < components.length; column++) {
      String name = components[column].getComponentName();
      if (!"CO2".equals(name) && !"OH-".equals(name)) {
        continue;
      }
      int component = components[column].getComponentNumber();
      double step = 1e-4 * fluid.getComponent(component).getNumberOfmoles();
      SystemInterface plus = perturbed(fluid, component, step);
      SystemInterface minus = perturbed(fluid, component, -step);
      for (int row = 0; row < components.length; row++) {
        int rowComponent = components[row].getComponentNumber();
        double numerical = (logActivity(plus, rowComponent) - logActivity(minus, rowComponent)) / (2.0 * step);
        if (!fullMatrix) {
          numerical += 1.0 / fluid.getPhase(0).getNumberOfMolesInPhase();
        }
        assertEquals(numerical, solver.M_matrix[row][column], 2e-5 * Math.max(1.0, Math.abs(numerical)),
            components[row].getComponentName() + " / " + name);
      }
    }
  }

  /** @return a finite, electrically neutral aqueous carbonate composition */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 55.5);
    fluid.addComponent("CO2", 0.01);
    fluid.addComponent("Na+", 0.001);
    fluid.addComponent("OH-", 0.001);
    fluid.chemicalReactionInit();
    fluid.setMixingRule(10);
    fluid.setNumberOfPhases(1);
    fluid.setPhaseType(0, PhaseType.AQUEOUS);
    fluid.setBeta(0, 1.0);
    fluid.init(3);
    return fluid;
  }

  /**
   * Rebuilds a one-phase composition after an independent mole perturbation.
   *
   * @param fluid original aqueous state
   * @param component component index to perturb
   * @param change additive mole change
   * @return independently initialized perturbed state
   */
  private SystemInterface perturbed(SystemInterface fluid, int component, double change) {
    SystemInterface trial = fluid.clone();
    trial.addComponent(component, change);
    double total = trial.getTotalNumberOfMoles();
    for (int species = 0; species < trial.getNumberOfComponents(); species++) {
      trial.getPhase(0).getComponent(species).setx(trial.getComponent(species).getNumberOfmoles() / total);
    }
    trial.init(3);
    return trial;
  }

  /**
   * Evaluates the reaction activity used by the chemical solver.
   *
   * @param fluid initialized aqueous state
   * @param component component index
   * @return logarithmic reaction activity on the model-selected mole-fraction basis
   */
  private double logActivity(SystemInterface fluid, int component) {
    ComponentInterface species = fluid.getPhase(0).getComponent(component);
    return Math.log(species.getx()) + (species.calcActivity()
        ? fluid.getPhase(0).getLogActivityCoefficient(component, fluid.getComponent("water").getComponentNumber())
        : 0.0);
  }
}
