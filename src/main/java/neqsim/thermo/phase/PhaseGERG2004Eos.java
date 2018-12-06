/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentGERG2004;
import neqsim.thermo.util.JNI.GERG2004EOS;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhaseGERG2004Eos extends PhaseEos {

    private static final long serialVersionUID = 1000;

    private GERG2004EOS gergEOS = new GERG2004EOS();
    double[] xFracGERG = new double[18];

    ;
    int IPHASE = 0;
    boolean okVolume = true;
    double enthalpy = 0.0, entropy = 0.0, gibbsEnergy = 0.0, CpGERG = 0.0, CvGERG = 0.0, internalEnery = 0.0, JTcoef = 0.0;

    /** Creates new PhaseSrkEos */
    public PhaseGERG2004Eos() {
        super();
    }

    public Object clone() {
        PhaseGERG2004Eos clonedPhase = null;
        try {
            clonedPhase = (PhaseGERG2004Eos) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedPhase;
    }

    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentGERG2004(componentName, moles, molesInPhase, compNumber);
    }

    public void setxFracGERG() {
        double sumFrac = 0.0;
        for (int j = 0; j < gergEOS.getNameList().length; j++) {
            if (hasComponent(gergEOS.getNameList()[j])) {
                xFracGERG[j] = getComponent(gergEOS.getNameList()[j]).getx();
                sumFrac += xFracGERG[j];
            } else {
                xFracGERG[j] = 0.0;
            }
        }
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) {
        IPHASE = phase == 0 ? -1 : -2;
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        setxFracGERG();

        if (!okVolume) {
            IPHASE = phase == 0 ? -2 : -1;
            super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        }
        if (type >= 1) {
            double[] temp = new double[18];
            temp = GERG2004EOS.SPHIOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

            for (int j = 0; j < gergEOS.getNameList().length; j++) {
                if (hasComponent(gergEOS.getNameList()[j])) {
                    if (temp[j] == -1111) {
                        IPHASE = -2;
                    }
                    if (temp[j] == -2222) {
                        IPHASE = -1;
                    }
                }
            }
            temp = GERG2004EOS.SPHIOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

            for (int j = 0; j < gergEOS.getNameList().length; j++) {
                if (hasComponent(gergEOS.getNameList()[j])) {
                    getComponent(gergEOS.getNameList()[j]).setFugacityCoefficient(temp[j]);
                }
            }

            double[] alloTPX = new double[17];
            alloTPX = GERG2004EOS.SALLOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE);

            gibbsEnergy = alloTPX[10]; // gergEOS.GOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            internalEnery = alloTPX[9]; // .UOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            enthalpy = alloTPX[2]; // gergEOS.HOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            entropy = alloTPX[3]; // gergEOS.SOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            CpGERG = alloTPX[4]; // gergEOS.CPOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            CvGERG = alloTPX[5]; // gergEOS.CPOTPX(temperature,pressure/10.0, xFracGERG[0],xFracGERG[1],xFracGERG[2],xFracGERG[3],xFracGERG[4],xFracGERG[5],xFracGERG[6],xFracGERG[7],xFracGERG[8],xFracGERG[9],xFracGERG[10],xFracGERG[11],xFracGERG[12],xFracGERG[13],xFracGERG[14],xFracGERG[15],xFracGERG[16],xFracGERG[17],IPHASE);
            super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        }
    }

    public double getGibbsEnergy() {
        return gibbsEnergy;
    }

    public double getJouleThomsonCoefficient() {
        return JTcoef;
    }

    public double getEnthalpy() {
        return enthalpy;
    }

    public double getEntropy() {
        return entropy;
    }

    public double getInternalEnergy() {
        return internalEnery;
    }

    public double getCp() {
        return CpGERG;
    }

    public double getCv() {
        return CvGERG;
    }

    public double molarVolume(double pressure, double temperature, double A, double B, int phase) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
        double temp = GERG2004EOS.ZOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE) * 8.314 * temperature / (pressure);
      
        temp = GERG2004EOS.ZOTPX(temperature, pressure / 10.0, xFracGERG[0], xFracGERG[1], xFracGERG[2], xFracGERG[3], xFracGERG[4], xFracGERG[5], xFracGERG[6], xFracGERG[7], xFracGERG[8], xFracGERG[9], xFracGERG[10], xFracGERG[11], xFracGERG[12], xFracGERG[13], xFracGERG[14], xFracGERG[15], xFracGERG[16], xFracGERG[17], IPHASE) * 8.314 * temperature / (pressure);
        okVolume = !(Math.abs(2222 + temp) < 0.1 || Math.abs(1111 + temp) < 0.1);
        return temp;
    }
}
