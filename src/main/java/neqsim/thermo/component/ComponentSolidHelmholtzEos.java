package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;

/**
 * EOS component used by pure-solid fundamental Helmholtz phases.
 *
 * <p>
 * This component deliberately bypasses {@link ComponentEos#init(double, double, double, double, int)} and
 * {@link ComponentEos#Finit(PhaseInterface, double, double, double, double, int, int)}. A fundamental solid Helmholtz
 * model has no cubic attractive term or component {@code a}/{@code
 * b} contribution.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ComponentSolidHelmholtzEos extends ComponentEos {
  private static final long serialVersionUID = 1000L;
  private double logFugacityCoefficient = Double.NaN;

  /**
   * Construct a solid Helmholtz component.
   *
   * @param name component name in the NeqSim component database
   * @param moles total component amount in mol
   * @param molesInPhase component amount in this phase in mol
   * @param compIndex component index in the phase
   */
  public ComponentSolidHelmholtzEos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    seta(0.0);
    setb(0.0);
  }

  /**
   * Initialize generic composition bookkeeping without invoking cubic EOS parameters.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param totalNumberOfMoles total system amount in mol
   * @param beta phase mole fraction from zero to one
   * @param initType NeqSim initialization level
   */
  @Override
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int initType) {
    if (totalNumberOfMoles < 0.0) {
      throw new IllegalArgumentException("Total number of moles cannot be negative.");
    }
    if (totalNumberOfMoles == 0.0) {
      K = 1.0;
      numberOfMolesInPhase = 0.0;
      numberOfMoles = 0.0;
      return;
    }
    if (initType == 0) {
      K = 1.0;
      z = numberOfMoles / totalNumberOfMoles;
      x = z;
    }
    numberOfMolesInPhase = totalNumberOfMoles * x * beta;
    numberOfMoles = totalNumberOfMoles * z;
    z = numberOfMoles / totalNumberOfMoles;
  }

  /**
   * Skip cubic composition derivatives because the phase kernel owns the Helmholtz evaluation.
   *
   * @param phase solid Helmholtz phase
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @param totalNumberOfMoles total system amount in mol
   * @param beta phase mole fraction
   * @param numberOfComponents number of phase components
   * @param initType NeqSim initialization level
   */
  @Override
  public void Finit(PhaseInterface phase, double temperature, double pressure, double totalNumberOfMoles, double beta,
      int numberOfComponents, int initType) {
    // Fundamental pure-solid derivatives are evaluated by PhaseSolidHelmholtzEos.
  }

  /** @return zero because no cubic attractive parameter is used */
  @Override
  public double calca() {
    return 0.0;
  }

  /** @return zero because no cubic covolume parameter is used */
  @Override
  public double calcb() {
    return 0.0;
  }

  /**
   * Calculate the pure-solid fugacity coefficient supplied by the phase Helmholtz state.
   *
   * @param phase phase containing this component
   * @return fugacity coefficient
   * @throws IllegalArgumentException if the phase is not a solid Helmholtz phase
   */
  @Override
  public double fugcoef(PhaseInterface phase) {
    if (!(phase instanceof PhaseSolidHelmholtzEos)) {
      throw new IllegalArgumentException("Component requires a PhaseSolidHelmholtzEos phase.");
    }
    logFugacityCoefficient = ((PhaseSolidHelmholtzEos) phase).getSolidState().getLogFugacityCoefficient();
    fugacityCoefficient = Math.exp(logFugacityCoefficient);
    return fugacityCoefficient;
  }

  /**
   * Return the native logarithmic fugacity coefficient without exponentiating the Helmholtz state.
   *
   * @return natural logarithm of the solid fugacity coefficient
   */
  @Override
  public double getLogFugacityCoefficient() {
    return logFugacityCoefficient;
  }

  /**
   * Return the pure-solid chemical potential from the phase Helmholtz state.
   *
   * @param phase phase containing this component
   * @return chemical potential in J/mol
   * @throws IllegalArgumentException if the phase is not a solid Helmholtz phase
   */
  @Override
  public double getChemicalPotential(PhaseInterface phase) {
    if (!(phase instanceof PhaseSolidHelmholtzEos)) {
      throw new IllegalArgumentException("Component requires a PhaseSolidHelmholtzEos phase.");
    }
    return ((PhaseSolidHelmholtzEos) phase).getSolidState().getGibbsEnergy();
  }
}