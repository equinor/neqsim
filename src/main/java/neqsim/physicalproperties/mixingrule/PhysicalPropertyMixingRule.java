/*
 * PhysicalPropertyMixingRule.java
 *
 * Created on 2. august 2001, 13:42
 */

package neqsim.physicalproperties.mixingrule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * PhysicalPropertyMixingRule class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhysicalPropertyMixingRule
    implements PhysicalPropertyMixingRuleInterface, ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhysicalPropertyMixingRule.class);

  public double[][] Gij;

  /**
   * <p>
   * Constructor for PhysicalPropertyMixingRule.
   * </p>
   */
  public PhysicalPropertyMixingRule() {}

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyMixingRule clone() {
    PhysicalPropertyMixingRule mixRule = null;

    try {
      mixRule = (PhysicalPropertyMixingRule) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    double[][] Gij2 = Gij.clone();
    for (int i = 0; i < Gij2.length; i++) {
      Gij2[i] = Gij2[i].clone();
    }
    mixRule.Gij = Gij2;
    return mixRule;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosityGij(int i, int j) {
    return Gij[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public void setViscosityGij(double val, int i, int j) {
    Gij[i][j] = val;
  }

  /**
   * <p>
   * getPhysicalPropertyMixingRule.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface}
   *         object
   */
  public PhysicalPropertyMixingRuleInterface getPhysicalPropertyMixingRule() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public void initMixingRules(PhaseInterface phase) {
    // logger.info("reading mix Gij viscosity..");
    Gij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    for (int l = 0; l < phase.getNumberOfComponents(); l++) {
      if (phase.getComponent(l).isIsTBPfraction() || phase.getComponent(l).getIonicCharge() != 0) {
        break;
      }
      String component_name = phase.getComponent(l).getComponentName();
      for (int k = l; k < phase.getNumberOfComponents(); k++) {
        if (k == l || phase.getComponent(k).getIonicCharge() != 0
            || phase.getComponent(k).isIsTBPfraction()) {
          break;
        } else {
          try (
              neqsim.util.database.NeqSimDataBase database =
                  new neqsim.util.database.NeqSimDataBase();
              java.sql.ResultSet dataSet =
                  database.getResultSet("SELECT gijvisc FROM inter WHERE (COMP1='" + component_name
                      + "' AND COMP2='" + phase.getComponent(k).getComponentName()
                      + "') OR (COMP1='" + phase.getComponent(k).getComponentName()
                      + "' AND COMP2='" + component_name + "')")) {
            if (dataSet.next()) {
              Gij[l][k] = Double.parseDouble(dataSet.getString("gijvisc"));
            } else {
              Gij[l][k] = 0.0;
            }
            Gij[k][l] = Gij[l][k];
          } catch (Exception ex) {
            logger.error("err in phys prop.....", ex);
          }
        }
      }
    }
  }
}
