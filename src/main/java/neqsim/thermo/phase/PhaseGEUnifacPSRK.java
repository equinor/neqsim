package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacPSRK;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 * <p>
 * PhaseGEUnifacPSRK class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUnifacPSRK extends PhaseGEUnifac {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhaseGEUnifacPSRK.class);

  /**
   * <p>
   * Constructor for PhaseGEUnifacPSRK.
   * </p>
   */
  public PhaseGEUnifacPSRK() {
    super();
    componentArray = new ComponentGEUnifacPSRK[MAX_NUMBER_OF_COMPONENTS];
  }

  /**
   * <p>
   * Constructor for PhaseGEUnifacPSRK.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of {@link double} objects
   * @param Dij an array of {@link double} objects
   * @param mixRule an array of {@link String} objects
   * @param intparam an array of {@link double} objects
   */
  public PhaseGEUnifacPSRK(PhaseInterface phase, double[][] alpha, double[][] Dij,
      String[][] mixRule, double[][] intparam) {
    super(phase, alpha, Dij, mixRule, intparam);
    componentArray = new ComponentGEUnifac[alpha[0].length];
    for (int i = 0; i < alpha[0].length; i++) {
      componentArray[i] = new ComponentGEUnifacPSRK(phase.getComponents()[i].getName(),
          phase.getComponents()[i].getNumberOfmoles(),
          phase.getComponents()[i].getNumberOfMolesInPhase(),
          phase.getComponents()[i].getComponentNumber());
    }
    this.setMixingRule(2);
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase);
    componentArray[compNumber] = new ComponentGEUnifacPSRK(name, moles, molesInPhase, compNumber);
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
      double temperature, double pressure, int phasetype) {
    double GE = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      GE += phase.getComponents()[i].getx() * Math.log(((ComponentGEUniquac) componentArray[i])
          .getGamma(phase, numberOfComponents, temperature, pressure, phasetype));
    }
    return R * phase.getTemperature() * GE * phase.getNumberOfMolesInPhase(); // phase.getNumberOfMolesInPhase()*
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
