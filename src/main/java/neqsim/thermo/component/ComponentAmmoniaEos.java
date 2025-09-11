package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseAmmoniaEos;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Component implementation for the ammonia reference equation of state. The formulation mirrors the
 * style of the Leachman hydrogen model where chemical potential derivatives are expressed through
 * reduced Helmholtz-energy derivatives returned from {@link neqsim.thermo.phase.PhaseAmmoniaEos}.
 *
 * @author esol
 */
public class ComponentAmmoniaEos extends ComponentEos {
  private static final long serialVersionUID = 1000L;

  /**
   * <p>
   * Constructor for ComponentAmmoniaEos.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param moles a double
   * @param molesInPhase a double
   * @param compIndex a int
   */
  public ComponentAmmoniaEos(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * Constructor for ComponentAmmoniaEos.
   * </p>
   *
   * @param number a int
   * @param TC a double
   * @param PC a double
   * @param M a double
   * @param a a double
   * @param moles a double
   */
  public ComponentAmmoniaEos(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentAmmoniaEos clone() {
    ComponentAmmoniaEos cloned = null;
    try {
      cloned = (ComponentAmmoniaEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
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
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffaT(double temperature) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double diffdiffaT(double temperature) {
    return 1.0;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseAmmoniaEos ph = (PhaseAmmoniaEos) phase;
    return ph.getAlphares()[0][0].val + ph.getAlphares()[0][1].val;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdN(int i, PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseAmmoniaEos ph = (PhaseAmmoniaEos) phase;
    double nTot = phase.getNumberOfMolesInPhase();
    return (2.0 * ph.getAlphares()[0][1].val + ph.getAlphares()[0][2].val) / nTot;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseAmmoniaEos ph = (PhaseAmmoniaEos) phase;
    return -(2.0 * ph.getAlphares()[0][1].val + ph.getAlphares()[0][2].val) / phase.getVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure) {
    PhaseAmmoniaEos ph = (PhaseAmmoniaEos) phase;
    return -(ph.getAlphares()[1][0].val + ph.getAlphares()[1][1].val) / temperature;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    double logFugacityCoefficient =
        dFdN(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure())
            - Math.log(phase.getZ());
    return Math.exp(logFugacityCoefficient);
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
    double hres = ((PhaseAmmoniaEos) phase).getHresTP();
    double temperature = phase.getTemperature();
    double n = phase.getNumberOfMolesInPhase();
    dfugdt = -hres / (n * ThermodynamicConstantsInterface.R * temperature * temperature);
    return dfugdt;
  }

  /** {@inheritDoc} */
  @Override
  public double[] logfugcoefdN(PhaseInterface phase) {
    int numberOfComponents = phase.getNumberOfComponents();
    double nTot = phase.getNumberOfMolesInPhase();
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();

    double[] residual = new double[numberOfComponents];
    double sum = 0.0;

    for (int i = 0; i < numberOfComponents; i++) {
      double ideal = -1.0 / nTot;
      if (getComponentNumber() == i) {
        double n = getNumberOfMolesInPhase();
        if (n > 0.0) {
          ideal += 1.0 / n;
        }
      }
      double total = dFdNdN(i, phase, numberOfComponents, temperature, pressure);
      residual[i] = total - ideal;
      double ni = phase.getComponent(i).getNumberOfMolesInPhase();
      sum += ni * residual[i];
      dfugdn[i] = ideal;
    }

    double correction = sum / nTot;
    for (int i = 0; i < numberOfComponents; i++) {
      dfugdn[i] += residual[i] - correction;
      dfugdx[i] = dfugdn[i] * nTot;
    }
    return dfugdn;
  }
}

