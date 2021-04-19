/*
 * ComponentGEUniquac.java
 *
 * Created on 10. juli 2000, 21:06
 */
package neqsim.thermo.component;

import neqsim.thermo.atomElement.UNIFACgroup;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentGEUnifac extends ComponentGEUniquac {

    private static final long serialVersionUID = 1000;

    java.util.ArrayList<UNIFACgroup> unifacGroups = new java.util.ArrayList();
    UNIFACgroup[] unifacGroupsArray = new UNIFACgroup[0];
    double[] lnGammakComp = null;
    double[] lnGammakMix = null;
    double Q = 0.0;
    double R = 0.0;
    int numberOfUnifacSubGroups = 133;
    static Logger logger = LogManager.getLogger(ComponentGEUnifac.class);

    /**
     * Creates new ComponentGEUniquac
     */
    public ComponentGEUnifac() {
    }

    public ComponentGEUnifac(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        if (!this.getClass().equals(ComponentGEUnifac.class)) {
            return;
        }
        if (component_name.contains("_PC")) {
            double number = getMolarMass() / 0.014;
            int intNumb = (int) Math.round(number) - 2;
            unifacGroups.add(new UNIFACgroup(1, 2));
            unifacGroups.add(new UNIFACgroup(2, intNumb));
            logger.info("adding unifac pseudo.." + intNumb);
            return;
        }
        try {
            neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet = null;
            try {
                dataSet = database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + component_name + "'"));
                dataSet.next();
                dataSet.getClob("name");
            } catch (Exception e) {
                dataSet.close();
                dataSet = database.getResultSet(("SELECT * FROM unifaccomp WHERE Name='" + component_name + "'"));
                dataSet.next();
            }

            for (int p = 1; p < numberOfUnifacSubGroups; p++) {
                int temp = Integer.parseInt(dataSet.getString("sub" + Integer.toString(p)));
                if (temp > 0) {
                    unifacGroups.add(new UNIFACgroup(p, temp));
                    // System.out.println("comp " + component_name + " adding UNIFAC group " + p);
                }
            }

            dataSet.close();
            database.getConnection().close();
        } catch (Exception e) {
            String err = e.toString();
            logger.error(err);
        }
    }

    public void addUNIFACgroup(int p, int n) {
        unifacGroups.add(new UNIFACgroup(p, n));
        unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
    }

    public double getQ() {
        double sum = 0.0;

        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            sum += this.getUnifacGroup(i).getQ() * getUnifacGroup(i).getN();
        }
        Q = sum;
        return sum;
    }

    /**
     * Getter for property R.
     *
     * @return Value of property R.
     *
     */
    public double getR() {
        double sum = 0.0;

        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            sum += this.getUnifacGroup(i).getR() * getUnifacGroup(i).getN();
        }
        R = sum;
        return sum;
    }

    @Override
	public double fugcoef(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        fugasityCoeffisient = (this.getGamma(phase, numberOfComponents, temperature, pressure, phasetype)
                * this.getAntoineVaporPressure(temperature) / pressure);
        return fugasityCoeffisient;
    }

    public void calclnGammak(int k, PhaseInterface phase) {
        double sum1Comp = 0.0, sum1Mix = 0.0;
        double sum3Comp = 0.0, sum3Mix = 0.0;

        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            sum1Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                    .getAij(getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
            sum1Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                    .getAij(getUnifacGroup(i).getGroupIndex(), getUnifacGroup(k).getGroupIndex()));
            double sum2Comp = 0.0, sum2Mix = 0.0;
            for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
                sum2Comp += getUnifacGroup(j).getQComp()
                        * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                                .getAij(getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
                sum2Mix += getUnifacGroup(j).getQMix()
                        * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                                .getAij(getUnifacGroup(j).getGroupIndex(), getUnifacGroup(i).getGroupIndex()));
            }
            sum3Comp += getUnifacGroup(i).getQComp() * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                    .getAij(getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex())) / sum2Comp;
            sum3Mix += getUnifacGroup(i).getQMix() * Math.exp(-1.0 / phase.getTemperature() * ((PhaseGEUnifac) phase)
                    .getAij(getUnifacGroup(k).getGroupIndex(), getUnifacGroup(i).getGroupIndex())) / sum2Mix;
        }
        double tempGammaComp = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Comp) - sum3Comp);
        double tempGammaMix = this.getUnifacGroup(k).getQ() * (1.0 - Math.log(sum1Mix) - sum3Mix);
        getUnifacGroup(k).setLnGammaComp(tempGammaComp);
        getUnifacGroup(k).setLnGammaMix(tempGammaMix);
    }

    @Override
	public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        double lngammaCombinational = 0.0, lngammaResidual = 0.0;
        dlngammadn = new double[numberOfComponents];
        dlngammadt = 0.0;
        ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();
        double temp1 = 0, temp2 = 0, suml = 0.0;
        double V = 0.0, F = 0.0;

        for (int j = 0; j < numberOfComponents; j++) {
            temp1 += compArray[j].getx() * compArray[j].getR();
            temp2 += (compArray[j].getQ() * compArray[j].getx());
            suml += compArray[j].getx()
                    * (10.0 / 2.0 * (compArray[j].getR() - compArray[j].getQ()) - (compArray[j].getR() - 1.0));
        }

        V = this.getx() * this.getR() / temp1;
        F = this.getx() * this.getQ() / temp2;
        double li = 10.0 / 2.0 * (getR() - getQ()) - (getR() - 1.0);
        // System.out.println("li " + li);
        lngammaCombinational = Math.log(V / getx()) + 10.0 / 2.0 * getQ() * Math.log(F / V) + li - V / getx() * suml;
        // System.out.println("ln gamma comb " + lngammaCombinational);

        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            getUnifacGroup(i).calcXComp(this);
            // getUnifacGroup(i).calcXMix((PhaseGEUnifac) phase);
            getUnifacGroup(i).calcQComp(this);
            getUnifacGroup(i).calcQMix((PhaseGEUnifac) phase);
        }
        lnGammakComp = new double[getNumberOfUNIFACgroups()];
        lnGammakMix = new double[getNumberOfUNIFACgroups()];
        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            calclnGammak(i, phase);
        }

        lngammaResidual = 0.0;
        for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
            lngammaResidual += getUnifacGroup(i).getN()
                    * (getUnifacGroup(i).getLnGammaMix() - getUnifacGroup(i).getLnGammaComp());
        }
        lngamma = lngammaResidual + lngammaCombinational;
        // System.out.println("gamma " + Math.exp(lngamma));
        gamma = Math.exp(lngamma);

        return gamma;
    }

    @Override
	public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        dfugdp = (Math.log(fugcoef(phase, numberOfComponents, temperature, pressure + 0.01, phasetype))
                - Math.log(fugcoef(phase, numberOfComponents, temperature, pressure - 0.01, phasetype))) / 0.02;
        return dfugdp;
    }

    @Override
	public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype) {
        dfugdt = (Math.log(fugcoef(phase, numberOfComponents, temperature + 0.01, pressure, phasetype))
                - Math.log(fugcoef(phase, numberOfComponents, temperature - 0.01, pressure, phasetype))) / 0.02;
        return dfugdt;
    }

    /*
     * public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents,
     * double temperature, double pressure, int phasetype){ // NumericalDerivative
     * deriv = new NumericalDerivative(); // System.out.println("dfugdP : " +
     * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents,
     * temperature, pressure, phasetype)); return
     * NumericalDerivative.fugcoefDiffPres(this, phase, numberOfComponents,
     * temperature, pressure, phasetype); }
     * 
     * public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents,
     * double temperature, double pressure, int phasetype){ NumericalDerivative
     * deriv = new NumericalDerivative(); // System.out.println("dfugdT : " +
     * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents,
     * temperature, pressure, phasetype)); return
     * NumericalDerivative.fugcoefDiffTemp(this, phase, numberOfComponents,
     * temperature, pressure, phasetype);
     * 
     * }
     */

    /**
     * Getter for property unifacGroups.
     *
     * @return Value of property unifacGroups.
     *
     */
    public java.util.ArrayList<UNIFACgroup> getUnifacGroups2() {
        return unifacGroups;
    }

    public UNIFACgroup[] getUnifacGroups() {
        return unifacGroupsArray;
    }

    public neqsim.thermo.atomElement.UNIFACgroup getUnifacGroup2(int i) {
        return unifacGroups.get(i);
    }

    public neqsim.thermo.atomElement.UNIFACgroup getUnifacGroup(int i) {
        return unifacGroupsArray[i];
    }

    /**
     * Setter for property unifacGroups.
     *
     * @param unifacGroups New value of property unifacGroups.
     *
     */
    public void setUnifacGroups(java.util.ArrayList<UNIFACgroup> unifacGroups) {
        this.unifacGroups = unifacGroups;
        unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
    }

    public int getNumberOfUNIFACgroups() {
        return unifacGroups.size();
    }

    /**
     * Setter for property Q.
     *
     * @param Q New value of property Q.
     *
     */
    public void setQ(double Q) {
        this.Q = Q;
    }

    /**
     * Setter for property R.
     *
     * @param R New value of property R.
     *
     */
    public void setR(double R) {
        this.R = R;
    }
}
