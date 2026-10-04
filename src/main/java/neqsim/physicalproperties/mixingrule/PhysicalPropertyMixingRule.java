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
 * PhysicalPropertyMixingRule class.
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

  /** Temperature decay coefficients for Gij in 1/K (column GIJVISCT of the INTER table). */
  public double[][] GijDecay;

  /**
   * Constructor for PhysicalPropertyMixingRule.
   */
  public PhysicalPropertyMixingRule() {
  }

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyMixingRule clone() {
    PhysicalPropertyMixingRule mixRule = null;

    try {
      mixRule = (PhysicalPropertyMixingRule) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw new AssertionError("Clone failed for PhysicalPropertyMixingRule", ex);
    }

    double[][] Gij2 = Gij.clone();
    for (int i = 0; i < Gij2.length; i++) {
      Gij2[i] = Gij2[i].clone();
    }
    mixRule.Gij = Gij2;
    if (GijDecay != null) {
      double[][] decay2 = GijDecay.clone();
      for (int i = 0; i < decay2.length; i++) {
        decay2[i] = decay2[i].clone();
      }
      mixRule.GijDecay = decay2;
    }
    return mixRule;
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosityGij(int i, int j) {
    return Gij[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double getViscosityGij(int i, int j, double temperature) {
    if (GijDecay == null || GijDecay[i][j] == 0.0) {
      return Gij[i][j];
    }
    return Gij[i][j] * Math.exp(-GijDecay[i][j] * (temperature - 298.15));
  }

  /** {@inheritDoc} */
  @Override
  public void setViscosityGij(double val, int i, int j) {
    Gij[i][j] = val;
  }

  /**
   * getPhysicalPropertyMixingRule.
   *
   * @return a {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface} object
   */
  public PhysicalPropertyMixingRuleInterface getPhysicalPropertyMixingRule() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public void initMixingRules(PhaseInterface phase) {
    // logger.info("reading mix Gij viscosity..");
    Gij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    GijDecay = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    for (int l = 0; l < phase.getNumberOfComponents(); l++) {
      if (phase.getComponent(l).isIsTBPfraction() || phase.getComponent(l).getIonicCharge() != 0) {
        continue;
      }
      String component_name = phase.getComponent(l).getComponentName();
      for (int k = l + 1; k < phase.getNumberOfComponents(); k++) {
        if (phase.getComponent(k).getIonicCharge() != 0 || phase.getComponent(k).isIsTBPfraction()) {
          continue;
        } else {
          try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
              java.sql.ResultSet dataSet = database.getResultSet("SELECT gijvisc, gijviscT FROM inter WHERE (COMP1='"
                  + component_name + "' AND COMP2='" + phase.getComponent(k).getComponentName() + "') OR (COMP1='"
                  + phase.getComponent(k).getComponentName() + "' AND COMP2='" + component_name + "')")) {
            if (dataSet.next()) {
              Gij[l][k] = Double.parseDouble(dataSet.getString("gijvisc"));
              String decay = dataSet.getString("gijviscT");
              GijDecay[l][k] = decay == null || decay.trim().isEmpty() ? 0.0 : Double.parseDouble(decay);
            } else {
              Gij[l][k] = 0.0;
              GijDecay[l][k] = 0.0;
            }
            Gij[k][l] = Gij[l][k];
            GijDecay[k][l] = GijDecay[l][k];
          } catch (Exception ex) {
            logger.error("err in phys prop.....", ex);
          }
        }
      }
    }
  }
}
