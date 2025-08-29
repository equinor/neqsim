package neqsim.thermo.phase;

import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * Phase implementation for the Pitzer activity coefficient model.
 */
public class PhasePitzer extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double[][] beta0;
  private double[][] beta1;
  private double[][] cphi;

  /** Constructor for PhasePitzer. */
  public PhasePitzer() {
    super();
    setPhysicalPropertyModel(PhysicalPropertyModel.SALT_WATER);
    int max = componentArray.length;
    beta0 = new double[max][max];
    beta1 = new double[max][max];
    cphi = new double[max][max];
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGePitzer(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math.log(((ComponentGePitzer) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    // Not used in Pitzer model
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    // Not used in Pitzer model
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    // Not used in Pitzer model
  }

  /**
   * Set binary Pitzer parameters.
   *
   * @param i component i
   * @param j component j
   * @param b0 beta0 parameter
   * @param b1 beta1 parameter
   * @param c cPhi parameter
   */
  public void setBinaryParameters(int i, int j, double b0, double b1, double c) {
    beta0[i][j] = b0;
    beta0[j][i] = b0;
    beta1[i][j] = b1;
    beta1[j][i] = b1;
    cphi[i][j] = c;
    cphi[j][i] = c;
  }

  /** Get beta0 parameter. */
  public double getBeta0ij(int i, int j) {
    return beta0[i][j];
  }

  /** Get beta1 parameter. */
  public double getBeta1ij(int i, int j) {
    return beta1[i][j];
  }

  /** Get Cphi parameter. */
  public double getCphiij(int i, int j) {
    return cphi[i][j];
  }

  /**
   * Calculate ionic strength.
   *
   * @return ionic strength
   */
  public double getIonicStrength() {
    double ionStrength = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ionStrength +=
          getComponent(i).getMolality(this) * Math.pow(getComponent(i).getIonicCharge(), 2.0);
    }
    return 0.5 * ionStrength;
  }

  /**
   * Get mass of solvent in kilograms.
   *
   * @return solvent mass
   */
  public double getSolventWeight() {
    double moles = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getComponentName().equals("water")) {
        moles += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
    }
    return moles;
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    return getMass() / getPhysicalProperties().getDensity() / numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresTP() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getHresdP() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTV() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getSresTP() {
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the excess heat capacity via finite-difference temperature derivatives of the
   * activity coefficients. In the current implementation the Pitzer binary parameters are
   * temperature independent, so the residual contribution evaluates to zero.
   * </p>
   */
  @Override
  public double getCpres() {
    double T = temperature;
    double P = pressure;
    int n = numberOfComponents;
    double dT = 1e-2;
    double sum1 = 0.0;
    double sum2 = 0.0;

    for (int i = 0; i < n; i++) {
      ComponentGePitzer comp = (ComponentGePitzer) componentArray[i];
      double ln0 = Math.log(comp.getGamma(this, n, T, P, getType()));
      double lnPlus = Math.log(comp.getGamma(this, n, T + dT, P, getType()));
      double lnMinus = Math.log(comp.getGamma(this, n, T - dT, P, getType()));
      double d1 = (lnPlus - lnMinus) / (2.0 * dT);
      double d2 = (lnPlus - 2.0 * ln0 + lnMinus) / (dT * dT);
      sum1 += comp.getx() * d1;
      sum2 += comp.getx() * d2;
      comp.getGamma(this, n, T, P, getType());
    }

    double cpex = -R * (T * T * sum2 + 2.0 * T * sum1);
    return cpex * numberOfMolesInPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getCvres() {
    return getCpres();
  }

  /** {@inheritDoc} */
  @Override
  public double getCp() {
    // Calculate the ideal heat-capacity contribution on a molar basis using
    // the pure-component liquid heat capacities, then scale by the phase mole
    // count and add the residual term. This mirrors the default Phase
    // implementation without multiplying by the phase moles twice.
    double cpIdeal = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      cpIdeal += componentArray[i].getx() * componentArray[i].getPureComponentCpLiquid(temperature);
    }
    return cpIdeal * numberOfMolesInPhase + getCpres();
  }

  /** {@inheritDoc} */
  @Override
  public double getCv() {
    return getCp();
  }
}
