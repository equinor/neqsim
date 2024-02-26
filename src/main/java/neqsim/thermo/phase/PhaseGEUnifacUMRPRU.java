package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacUMRPRU;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 * <p>
 * PhaseGEUnifacUMRPRU class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUnifacUMRPRU extends PhaseGEUnifac {
  private static final long serialVersionUID = 1000;
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
    super();
    componentArray =
        new ComponentGEUnifacUMRPRU[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUnifacUMRPRU.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of {@link double} objects
   * @param Dij an array of {@link double} objects
   * @param mixRule an array of {@link String} objects
   * @param intparam an array of {@link double} objects
   */
  public PhaseGEUnifacUMRPRU(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGEUnifacUMRPRU(phase.getComponents()[i].getName(),
          phase.getComponents()[i].getNumberOfmoles(),
          phase.getComponents()[i].getNumberOfMolesInPhase(),
          phase.getComponents()[i].getComponentNumber());
      componentArray[i].setAttractiveTerm(phase.getComponents()[i].getAttractiveTermNumber());
    }
    this.setMixingRule(2);
  }

  /**
   * Calculate common temp.
   *
   * @param phase a PhaseInterface
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param phaseType a int
   */
  public void calcCommontemp(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType phaseType) {
    FCommontemp = 0;
    VCommontemp = 0;
    ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();

    for (int j = 0; j < numberOfComponents; j++) {
      FCommontemp += (compArray[j].getQ() * compArray[j].getx());
      VCommontemp += compArray[j].getx() * compArray[j].getR();
    }
  }

  public double getVCommontemp() {
    return VCommontemp;
  }

  public double getFCommontemp() {
    return FCommontemp;
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] = new ComponentGEUnifacUMRPRU(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    if (!checkedGroups) {
      checkGroups();
    }
    calcbij();
    calccij();
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType phaseType) {
    double GE = 0.0;
    calcCommontemp(phase, numberOfComponents, temperature, pressure, phaseType);
    // ((ComponentGEUnifacUMRPRU) phase.getComponents()[0]).commonInit(phase, numberOfComponents,
    // temperature, pressure, phaseType);

    initQmix();
    if (getInitType() > 2) {
      initQmixdN();
    }
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponents()[i].getx() * Math.log(((ComponentGEUniquac) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, phaseType));
    }
    return R * phase.getTemperature() * GE * phase.getNumberOfMolesInPhase();
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
   * @param name a {@link String} object
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
   * @param name a {@link String} object
   * @return an array of {@link double} objects
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
