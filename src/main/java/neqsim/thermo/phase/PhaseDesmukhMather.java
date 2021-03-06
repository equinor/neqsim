/*
 * PhaseGENRTL.java
 *
 * Created on 17. juli 2000, 20:51
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentDesmukhMather;
import neqsim.thermo.component.ComponentGEInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseDesmukhMather extends PhaseGE {

    private static final long serialVersionUID = 1000;

    double GE = 0.0;
    double[][] aij;
    double[][] bij;
    /** Creates new PhaseGENRTLmodifiedHV */

    static Logger logger = LogManager.getLogger(PhaseDesmukhMather.class);

    public PhaseDesmukhMather() {
        super();
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentDesmukhMather(componentName, moles, molesInPhase, compNumber);
    }

    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int initType, int phase, double beta) {
        super.init(totalNumberOfMoles, numberOfComponents, initType, phase, beta);
        if (initType != 0) {
            phaseTypeName = phase == 0 ? "liquid" : "gas";
        }
        setMolarVolume(0.980e-3 * getMolarMass() * 1e5);
        Z = pressure * getMolarVolume() / (R * temperature);
    }

    @Override
	public void setMixingRule(int type) {
        super.setMixingRule(type);
        this.aij = new double[numberOfComponents][numberOfComponents];
        this.bij = new double[numberOfComponents][numberOfComponents];
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        for (int k = 0; k < getNumberOfComponents(); k++) {
            String component_name = getComponents()[k].getComponentName();

            for (int l = k; l < getNumberOfComponents(); l++) {
                try {
                    if (k == l) {
                        if (getComponents()[l].getComponentName().equals("MDEA")
                                && getComponents()[k].getComponentName().equals("MDEA")) {
                            aij[k][l] = -0.0828487;
                            this.aij[l][k] = this.aij[k][l];
                        }
                    } else {
                        int templ = l, tempk = k;
                        // database = new util.database.NeqSimDataBase();
                        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM inter WHERE (comp1='"
                                + component_name + "' AND comp2='" + getComponents()[l].getComponentName()
                                + "') OR (comp1='" + getComponents()[l].getComponentName() + "' AND comp2='"
                                + component_name + "')");
                        dataSet.next();

                        if (dataSet.getString("comp1").trim().equals(getComponents()[l].getComponentName())) {
                            templ = k;
                            tempk = l;
                        }
                        this.aij[k][l] = Double.parseDouble(dataSet.getString("aijDesMath"));
                        this.bij[k][l] = Double.parseDouble(dataSet.getString("bijDesMath"));
                        this.aij[l][k] = this.aij[k][l];
                        this.bij[l][k] = this.bij[k][l];

                        // System.out.println("aij " + this.aij[l][k]);
                        dataSet.close();
                        // database.getConnection().close();
                    }
                } catch (Exception e) {
                    String err = e.toString();
                    logger.info("comp names " + component_name);
                    logger.error(err);
                }
            }
        }
    }

    public void setAij(double[][] alpha) {
        for (int i = 0; i < alpha.length; i++) {
            System.arraycopy(aij[i], 0, this.aij[i], 0, alpha[0].length);
        }
    }

    public void setBij(double[][] Dij) {
        for (int i = 0; i < Dij.length; i++) {
            System.arraycopy(bij[i], 0, this.bij[i], 0, Dij[0].length);
        }
    }

    public double getBetaDesMatij(int i, int j) {
        return aij[i][j] + bij[i][j] * temperature;
    }

    public double getAij(int i, int j) {
        return aij[i][j];
    }

    public double getBij(int i, int j) {
        return bij[i][j];
    }

    @Override
	public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        GE = 0;
        for (int i = 0; i < numberOfComponents; i++) {
            GE += phase.getComponents()[i].getx() * Math.log(((ComponentDesmukhMather) componentArray[i])
                    .getGamma(phase, numberOfComponents, temperature, pressure, phasetype));
        }
        // System.out.println("ge " + GE);
        return R * temperature * numberOfMolesInPhase * GE;// phase.getNumberOfMolesInPhase()*
    }

    @Override
	public double getGibbsEnergy() {
        return R * temperature * numberOfMolesInPhase * (GE + Math.log(pressure));
    }

    @Override
	public double getExessGibbsEnergy() {
        // double GE = getExessGibbsEnergy(this, numberOfComponents, temperature,
        // pressure, phaseType);
        return GE;
    }

    @Override
	public double getActivityCoefficient(int k, int p) {
        return ((ComponentGEInterface) getComponent(k)).getGamma();
    }

    @Override
	public double getActivityCoefficient(int k) {
        return ((ComponentGEInterface) getComponent(k)).getGamma();
    }

    public double getIonicStrength() {
        double ionStrength = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            ionStrength += getComponent(i).getMolality(this) * Math.pow(getComponent(i).getIonicCharge(), 2.0);
            // getComponent(i).getMolarity(this)*Math.pow(getComponent(i).getIonicCharge(),2.0);
        }
        return 0.5 * ionStrength;
    }

    public double getSolventWeight() {
        double moles = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).getReferenceStateType().equals("solvent")) {
                moles += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
            }
        }
        return moles;
    }

    public double getSolventDensity() {
        double moles = 0.0;

        return 1020.0;
    }

    public double getSolventMolarMass() {
        double molesMass = 0.0, moles = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (getComponent(i).getReferenceStateType().equals("solvent")) {
                molesMass += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getMolarMass();
                moles = getComponent(i).getNumberOfMolesInPhase();
            }
        }
        return molesMass / moles;
    }
}
