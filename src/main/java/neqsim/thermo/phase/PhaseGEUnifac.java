/*
 * PhaseGEUniquac.java
 *
 * Created on 11. juli 2000, 21:01
 */
package neqsim.thermo.phase;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.atomElement.UNIFACgroup;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEUniquac;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseGEUnifac extends PhaseGEUniquac {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new PhaseGEUniquac
     */
    double[][] aij = new double[1][1];
    double[][] bij = new double[1][1];
    double[][] cij = new double[1][1];
    boolean checkedGroups = false;
    static Logger logger = LogManager.getLogger(PhaseGEUnifac.class);

    public PhaseGEUnifac() {
        super();
        componentArray = new ComponentGEUnifac[MAX_NUMBER_OF_COMPONENTS];
    }

    public PhaseGEUnifac(PhaseInterface phase, double[][] alpha, double[][] Dij, String[][] mixRule,
            double[][] intparam) {
        super(phase, alpha, Dij, mixRule, intparam);
        componentArray = new ComponentGEUnifac[alpha[0].length];
        for (int i = 0; i < alpha[0].length; i++) {
            componentArray[i] = new ComponentGEUnifac(phase.getComponents()[i].getName(),
                    phase.getComponents()[i].getNumberOfmoles(),
                    phase.getComponents()[i].getNumberOfMolesInPhase(),
                    phase.getComponents()[i].getComponentNumber());
        }
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase,
            int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] =
                new ComponentGEUnifac(componentName, moles, molesInPhase, compNumber);
    }

    @Override
    public void setMixingRule(int type) {
        super.setMixingRule(type);
        if (!checkedGroups) {
            checkGroups();
        }
        logger.info("checking unifac groups...");
        calcaij();
    }

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) { // type = 0
                           // start
                           // init type
                           // =1 gi nye
                           // betingelser
        // if(type==0) calcaij();
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        if (type == 0) {
            super.init(totalNumberOfMoles, numberOfComponents, 1, phase, beta);
        }
    }

    public void calcaij() {
        aij = new double[((ComponentGEUnifac) getComponent(0))
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
                                .getResultSet(("SELECT * FROM unifacinterparam WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                        dataSet.getClob("MainGroup");
                    } catch (Exception e) {
                        dataSet.close();
                        dataSet = database
                                .getResultSet(("SELECT * FROM unifacinterparam WHERE MainGroup="
                                        + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                                .getMainGroup()
                                        + ""));
                        dataSet.next();
                    }

                    aij[i][j] = Double.parseDouble(dataSet.getString("n"
                            + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j).getMainGroup()
                            + ""));
                    if (Math.abs(aij[i][j]) < 1e-6) {
                        logger.info(" i "
                                + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(i)
                                        .getMainGroup()
                                + " j " + ((ComponentGEUnifac) getComponent(0)).getUnifacGroup(j)
                                        .getMainGroup()
                                + "  aij " + aij[i][j]);
                    }
                    dataSet.close();
                    database.getConnection().close();
                } catch (Exception e) {
                    logger.error("error", e);
                    String err = e.toString();
                    logger.error(err);
                }
            }
        }
        logger.info("finished finding interaction coefficient...A");
    }

    public void checkGroups() {
        ArrayList<neqsim.thermo.atomElement.UNIFACgroup> unifacGroups =
                new ArrayList<UNIFACgroup>();

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < ((ComponentGEUnifac) getComponent(i))
                    .getNumberOfUNIFACgroups(); j++) {
                if (!unifacGroups
                        .contains(((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j))) {
                    unifacGroups.add(((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j));
                } else
                    ;// System.out.println("no");
            }
        }

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < unifacGroups.size(); j++) {
                if (!((ComponentGEUnifac) getComponent(i)).getUnifacGroups2()
                        .contains(unifacGroups.get(j))) {
                    ((ComponentGEUnifac) getComponent(i))
                            .addUNIFACgroup(unifacGroups.get(j).getSubGroup(), 0);
                }
            }
        }

        for (int i = 0; i < numberOfComponents; i++) {
            neqsim.thermo.atomElement.UNIFACgroup[] array =
                    ((ComponentGEUnifac) getComponent(i)).getUnifacGroups();
            java.util.Arrays.sort(array);
            ArrayList<UNIFACgroup> phaseList = new ArrayList<UNIFACgroup>(0);
            phaseList.addAll(Arrays.asList(array));
            ((ComponentGEUnifac) getComponent(i)).setUnifacGroups(phaseList);
        }

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < ((ComponentGEUnifac) getComponent(i))
                    .getNumberOfUNIFACgroups(); j++) {
                ((ComponentGEUnifac) getComponent(i)).getUnifacGroup(j).setGroupIndex(j);
                // System.out.println("i " + i + " " +
                // ((ComponentGEUnifac)getComponent(i)).getUnifacGroup(j).getSubGroup());
            }
        }
        checkedGroups = true;
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
        return R * phase.getTemperature() * GE * phase.getNumberOfMolesInPhase();
    }

    @Override
    public double getExessGibbsEnergy() {
        return getExessGibbsEnergy(this, numberOfComponents, temperature, pressure, phaseType);
    }

    @Override
    public double getGibbsEnergy() {
        double val = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            val += getComponent(i).getNumberOfMolesInPhase()
                    * (getComponent(i).getLogFugasityCoeffisient());
        }
        return R * temperature * ((val) + Math.log(pressure) * numberOfMolesInPhase);
    }

    public double getAij(int i, int j) {
        return aij[i][j];
    }

    public void setAij(int i, int j, double val) {
        aij[i][j] = val;
    }

    public double getBij(int i, int j) {
        return bij[i][j];
    }

    public void setBij(int i, int j, double val) {
        bij[i][j] = val;
    }

    public double getCij(int i, int j) {
        return cij[i][j];
    }

    public void setCij(int i, int j, double val) {
        cij[i][j] = val;
    }
}
