package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * Phase implementation for the Pitzer activity coefficient model.
 *
 * @author esol
 */
public class PhasePitzer extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(PhasePitzer.class);

  private double[][] beta0;
  private double[][] beta1;
  private double[][] cphi;

  /** T-dependent coefficients for beta0: beta0(T) = beta0_25 + beta0_T1/T + beta0_T2*T. */
  private double[][] beta0T1;
  private double[][] beta0T2;
  /** T-dependent coefficients for beta1. */
  private double[][] beta1T1;
  private double[][] beta1T2;
  /** T-dependent coefficients for Cphi. */
  private double[][] cphiT1;
  private double[][] cphiT2;
  /** Whether parameters have been loaded from database. */
  private boolean parametersLoaded = false;

  /** Constructor for PhasePitzer. */
  public PhasePitzer() {
    super();
    setPhysicalPropertyModel(PhysicalPropertyModel.SALT_WATER);
    int max = componentArray.length;
    beta0 = new double[max][max];
    beta1 = new double[max][max];
    cphi = new double[max][max];
    beta0T1 = new double[max][max];
    beta0T2 = new double[max][max];
    beta1T1 = new double[max][max];
    beta1T2 = new double[max][max];
    cphiT1 = new double[max][max];
    cphiT2 = new double[max][max];
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

  /**
   * Loads Pitzer binary parameters from the PitzerParameters database table.
   *
   * <p>
   * Matches ion names to components present in this phase and sets beta0, beta1, Cphi and their
   * temperature-dependent coefficients.
   * </p>
   */
  public void loadParametersFromDatabase() {
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM pitzerparameters")) {
      while (dataSet.next()) {
        String ion1Name = dataSet.getString("ion1").trim();
        String ion2Name = dataSet.getString("ion2").trim();

        int idx1 = -1;
        int idx2 = -1;
        for (int k = 0; k < numberOfComponents; k++) {
          String compName = getComponent(k).getComponentName();
          if (compName.equals(ion1Name)) {
            idx1 = k;
          }
          if (compName.equals(ion2Name)) {
            idx2 = k;
          }
        }
        if (idx1 < 0 || idx2 < 0) {
          continue;
        }

        double b0 = dataSet.getDouble("beta0_25");
        double b1 = dataSet.getDouble("beta1_25");
        double cp = dataSet.getDouble("Cphi_25");
        setBinaryParameters(idx1, idx2, b0, b1, cp);

        double b0t1 = dataSet.getDouble("beta0_T1");
        double b0t2 = dataSet.getDouble("beta0_T2");
        double b1t1 = dataSet.getDouble("beta1_T1");
        double b1t2 = dataSet.getDouble("beta1_T2");
        double ct1 = dataSet.getDouble("Cphi_T1");
        double ct2 = dataSet.getDouble("Cphi_T2");

        beta0T1[idx1][idx2] = b0t1;
        beta0T1[idx2][idx1] = b0t1;
        beta0T2[idx1][idx2] = b0t2;
        beta0T2[idx2][idx1] = b0t2;
        beta1T1[idx1][idx2] = b1t1;
        beta1T1[idx2][idx1] = b1t1;
        beta1T2[idx1][idx2] = b1t2;
        beta1T2[idx2][idx1] = b1t2;
        cphiT1[idx1][idx2] = ct1;
        cphiT1[idx2][idx1] = ct1;
        cphiT2[idx1][idx2] = ct2;
        cphiT2[idx2][idx1] = ct2;
      }
      parametersLoaded = true;
    } catch (Exception ex) {
      logger.error("Failed to load Pitzer parameters from database", ex);
    }
  }

  /**
   * Get T-dependent beta0 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return beta0 at temperature T
   */
  public double getBeta0ij(int i, int j, double TK) {
    double b0_25 = beta0[i][j];
    double t1 = beta0T1[i][j];
    double t2 = beta0T2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return b0_25;
    }
    // beta0(T) = beta0_25 + t1*(1/T - 1/298.15) + t2*(T - 298.15)
    return b0_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * (TK - 298.15);
  }

  /**
   * Get T-dependent beta1 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return beta1 at temperature T
   */
  public double getBeta1ij(int i, int j, double TK) {
    double b1_25 = beta1[i][j];
    double t1 = beta1T1[i][j];
    double t2 = beta1T2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return b1_25;
    }
    return b1_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * (TK - 298.15);
  }

  /**
   * Get T-dependent Cphi parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param TK temperature in Kelvin
   * @return Cphi at temperature T
   */
  public double getCphiij(int i, int j, double TK) {
    double c_25 = cphi[i][j];
    double t1 = cphiT1[i][j];
    double t2 = cphiT2[i][j];
    if (Math.abs(t1) < 1e-20 && Math.abs(t2) < 1e-20) {
      return c_25;
    }
    return c_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * (TK - 298.15);
  }

  /**
   * Returns whether parameters have been loaded from database.
   *
   * @return true if loaded
   */
  public boolean isParametersLoaded() {
    return parametersLoaded;
  }

  /**
   * Get beta0 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return beta0 parameter for components i and j
   */
  public double getBeta0ij(int i, int j) {
    return beta0[i][j];
  }

  /**
   * Get beta1 parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return beta1 parameter for components i and j
   */
  public double getBeta1ij(int i, int j) {
    return beta1[i][j];
  }

  /**
   * Get Cphi parameter.
   *
   * @param i component index i
   * @param j component index j
   * @return Cphi parameter for components i and j
   */
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
