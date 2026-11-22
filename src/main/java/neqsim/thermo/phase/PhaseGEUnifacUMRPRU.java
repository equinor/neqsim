package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacUMRPRU;
import neqsim.thermo.component.ComponentGEUniquac;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * PhaseGEUnifacUMRPRU class.
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

  /** True when the equation-of-state phase has a CPA association term, which uses its own interaction tables. */
  private boolean cpaInteractionParameters = false;

  /**
   * Constructor for PhaseGEUnifacUMRPRU.
   */
  public PhaseGEUnifacUMRPRU() {
    componentArray = new ComponentGEUnifacUMRPRU[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * Constructor for PhaseGEUnifacUMRPRU.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGEUnifacUMRPRU(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
      double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    cpaInteractionParameters = phase instanceof PhaseUMRCPA;
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      ComponentInterface source = phase.getComponent(i);
      String name = source.getComponentName();
      boolean pseudoComponent = source.isIsTBPfraction() || source.isIsPlusFraction() || name.contains("_PC");
      // A renamed cut still uses the pseudo-component group model, independent of its display name.
      String lookupName = pseudoComponent && !name.contains("_PC") ? name + "_PC" : name;
      ComponentGEUnifacUMRPRU component = new ComponentGEUnifacUMRPRU(lookupName, source.getNumberOfmoles(),
          source.getNumberOfMolesInPhase(), source.getComponentNumber());
      if (pseudoComponent) {
        component.setMolarMass(source.getMolarMass());
        component.initPCUNIFACGroups();
        component.setComponentName(name);
      }
      component.setAttractiveTerm(source.getAttractiveTermNumber());
      if (!pseudoComponent && !cpaInteractionParameters && isMcAttractiveTerm(source.getAttractiveTermNumber())) {
        component.applyGroupAssignmentOverride("unifaccompumrprumc");
      }
      componentArray[i] = component;
    }
    this.setMixingRule(EosMixingRuleType.CLASSIC);
  }

  /**
   * Returns whether an attractive term belongs to the Mathias-Copeman UMR-PRU variants that read the {@code _umrmc}
   * group-interaction tables and the {@code UNIFACcompUMRPRUMC} group-assignment overrides.
   *
   * @param attractiveTermNumber attractive term number of the EOS component
   * @return true for attractive terms 13 and 19
   */
  private static boolean isMcAttractiveTerm(int attractiveTermNumber) {
    return attractiveTermNumber == 13 || attractiveTermNumber == 19;
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
  public void calcCommontemp(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    FCommontemp = 0;
    VCommontemp = 0;
    ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();

    for (int j = 0; j < numberOfComponents; j++) {
      FCommontemp += (compArray[j].getQ() * compArray[j].getx());
      VCommontemp += compArray[j].getx() * compArray[j].getR();
    }
  }

  /**
   * getVCommontemp.
   *
   * @return a double
   */
  public double getVCommontemp() {
    return VCommontemp;
  }

  /**
   * getFCommontemp.
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
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
      PhaseType pt) {
    calcCommontemp(phase, numberOfComponents, temperature, pressure, pt);
    // ((ComponentGEUnifacUMRPRU) phase.getComponent(0)).commonInit(phase, numberOfComponents,
    // temperature, pressure, pt);

    initQmix();
    if (getInitType() > 2) {
      initQmixdN();
    }
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math
          .log(((ComponentGEUniquac) componentArray[i]).getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /**
   * initQmix.
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
   * initQmixdN.
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
   * getQmix.
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
   * getQmixdN.
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

  /**
   * Returns whether this phase should use the Mathias-Copeman UMR group-interaction parameter tables (suffix
   * {@code _umrmc}) instead of the original UMR-PRU tables (suffix {@code _umr}).
   *
   * <p>
   * The temperature-dependent group-interaction parameters <i>A<sub>nm</sub>(T) = a<sub>nm</sub> + b<sub>nm</sub>T +
   * c<sub>nm</sub>T<sup>2</sup></i> stored in the {@code _umrmc} tables are shared by the Mathias-Copeman UMR-PRU
   * variants. Both {@code SystemUMRPRUMCEos} (attractive term number 13, 3-parameter Mathias-Copeman alpha) and
   * {@code SystemUMRPRUMCEosNew} (attractive term number 19, 5-parameter Mathias-Copeman alpha) map to the
   * {@code _umrmc} tables here. Any future variant that should consume the {@code _umrmc} tables must be added to this
   * single decision point. UMR-CPA uses the same attractive terms but reads the {@code _umrcpa} tables, see
   * {@link #getInteractionTableName(String)}.
   * </p>
   *
   * @return {@code true} when the {@code _umrmc} tables should be queried, {@code false} for the original {@code _umr}
   * tables
   */
  protected boolean useMcInteractionParameters() {
    int term = getPhase().getComponent(0).getAttractiveTermNumber();
    return term == 13 || term == 19 || term == 22;
  }

  /**
   * Select the group-interaction table of this phase.
   *
   * <p>
   * UMR-CPA describes hydrogen bonding with an association term, so its water group parameters differ from those of
   * UMR-PRU with a Mathias-Copeman alpha function, where the group model carries the whole water non-ideality. The two
   * models therefore read separate tables: {@code _umrcpa} and {@code _umrmc}.
   * </p>
   *
   * @param parameter interaction parameter, "a", "b" or "c"
   * @return database table name
   */
  protected String getInteractionTableName(String parameter) {
    String suffix = "_umr";
    if (useMcInteractionParameters()) {
      suffix = cpaInteractionParameters ? "_umrcpa" : "_umrmc";
    }
    return "unifacinterparam" + parameter + suffix;
  }

  /** {@inheritDoc} */
  @Override
  public void calcaij() {
    java.sql.ResultSet dataSet = null;

    aij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
        dataSet = database.getResultSet(("SELECT * FROM " + getInteractionTableName("a") + " WHERE MainGroup="
            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          aij[i][j] = Double.parseDouble(
              dataSet.getString("n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
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
   * calcbij.
   */
  public void calcbij() {
    java.sql.ResultSet dataSet = null;

    bij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
        dataSet = database.getResultSet(("SELECT * FROM " + getInteractionTableName("b") + " WHERE MainGroup="
            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          bij[i][j] = Double.parseDouble(
              dataSet.getString("n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    // System.out.println("finished finding interaction coefficient...C_UMR");
  }

  /**
   * calccij.
   */
  public void calccij() {
    java.sql.ResultSet dataSet = null;

    cij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups()];

    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
        dataSet = database.getResultSet(("SELECT * FROM " + getInteractionTableName("c") + " WHERE MainGroup="
            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
        dataSet.next();
        // dataSet.getClob("MainGroup");
        for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
          cij[i][j] = Double.parseDouble(
              dataSet.getString("n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    // System.out.println("finished finding interaction coefficient...C_UMR");
  }
}
