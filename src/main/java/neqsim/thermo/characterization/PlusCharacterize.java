/*
 * TBPCharacterize.java
 *
 * Created on 3. januar 2003, 10:03
 */
package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author  ESOL
 */
public class PlusCharacterize extends Object implements java.io.Serializable, CharacteriseInterface {
    private static final long serialVersionUID = 1000;    
    double TBPfractions[] = null;
    boolean firsttime = true;
    double MPlus = 300.0, zPlus = 0.3, densPlus = 0.98;
    private double densLastTBP = 0.78;
    int carbonNumberVector[] = null;
    protected boolean pseudocomponents = true;
    int firstPlusFractionNumber = 1;
    int lastPlusFractionNumber = 80;
    int numberOfPseudocomponents = 5;//(lastPlusFractionNumber-firstPlusFractionNumber)*50;
    int length = 0;
    double[] coefs = {4.4660105006, -1.1266303727, 0.80, 0.0408709562};
    double[] SRKcoefs = {4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785};
    double[] PRcoefs = {4.4660105006, -1.1266303727, 8.1927423578, -3.4668277785};
    double[] plusCoefs = {0.0007774204804, -0.02390179};
    transient SystemInterface system = null;
    static Logger logger = Logger.getLogger(PlusCharacterize.class);

    /** Creates a new instance of TBPCharacterize */
    public PlusCharacterize() {
    }

    public PlusCharacterize(SystemInterface system) {
        this.system = system;


    }

