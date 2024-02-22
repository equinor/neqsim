package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentDesmukhMather;
import neqsim.thermo.component.ComponentGEInterface;

/**
 * <p>
 * PhaseDesmukhMather class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseDesmukhMather extends PhaseGE {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhaseDesmukhMather.class);

  double GE = 0.0;
  double[][] aij;
  double[][] bij;

  /**
   * <p>
   * Constructor for PhaseDesmukhMather.
   * </p>
   */
  public PhaseDesmukhMather() {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] = new ComponentDesmukhMather(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType phase,
      double beta) {
    super.init(totalNumberOfMoles, numberOfComponents, initType, phase, beta);
    if (initType != 0) {
      setType(phase);
      // phaseTypeName = phase == 0 ? "liquid" : "gas";
    }
    setMolarVolume(0.980e-3 * getMolarMass() * 1e5);
    Z = pressure * getMolarVolume() / (R * temperature);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    this.aij = new double[numberOfComponents][numberOfComponents];
    this.bij = new double[numberOfComponents][numberOfComponents];
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      for (int k = 0; k < getNumberOfComponents(); k++) {
        String component_name = getComponents()[k].getComponentName();

        for (int l = k; l < getNumberOfComponents(); l++) {
          if (k == l) {
            if (getComponents()[l].getComponentName().equals("MDEA")
                && getComponents()[k].getComponentName().equals("MDEA")) {
              aij[k][l] = -0.0828487;
              this.aij[l][k] = this.aij[k][l];
            }
          } else {
            try (java.sql.ResultSet dataSet =
                database.getResultSet("SELECT * FROM inter WHERE (comp1='" + component_name
                    + "' AND comp2='" + getComponents()[l].getComponentName() + "') OR (comp1='"
                    + getComponents()[l].getComponentName() + "' AND comp2='" + component_name
                    + "')");) {
              dataSet.next();

              // if
              // (dataSet.getString("comp1").trim().equals(getComponents()[l].getComponentName())) {
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

  /**
   * <p>
   * Setter for the field <code>aij</code>.
   * </p>
   *
   * @param alpha an array of {@link double} objects
   */
  public void setAij(double[][] alpha) {
    for (int i = 0; i < alpha.length; i++) {
      System.arraycopy(aij[i], 0, this.aij[i], 0, alpha[0].length);
    }
  }

  /**
   * <p>
   * Setter for the field <code>bij</code>.
   * </p>
   *
   * @param Dij an array of {@link double} objects
   */
  public void setBij(double[][] Dij) {
    for (int i = 0; i < Dij.length; i++) {
      System.arraycopy(bij[i], 0, this.bij[i], 0, Dij[0].length);
    }
  }

  /**
   * <p>
   * getBetaDesMatij.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getBetaDesMatij(int i, int j) {
    return aij[i][j] + bij[i][j] * temperature;
  }

  /**
   * <p>
   * Getter for the field <code>aij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getAij(int i, int j) {
    return aij[i][j];
  }

  /**
   * <p>
   * Getter for the field <code>bij</code>.
   * </p>
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
    // pressure, phaseType);
    return GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, int phasetype) {
    GE = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponents()[i].getx() * Math.log(((ComponentDesmukhMather) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, phasetype));
    }
    // System.out.println("ge " + GE);
    return R * temperature * numberOfMolesInPhase * GE; // phase.getNumberOfMolesInPhase()*
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
   * <p>
   * getIonicStrength.
   * </p>
   *
   * @return a double
   */
  public double getIonicStrength() {
    double ionStrength = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      ionStrength +=
          getComponent(i).getMolality(this) * Math.pow(getComponent(i).getIonicCharge(), 2.0);
      // getComponent(i).getMolarity(this)*Math.pow(getComponent(i).getIonicCharge(),2.0);
    }
    return 0.5 * ionStrength;
  }

  /**
   * <p>
   * getSolventWeight.
   * </p>
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
   * <p>
   * getSolventDensity.
   * </p>
   *
   * @return a double
   */
  public double getSolventDensity() {
    return 1020.0;
  }

  /**
   * <p>
   * getSolventMolarMass.
   * </p>
   *
   * @return a double
   */
  public double getSolventMolarMass() {
    double molesMass = 0.0;
    double moles = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      if (getComponent(i).getReferenceStateType().equals("solvent")) {
        molesMass += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
        moles = getComponent(i).getNumberOfMolesInPhase();
      }
    }
    return molesMass / moles;
  }
}
