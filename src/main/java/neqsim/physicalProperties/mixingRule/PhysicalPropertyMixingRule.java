/*
 * PhysicalPropertyMixingRule.java
 *
 * Created on 2. august 2001, 13:42
 */

package neqsim.physicalProperties.mixingRule;



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
    private static final long serialVersionUID = 1000;
    

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
     * @return a {@link neqsim.physicalProperties.mixingRule.PhysicalPropertyMixingRuleInterface}
     *         object
     */
    public PhysicalPropertyMixingRuleInterface getPhysicalPropertyMixingRule() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public void initMixingRules(PhaseInterface phase) {
        // 
        Gij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
        for (int l = 0; l < phase.getNumberOfComponents(); l++) {
            if (phase.getComponent(l).isIsTBPfraction() || phase.getComponent(l).getIonicCharge() != 0) {
                break;
            }
            String component_name = phase.getComponents()[l].getComponentName();
            for (int k = l; k < phase.getNumberOfComponents(); k++) {
                if (k == l || phase.getComponent(k).getIonicCharge() != 0
                        || phase.getComponent(k).isIsTBPfraction()) {
                    break;
                } else {
                    //try {
          /*
            dataSet = database.getResultSet("SELECT gijvisc FROM inter WHERE (COMP1='"
                + component_name + "' AND COMP2='" + phase.getComponents()[k].getComponentName()
                + "') OR (COMP1='" + phase.getComponents()[k].getComponentName() + "' AND COMP2='"
                + component_name + "')");
*/
                    neqsim.util.database.INTER objINTER = new neqsim.util.database.INTER();
                    String inter = component_name + "|" + phase.getComponents()[l].getComponentName();
                    if (objINTER.objDictionary.get(inter) == null)
                    {
                        inter = phase.getComponents()[l].getComponentName() + "|" + component_name;
                    }

                    if (objINTER.objDictionary.get(inter) != null)
                    {
                        Gij[l][k] = objINTER.objDictionary.get(inter).get("GIJVISC");
                    }
                    else
                    {
                        Gij[l][k] = 0.0;
                    }
                    Gij[k][l] = Gij[l][k];

            /*
          } catch (Exception ex) {

            String err = ex.toString();

          } finally {
            try {
              if (dataSet != null) {
                dataSet.close();
              }
            } catch (Exception ex) {

            }
          }

          */
                }
            }
        }
    }
}
