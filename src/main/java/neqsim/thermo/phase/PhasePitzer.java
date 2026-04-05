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
  /** Second virial coefficient for 2-2 electrolytes (Harvie &amp; Weare 1984). */
  private double[][] beta2;
  /** Cation-cation or anion-anion mixing parameter theta (Harvie &amp; Weare 1984). */
  private double[][] theta;
  /** Ternary mixing parameter psi (cation-cation-anion or anion-anion-cation). */
  private double[][][] psi;

  /** T-dependent coefficients for beta0: beta0(T) = beta0_25 + T1*(1/T-1/Tr) + T2*ln(T/Tr). */
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
    beta2 = new double[max][max];
    theta = new double[max][max];
    psi = new double[max][max][max];
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
    if (!parametersLoaded) {
      loadParametersFromDatabase();
    }
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
   * Set T-dependent coefficients for beta0 (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setBeta0T(int i, int j, double t1, double t2) {
    beta0T1[i][j] = t1;
    beta0T1[j][i] = t1;
    beta0T2[i][j] = t2;
    beta0T2[j][i] = t2;
  }

  /**
   * Set T-dependent coefficients for beta1 (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setBeta1T(int i, int j, double t1, double t2) {
    beta1T1[i][j] = t1;
    beta1T1[j][i] = t1;
    beta1T2[i][j] = t2;
    beta1T2[j][i] = t2;
  }

  /**
   * Set T-dependent coefficients for Cphi (Silvester-Pitzer form).
   *
   * @param i component index i
   * @param j component index j
   * @param t1 coefficient for (1/T - 1/Tr) term
   * @param t2 coefficient for ln(T/Tr) term
   */
  public void setCphiT(int i, int j, double t1, double t2) {
    cphiT1[i][j] = t1;
    cphiT1[j][i] = t1;
    cphiT2[i][j] = t2;
    cphiT2[j][i] = t2;
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

        // Load beta2 for 2-2 electrolytes
        try {
          double b2 = dataSet.getDouble("beta2_25");
          if (Math.abs(b2) > 1e-20) {
            beta2[idx1][idx2] = b2;
            beta2[idx2][idx1] = b2;
          }
        } catch (Exception ex2) {
          // Column may not exist in older databases
        }

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
      parametersLoaded = true; // prevent infinite retries
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
    // Silvester-Pitzer form: beta0(T) = beta0_25 + t1*(1/T - 1/Tr) + t2*ln(T/Tr)
    return b0_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
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
    return b1_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
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
    return c_25 + t1 * (1.0 / TK - 1.0 / 298.15) + t2 * Math.log(TK / 298.15);
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
   * Get beta2 parameter for 2-2 electrolytes.
   *
   * @param i component index i
   * @param j component index j
   * @return beta2 parameter
   */
  public double getBeta2ij(int i, int j) {
    return beta2[i][j];
  }

  /**
   * Set beta2 parameter for 2-2 electrolytes.
   *
   * @param i component index i
   * @param j component index j
   * @param value beta2 value
   */
  public void setBeta2(int i, int j, double value) {
    beta2[i][j] = value;
    beta2[j][i] = value;
  }

  /**
   * Get theta mixing parameter for same-sign ion pair.
   *
   * @param i component index i
   * @param j component index j
   * @return theta parameter
   */
  public double getThetaij(int i, int j) {
    return theta[i][j];
  }

  /**
   * Set theta mixing parameter for same-sign ion pair.
   *
   * @param i component index i
   * @param j component index j
   * @param value theta value
   */
  public void setTheta(int i, int j, double value) {
    theta[i][j] = value;
    theta[j][i] = value;
  }

  /**
   * Get psi ternary mixing parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param k component index k
   * @return psi parameter
   */
  public double getPsiijk(int i, int j, int k) {
    return psi[i][j][k];
  }

  /**
   * Set psi ternary mixing parameter.
   *
   * @param i component index i
   * @param j component index j
   * @param k component index k
   * @param value psi value
   */
  public void setPsi(int i, int j, int k, double value) {
    psi[i][j][k] = value;
    psi[j][i][k] = value;
    psi[i][k][j] = value;
    psi[j][k][i] = value;
    psi[k][i][j] = value;
    psi[k][j][i] = value;
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

  /**
   * {@inheritDoc}
   *
   * <p>
   * Computes brine density as a function of temperature, pressure, and salinity using the Rowe-Chou
   * (1970) correlation for NaCl-equivalent brine, extended with pressure correction. Fall-back is
   * pure water density from Kell (1975). Much more accurate than the inherited hard-coded 997
   * kg/m3.
   * </p>
   */
  @Override
  public double getDensity() {
    double TC = temperature - 273.15;
    if (TC < 0.0) {
      TC = 0.0;
    }
    if (TC > 300.0) {
      TC = 300.0;
    }

    // Pure water density (Kell 1975 polynomial, 0-150°C at ~1 atm)
    double rhoW;
    if (TC <= 100.0) {
      rhoW = 999.83 + 5.0948e-2 * TC - 7.5722e-3 * TC * TC + 3.8907e-5 * TC * TC * TC
          - 1.2e-7 * TC * TC * TC * TC;
    } else {
      // IAPWS approximate saturated-liquid density above 100°C
      double dT = TC - 100.0;
      rhoW = 958.0 - 1.08 * dT - 0.0028 * dT * dT;
    }
    if (rhoW < 700.0) {
      rhoW = 700.0;
    }

    // Calculate NaCl-equivalent salinity (total dissolved solids in kg/m³)
    // S = sum(m_ion * MW_ion) / (sum(m_ion * MW_ion) + 1 kg water) * 1e6 [ppm]
    double ionMassKg = 0.0;
    double waterMassKg = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (Math.abs(getComponent(i).getIonicCharge()) > 0.5) {
        ionMassKg += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
      if (getComponent(i).getComponentName().equals("water")) {
        waterMassKg += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
    }
    double totalMass = ionMassKg + waterMassKg;
    double S = (totalMass > 1e-20) ? ionMassKg / totalMass : 0.0; // mass fraction of salts

    // Brine density correction: rho_brine ≈ rho_water + S * (800 + 0.4*S*1e6)
    // Simplified Rowe-Chou type: rho_b = rhoW / (1 - S*(0.668 + 0.44*S + 1e-6*S*TC*TC))
    // This approximation gives ~1020 kg/m³ for 3 wt% NaCl at 25°C, ~1200 kg/m³ for 26 wt%
    double rhoBrine;
    if (S > 1e-10) {
      // McCain (1991) correlation adapted for NaCl brines
      rhoBrine = rhoW + 668.0 * S + 440.0 * S * S;
    } else {
      rhoBrine = rhoW;
    }

    // Pressure correction: approximate compressibility of brine
    // dp in bar from atmospheric; compressibility ~4.5e-5 /bar for water, less for brine
    double pressureBar = pressure;
    if (pressureBar > 1.013) {
      double kappa = 4.5e-5 / (1.0 + 3.0 * S); // brine is less compressible
      rhoBrine *= (1.0 + kappa * (pressureBar - 1.013));
    }

    return rhoBrine;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    return getMass() / getDensity() / numberOfMolesInPhase;
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
