/*
 * ComponentGEUniquacmodifiedHV.java
 *
 * Created on 18. juli 2000, 20:24
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Abstract class ComponentGEUniquacmodifiedHV
 *
 * @author Even Solbraa
 */
public abstract class ComponentGEUniquacmodifiedHV extends ComponentGEUniquac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentGEUniquacmodifiedHV.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGEUniquacmodifiedHV(String name, double moles, double molesInPhase,
      int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();
    // PhaseGEInterface GEPhase = phaseny.getGEphase();

    return 1; // super.getGamma(GEPhase, numberOfComponents, temperature, pressure, pt);
  }
}