    public boolean hasPlusFraction() {
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                return true;
            }
        }
        return false;
    }

    public void setHeavyTBPtoPlus() {
        int plusCompNumber = 0, compNumber = 0;
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            try {
                if (system.getPhase(0).getComponent(i).isIsTBPfraction()) {
                    Integer firstPlusNumber = new Integer(0);
                    if (system.getPhase(0).getComponent(i).getComponentName().substring(3, 4).equals("_")) {
                        firstPlusNumber = new Integer(system.getPhase(0).getComponent(i).getComponentName().substring(1, 3));
                    } else {
                        firstPlusNumber = new Integer(system.getPhase(0).getComponent(i).getComponentName().substring(1, 2));
                    }
                    if (plusCompNumber < firstPlusNumber.intValue()) {
                        plusCompNumber = firstPlusNumber.intValue();
                        compNumber = i;
                    }
                }
            } catch (Exception e) {
                e.toString();
            }
        }
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            system.getPhase(i).getComponent(compNumber).setIsTBPfraction(false);
            system.getPhase(i).getComponent(compNumber).setIsPlusFraction(true);

            MPlus = system.getPhase(i).getComponent(compNumber).getMolarMass();
            zPlus = system.getPhase(i).getComponent(compNumber).getz();
            densPlus = system.getPhase(i).getComponent(compNumber).getNormalLiquidDensity();
        }
        coefs[2] = system.getPhase(0).getComponent(compNumber - 1).getNormalLiquidDensity() + 0.03;
        densLastTBP = system.getPhase(0).getComponent(compNumber - 1).getNormalLiquidDensity();

    }

    public void solve() {
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            try {
                if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                    Integer firstPlusNumber = new Integer(0);
                    if (system.getPhase(0).getComponent(i).getComponentName().substring(3, 4).equals("_")) {
                        firstPlusNumber = new Integer(system.getPhase(0).getComponent(i).getComponentName().substring(1, 3));
                    } else {
                        firstPlusNumber = new Integer(system.getPhase(0).getComponent(i).getComponentName().substring(1, 2));
                    }
                    if (firstPlusFractionNumber < firstPlusNumber.intValue()) {
                        firstPlusFractionNumber = firstPlusNumber.intValue();
                    }

                }
            } catch (Exception e) {
                e.toString();
            }
        }
        logger.info("first plus fraction number " + firstPlusFractionNumber);

        //      NewtonSolveABCDplus solver = new NewtonSolveABCDplus(system, this);
        //NewtonSolveCDplus solver2 = new NewtonSolveCDplus(system, this);
        //      solver.solve();
        // solver2.solve();

     //   NewtonSolveABCD2 solver3 = new NewtonSolveABCD2(system, this);
     //   solver3.solve();
    }

    /** Getter for property coefs.
     * @return Value of property coefs.
     */
    public double[] getCoefs() {
        return this.coefs;
    }

    public double getCoef(int i) {
        return this.coefs[i];
    }

    /** Setter for property coefs.
     * @param coefs New value of property coefs.
     */
    public void setCoefs(double[] coefs) {
        System.arraycopy(coefs, 0, this.coefs, 0, coefs.length);

        if (firsttime) {
            if (coefs.length == 3) {
                double Dtot = 0.0;
                for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
                    Dtot += (getDensPlus() - this.getCoef(2)) / Math.log(i);// (this.getCoef(2)+this.getCoef(3)*Math.log(i)-this.getCoef(2))/Math.log(i);
                }
                double lengthPlus = this.getLastPlusFractionNumber() - this.getFirstPlusFractionNumber();
                logger.info("length plus " + lengthPlus);
                Dtot /= lengthPlus;
                logger.info("D " + Dtot);
                this.coefs[3] = Dtot;
            }
            firsttime = false;
        }

        double zSum = 0.0, mSum = 0.0, densSum = 0.0;
        int iter = 0;
        do {
            iter++;
            zSum = 0.0;
            mSum = 0.0;
            densSum = 0.0;
            for (int i = this.getFirstPlusFractionNumber(); i < this.getLastPlusFractionNumber(); i++) {
                double ztemp = Math.exp(this.getCoef(0) + this.getCoef(1) * (i));
                double M = PVTsimMolarMass[i - 6] / 1000.0;
                double dens = this.getCoef(2) + this.getCoef(3) * Math.log(i);
                zSum += ztemp;
                mSum += ztemp * M;
                densSum += (ztemp * M / dens);
            }
            densSum = mSum / densSum;

            this.coefs[3] += 1.0 * (densPlus - densSum) / densSum * this.coefs[3];
            //System.out.println("coef " + this.coefs[3]);
        } while (Math.abs(densPlus - densSum) > 1e-6 && iter < 1000);
    }

    /** Setter for property coefs.
     * @param coefs New value of property coefs.
     */
    public void setCoefs(double coef, int i) {
        this.coefs[i] = coef;
    }

    /** Getter for property length.
     * @return Value of property length.
     */
    public int getLength() {
        return length;
    }

    public void generatePlusFractions(int start, int end, double zplus, double Mplus) {
    }

    public void addHeavyEnd() {
    }

    public void generateTBPFractions() {
    }

    public void addCharacterizedPlusFraction() {
        if (!pseudocomponents) {
            numberOfPseudocomponents = getLastPlusFractionNumber() - getFirstPlusFractionNumber() + 1;
        }

        double[] zPlus = new double[numberOfPseudocomponents];
        double[] MPlus = new double[numberOfPseudocomponents];

        double weightFrac = 0.0;
        double weightTot = 0.0;

        for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
            weightTot += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
        }

        double meanWeightFrac = weightTot / (numberOfPseudocomponents + 0.000001);
        int pluscomp = 0;
        zPlus = new double[numberOfPseudocomponents];
        MPlus = new double[numberOfPseudocomponents];
        int k = 0;
        int firstPS = firstPlusFractionNumber;
        double Maverage = 0.0, denstemp1 = 0.0, denstemp2 = 0.0;

        double totalNumberOfMoles = system.getNumberOfMoles();

        for (int i = getFirstPlusFractionNumber(); i < getLastPlusFractionNumber(); i++) {
            zPlus[k] += Math.exp(getCoef(0) + getCoef(1) * i);
            MPlus[k] += PVTsimMolarMass[i - 6] / 1000.0;
            denstemp1 += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
            denstemp2 += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0 / (getCoef(2) + getCoef(3) * Math.log(i));
            //System.out.println("dens " + denstemp1/denstemp2);
            Maverage += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
            weightFrac += Math.exp(getCoef(0) + getCoef(1) * i) * PVTsimMolarMass[i - 6] / 1000.0;
            //System.out.println("weigth " + weightFrac + " i" + i);
            if (weightFrac >= meanWeightFrac || !pseudocomponents || i == getLastPlusFractionNumber() - 1) {
                pluscomp++;
                String name = (i == firstPS) ? "PC" + Integer.toString(firstPS) : "PC" + Integer.toString(firstPS) + "-" + Integer.toString(i);
                system.addTBPfraction(name, totalNumberOfMoles * zPlus[k], Maverage / zPlus[k], denstemp1 / denstemp2);
                denstemp1 = 0.0;
                denstemp2 = 0.0;
                weightFrac = 0.0;
                Maverage = 0.0;
                k++;
                firstPS = i + 1;
            }
        }


        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                system.removeComponent(system.getPhase(0).getComponent(i).getName());
                break;
            }
        }
    }

    public void addPseudoTBPfraction(int start, int end) {
    }

    /** Getter for property carbonNumberVector.
     * @return Value of property carbonNumberVector.
     */
    public int[] getCarbonNumberVector() {
        return this.carbonNumberVector;
    }

    /** Setter for property carbonNumberVector.
     * @param carbonNumberVector New value of property carbonNumberVector.
     */
    public void setCarbonNumberVector(int[] carbonNumberVector) {
        this.carbonNumberVector = carbonNumberVector;
    }

    public int getFirstPlusFractionNumber() {
        return firstPlusFractionNumber;
    }

    public int getLastPlusFractionNumber() {
        return lastPlusFractionNumber;
    }

    /** Setter for property firstPlusFractionNumber.
     * @param firstPlusFractionNumber New value of property firstPlusFractionNumber.
     */
    public void setFirstPlusFractionNumber(int firstPlusFractionNumber) {
        this.firstPlusFractionNumber = firstPlusFractionNumber;
    }

    /** Getter for property startPlus.
     * @return Value of property startPlus.
     */
    public int getStartPlus() {
        return firstPlusFractionNumber;
    }

    /** Setter for property startPlus.
     * @param startPlus New value of property startPlus.
     */
    public void setStartPlus(int startPlus) {
        this.firstPlusFractionNumber = firstPlusFractionNumber;
    }

    /** Getter for property MPlus.
     * @return Value of property MPlus.
     */
    public double getMPlus() {
        return MPlus;
    }

    /** Setter for property MPlus.
     * @param MPlus New value of property MPlus.
     */
    public void setMPlus(double MPlus) {
        this.MPlus = MPlus;
    }

    /** Getter for property zPlus.
     * @return Value of property zPlus.
     */
    public double getZPlus() {
        return zPlus;
    }

    /** Setter for property zPlus.
     * @param zPlus New value of property zPlus.
     */
    public void setZPlus(double zPlus) {
        this.zPlus = zPlus;
    }

    /** Getter for property plusCoefs.
     * @return Value of property plusCoefs.
     */
    public double[] getPlusCoefs() {
        return this.plusCoefs;
    }

    public double getPlusCoefs(int i) {
        return this.plusCoefs[i];
    }

    /** Setter for property plusCoefs.
     * @param plusCoefs New value of property plusCoefs.
     */
    public void setPlusCoefs(double[] plusCoefs) {
        this.plusCoefs = plusCoefs;
    }

    /** Getter for property densPlus.
     * @return Value of property densPlus.
     *
     */
    public double getDensPlus() {
        return densPlus;
    }

    /** Setter for property densPlus.
     * @param densPlus New value of property densPlus.
     *
     */
    public void setDensPlus(double densPlus) {
        this.densPlus = densPlus;
    }

    public boolean groupTBPfractions() {
        return true;
    }

    /** Getter for property numberOfPseudocomponents.
     * @return Value of property numberOfPseudocomponents.
     *
     */
    public int getNumberOfPseudocomponents() {
        return numberOfPseudocomponents;
    }

    /** Setter for property numberOfPseudocomponents.
     * @param numberOfPseudocomponents New value of property numberOfPseudocomponents.
     *
     */
    public void setNumberOfPseudocomponents(int numberOfPseudocomponents) {
        this.numberOfPseudocomponents = numberOfPseudocomponents;
    }

    /** Getter for property pseudocomponents.
     * @return Value of property pseudocomponents.
     *
     */
    public boolean isPseudocomponents() {
        return pseudocomponents;
    }

    /** Setter for property pseudocomponents.
     * @param pseudocomponents New value of property pseudocomponents.
     *
     */
    public void setPseudocomponents(boolean pseudocomponents) {
        this.pseudocomponents = pseudocomponents;
    }

    public void removeTBPfraction() {
        java.util.ArrayList list = new java.util.ArrayList();
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double boilpoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
            if (boilpoint >= 69.0) {
                list.add(system.getPhase(0).getComponent(i).getName());
            }
        }

        for (int i = 0; i < list.size(); i++) {
            try {
                system.removeComponent((String) list.get(i));
                logger.info("removing " + list.get(i));
            } catch (Exception e) {
                logger.error("not able to remove " + list.get(i));
                //return;
            }
        }
    }

    public void addTBPFractions() {
    }

    /**
     * @return the densLastTBP
     */
    public double getDensLastTBP() {
        return densLastTBP;
    }

    /**
     * @param densLastTBP the densLastTBP to set
     */
    public void setDensLastTBP(double densLastTBP) {
        this.densLastTBP = densLastTBP;
    }

    public void characterizePlusFraction() {
        system.init(0);
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            if (system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                MPlus = system.getPhase(0).getComponent(i).getMolarMass();
                zPlus = system.getPhase(0).getComponent(i).getz();
                densPlus = system.getPhase(0).getComponent(i).getNormalLiquidDensity();
            }
        }

        coefs[0]=0.1;
        coefs[1]= Math.log(zPlus)/getFirstPlusFractionNumber();
        solve();
    }
}
