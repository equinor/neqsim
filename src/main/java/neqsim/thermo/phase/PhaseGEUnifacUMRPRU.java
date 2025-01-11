package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacUMRPRU;
import neqsim.thermo.component.ComponentGEUniquac;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhaseGEUnifacUMRPRU class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUnifacUMRPRU extends PhaseGEUnifac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseGEUnifacUMRPRU.class);

  double[] Qmix = null;
  double[][] QmixdN = null;
  String[] gropuNames = null;
  double VCommontemp = 0.0;
  double FCommontemp = 0.0;

  /**
   * <p>
   * Constructor for PhaseGEUnifacUMRPRU.
   * </p>
   */
  public PhaseGEUnifacUMRPRU() {
    componentArray =
        new ComponentGEUnifacUMRPRU[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUnifacUMRPRU.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGEUnifacUMRPRU(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGEUnifacUMRPRU(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
      componentArray[i].setAttractiveTerm(phase.getComponent(i).getAttractiveTermNumber());
    }
    this.setMixingRule(EosMixingRuleType.CLASSIC);
  }

  /**
   * Calculate common temp.
   *
   * @param phase a PhaseInterface
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   */
  public void calcCommontemp(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    FCommontemp = 0;
    VCommontemp = 0;
    ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();

    for (int j = 0; j < numberOfComponents; j++) {
      FCommontemp += (compArray[j].getQ() * compArray[j].getx());
      VCommontemp += compArray[j].getx() * compArray[j].getR();
    }
  }

  /**
   * <p>
   * getVCommontemp.
   * </p>
   *
   * @return a double
   */
  public double getVCommontemp() {
    return VCommontemp;
  }

  /**
   * <p>
   * getFCommontemp.
   * </p>
   *
   * @return a double
   */
  public double getFCommontemp() {
    return FCommontemp;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGEUnifacUMRPRU(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(MixingRuleTypeInterface mr) {
    super.setMixingRule(mr);
    if (!checkedGroups) {
      checkGroups();
    }
    calcbij();
    calccij();
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    calcCommontemp(phase, numberOfComponents, temperature, pressure, pt);
    // ((ComponentGEUnifacUMRPRU) phase.getComponent(0)).commonInit(phase, numberOfComponents,
    // temperature, pressure, pt);

    initQmix();
    if (getInitType() > 2) {
      initQmixdN();
    }
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math.log(((ComponentGEUniquac) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /**
   * <p>
   * initQmix.
   * </p>
   */
  public void initQmix() {
    int numberOfGroups = ((ComponentGEUnifac) componentArray[0]).getUnifacGroups().length;
    Qmix = new double[numberOfGroups];
    gropuNames = new String[numberOfGroups];
    for (int i = 0; i < numberOfGroups; i++) {
      gropuNames[i] = ((ComponentGEUnifac) componentArray[0]).getUnifacGroup(i).getGroupName();
      Qmix[i] = ((ComponentGEUnifac) componentArray[0]).getUnifacGroup(i).calcQMix(this);
    }
  }

  /**
   * <p>
   * initQmixdN.
   * </p>
   */
  public void initQmixdN() {
    int numberOfGroups = ((ComponentGEUnifac) componentArray[0]).getUnifacGroups().length;
    QmixdN = new double[numberOfGroups][componentArray.length];
    gropuNames = new String[numberOfGroups];
    for (int i = 0; i < numberOfGroups; i++) {
      gropuNames[i] = ((ComponentGEUnifac) componentArray[0]).getUnifacGroup(i).getGroupName();
      QmixdN[i] = ((ComponentGEUnifac) componentArray[0]).getUnifacGroup(i).calcQMixdN(this);
    }
  }

  /**
   * <p>
   * getQmix.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a double
   */
  public double getQmix(String name) {
    // int test = ((ComponentGEUnifac) componentArray[0]).getUnifacGroups().length;
    for (int i = 0; i < gropuNames.length; i++) {
      if (name.equals(gropuNames[i])) {
        return Qmix[i];
      }
    }
    return 0.0;
  }

  /**
   * <p>
   * getQmixdN.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] getQmixdN(String name) {
    // int test = ((ComponentGEUnifac) componentArray[0]).getUnifacGroups().length;
    for (int i = 0; i < gropuNames.length; i++) {
      if (name.equals(gropuNames[i])) {
        return QmixdN[i];
      }
    }
    return QmixdN[0];
  }

  /** {@inheritDoc} */
  @Override
  public void calcaij() {
    java.sql.ResultSet dataSet = null;

    aij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        if (getPhase().getComponent(0).getAttractiveTermNumber() == 13) {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparama_umrmc WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        } else {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparama_umr WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        }
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          aij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      } finally {
        try {
          if (dataSet != null) {
            dataSet.close();
          }
        } catch (Exception ex) {
          logger.error("err closing dataSet...", ex);
        }
      }
    }
    // System.out.println("finished finding interaction coefficient...C_UMR");
  }

  /**
   * <p>
   * calcbij.
   * </p>
   */
  public void calcbij() {
    java.sql.ResultSet dataSet = null;

    bij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        if (getPhase().getComponent(0).getAttractiveTermNumber() == 13) {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparamb_umrmc WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        } else {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparamb_umr WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        }
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          bij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    // System.out.println("finished finding interaction coefficient...C_UMR");
  }

  /**
   * <p>
   * calccij.
   * </p>
   */
  public void calccij() {
    java.sql.ResultSet dataSet = null;

    cij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        if (getPhase().getComponent(0).getAttractiveTermNumber() == 13) {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparamc_umrmc WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        } else {
          dataSet = database.getResultSet(("SELECT * FROM unifacinterparamc_umr WHERE MainGroup="
              + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        }
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          cij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    // System.out.println("finished finding interaction coefficient...C_UMR");
  }
}
