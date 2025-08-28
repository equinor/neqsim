package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentGERG2008 class.
 * </p>
 *
 * @author victorigi
 * @version $Id: $Id
 */
public class ComponentGERG2008Eos extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentGERG2008.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */

  // --- DISCLAIMER BEGIN ---
  // This class is not yet done
  // Some of the properties releated to the helmholtz energy and its derivatives
  // are not yet implemented
  // --- DISCLAIMER END ---
  public ComponentGERG2008Eos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Constructor for ComponentGERG2008.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentGERG2008Eos(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentGERG2008Eos clone() {
    ComponentGERG2008Eos clonedComponent = null;
    try {
      clonedComponent = (ComponentGERG2008Eos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void Finit(PhaseInterface phase, double T, double p, double totalNumberOfMoles,
      double beta, int numberOfComponents, int initType) {
    super.Finit(phase, T, p, totalNumberOfMoles, beta, numberOfComponents, initType);

    if (initType == 3) {
      double phi = fugcoef(phase);
      phase.getComponent(getComponentNumber()).setFugacityCoefficient(phi);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double alpha(double temperature) {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseGERG2008Eos ph = (PhaseGERG2008Eos) phase;
    double logXi = Math.log(getx());
    double ideal = ph.getAlpha0() != null ? ph.getAlpha0()[0].val : 0.0;
    double residual = ph.getAlphaRes() != null ? ph.getAlphaRes()[0][0].val : 0.0;
    return logXi + ideal + residual;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    double term = 0.0;
    if (getComponentNumber() == i) {
      double moles = getNumberOfMolesInPhase();
      term = 1.0 / moles;
    }
    return term;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseGERG2008Eos ph = (PhaseGERG2008Eos) phase;
    double alphar = ph.getAlphaRes() != null ? ph.getAlphaRes()[0][1].val : 0.0;
    return -(1.0 + alphar) / phase.getVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseGERG2008Eos ph = (PhaseGERG2008Eos) phase;
    double a0T = ph.getAlpha0() != null ? ph.getAlpha0()[1].val : 0.0;
    double arT = ph.getAlphaRes() != null ? ph.getAlphaRes()[1][0].val : 0.0;
    return -(a0T + arT) / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    double logFugacityCoefficient =
        dFdN(phase, phase.getNumberOfComponents(), temperature, pressure) - Math.log(phase.getZ());
    double fugacityCoefficient = Math.exp(logFugacityCoefficient);
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdP(PhaseInterface phase) {
    double Z = phase.getZ();
    double pressure = phase.getPressure();
    dfugdp = (Z - 1.0) / pressure;
    return dfugdp;
  }

  /** {@inheritDoc} */
  @Override
  public double logfugcoefdT(PhaseInterface phase) {
    double hres = phase.getHresTP();
    double temperature = phase.getTemperature();
    double n = phase.getNumberOfMolesInPhase();
    dfugdt = -hres / (n * ThermodynamicConstantsInterface.R * temperature * temperature);
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    double totalMoles = phase.getNumberOfMolesInPhase();
    int numberOfComponents = phase.getNumberOfComponents();
    for (int j = 0; j < numberOfComponents; j++) {
      double val = -1.0 / totalMoles;
      if (getComponentNumber() == j) {
        double moles = getNumberOfMolesInPhase();
        val += 1.0 / moles;
      }
      dfugdn[j] = val;
      dfugdx[j] = val * totalMoles;
    }
    return dfugdn;
  }
}
