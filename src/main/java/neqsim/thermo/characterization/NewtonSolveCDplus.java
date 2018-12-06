package neqsim.thermo.characterization;

import Jama.*;
import neqsim.thermo.system.SystemInterface;

public class NewtonSolveCDplus extends Object implements java.io.Serializable {
    private static final long serialVersionUID = 1000;    
    int iter = 0;
    Matrix Jac;
    Matrix fvec;
    Matrix sol, dx;
    int numberOfComponents = 0;
    PlusCharacterize characterizeClass;
    SystemInterface system = null;

    public NewtonSolveCDplus() {
    }

    public NewtonSolveCDplus(SystemInterface system, PlusCharacterize characterizeClass) {
        this.system = system;
        this.characterizeClass = characterizeClass;
        numberOfComponents = system.getPhase(0).getNumberOfComponents();
        Jac = new Matrix(3, 3);
        fvec = new Matrix(3, 1);
        sol = new Matrix(3, 1);
        sol.set(0, 0, characterizeClass.getCoef(0));
        sol.set(1, 0, characterizeClass.getCoef(1));
        sol.set(2, 0, characterizeClass.getCoef(2));
        // sol.set(3,0,characterizeClass.getCoef(3));
        //        sol.set(2,0,characterizeClass.getCoef(2));
        //        sol.set(3,0,characterizeClass.getCoef(3));
    }

    public void setfvec() {
        double zSum = 0.0, mSum = 0.0, densSum = 0.0;
        for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass.getLastPlusFractionNumber(); i++) {
            double ztemp = Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * (i));
            double M = PlusCharacterize.PVTsimMolarMass[i - 6] / 1000.0;
            double dens = characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i);
            zSum += ztemp;
            mSum += ztemp * M;
            densSum += (ztemp * M / dens);
        }
        densSum = mSum / densSum;
        double lengthPlus = characterizeClass.getLastPlusFractionNumber() - characterizeClass.getFirstPlusFractionNumber();
        System.out.println("diff " + lengthPlus);
        //Dtot /= lengthPlus;
        // System.out.println("Dtot "+ Dtot);
        System.out.println("zsum " + zSum);
        //        System.out.println("zplus " + characterizeClass.getZPlus());
        fvec.set(0, 0,
                zSum - characterizeClass.getZPlus());

        fvec.set(1, 0,
                mSum / zSum - characterizeClass.getMPlus());

        fvec.set(2, 0,
                densSum - characterizeClass.getDensPlus());
    }

    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;

        double tempJ = 0.0, sumdyidbeta = 0, sumdxidbeta = 0;
        int nofc = numberOfComponents;

        for (int j = 0; j < 3; j++) {
            double nTot = 0.0, mTot = 0.0, nTot2 = 0.0;
            for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass.getLastPlusFractionNumber(); i++) {
                nTot += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                nTot2 += i * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
            }
            if (j == 0) {
                tempJ = nTot;
            } else if (j == 1) {
                tempJ = nTot2;
            } else {
                tempJ = 0.0;
            }
            Jac.set(0, j, tempJ);
        }

        for (int j = 0; j < 3; j++) {
            double mTot1 = 0.0, mTot2 = 0.0, mSum = 0.0, zSum2 = 0.0, zSum = 0.0, zSum3 = 0.0;
            for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass.getLastPlusFractionNumber(); i++) {
                mTot1 += (PlusCharacterize.PVTsimMolarMass[i - 6] / 1000.0) * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                mTot2 += i * (PlusCharacterize.PVTsimMolarMass[i - 6] / 1000.0) * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                zSum2 += Math.pow(Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i), 2.0);
                zSum += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                zSum3 += i * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
            }
            if (j == 0) {
                tempJ = (mTot1 * zSum - mTot1 * zSum) / zSum2;
            } else if (j == 1) {
                tempJ = (mTot2 * zSum - mTot1 * zSum3) / zSum2;
            } else {
                tempJ = 0.0;
            }
            Jac.set(1, j, tempJ);
        }

        for (int j = 0; j < 3; j++) {
            double A = 0.0, B = 0.0, Bpow2 = 0.0, Ader1 = 0.0, Bder1 = 0.0, Ader2 = 0.0, Bder2 = 0.0, Bder3 = 0.0, Bder4 = 0.0;
            for (int i = characterizeClass.getFirstPlusFractionNumber(); i < characterizeClass.getLastPlusFractionNumber(); i++) {
                double M = PlusCharacterize.PVTsimMolarMass[i - 6] / 1000.0;
                double dens = characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i);
                A += M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                B += M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens;
                Bpow2 += Math.pow(M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens, 2.0);
                Ader1 = A;
                Bder1 += Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M / dens;
                Ader2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i);
                Bder2 += i * M * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) / dens;
                Bder3 += -Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), -2.0) * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M;
                Bder4 += -Math.log(i) * Math.pow(characterizeClass.getCoef(2) + characterizeClass.getCoef(3) * Math.log(i), -2.0) * Math.exp(characterizeClass.getCoef(0) + characterizeClass.getCoef(1) * i) * M;
            }
            if (j == 0) {
                tempJ = (Ader1 * B - Bder1 * A) / Bpow2;
            } else if (j == 1) {
                tempJ = (Ader2 * B - Bder2 * A) / Bpow2;
            } else if (j == 2) {
                tempJ = (-Bder3 * A) / Bpow2;
            } else if (j == 3) {
                tempJ = (-Bder4 * A) / Bpow2;
            }
            Jac.set(2, j, tempJ);
        }


    }

    public void solve() {
        iter = 0;
        do {
            iter++;
            setfvec();
            System.out.println("fvec ");
            fvec.print(10, 17);
            setJac();
            Jac.print(10, 6);
            //     System.out.println("rank " + Jac.rank());
            dx = Jac.solve(fvec);
            System.out.println("dx ");
            dx.print(10, 3);
            if (iter < 10) {
                sol.minusEquals(dx.times((iter) / (iter + 5000.0)));
            } else {
                sol.minusEquals(dx.times((iter) / (iter + 50.0)));
            }
            //System.out.println("sol ");
            //sol.print(10,10);
            characterizeClass.setCoefs(sol.transpose().copy().getArray()[0]);
        } while (((fvec.norm2() > 1e-6 || iter < 15) && iter < 3000));
        System.out.println("ok char");
        sol.print(10, 10);
    }
}
