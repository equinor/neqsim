package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacPSRK;
import neqsim.thermo.component.ComponentGEUniquac;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;

/**
 * <p>
 * PhaseGEUnifacPSRK class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUnifacPSRK extends PhaseGEUnifac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseGEUnifacPSRK.class);

  /**
   * <p>
   * Constructor for PhaseGEUnifacPSRK.
   * </p>
   */
  public PhaseGEUnifacPSRK() {
    componentArray = new ComponentGEUnifacPSRK[ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUnifacPSRK.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public PhaseGEUnifacPSRK(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGEUnifacPSRK(phase.getComponent(i).getName(),
          phase.getComponent(i).getNumberOfmoles(), phase.getComponent(i).getNumberOfMolesInPhase(),
          phase.getComponent(i).getComponentNumber());
    }
    this.setMixingRule(EosMixingRuleType.byValue(2));
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentGEUnifacPSRK(name, moles, molesInPhase, compNumber);
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
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponent(i).getx() * Math.log(((ComponentGEUniquac) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, pt));
    }
    return R * phase.getTemperature() * phase.getNumberOfMolesInPhase() * GE;
  }

  /**
   * <p>
   * calcbij.
   * </p>
   */
  public void calcbij() {
    bij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];
    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
        try (neqsim.util.database.NeqSimDataBase database =
            new neqsim.util.database.NeqSimDataBase()) {
          java.sql.ResultSet dataSet = null;
          try {
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparamb WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
            dataSet.getClob("MainGroup");
          } catch (Exception ex) {
            dataSet.close();
            logger.error(ex.getMessage(), ex);
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparamb WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
          }

          bij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
          dataSet.close();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
      }
    }
    logger.info("finished finding interaction coefficient...B");
  }

  /**
   * <p>
   * calccij.
   * </p>
   */
  public void calccij() {
    cij = new double[((ComponentGEUnifac) getComponent(0))
        .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
            .getNumberOfUNIFACgroups()];
    for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); j++) {
        try (neqsim.util.database.NeqSimDataBase database =
            new neqsim.util.database.NeqSimDataBase()) {
          java.sql.ResultSet dataSet = null;
          try {
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparamc WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
            dataSet.getClob("MainGroup");
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            dataSet.close();
            dataSet = database.getResultSet(("SELECT * FROM unifacinterparamc WHERE MainGroup="
                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i).getMainGroup() + ""));
            dataSet.next();
          }

          cij[i][j] = Double.parseDouble(dataSet.getString(
              "n" + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup() + ""));
          // System.out.println("aij " + aij[i][j]);
          dataSet.close();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
      }
    }
    logger.info("finished finding interaction coefficient...C");
  }
}
