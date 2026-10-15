package neqsim.thermodynamicoperations.flashops;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Species-conserving, phase-charge-neutral amount directions for a fixed fluid active set.
 *
 * <p>
 * One charged reference species is eliminated in each aqueous liquid. A direction for another ion transfers that ion
 * and its charge-balancing reference-ion amount together. This is the null-space form of the electroneutrality
 * elimination in Nikolaidis et al. (2022), doi:10.1016/j.fluid.2022.113441, and Perdomo et al. (2025),
 * doi:10.1016/j.compchemeng.2024.108977. Molecular species retain their ordinary phase-transfer directions. This class
 * does not determine phase stability or change the thermodynamic model.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class ElectroneutralPhaseCoordinates implements java.io.Serializable {
  private static final long serialVersionUID = 1000;
  private final int phases;
  private final int components;
  private final boolean[] liquid;
  private final double[] charge;
  private final double[] feed;
  private final int referenceIon;
  private final int referenceLiquid;
  private final int[] variablePhase;
  private final int[] variableComponent;

  /**
   * Captures a fixed active set containing at least two aqueous liquids.
   *
   * @param system initialized nonreactive fluid
   * @throws IllegalArgumentException if the charge basis or active set is unsupported
   */
  ElectroneutralPhaseCoordinates(SystemInterface system) {
    phases = system.getNumberOfPhases();
    components = system.getNumberOfComponents();
    liquid = new boolean[phases];
    charge = new double[components];
    feed = new double[components];
    int lastLiquid = -1;
    int liquids = 0;
    for (int phase = 0; phase < phases; phase++) {
      PhaseType root = system.getPhaseType(phase);
      if (root != PhaseType.GAS && root != PhaseType.LIQUID && root != PhaseType.OIL && root != PhaseType.AQUEOUS
          && root != PhaseType.LIQUID_ASPHALTENE) {
        throw new IllegalArgumentException("Electroneutral coordinates support fluid phases only");
      }
      liquid[phase] = ElectroneutralLiquidPartition.isLiquidHost(system, phase);
      if (liquid[phase]) {
        lastLiquid = phase;
        liquids++;
      }
    }
    int anchor = -1;
    double totalCharge = 0.0;
    for (int component = 0; component < components; component++) {
      feed[component] = system.getComponent(component).getz();
      charge[component] = system.getComponent(component).getIonicCharge();
      if (FlashPhaseConstraints.isIon(system.getComponent(component)) && charge[component] == 0.0
          && feed[component] > 0.0) {
        throw new IllegalArgumentException("Electroneutral coordinates require explicit nonzero ionic charges");
      }
      totalCharge += charge[component] * feed[component];
      if (charge[component] != 0.0 && feed[component] > 0.0 && (anchor < 0 || feed[component] > feed[anchor])) {
        anchor = component;
      }
    }
    if (liquids < 2 || anchor < 0 || Math.abs(totalCharge) > 1.0e-12 || system.isChemicalSystem()) {
      throw new IllegalArgumentException(
          "Electroneutral coordinates require a neutral nonreactive multiple-liquid feed");
    }
    referenceIon = anchor;
    referenceLiquid = lastLiquid;
    List<Integer> componentMap = new ArrayList<Integer>();
    List<Integer> phaseMap = new ArrayList<Integer>();
    for (int component = 0; component < components; component++) {
      if (!(feed[component] > 0.0) || component == referenceIon) {
        continue;
      }
      int reference = charge[component] == 0.0 ? phases - 1 : referenceLiquid;
      for (int phase = 0; phase < phases; phase++) {
        if (phase != reference && (charge[component] == 0.0 || liquid[phase])) {
          componentMap.add(component);
          phaseMap.add(phase);
        }
      }
    }
    variableComponent = new int[componentMap.size()];
    variablePhase = new int[componentMap.size()];
    for (int row = 0; row < variableComponent.length; row++) {
      variableComponent[row] = componentMap.get(row);
      variablePhase[row] = phaseMap.get(row);
    }
  }

  /** @return the independent dimension */
  int size() {
    return variableComponent.length;
  }

  /**
   * Creates an interior initialization by mixing each liquid's ions with a small fraction of the common ionic feed. The
   * convex combination preserves species inventories and every phase charge; it is not a concentration floor.
   *
   * @param system initialized system to seed
   * @param fraction convex mixing fraction, strictly between zero and one
   */
  void seed(SystemInterface system, double fraction) {
    double[] amounts = amounts(system);
    boolean missing = false;
    double liquidAmount = 0.0;
    for (int phase = 0; phase < phases; phase++) {
      if (liquid[phase]) {
        liquidAmount += system.getBeta(phase);
        for (int component = 0; component < components; component++) {
          missing |= charge[component] != 0.0 && feed[component] > 0.0
              && amounts[phase * components + component] == 0.0;
        }
      }
    }
    if (!missing) {
      return;
    }
    if (!(fraction > 0.0 && fraction < 1.0)) {
      throw new IllegalArgumentException("Interior initialization requires a convex mixing fraction");
    }
    for (int phase = 0; phase < phases; phase++) {
      if (liquid[phase]) {
        double weight = system.getBeta(phase) / liquidAmount;
        for (int component = 0; component < components; component++) {
          if (charge[component] != 0.0) {
            int index = phase * components + component;
            amounts[index] = (1.0 - fraction) * amounts[index] + fraction * weight * feed[component];
          }
        }
      }
    }
    reconstruct(amounts);
    apply(system, amounts);
    system.init(3);
  }

  /**
   * Reads complete phase amounts on the total-feed mole basis.
   *
   * @param system initialized system
   * @return phase-major amounts
   */
  double[] amounts(SystemInterface system) {
    double[] amounts = new double[phases * components];
    for (int phase = 0; phase < phases; phase++) {
      for (int component = 0; component < components; component++) {
        amounts[phase * components + component] = system.getBeta(phase)
            * system.getPhase(phase).getComponent(component).getx();
      }
    }
    reconstruct(amounts);
    return amounts;
  }

  /**
   * Applies a complete feasible amount vector to the system.
   *
   * @param system destination system
   * @param amounts phase-major amounts
   */
  void apply(SystemInterface system, double[] amounts) {
    for (int phase = 0; phase < phases; phase++) {
      double beta = 0.0;
      for (int component = 0; component < components; component++) {
        beta += amounts[phase * components + component];
      }
      system.setBeta(phase, beta);
      for (int component = 0; component < components; component++) {
        system.getPhase(phase).getComponent(component).setx(amounts[phase * components + component] / beta);
      }
    }
  }

  /**
   * Checks strictly positive participating amounts without an absolute species floor.
   *
   * @param amounts complete phase amounts
   * @return whether all amount and phase bounds are feasible
   */
  boolean feasible(double[] amounts) {
    for (int phase = 0; phase < phases; phase++) {
      double beta = 0.0;
      for (int component = 0; component < components; component++) {
        double value = amounts[phase * components + component];
        boolean present = feed[component] > 0.0 && (charge[component] == 0.0 || liquid[phase]);
        if (!Double.isFinite(value) || (present ? !(value > 0.0) : value != 0.0)) {
          return false;
        }
        beta += value;
      }
      if (!(beta > 1.0e-12 && beta < 1.0 - 1.0e-12)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Computes the chemical-potential gradient along one charge-neutral transfer direction.
   *
   * @param system initialized system
   * @param row independent variable
   * @return reduced dimensionless affinity
   */
  double gradient(SystemInterface system, int row) {
    int component = variableComponent[row];
    int phase = variablePhase[row];
    int reference = charge[component] == 0.0 ? phases - 1 : referenceLiquid;
    double result = logFugacity(system, phase, component) - logFugacity(system, reference, component);
    if (charge[component] != 0.0) {
      result -= charge[component] / charge[referenceIon]
          * (logFugacity(system, phase, referenceIon) - logFugacity(system, reference, referenceIon));
    }
    return result;
  }

  /**
   * Projects the amount Hessian onto two charge-neutral transfer directions.
   *
   * @param system initialized system with composition derivatives
   * @param row first independent variable
   * @param column second independent variable
   * @return reduced Gibbs curvature
   */
  double curvature(SystemInterface system, int row, int column) {
    double result = 0.0;
    int i = variableComponent[row];
    int j = variableComponent[column];
    double ai = -charge[i] / charge[referenceIon];
    double aj = -charge[j] / charge[referenceIon];
    for (int phase = 0; phase < phases; phase++) {
      double weight = phaseWeight(row, phase) * phaseWeight(column, phase);
      if (weight != 0.0) {
        double value = phaseCurvature(system, phase, i, j);
        if (ai != 0.0) {
          value += ai * phaseCurvature(system, phase, referenceIon, j);
        }
        if (aj != 0.0) {
          value += aj * phaseCurvature(system, phase, i, referenceIon);
        }
        if (ai != 0.0 && aj != 0.0) {
          value += ai * aj * phaseCurvature(system, phase, referenceIon, referenceIon);
        }
        result += weight * value;
      }
    }
    return result;
  }

  /**
   * Returns one coordinate's stored amount.
   *
   * @param amounts complete phase amounts
   * @param row independent variable
   * @return independent species amount
   */
  double coordinate(double[] amounts, int row) {
    return amounts[variablePhase[row] * components + variableComponent[row]];
  }

  /**
   * Adds one signed independent-coordinate change; eliminated coordinates are reconstructed afterward.
   *
   * @param amounts destination complete amount vector
   * @param row independent variable
   * @param change signed independent-coordinate change
   */
  void increment(double[] amounts, int row, double change) {
    amounts[variablePhase[row] * components + variableComponent[row]] += change;
  }

  /**
   * Reconstructs independent-species reference amounts and eliminated reference-ion amounts.
   *
   * @param amounts complete phase amounts
   */
  void reconstruct(double[] amounts) {
    for (int component = 0; component < components; component++) {
      if (component == referenceIon) {
        continue;
      }
      int reference = charge[component] == 0.0 ? phases - 1 : referenceLiquid;
      double remaining = feed[component];
      for (int phase = 0; phase < phases; phase++) {
        if (phase != reference) {
          remaining -= amounts[phase * components + component];
        }
      }
      amounts[reference * components + component] = remaining;
    }
    for (int phase = 0; phase < phases; phase++) {
      double phaseCharge = 0.0;
      for (int component = 0; component < components; component++) {
        if (component != referenceIon) {
          phaseCharge += charge[component] * amounts[phase * components + component];
        }
      }
      amounts[phase * components + referenceIon] = -phaseCharge / charge[referenceIon];
    }
  }

  /**
   * Returns the phase incidence of a transfer column.
   *
   * @param row independent variable
   * @param phase logical phase
   * @return plus one, minus one or zero
   */
  private double phaseWeight(int row, int phase) {
    int reference = charge[variableComponent[row]] == 0.0 ? phases - 1 : referenceLiquid;
    return phase == variablePhase[row] ? 1.0 : phase == reference ? -1.0 : 0.0;
  }

  /**
   * Evaluates a dimensionless chemical potential without exponentiating the fugacity coefficient.
   *
   * @param system initialized system
   * @param phase logical phase
   * @param component component index
   * @return log of fugacity divided by pressure
   */
  private static double logFugacity(SystemInterface system, int phase, int component) {
    return Math.log(system.getPhase(phase).getComponent(component).getx())
        + system.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
  }

  /**
   * Evaluates an unprojected phase-amount derivative.
   *
   * @param system initialized system with derivatives
   * @param phase logical phase
   * @param i chemical-potential component
   * @param j differentiated amount component
   * @return dimensionless amount Hessian entry
   */
  private static double phaseCurvature(SystemInterface system, int phase, int i, int j) {
    return ((i == j ? 1.0 / system.getPhase(phase).getComponent(i).getx() : 0.0) - 1.0
        + system.getPhase(phase).getComponent(i).getdfugdx(j)) / system.getBeta(phase);
  }
}
