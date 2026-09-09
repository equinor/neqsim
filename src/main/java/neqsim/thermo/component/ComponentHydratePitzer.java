package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 * Van der Waals-Platteeuw hydrate with the same liquid-water reference as the Pitzer aqueous phase.
 *
 * <p>
 * Reuses the database Langmuir constants and empty-lattice chemical potentials of the standard hydrate model. The
 * solvent reference cancels when comparing this fugacity with {@code aW * pSatWater} from ComponentGePitzer. In
 * particular, an SRK pure-water fugacity must not be used on just one side of that comparison. Pressure enters the
 * lattice-to-liquid chemical potential through its volume difference; no additional pure-water Poynting factor is
 * applied to either side. This is an incipient hydrate model, not a hydrate amount or kinetics calculation.
 * </p>
 *
 * @author NeqSim
 */
public class ComponentHydratePitzer extends ComponentHydratePVTsim {
  private static final long serialVersionUID = 1000;

  /** Requested structure: 0 selects the stable structure, 1 or 2 fixes it. */
  private int requestedStructure;

  /**
   * Creates a hydrate component with the Pitzer solvent reference.
   *
   * @param name component name
   * @param moles total component moles
   * @param molesInPhase component moles in phase
   * @param compIndex component index
   */
  public ComponentHydratePitzer(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentHydratePitzer clone() {
    ComponentHydratePitzer copy = (ComponentHydratePitzer) super.clone();
    copy.reffug = reffug.clone();
    return copy;
  }

  /**
   * Selects the structure without disabling stable-structure selection after the first evaluation.
   *
   * @param structure 0 for automatic selection, 1 for sI, 2 for sII
   */
  public void setRequestedStructure(int structure) {
    if (structure < 0 || structure > 2) {
      throw new IllegalArgumentException("Hydrate structure must be 0 (automatic), 1 or 2");
    }
    requestedStructure = structure;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    if (!"water".equals(componentName)) {
      fugacityCoefficient = 1.0e50;
      return fugacityCoefficient;
    }
    double minimum = Double.POSITIVE_INFINITY;
    int first = requestedStructure == 0 ? 0 : requestedStructure - 1;
    int last = requestedStructure == 0 ? 1 : first;
    for (int structure = first; structure <= last; structure++) {
      double logOccupancy = 0.0;
      for (int cavity = 0; cavity < 2; cavity++) {
        double loading = 0.0;
        for (int j = 0; j < numberOfComps; j++) {
          if (phase.getComponent(j).isHydrateFormer()) {
            loading += ((ComponentHydrate) phase.getComponent(j)).calcCKI(structure, cavity, phase) * reffug[j];
          }
        }
        // log(1 - sum(theta)) = -log(1 + sum(C*f)); stable even for nearly full cavities.
        logOccupancy -= getCavprwat()[structure][cavity] * Math.log1p(loading);
      }
      double logRatio = calcDeltaChemPot(phase, numberOfComps, temp, pres, structure) + logOccupancy;
      if (logRatio < minimum) {
        minimum = logRatio;
        hydrateStructure = structure;
      }
    }
    fugacityCoefficient = getAntoineVaporPressure(temp) * Math.exp(minimum) / pres;
    return fugacityCoefficient;
  }
}
