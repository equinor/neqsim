package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSolidHelmholtzEos;
import neqsim.thermo.util.solid.SolidHelmholtzEquation;
import neqsim.thermo.util.solid.SolidHelmholtzState;

/**
 * Base phase for a pure solid represented by a fundamental Helmholtz equation of state.
 *
 * <p>
 * The class extends {@link PhaseEos} for integration with NeqSim's EOS hierarchy, but explicitly owns initialization so
 * cubic mixing rules, cubic volume roots, and fluid phase classification are never invoked.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class PhaseSolidHelmholtzEos extends PhaseEos {
  private static final long serialVersionUID = 1000L;
  private static final double MOLAR_VOLUME_SI_TO_NEQSIM = 1.0e5;

  private final SolidHelmholtzEquation solidEquation;
  private SolidHelmholtzState solidState;

  /**
   * Construct a solid Helmholtz phase.
   *
   * @param solidEquation substance-specific fundamental Helmholtz equation
   */
  public PhaseSolidHelmholtzEos(SolidHelmholtzEquation solidEquation) {
    if (solidEquation == null) {
      throw new IllegalArgumentException("Solid Helmholtz equation cannot be null.");
    }
    this.solidEquation = solidEquation;
    setType(PhaseType.SOLID);
    thermoPropertyModelName = "Solid Helmholtz Eos";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSolidHelmholtzEos clone() {
    return (PhaseSolidHelmholtzEos) super.clone();
  }

  /**
   * Add a component without constructing a cubic-EOS component.
   *
   * @param name component name
   * @param moles total component amount in mol
   * @param molesInPhase component amount in this phase in mol
   * @param compNumber component index
   */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSolidHelmholtzEos(name, moles, molesInPhase, compNumber);
  }

  /**
   * Initialize solid bookkeeping and evaluate the fundamental Helmholtz state.
   *
   * @param totalNumberOfMoles total system amount in mol
   * @param numberOfComponents number of components; currently must equal one
   * @param initType NeqSim initialization level
   * @param phaseType requested phase type; ignored because this phase remains solid
   * @param beta phase mole fraction from zero to one
   */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType phaseType, double beta) {
    if (totalNumberOfMoles < 0.0) {
      throw new IllegalArgumentException("Total number of moles cannot be negative.");
    }
    if (numberOfComponents != 1) {
      throw new IllegalArgumentException("A pure-solid Helmholtz phase requires one component.");
    }
    this.beta = beta;
    numberOfMolesInPhase = beta * totalNumberOfMoles;
    setType(PhaseType.SOLID);
    setInitType(initType);
    this.numberOfComponents = numberOfComponents;
    for (int i = 0; i < numberOfComponents; i++) {
      componentArray[i].init(temperature, pressure, totalNumberOfMoles, beta, initType);
    }
    solidState = solidEquation.evaluate(temperature, pressure);
    setMolarVolume(solidState.getMolarVolume() * MOLAR_VOLUME_SI_TO_NEQSIM);
    Z = pressure * getMolarVolume() / (R * temperature);
    setType(PhaseType.SOLID);
  }

  /**
   * Get the last evaluated fundamental Helmholtz state.
   *
   * @return evaluated solid state
   * @throws IllegalStateException if the phase has not been initialized
   */
  public SolidHelmholtzState getSolidState() {
    if (solidState == null) {
      throw new IllegalStateException("Solid Helmholtz phase has not been initialized.");
    }
    return solidState;
  }

  /**
   * Get the fundamental equation represented by this phase.
   *
   * @return solid Helmholtz equation
   */
  public SolidHelmholtzEquation getSolidEquation() {
    return solidEquation;
  }

  /** @return total phase Helmholtz energy in J */
  @Override
  public double getHelmholtzEnergy() {
    return getSolidState().getHelmholtzEnergy() * numberOfMolesInPhase;
  }

  /** @return total phase internal energy in J */
  @Override
  public double getInternalEnergy() {
    return getSolidState().getInternalEnergy() * numberOfMolesInPhase;
  }

  /** @return total phase entropy in J/K */
  @Override
  public double getEntropy() {
    return getSolidState().getEntropy() * numberOfMolesInPhase;
  }

  /** @return total phase enthalpy in J */
  @Override
  public double getEnthalpy() {
    return getSolidState().getEnthalpy() * numberOfMolesInPhase;
  }

  /** @return total phase Gibbs energy in J */
  @Override
  public double getGibbsEnergy() {
    return getSolidState().getGibbsEnergy() * numberOfMolesInPhase;
  }

  /** @return total phase constant-pressure heat capacity in J/K */
  @Override
  public double getCp() {
    return getSolidState().getHeatCapacityCp() * numberOfMolesInPhase;
  }

  /** @return total phase constant-volume heat capacity in J/K */
  @Override
  public double getCv() {
    return getSolidState().getHeatCapacityCv() * numberOfMolesInPhase;
  }
}