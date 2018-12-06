/*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */
package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentPCSAFT;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class PhasePCSAFT extends PhaseSrkEos {

    private static final long serialVersionUID = 1000;

    double nSAFT = 1.0;
    double dnSAFTdV = 1.0, dnSAFTdVdV = 1.0;
    double dmeanSAFT = 0.0;
    double dSAFT = 1.0;
    double mSAFT = 1.0;
    double mdSAFT = 1.0;
    double nmSAFT = 1.0;
    double mmin1SAFT = 1.0;
    double ghsSAFT = 1.0;
    double aHSSAFT = 1.0;
    double volumeSAFT = 1.0;
    double daHCSAFTdN = 1.0;
    double daHSSAFTdN = 1.0, dgHSSAFTdN = 1.0;
    double daHSSAFTdNdN = 1.0, dgHSSAFTdNdN = 1.0;
    int useHS = 1, useDISP1 = 1, useDISP2 = 1;
    private double[][] aConstSAFT = {{
            0.9105631445,
            0.6361281449,
            2.6861347891,
            -26.547362491,
            97.759208784,
            -159.59154087,
            91.297774084
        },
        {
            -0.3084016918,
            0.1860531159,
            -2.5030047259,
            21.419793629,
            -65.255885330,
            83.318680481,
            -33.746922930
        }, {
            -0.0906148351,
            0.4527842806,
            0.5962700728,
            -1.7241829131,
            -4.1302112531,
            13.776631870,
            -8.6728470368
        }};
    private double[][] bConstSAFT = {{0.7240946941,
            2.2382791861,
            -4.0025849485,
            -21.003576815,
            26.855641363,
            206.55133841,
            -355.60235612
        }, {-0.5755498075,
            0.6995095521,
            3.8925673390,
            -17.215471648,
            192.67226447,
            -161.82646165,
            -165.20769346
        }, {0.0976883116,
            -0.2557574982,
            -9.1558561530,
            20.642075974,
            -38.804430052,
            93.626774077,
            -29.666905585
        }};
    private double F1dispVolTerm = 1.0, F1dispSumTerm = 1.0, F1dispI1 = 1.0, F2dispI2 = 1.0, F2dispZHC = 1.0, F2dispZHCdN = 1.0, F2dispZHCdm = 1.0, F2dispZHCdV = 1.0, F2dispI2dVdV = 0.0, F2dispZHCdVdV = 0.0, F1dispI1dNdN = 1.0;
    private double F1dispVolTermdV = 1.0, F1dispVolTermdVdV = 1.0, F1dispI1dN = 1.0, F1dispI1dm = 1.0, F1dispI1dV = 1.0, F2dispI2dV = 1.0, F2dispI2dN = 1.0, F2dispI2dm = 1.0, F2dispSumTerm = 0.0, F2dispZHCdNdN = 1.0, F2dispI2dNdN = 1.0, F1dispI1dVdV = 0.0;

    /** Creates new PhaseSrkEos */
    public PhasePCSAFT() {
        super();
    }

    public Object clone() {
        PhasePCSAFT clonedPhase = null;
        try {
            clonedPhase = (PhasePCSAFT) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedPhase;
    }

    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentPCSAFT(componentName, moles, molesInPhase, compNumber);
    }

    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0 start init type =1 gi nye betingelser
        if (type > 0) {
            for (int i = 0; i < numberOfComponents; i++) {
                componentArray[i].Finit(this, temperature, pressure, totalNumberOfMoles, beta, numberOfComponents, type);
            }
        }
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    public void volInit() {
        volumeSAFT = getVolume() * 1.0e-5;
        setDmeanSAFT(calcdmeanSAFT());
        setDSAFT(calcdSAFT());
//        System.out.println("saft volume " + getVolumeSAFT());
//        System.out.println("dsaft " + getDSAFT());
        setNSAFT(1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / volumeSAFT * getDSAFT());
        dnSAFTdV = -1.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 2.0) * getDSAFT();
        dnSAFTdVdV = 2.0 * ThermodynamicConstantsInterface.pi / 6.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / Math.pow(volumeSAFT, 3.0) * getDSAFT();
//        System.out.println("N SAFT " + getNSAFT());
        setGhsSAFT((1.0 - nSAFT / 2.0) / Math.pow(1.0 - nSAFT, 3.0));
        setmSAFT(calcmSAFT());
        setMmin1SAFT(calcmmin1SAFT());
        setmdSAFT(calcmdSAFT());
        setAHSSAFT((4.0 * getNSAFT() - 3.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 2.0));
        daHSSAFTdN = ((4.0 - 6.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 2.0) - (4.0 * getNSAFT() - 3 * Math.pow(getNSAFT(), 2.0)) * 2.0 * (1.0 - getNSAFT()) * (-1.0)) / Math.pow(1.0 - getNSAFT(), 4.0);
        daHSSAFTdNdN = (-6.0 * Math.pow(1.0 - getNSAFT(), 2.0) + 2.0 * (1.0 - getNSAFT()) * (4.0 - 6 * getNSAFT())) / Math.pow(1.0 - getNSAFT(), 4.0) + ((8.0 - 12.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 3.0) + (8.0 - 6.0 * Math.pow(getNSAFT(), 2.0)) * 3.0 * Math.pow(1.0 - getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 6.0);
        dgHSSAFTdN = (-0.5 * Math.pow(1.0 - getNSAFT(), 3.0) - (1.0 - getNSAFT() / 2.0) * 3.0 * Math.pow(1.0 - nSAFT, 2.0) * (-1.0)) / Math.pow(1.0 - getNSAFT(), 6.0);
        dgHSSAFTdNdN = -3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 2.0) / Math.pow(1.0 - getNSAFT(), 6.0) + (-3.0 / 2.0 * Math.pow(1.0 - getNSAFT(), 4.0) + 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (3.0 - 3.0 / 2.0 * getNSAFT())) / Math.pow(1.0 - getNSAFT(), 8.0);

        setF1dispVolTerm(ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / getVolumeSAFT());
        F1dispSumTerm = calcF1dispSumTerm();
        F1dispI1 = calcF1dispI1();
        F1dispVolTermdV = -ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / Math.pow(getVolumeSAFT(), 2.0);
        F1dispVolTermdVdV = 2.0 * ThermodynamicConstantsInterface.avagadroNumber * getNumberOfMolesInPhase() / Math.pow(getVolumeSAFT(), 3.0);
        F1dispI1dN = calcF1dispI1dN();
        F1dispI1dNdN = calcF1dispI1dNdN();
        F1dispI1dm = calcF1dispI1dm();
        F1dispI1dV = F1dispI1dN * getDnSAFTdV();
        F1dispI1dVdV = F1dispI1dNdN * getDnSAFTdV() * getDnSAFTdV() + F1dispI1dN * dnSAFTdVdV; //F1dispI1dNdN*dnSAFTdVdV;
        setF2dispSumTerm(calcF2dispSumTerm());
        setF2dispI2(calcF2dispI2());
        F2dispI2dN = calcF2dispI2dN();
        F2dispI2dNdN = calcF2dispI2dNdN();
        F2dispI2dm = calcF2dispI2dm();
        F2dispI2dV = F2dispI2dN * getDnSAFTdV();
        F2dispI2dVdV = F2dispI2dNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispI2dN * dnSAFTdVdV;//F2dispI2dNdN*dnSAFTdVdV;;

        F2dispZHC = calcF2dispZHC();
        F2dispZHCdN = calcF2dispZHCdN();
        F2dispZHCdNdN = calcF2dispZHCdNdN();
        setF2dispZHCdm(calcF2dispZHCdm());
        F2dispZHCdV = F2dispZHCdN * getDnSAFTdV();
        F2dispZHCdVdV = F2dispZHCdNdN * getDnSAFTdV() * getDnSAFTdV() + F2dispZHCdN * dnSAFTdVdV; //F2dispZHCdNdN*dnSAFTdVdV*0;
    }

    public double calcF2dispZHC() {
        double temp = 1.0 + getmSAFT() * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0) + (1.0 - getmSAFT()) * (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0) - 2 * Math.pow(getNSAFT(), 4.0)) / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
        return 1.0 / temp;
    }

    public double calcF2dispZHCdm() {
        double temp = -Math.pow(F2dispZHC, 2.0);
        return temp * ((8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0)) / Math.pow(1.0 - getNSAFT(), 4.0) - (20 * getNSAFT() - 27 * Math.pow(getNSAFT(), 2.0) + 12 * Math.pow(getNSAFT(), 3.0) - 2 * Math.pow(getNSAFT(), 4.0)) / Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0));
    }

    public double calcF2dispZHCdN() {
        double temp0 = -Math.pow(F2dispZHC, 2.0);
        double temp1 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
        double temp2 = 20.0 * getNSAFT() - 27.0 * Math.pow(getNSAFT(), 2.0) + 12.0 * Math.pow(getNSAFT(), 3.0) - 2.0 * Math.pow(getNSAFT(), 4.0);
        // ikke rett implementert
        return temp0 * (getmSAFT() * ((8.0 - 4.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 4.0) - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0) * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0))) / Math.pow(1.0 - getNSAFT(), 8.0) +
                (1.0 - getmSAFT()) * ((20.0 - (2.0 * 27.0) * getNSAFT() + (12.0 * 3.0) * Math.pow(getNSAFT(), 2.0) - 8.0 * Math.pow(getNSAFT(), 3.0)) * temp1 - (2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0)) * (-3.0 + 2.0 * getNSAFT())) * temp2) / Math.pow(temp1, 2.0));
    }

    public double calcF2dispZHCdNdN() {
        double temp0 = 2.0 * Math.pow(F2dispZHC, 3.0);
        double temp1 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 2.0);
        double temp11 = Math.pow((1.0 - getNSAFT()) * (2.0 - getNSAFT()), 3.0);
        double temp2 = 20.0 * getNSAFT() - 27.0 * Math.pow(getNSAFT(), 2.0) + 12.0 * Math.pow(getNSAFT(), 3.0) - 2.0 * Math.pow(getNSAFT(), 4.0);

        double temp1der = 2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0)) * (-3.0 + 2.0 * getNSAFT());
        double temp11der = 3.0 * Math.pow(2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0), 2.0) * (-3.0 + 2.0 * getNSAFT());
