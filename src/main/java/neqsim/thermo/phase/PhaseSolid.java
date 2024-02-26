/*
 * PhaseSolid.java
 *
 * Created on 18. august 2001, 12:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentSolid;

/**
 * <p>
 * Abstract PhaseSolid class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class PhaseSolid extends PhaseSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseSolid.
   * </p>
   */
  public PhaseSolid() {
    super();
    setType(PhaseType.SOLID);
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSolid clone() {
    PhaseSolid clonedPhase = null;
    try {
      clonedPhase = (PhaseSolid) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int type, PhaseType phaseType,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, type, phaseType, beta);
    setType(PhaseType.SOLID);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] = new ComponentSolid(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getEnthalpy() {
    double fusionHeat = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      fusionHeat += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getHeatOfFusion();
    }
    return super.getEnthalpy() - fusionHeat;
  }

  /**
   * <p>
   * setSolidRefFluidPhase.
   * </p>
   *
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface refPhase) {
    for (int i = 0; i < numberOfComponents; i++) {
      ((ComponentSolid) componentArray[i]).setSolidRefFluidPhase(refPhase);
    }
  }

  /**
   * method to get density of a phase note: at the moment return density of water (997 kg/m3).
   *
   * @return density with unit kg/m3
   */
  public double getDensityTemp() {
    double density = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      density += getWtFrac(i)
          * ((ComponentSolid) componentArray[i]).getPureComponentSolidDensity(getTemperature())
          * 1000.0;
    }
    molarVolume = density / getMolarMass() * 1e-5;
    return density;
  }
}
