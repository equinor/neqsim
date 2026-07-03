package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentDesmukhMather;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.util.exception.IsNaNException;
import neqsim.util.exception.TooManyIterationsException;

/**
 * PhaseDesmukhMather class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseDesmukhMather extends PhaseGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseDesmukhMather.class);

  double GE = 0.0;
  double[][] aij;
  double[][] bij;

  /**
   * Constructor for PhaseDesmukhMather.
   */
  public PhaseDesmukhMather() {
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentDesmukhMather(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType != 0) {
      setType(pt);
    }
    setMolarVolume(getMass() / getDensity() / numberOfMolesInPhase * 1e5);
    Z = pressure * getMolarVolume() / (R * temperature);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    this.aij = new double[numberOfComponents][numberOfComponents];
    this.bij = new double[numberOfComponents][numberOfComponents];
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      for (int k = 0; k < getNumberOfComponents(); k++) {
        String component_name = getComponent(k).getComponentName();

        for (int l = k; l < getNumberOfComponents(); l++) {
          if (k == l) {
            if (getComponent(l).getComponentName().equals("MDEA")
                && getComponent(k).getComponentName().equals("MDEA")) {
              aij[k][l] = -0.0828487;
              this.aij[l][k] = this.aij[k][l];
            }
          } else {
            try (java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM inter WHERE (comp1='"
                + component_name + "' AND comp2='" + getComponent(l).getComponentName() + "') OR (comp1='"
                + getComponent(l).getComponentName() + "' AND comp2='" + component_name + "')");) {
              dataSet.next();

              // if
              // (dataSet.getString("comp1").trim().equals(getComponent(l).getComponentName()))
              // {
              // templ = k;
              // tempk = l;
              // }
              this.aij[k][l] = Double.parseDouble(dataSet.getString("aijDesMath"));
              this.bij[k][l] = Double.parseDouble(dataSet.getString("bijDesMath"));
              this.aij[l][k] = this.aij[k][l];
              this.bij[l][k] = this.bij[k][l];
            } catch (Exception ex) {
              logger.info("comp names " + component_name);
              logger.error(ex.getMessage(), ex);
            }
          }
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setAlpha(double[][] alpha) {
    throw new UnsupportedOperationException("Unimplemented method 'setAlpha'");
  }

  /**
   * Getter for the field <code>aij</code>.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getAij(int i, int j) {
    return aij[i][j];
  }

  /**
   * Setter for the field <code>aij</code>.
   *
   * @param alpha an array of type double
   */
  public void setAij(double[][] alpha) {
    for (int i = 0; i < alpha.length; i++) {
      System.arraycopy(alpha[i], 0, this.aij[i], 0, alpha[0].length);
    }
  }

  /**
   * Setter for the field <code>bij</code>.
   *
   * @param Bij an array of type double
   */
  public void setBij(double[][] Bij) {
    for (int i = 0; i < Bij.length; i++) {
      System.arraycopy(Bij[i], 0, this.bij[i], 0, Bij[0].length);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setDij(double[][] Dij) {
    throw new UnsupportedOperationException("Unimplemented method 'setDij'");
  }

  /** {@inheritDoc} */
  @Override
  public void setDijT(double[][] DijT) {
    throw new UnsupportedOperationException("Unimplemented method 'setDijT'");
  }

  /**
   * getBetaDesMatij.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBetaDesMatij(int i, int j) {
    return aij[i][j] + bij[i][j] * temperature;
  }

  /**
   * Getter for the field <code>bij</code>.
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBij(int i, int j) {
    return bij[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    // double GE = getExcessGibbsEnergy(this, numberOfComponents, temperature,
    // pressure, pt);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math.log(
          ((ComponentDesmukhMather) componentArray[i]).getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * temperature * numberOfMolesInPhase * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k, int p) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /** {@inheritDoc} */
  @Override
  public double getActivityCoefficient(int k) {
    return ((ComponentGEInterface) getComponent(k)).getGamma();
  }

  /**
   * getIonicStrength.
   *
   * @return a double
   */
  public double getIonicStrength() {
    double ionStrength = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ionStrength += getComponent(i).getMolality(this) * Math.pow(getComponent(i).getIonicCharge(), 2.0);
      // getComponent(i).getMolarity(this)*Math.pow(getComponent(i).getIonicCharge(),2.0);
    }
    return 0.5 * ionStrength;
  }

  /**
   * getSolventWeight.
   *
   * @return a double
   */
  public double getSolventWeight() {
    double moles = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getReferenceStateType().equals("solvent")) {
        moles += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      }
    }
    return moles;
  }

  /**
   * getSolventDensity.
   *
   * @return a double
   */
  public double getSolventDensity() {
    double solventMass = 0.0;
    double solventVolume = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getReferenceStateType().equals("solvent")) {
        double mass = getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
        double density = getComponent(i).getNormalLiquidDensity();
        if (!Double.isFinite(density) || density < 500.0) {
          density = getComponent(i).getComponentName().equals("water") ? 997.0 : 1020.0;
        }
        solventMass += mass;
        solventVolume += mass / density;
      }
    }
    return solventVolume > 0.0 ? solventMass / solventVolume : 1020.0;
  }

  /**
   * getSolventMolarMass.
   *
   * @return a double
   */
  public double getSolventMolarMass() {
    double molesMass = 0.0;
    double moles = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getReferenceStateType().equals("solvent")) {
        molesMass += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
        moles += getComponent(i).getNumberOfMolesInPhase();
      }
    }
    return moles > 0.0 ? molesMass / moles : getMolarMass();
  }

  /** {@inheritDoc} */
  @Override
  public double getDensity() {
    double mass = getMass();
    double volume = 0.0;
    double solventDensity = getSolventDensity();
    for (int i = 0; i < numberOfComponents; i++) {
      double componentMass = getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
      if (getComponent(i).isIsIon()) {
        volume += componentMass / solventDensity;
      } else {
        double density = getComponent(i).getNormalLiquidDensity();
        if (!Double.isFinite(density) || density < 500.0) {
          density = getComponent(i).getReferenceStateType().equals("solvent") ? solventDensity : 1000.0;
        }
        volume += componentMass / density;
      }
    }
    return volume > 0.0 ? mass / volume : solventDensity;
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws IsNaNException, TooManyIterationsException {
    return getMass() / getDensity() / numberOfMolesInPhase;
  }
}
