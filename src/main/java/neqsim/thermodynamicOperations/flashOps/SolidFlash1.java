/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * TPflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */
package neqsim.thermodynamicOperations.flashOps;

import Jama.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SolidFlash1 extends TPflash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    //   SystemInterface clonedSystem;
    boolean multiPhaseTest = false;
    double dQdbeta[];
    double Qmatrix[][];
    double E[];
    double Q = 0;
    int solidsNumber = 0;
    int solidIndex = 0;
    double totalSolidFrac = 0.0;
    int FluidPhaseActiveDescriptors[];  //  1 = active; 0 = inactive

    /**
     * Creates new TPflash
     */
    public SolidFlash1() {
    }

    public SolidFlash1(SystemInterface system) {
        super(system);
    }

    public void calcMultiPhaseBeta() {
    }

    public void setXY() {
        for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                if (i != solidIndex) {
                    system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz() / E[i] / system.getPhase(k).getComponent(i).getFugasityCoeffisient());
                } else {
                    system.getPhase(k).getComponent(i).setx(system.getPhases()[3].getComponent(i).getFugasityCoefficient() / system.getPhase(k).getComponent(i).getFugasityCoeffisient());
                }
            }
        }
    }

    public void checkX() {
        for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
            double x = 0.0;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                x += system.getPhase(k).getComponent(i).getx();
            }
            System.out.println("x tot " + x + " PHASE " + k);
            if (x < 1.0 - 1e-6) {
                //System.out.println("removing phase " + k);
                system.setBeta(system.getNumberOfPhases() - 2, system.getBeta(system.getNumberOfPhases() - 1));
                system.setBeta(0, 1.0 - system.getBeta(system.getNumberOfPhases() - 1));
                system.setNumberOfPhases(system.getNumberOfPhases() - 1);
                system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
                system.init(1);
                calcE();
                setXY();
                return;
            }
        }
    }

    public void calcE() {
        E = new double[system.getPhases()[0].getNumberOfComponents()];

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            E[i] = 0.0;
            for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
                E[i] += system.getBeta(k) / system.getPhase(k).getComponent(i).getFugasityCoeffisient();
            }
        }
    }

    public double calcQ() {
        Q = 0;
        double betaTotal = 0;
        dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];
        Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases() - solidsNumber];

        for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
            betaTotal += system.getBeta(k);
        }

        Q = betaTotal;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            if (i != solidIndex) {
                Q -= Math.log(E[i]) * system.getPhase(0).getComponents()[i].getz();
            }
        }
        for (int i = 0; i < solidsNumber; i++) {
            Q += system.getPhase(0).getComponent(solidIndex).getz() * (1 - Math.log(system.getPhase(0).getComponent(solidIndex).getz() / system.getPhases()[3].getComponent(solidIndex).getFugasityCoefficient()));
            for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
                Q -= system.getBeta(j) * system.getPhases()[3].getComponent(solidIndex).getFugasityCoefficient() / system.getPhase(j).getComponent(solidIndex).getFugasityCoefficient();
            }
        }
        return Q;
    }

    public void calcQbeta() {
        for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
            dQdbeta[k] = 1.0;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                if (i == solidIndex) {
                    dQdbeta[k] -= system.getPhases()[3].getComponents()[solidIndex].getFugasityCoeffisient() / system.getPhase(k).getComponent(solidIndex).getFugasityCoeffisient();
                } else {
                    dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i] / system.getPhase(k).getComponent(i).getFugasityCoeffisient();
                }
            }
        }
    }

    public void calcGradientAndHesian() {
        Qmatrix = new double[system.getNumberOfPhases() - solidsNumber][system.getNumberOfPhases() - solidsNumber];
        dQdbeta = new double[system.getNumberOfPhases() - solidsNumber];

        for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
            dQdbeta[k] = 1.0;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                if (i == solidIndex) {
                    dQdbeta[k] -= system.getPhases()[3].getComponent(solidIndex).getFugasityCoeffisient() / system.getPhase(k).getComponent(solidIndex).getFugasityCoeffisient();
                } else {
                    dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() / E[i] / system.getPhase(k).getComponent(i).getFugasityCoeffisient();
                }
            }
        }

        for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            Qmatrix[i][i] = 0;
            for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
                for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
                    if (k != solidIndex) {
                        Qmatrix[i][j] += system.getPhase(0).getComponent(k).getz() / (E[k] * E[k] * system.getPhase(j).getComponent(k).getFugasityCoeffisient() * system.getPhase(i).getComponent(k).getFugasityCoeffisient());
                    }
                }
            }
        }

        for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            if (FluidPhaseActiveDescriptors[i] == 0) {
                dQdbeta[i] = 0.0;
                for (int j = 0; j < system.getNumberOfPhases() - solidsNumber; j++) {
                    Qmatrix[i][j] = 0.0;
                }
                Qmatrix[i][i] = 1.0;
            }
        }
        for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            if (FluidPhaseActiveDescriptors[i] == 1) {
                Qmatrix[i][i] += 10e-15;
            }
        }
    }

    public void solveBeta() {
        double oldBeta[] = new double[system.getNumberOfPhases() - solidsNumber];
        double newBeta[] = new double[system.getNumberOfPhases() - solidsNumber];
        int iter = 0;
        Matrix ans = new Matrix(system.getNumberOfPhases() - solidsNumber, 1);
        double Qold = 0;
        double betaReductionFactor = 1.0;
        double Qnew = 0;
        do {
            //system.init(1);
            calcE();
            Qold = calcQ();
            calcGradientAndHesian();
            oldBeta = new double[system.getNumberOfPhases() - solidsNumber];
            newBeta = new double[system.getNumberOfPhases() - solidsNumber];
            iter++;
            for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
                oldBeta[k] = system.getBeta(k);
            }

            Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
            Matrix betaMatrixOld = new Matrix(oldBeta, 1).transpose();
            Matrix betaMatrixTemp = new Matrix(oldBeta, 1).transpose();
            Matrix dQM = new Matrix(dQdbeta, 1);
            Matrix dQdBM = new Matrix(Qmatrix);

            try {
                ans = dQdBM.solve(dQM.transpose());
            } catch (Exception e) {
                // ans = dQdBM.solve(dQM.transpose());
            }
            dQdBM.print(10, 10);
            System.out.println("BetaStep:  ");
            ans.print(30, 30);

            betaReductionFactor = 1.0;
            System.out.println("Oldbeta befor update");
            betaMatrix.print(10, 10);
            betaMatrixTemp = betaMatrix.minus(ans.times(betaReductionFactor));
            System.out.println("Beta before multiplying reduction Factoer");
            betaMatrixTemp.print(10, 2);

            double minBetaTem = 1000000;
            int minBetaTemIndex = 0;

            for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
                if (betaMatrixTemp.get(i, 0) * FluidPhaseActiveDescriptors[i] < minBetaTem) {
                    minBetaTem = betaMatrixTemp.get(i, 0);
                    minBetaTemIndex = i;
                }
            }

            for (int k = 0; k < system.getNumberOfPhases() - solidsNumber; k++) {
                if ((minBetaTem < -1.0E-10)) {
                    betaReductionFactor = 1.0 + betaMatrixTemp.get(minBetaTemIndex, 0) / ans.get(minBetaTemIndex, 0);
                }
            }
            System.out.println("Reduction Factor " + betaReductionFactor);
            betaMatrixTemp = betaMatrix.minus(ans.times(betaReductionFactor));

            System.out.println("Beta after multiplying reduction Factoer");
            betaMatrixTemp.print(10, 2);

            betaMatrixOld = betaMatrix.copy();
            betaMatrix = betaMatrixTemp.copy();
            boolean deactivatedPhase = false;
            for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
                system.setBeta(i, betaMatrix.get(i, 0));
                System.out.println("Fluid Phase fraction" + system.getBeta(i));
                if (Math.abs(system.getBeta(i)) < 1.0e-10) {
                    FluidPhaseActiveDescriptors[i] = 0;
                    deactivatedPhase = true;
                }
            }

            Qnew = calcQ();

            System.out.println("Qold = " + Qold);
            System.out.println("Qnew = " + Qnew);

            if (Qnew > Qold + 1.0e-10 && !deactivatedPhase) {
                System.out.println("Qnew > Qold...............................");
                int iter2 = 0;
                do {
                    iter2++;
                    betaReductionFactor /= 2.0;
                    betaMatrixTemp = betaMatrixOld.minus(ans.times(betaReductionFactor));
                    betaMatrix = betaMatrixTemp;
                    for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
                        system.setBeta(i, betaMatrix.get(i, 0));
                        if (Math.abs(system.getBeta(i)) < 1.0e-10) {
                            FluidPhaseActiveDescriptors[i] = 0;
                        } else {
                            FluidPhaseActiveDescriptors[i] = 1;
                        }
                    }
                    Qnew = calcQ();
                } while (Qnew > Qold + 1.0e-10 && iter2 < 5);
            }
        } while ((Math.abs(ans.norm1()) > 1e-7 && iter < 100) || iter < 2);
    }

    public void checkGibbs() {
        double gibbs1 = 0, gibbs2 = 0;
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
            system.setPhaseType(i, 0);
            system.init(1);
            gibbs1 = system.getPhase(i).getGibbsEnergy();
            system.setPhaseType(i, 1);
            system.init(1);
            gibbs2 = system.getPhase(i).getGibbsEnergy();
            if (gibbs1 < gibbs2) {
                system.setPhaseType(i, 0);
            } else {
                system.setPhaseType(i, 1);
            }
            system.init(1);
        }
    }

    public double calcSolidBeta() {
        double tempVar = system.getPhase(0).getComponents()[solidIndex].getz();
        double beta = 1.0;
        for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            if (FluidPhaseActiveDescriptors[i] == 1) {
                tempVar -= system.getBeta(i) * system.getPhases()[3].getComponent(solidIndex).getFugasityCoeffisient() / system.getPhase(i).getComponent(solidIndex).getFugasityCoeffisient();
                beta -= system.getBeta(i);
            }
        }
        if (tempVar > 0 && tempVar < 1.0) {
            system.setBeta(system.getNumberOfPhases() - 1, tempVar);
            System.out.println("Solid PhaseFraction  " + tempVar);
        }
        return tempVar;
    }

    public void run() {

        int iter = 0;

        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        system.setSolidPhaseCheck(false);
        ops.TPflash(false);
        //system.display();
        FluidPhaseActiveDescriptors = new int[system.getNumberOfPhases()];
        for (int i = 0; i < FluidPhaseActiveDescriptors.length; i++) {
            FluidPhaseActiveDescriptors[i] = 1;
        }

        system.setSolidPhaseCheck(true);
        if (checkAndAddSolidPhase() == 0) {
            return;
        }
        //    if (system.getPhase(0).getNumberOfComponents() <= 2) {
        //       solvebeta1();
        //  }else{
        double oldBeta = 0.0, beta = 0.0;
        do {
            oldBeta = beta;
            iter++;
            this.solveBeta();
            calcE();
            setXY();
            system.init(1);
            calcQbeta();

            beta = calcSolidBeta();
            /*
             for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
             if (FluidPhaseActiveDescriptors[i] == 0) {
             System.out.println("dQdB " + i + " " + dQdbeta[i]);
             if (dQdbeta[i] < 0) {
             FluidPhaseActiveDescriptors[i] = 1;
             }
             }
             }*/
        } while (Math.abs((beta - oldBeta) / beta) > 1e-6 && iter < 20);

        for (int i = 0; i < system.getNumberOfPhases() - solidsNumber; i++) {
            if (FluidPhaseActiveDescriptors[i] == 0) {
                system.deleteFluidPhase(i);
            }
        }
        system.init(1);

    }

    public int checkAndAddSolidPhase() {

        double[] solidCandidate = new double[system.getPhases()[0].getNumberOfComponents()];

        for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {

            if (system.getTemperature() > system.getPhase(0).getComponent(k).getTriplePointTemperature()) {
                solidCandidate[k] = 0;
            } else {
                solidCandidate[k] = system.getPhase(0).getComponents()[k].getz();
                system.getPhases()[3].getComponent(k).setx(1.0);
                for (int i = 0; i < system.getNumberOfPhases(); i++) {
                    double e = system.getBeta(i) * system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3]);
                    solidCandidate[k] -= system.getBeta(i) * system.getPhases()[3].getComponent(k).fugcoef(system.getPhases()[3]) / system.getPhase(i).getComponent(k).getFugasityCoeffisient();
                }
            }
        }
        int oldSolidsNumber = solidsNumber;
        solidsNumber = 0;
        totalSolidFrac = 0;
        for (int i = 0; i < solidCandidate.length; i++) {
            if (solidCandidate[i] > 1e-20) {
                system.getPhases()[3].getComponent(i).setx(1.0);
                solidIndex = i;
                solidsNumber++;
                totalSolidFrac += solidCandidate[i];
            } else {
                system.getPhases()[3].getComponent(i).setx(0.0);
            }
        }
        for (int i = oldSolidsNumber; i < solidsNumber; i++) {
            system.setNumberOfPhases(system.getNumberOfPhases() + 1);
            system.setPhaseIndex(system.getNumberOfPhases() - 1, 3);
            system.setBeta(system.getNumberOfPhases() - 1, solidCandidate[solidIndex]);
            //system.setBeta(system.getNumberOfPhases() - 2, system.getBeta(system.getNumberOfPhases() - 2) - solidCandidate[solidIndex]);
        }

        return solidsNumber;
    }

    public double solvebeta1() {
        double numberOfMolesFreeze = system.getPhase(0).getComponent(solidIndex).getNumberOfmoles();
        double solidCandidate = 0;
        int iter = 0;
        double dn = -0.01;
        double solidCandidateOld = 0;
        do {
            solidCandidateOld = solidCandidate;
            system.addComponent(system.getPhase(0).getComponent(solidIndex).getComponentName(), dn);
            ThermodynamicOperations ops = new ThermodynamicOperations(system);
            //     system.init(0);
            system.setSolidPhaseCheck(false);
            ops.TPflash();
            //     system.setSolidPhaseCheck(true);

            iter++;
            solidCandidate = system.getPhase(0).getComponents()[solidIndex].getz();
            for (int i = 0; i < system.getNumberOfPhases(); i++) {
                solidCandidate -= system.getPhases()[3].getComponent(solidIndex).fugcoef(system.getPhases()[3]) / system.getPhase(i).getComponent(solidIndex).getFugasityCoeffisient();
            }
            double dsoliddn = (solidCandidate - solidCandidateOld) / dn;
            dn = -0.5 * solidCandidate / dsoliddn;
            System.out.println("solid cand " + solidCandidate);
        } while (solidCandidate > 1e-5 && iter < 50);

        return 1.0;
    }
}
