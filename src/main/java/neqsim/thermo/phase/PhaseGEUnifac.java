package neqsim.thermo.phase;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.atomelement.UNIFACgroup;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUniquac;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhaseGEUnifac class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUnifac extends PhaseGEUniquac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseGEUnifac.class);

  double[][] aij = new double[1][1];
  double[][] bij = new double[1][1];
  double[][] cij = new double[1][1];
  boolean checkedGroups = false;

  /**
   * <p>
   * Constructor for PhaseGEUnifac.
   * </p>
   */
  public PhaseGEUnifac() {
    componentArray = new ComponentGEUnifac[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUnifac.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGEUnifac(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGEUnifac(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGEUnifac(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    if (!checkedGroups) {
      checkGroups();
    }
    logger.info("checking unifac groups...");
    calcaij();
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    // if(type==0) calcaij();
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType == 0) {
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
    }
  }

  /**
   * <p>
   * calcaij.
   * </p>
   */
  public void calcaij() {
    aij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];
    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
        try (neqsim.util.database.NeqSimDataBase database =
            new neqsim.util.database.NeqSimDataBase()) {
          java.sql.ResultSet dataSet = null;
          try {
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparam WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
            dataSet.getClob("MainGroup");
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            dataSet.close();
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparam WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
          }

          aij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          if (Math.abs(aij[i][j]) < 1e-6) {
            logger
                .info(" i " + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup()
                    + " j " + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup()
                    + "  aij " + aij[i][j]);
          }
          dataSet.close();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
      }
    }
    logger.info("finished finding interaction coefficient...A");
  }

  /**
   * <p>
   * checkGroups.
   * </p>
   */
  public void checkGroups() {
    ArrayList<neqsim.thermo.atomelement.UNIFACgroup> unifacGroups = new ArrayList<UNIFACgroup>();

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < ((ComponentGEUnifac) getComponent(i)).getNumberOfUNIFACgroups(); j++) {
        if (!unifacGroups.contains(((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j))) {
          unifacGroups.add(((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j));
        } else
          ; // System.out.println("no");
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < unifacGroups.size(); j++) {
        if (!((ComponentGEUnifac) getComponent(i)).getUnifacGroups2()
            .contains(unifacGroups.get(j))) {
          ((ComponentGEUnifac) getComponent(i)).addUNIFACgroup(unifacGroups.get(j).getSubGroup(),
              0);
        }
      }
    }

    for (int i = 0; i < numberOfComponents; i++) {
      neqsim.thermo.atomelement.UNIFACgroup[] array =
          ((ComponentGEUnifac) getComponent(i)).getUnifacGroups();
      java.util.Arrays.sort(array);
      ArrayList<UNIFACgroup> phaseList = new ArrayList<UNIFACgroup>(0);
      phaseList.addAll(Arrays.asList(array));
      ((ComponentGEUnifac) getComponent(i)).setUnifacGroups(phaseList);
    }

    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < ((ComponentGEUnifac) getComponent(i)).getNumberOfUNIFACgroups(); j++) {
        ((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j).setGroupIndex(j);
        // System.out.println("i " + i + " " +
        // ((ComponentGEUnifac)getComponent(i)).getUnifacGroup(j).getSubGroup());
      }
    }
    checkedGroups = true;
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy() {
    return getExcessGibbsEnergy(this, numberOfComponents, temperature, pressure, pt);
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math.log(((ComponentGEUniquac) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /** {@inheritDoc} */
  @Override
  public double getGibbsEnergy() {
    double val = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      val +=
          getComponent(i).getNumberOfMolesInPhase() * (getComponent(i).getLogFugacityCoefficient());
    }
    return R * temperature * numberOfMolesInPhase * (val + Math.log(pressure));
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
   * Setter for the field <code>aij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param val a double
   */
  public void setAij(int i, int j, double val) {
    aij[i][j] = val;
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

  /**
   * <p>
   * Setter for the field <code>bij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param val a double
   */
  public void setBij(int i, int j, double val) {
    bij[i][j] = val;
  }

  /**
   * <p>
   * Getter for the field <code>cij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getCij(int i, int j) {
    return cij[i][j];
  }

  /**
   * <p>
   * Setter for the field <code>cij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @param val a double
   */
  public void setCij(int i, int j, double val) {
    cij[i][j] = val;
  }
}
