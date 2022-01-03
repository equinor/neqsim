package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUnifacPSRK;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGEUnifacPSRK extends PhaseGEUnifac {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(PhaseGEUnifacPSRK.class);

    /** Creates new PhaseGEUnifacPSRK */
    public PhaseGEUnifacPSRK() {
        super();
        componentArray = new ComponentGEUnifacPSRK[MAX_NUMBER_OF_COMPONENTS];
    }

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

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentGEUnifacPSRK(componentName, moles, molesInPhase, compNumber);
    }

    @Override
    public void setMixingRule(int type) {
        super.setMixingRule(type);
        if (!checkedGroups) {
            checkGroups();
        }
        calcbij();
        calccij();
    }

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    @Override
    public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
            double temperature, double pressure, int phasetype) {
        double GE = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            GE += phase.getComponents()[i].getx()
                    * Math.log(((ComponentGEUniquac) componentArray[i]).getGamma(phase,
                            numberOfComponents, temperature, pressure, phasetype));
        }
        return R * phase.getTemperature() * GE * phase.getNumberOfMolesInPhase();// phase.getNumberOfMolesInPhase()*
    }

    public void calcbij() {
        bij = new double[((ComponentGEUnifac) getComponent(0))
                .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
                        .getNumberOfUNIFACgroups()];
        for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
            for (int j = 0; j < ((ComponentGEUnifac) getComponent(0))
                    .getNumberOfUNIFACgroups(); j++) {
                try {
                    neqsim.util.database.NeqSimDataBase database =
                            new neqsim.util.database.NeqSimDataBase();
                    java.sql.ResultSet dataSet = null;
                    try {
                        dataSet = database
                                .getResultSet(("SELECT * FROM unifacinterparamb WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                        dataSet.getClob("MainGroup");
                    } catch (Exception e) {
                        dataSet.close();
                        dataSet = database
                                .getResultSet(("SELECT * FROM unifacinterparamb WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                    }

                    bij[i][j] = Double.parseDouble(dataSet.getString("n"
                            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup()
                            + ""));
                    // System.out.println("aij " + aij[i][j]);
                    dataSet.close();
                    database.getConnection().close();
                }

                catch (Exception e) {
                    String err = e.toString();
                    logger.error(err, e);
                }
            }
        }
        logger.info("finished finding interaction coefficient...B");
    }

    public void calccij() {
        cij = new double[((ComponentGEUnifac) getComponent(0))
                .getNumberOfUNIFACgroups()][((ComponentGEUnifac) getComponent(0))
                        .getNumberOfUNIFACgroups()];
        for (int i = 0; i < ((ComponentGEUnifac) getComponent(0)).getNumberOfUNIFACgroups(); i++) {
            for (int j = 0; j < ((ComponentGEUnifac) getComponent(0))
                    .getNumberOfUNIFACgroups(); j++) {
                try {
                    neqsim.util.database.NeqSimDataBase database =
                            new neqsim.util.database.NeqSimDataBase();
                    java.sql.ResultSet dataSet = null;
                    try {
                        dataSet = database
                                .getResultSet(("SELECT * FROM unifacinterparamc WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                        dataSet.getClob("MainGroup");
                    } catch (Exception e) {
                        dataSet.close();
                        dataSet = database
                                .getResultSet(("SELECT * FROM unifacinterparamc WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                    }

                    cij[i][j] = Double.parseDouble(dataSet.getString("n"
                            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup()
                            + ""));
                    // System.out.println("aij " + aij[i][j]);
                    dataSet.close();
                    database.getConnection().close();
                }

                catch (Exception e) {
                    String err = e.toString();
                    logger.error(err, e);
                }
            }
        }
        logger.info("finished finding interaction coefficient...C");
    }

}