// ikke rett implementert
        double temp3 = (getmSAFT() * ((8.0 - 4.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 4.0) - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0) * (8.0 * getNSAFT() - 2.0 * Math.pow(getNSAFT(), 2.0))) / Math.pow(1.0 - getNSAFT(), 8.0) +
                (1.0 - getmSAFT()) * ((20.0 - (2.0 * 27.0) * getNSAFT() + (12.0 * 3.0) * Math.pow(getNSAFT(), 2.0) - 8.0 * Math.pow(getNSAFT(), 3.0)) * temp1 - (2.0 * (2.0 - 3.0 * getNSAFT() + Math.pow(getNSAFT(), 2.0)) * (-3.0 + 2.0 * getNSAFT())) * temp2) / Math.pow(temp1, 2.0));

        double temp4 = -Math.pow(F2dispZHC, 2.0);
        double dZdndn = getmSAFT() * ((-4.0 * Math.pow(1.0 - getNSAFT(), 4.0) - 4.0 * Math.pow(1.0 - getNSAFT(), 3.0) * (-1.0) * (8.0 - 4.0 * getNSAFT())) / Math.pow(1.0 - getNSAFT(), 8.0) + ((32.0 - 16.0 * getNSAFT()) * Math.pow(1.0 - getNSAFT(), 5.0) - 5.0 * Math.pow(1.0 - getNSAFT(), 4.0) * (-1.0) * (32.0 * getNSAFT() - 8.0 * Math.pow(getNSAFT(), 2.0))) / Math.pow(1.0 - getNSAFT(), 10.0)) +
                (1.0 - getmSAFT()) * (((-54.0 + 72.0 * getNSAFT() - 24.0 * Math.pow(getNSAFT(), 2.0)) * temp1 - temp1der * (20.0 - 54.0 * getNSAFT() + 36.0 * Math.pow(getNSAFT(), 2.0) - 8.0 * Math.pow(getNSAFT(), 3.0))) / Math.pow(temp1, 2.0) -
                ((-40.0 * Math.pow(getNSAFT(), 4.0) + 240.0 * Math.pow(getNSAFT(), 3.0) - 3.0 * 180.0 * Math.pow(getNSAFT(), 2.0) + 242.0 * 2.0 * getNSAFT() - 120.0) * temp11 - temp11der * (-8.0 * Math.pow(getNSAFT(), 5.0) + 60.0 * Math.pow(getNSAFT(), 4.0) - 180.0 * Math.pow(getNSAFT(), 3.0) + 242.0 * Math.pow(getNSAFT(), 2.0) - 120.0 * getNSAFT())) / Math.pow(temp11, 2.0));


        return temp0 * Math.pow(temp3, 2.0) + temp4 * dZdndn;
    }

    public double calcmSAFT() {
        double temp2 = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi() / getNumberOfMolesInPhase();
        }

        return temp2;
    }

    public double calcF1dispSumTerm() {
        double temp1 = 0.0;

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase() *
                        getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi() *
                        Math.sqrt(getComponent(i).getEpsikSAFT() / temperature * getComponent(j).getEpsikSAFT() / temperature) * (1.0 - mixRule.getBinaryInteractionParameter(i, j)) *
                        Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
            }
        }
        return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
    }

    public double calcF2dispSumTerm() {
        double temp1 = 0.0;

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                temp1 += getComponent(i).getNumberOfMolesInPhase() * getComponent(j).getNumberOfMolesInPhase() *
                        getComponent(i).getmSAFTi() * getComponent(j).getmSAFTi() *
                        getComponent(i).getEpsikSAFT() / temperature * getComponent(j).getEpsikSAFT() / temperature * Math.pow((1.0 - mixRule.getBinaryInteractionParameter(i, j)), 2.0) *
                        Math.pow(0.5 * (getComponent(i).getSigmaSAFTi() + getComponent(j).getSigmaSAFTi()), 3.0);
            }
        }
        return temp1 / Math.pow(getNumberOfMolesInPhase(), 2.0);
    }

    public double calcF1dispI1dN() {
        double temp1 = 0.0;
        for (int i = 1; i < 7; i++) {
            temp1 += i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
        }
        return temp1;
    }

    public double calcF1dispI1dNdN() {
        double temp1 = 0.0;
        for (int i = 2; i < 7; i++) {
            temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
        }
        return temp1;
    }

    public double calcF1dispI1dm() {
        double temp1 = 0.0;
        for (int i = 0; i < 7; i++) {
            temp1 += getaSAFTdm(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
        }
        return temp1;
    }

    public double calcF2dispI2dN() {
        double temp1 = 0.0;
        for (int i = 1; i < 7; i++) {
            temp1 += i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 1.0);
        }
        return temp1;
    }

    public double calcF2dispI2dNdN() {
        double temp1 = 0.0;
        for (int i = 2; i < 7; i++) {
            temp1 += (i - 1.0) * i * getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i - 2.0);
        }
        return temp1;
    }

    public double calcF2dispI2dm() {
        double temp1 = 0.0;
        for (int i = 0; i < 7; i++) {
            temp1 += getaSAFTdm(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
        }
        return temp1;
    }

    public double calcF1dispI1() {
        double temp1 = 0.0;
        for (int i = 0; i < 7; i++) {
            temp1 += getaSAFT(i, getmSAFT(), aConstSAFT) * Math.pow(getNSAFT(), i);
        }
        return temp1;
    }

    public double calcF2dispI2() {
        double temp1 = 0.0;
        for (int i = 0; i < 7; i++) {
            temp1 += getaSAFT(i, getmSAFT(), bConstSAFT) * Math.pow(getNSAFT(), i);
        }
        return temp1;
    }

    public double getaSAFT(int i, double m, double ab[][]) {
        return ab[0][i] + (m - 1.0) / m * ab[1][i] + (m - 1.0) / m * (m - 2.0) / m * ab[2][i];
    }

    public double getaSAFTdm(int i, double m, double ab[][]) {
        return (m - (m - 1.0)) / (m * m) * ab[1][i] + ((2.0 * m - 3.0) * m * m - 2 * m * (m * m - 3 * m + 2)) / Math.pow(m, 4.0) * ab[2][i];
    }

    public double calcmdSAFT() {
        double temp2 = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase() * getComponent(i).getmSAFTi() * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
        }

        return temp2;
    }

    public double calcmmin1SAFT() {
        double temp2 = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp2 += getComponent(i).getNumberOfMolesInPhase() / getNumberOfMolesInPhase() * (getComponent(i).getmSAFTi() - 1.0);
        }

        return temp2;
    }

    public double calcdmeanSAFT() {
        double temp = 0.0, temp2 = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi() * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
            temp2 += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi();
        }
        return Math.pow(temp / temp2, 1.0 / 3.0);
    }

    public double calcdSAFT() {
        double temp = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            temp += getComponent(i).getNumberOfMolesInPhase() * getComponent(i).getmSAFTi() * Math.pow(((ComponentPCSAFT) getComponent(i)).getdSAFTi(), 3.0);
        }
        // System.out.println("d saft calc " + temp/getNumberOfMolesInPhase());
        return temp / getNumberOfMolesInPhase();
    }

    public double getNSAFT() {
        return nSAFT;
    }

    public void setNSAFT(double nSAFT) {
        this.nSAFT = nSAFT;
    }

    public double getDSAFT() {
        return dSAFT;
    }

    public void setDSAFT(double dSAFT) {
        this.dSAFT = dSAFT;
    }

    public double getGhsSAFT() {
        return ghsSAFT;
    }

    public void setGhsSAFT(double ghsSAFT) {
        this.ghsSAFT = ghsSAFT;
    }

    public double F_HC_SAFT() {
        return getNumberOfMolesInPhase() * (getmSAFT() * getAHSSAFT() - getMmin1SAFT() * Math.log(getGhsSAFT()));///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_HC_SAFTdV() {
        return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdN * getDnSAFTdV() -
                getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * getDnSAFTdV());///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_HC_SAFTdVdV() {
        return getNumberOfMolesInPhase() * (getmSAFT() * daHSSAFTdNdN * getDnSAFTdV() * getDnSAFTdV() + getmSAFT() * daHSSAFTdN * dnSAFTdVdV + getMmin1SAFT() * Math.pow(getGhsSAFT(), -2.0) * Math.pow(getDgHSSAFTdN(), 2.0) * getDnSAFTdV() - getMmin1SAFT() * Math.pow(getGhsSAFT(), -1.0) * dgHSSAFTdNdN * dnSAFTdV * dnSAFTdV - getMmin1SAFT() * 1.0 / getGhsSAFT() * getDgHSSAFTdN() * dnSAFTdVdV); //(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_HC_SAFTdVdVdV() {
        return 0.0;
    }

    public double F_DISP1_SAFT() {
        return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi * getF1dispVolTerm() * getF1dispSumTerm() * getF1dispI1());///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_DISP1_SAFTdV() {
        return getNumberOfMolesInPhase() * (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * getF1dispI1() - 2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm() * F1dispI1dV);///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_DISP1_SAFTdVdV() {
        return getNumberOfMolesInPhase() * ((-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdVdV * getF1dispSumTerm() * getF1dispI1()) +
                (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dV) +
                (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTermdV * getF1dispSumTerm() * F1dispI1dV) +
                (-2.0 * ThermodynamicConstantsInterface.pi * F1dispVolTerm * getF1dispSumTerm() * F1dispI1dVdV));
    }

    public double F_DISP2_SAFT() {
        return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT() * getF1dispVolTerm() * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC());///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_DISP2_SAFTdV() {
        return getNumberOfMolesInPhase() * (-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC() - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV * getF2dispZHC() - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2() * F2dispZHCdV);///(ThermodynamicConstantsInterface.R*temperature);
    }

    public double dF_DISP2_SAFTdVdV() {
        return getNumberOfMolesInPhase() * ((-ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdVdV * getF2dispSumTerm() * getF2dispI2() * getF2dispZHC()) - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispZHC() * F2dispI2dV) - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2() * F2dispZHCdV) - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * F2dispI2dV * getF2dispZHC() - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dVdV * getF2dispZHC() - ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV * F2dispZHCdV - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTermdV * getF2dispSumTerm() * getF2dispI2() * F2dispZHCdV) - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * F2dispI2dV * F2dispZHCdV) - (ThermodynamicConstantsInterface.pi * getmSAFT() * F1dispVolTerm * getF2dispSumTerm() * getF2dispI2() * F2dispZHCdVdV));
    }

    public double getF() {
//        System.out.println("F-HC " + useHS*F_HC_SAFT());
//
//        System.out.println("F-DISP1 " + useDISP1*F_DISP1_SAFT());
//
//        System.out.println("F-DISP2 " + useDISP2*F_DISP2_SAFT());
        return useHS * F_HC_SAFT() + useDISP1 * F_DISP1_SAFT() + useDISP2 * F_DISP2_SAFT();
    }

    public double dFdV() {
//        System.out.println("N-saft " + getNSAFT());
//        System.out.println("F-HC " + useHS*F_HC_SAFT());
//        System.out.println("F-DISP1 " + useDISP1*F_DISP1_SAFT());
//
//        System.out.println("F-DISP2 " + useDISP2*F_DISP2_SAFT());

        return (useHS * dF_HC_SAFTdV() + useDISP1 * dF_DISP1_SAFTdV() + useDISP2 * dF_DISP2_SAFTdV()) * 1.0e-5;
    }

    public double dFdVdV() {
        return (useHS * dF_HC_SAFTdVdV() + useDISP1 * dF_DISP1_SAFTdVdV() + useDISP2 * dF_DISP2_SAFTdVdV()) * 1.0e-10;
    }

    public double getmdSAFT() {
        return mdSAFT;
    }

    public void setmdSAFT(double mdSAFT) {
        this.mdSAFT = mdSAFT;
    }

    public double getmSAFT() {
        return mSAFT;
    }

    public void setmSAFT(double mSAFT) {
        this.mSAFT = mSAFT;
    }

    public double getAHSSAFT() {
        return aHSSAFT;
    }

    public void setAHSSAFT(double aHSSAFT) {
        this.aHSSAFT = aHSSAFT;
    }

    public double getMmin1SAFT() {
        return mmin1SAFT;
    }

    public void setMmin1SAFT(double mmin1SAFT) {
        this.mmin1SAFT = mmin1SAFT;
    }

    public double getVolumeSAFT() {
        return volumeSAFT;
    }

    public void setVolumeSAFT(double volumeSAFT) {
        this.volumeSAFT = volumeSAFT;
    }

    public double getDgHSSAFTdN() {
        return dgHSSAFTdN;
    }

    public void setDgHSSAFTdN(double dgHSSAFTdN) {
        this.dgHSSAFTdN = dgHSSAFTdN;
    }

    public double getDnSAFTdV() {
        return dnSAFTdV;
    }

    public void setDnSAFTdV(double dnSAFTdV) {
        this.dnSAFTdV = dnSAFTdV;
    }

    public double getF1dispVolTerm() {
        return F1dispVolTerm;
    }

    public void setF1dispVolTerm(double F1dispVolTerm) {
        this.F1dispVolTerm = F1dispVolTerm;
    }

    public double getF1dispSumTerm() {
        return F1dispSumTerm;
    }

    public double getF1dispI1() {
        return F1dispI1;
    }

    public double getF2dispI2() {
        return F2dispI2;
    }

    public void setF2dispI2(double F2dispI2) {
        this.F2dispI2 = F2dispI2;
    }

    public double getF2dispZHC() {
        return F2dispZHC;
    }

    public void setF2dispZHC(double F2dispZHC) {
        this.F2dispZHC = F2dispZHC;
    }

    public double getF2dispZHCdN() {
        return F2dispZHCdN;
    }

    public double getF2dispZHCdm() {
        return F2dispZHCdm;
    }

    public double molarVolume22(double pressure, double temperature, double A, double B, int phase) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {

        double volume = phase == 0 ? getB() / (2.0 / (2.0 + temperature / getPseudoCriticalTemperature())) : (numberOfMolesInPhase * temperature * R) / pressure;
        setMolarVolume(volume / numberOfMolesInPhase);
        double oldMolarVolume = 0;
        int iterations = 0;
        double h = 0, dh = 0.0, d1 = 0.0;
        do {
            iterations++;
            this.volInit();
            oldMolarVolume = getMolarVolume();
            h = pressure - calcPressure();
            dh = -calcPressuredV();
            d1 = -h / dh;
            double newVolume = getMolarVolume() + 0.9 * d1 / numberOfMolesInPhase;
            if (newVolume > 1e-100) {
                setMolarVolume(newVolume);
            } else {
                setMolarVolume(oldMolarVolume / 10.0);
            }
            Z = pressure * getMolarVolume() / (R * temperature);
            //
        } while (Math.abs((oldMolarVolume - getMolarVolume()) / oldMolarVolume) > 1.0e-10 && iterations < 200);
        //   System.out.println("Z " + Z + " iterations " + iterations);
        return getMolarVolume();
    }

    public double molarVolume(double pressure, double temperature, double A, double B, int phase) throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {

        double BonV = phase == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        //double BonV = phase== 0 ? 0.99:1e-5;
        if (BonV < 0) {
            BonV = 1.0e-6;
        }
        if (BonV > 1.0) {
            BonV = 1.0 - 1.0e-6;
        }
        double BonVold = BonV;
        double Btemp = 0, Dtemp = 0, h = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
        double d1 = 0, d2 = 0;
        Btemp = getB();
        Dtemp = getA();
        if (Btemp <= 0) {
            System.out.println("b negative in volume calc");
        }
        setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
        int iterations = 0;
        //System.out.println("volume " + getVolume());
        do {
            iterations++;
            this.volInit();

            //System.out.println("saft volume " + getVolumeSAFT());
            BonVold = BonV;
            h = BonV - Btemp / numberOfMolesInPhase * dFdV() - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
            dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
            dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV()) - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

            d1 = -h / dh;
            d2 = -dh / dhh;
            BonV += d1;

            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);
            // System.out.println("Z " + Z);
        } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < 2000);
//        System.out.println("error BonV " + Math.abs((BonV-BonVold)/BonV));
        //       System.out.println("iterations " + iterations);
        if (BonV < 0) {
            BonV = pressure * getB() / (numberOfMolesInPhase * temperature * R);
            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);

        }

        if (iterations >= 2000) {
            throw new neqsim.util.exception.TooManyIterationsException();
        }
        if (Double.isNaN(getMolarVolume())) {
            throw new neqsim.util.exception.IsNaNException();
        }

        // if(phaseType==0) System.out.println("density " + getDensity());//"BonV: " + BonV + " "+"  itert: " +   iterations +" " + "  phase " + phaseType+ "  " + h + " " +dh + " B " + Btemp + "  D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());

        return getMolarVolume();
    }

    public double getDmeanSAFT() {
        return dmeanSAFT;
    }

    public void setDmeanSAFT(double dmeanSAFT) {
        this.dmeanSAFT = dmeanSAFT;
    }

    public double getNmSAFT() {
        return nmSAFT;
    }

    public void setNmSAFT(double nmSAFT) {
        this.nmSAFT = nmSAFT;
    }

    public double getF2dispSumTerm() {
        return F2dispSumTerm;
    }

    public void setF2dispSumTerm(double F2dispSumTerm) {
        this.F2dispSumTerm = F2dispSumTerm;
    }

    public void setF2dispZHCdm(double F2dispZHCdm) {
        this.F2dispZHCdm = F2dispZHCdm;
    }
}
