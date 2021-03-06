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
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SolidFlash extends TPflash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SolidFlash.class);

    // SystemInterface clonedSystem;
    boolean multiPhaseTest = false;
    double dQdbeta[];
    double Qmatrix[][];
    double E[];
    double Q = 0;
    int solidComponent = 0;
    boolean hasRemovedPhase = false;
    boolean secondTime = false;

    /** Creates new TPflash */
    public SolidFlash() {
    }

    public SolidFlash(SystemInterface system) {
        super(system);
    }

    public void setSolidComponent(int i) {
        solidComponent = i;
    }

    public SolidFlash(SystemInterface system, boolean check) {
        super(system, check);
    }

    public void calcMultiPhaseBeta() {
    }

    public void setXY() {
        for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                system.getPhase(k).getComponent(i).setx(system.getPhase(0).getComponent(i).getz() / E[i]
                        / system.getPhase(k).getComponent(i).getFugasityCoeffisient());
                /*
                 * if (system.getPhase(k).getComponent(i).getx() > 1.0) {
                 * system.getPhase(k).getComponent(i).setx(1.0 - 1e-30); } if
                 * (system.getPhase(k).getComponent(i).getx() < 0.0) {
                 * system.getPhase(k).getComponent(i).setx(1.0e-30); }
                 */

            }
            system.getPhase(k).normalize();
        }
    }

    public void checkX() {
        for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
            double x = 0.0;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                x += system.getPhase(k).getComponent(i).getx();
            }
            // logger.info("x tot " + x + " PHASE " + k);
            if (x < 1.0 - 1e-6) {
                // logger.info("removing phase " + k);
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
            for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
                E[i] += system.getBeta(k) / system.getPhase(k).getComponent(i).getFugasityCoeffisient();
            }
            // logger.info("Ei " +E[i]);
            // if(
        }
        // E[solidComponent] +=
        // system.getBeta(system.getNumberOfPhases()-1)/system.getPhase(3).getComponent(solidComponent).getFugasityCoeffisient();
        E[solidComponent] = system.getPhase(0).getComponent(solidComponent).getz()
                / system.getPhases()[3].getComponents()[solidComponent].getFugasityCoeffisient();
        // logger.info("Ei " +E[solidComponent]);
        // logger.info("fug "
        // +system.getPhase(3).getComponent(solidComponent).getFugasityCoeffisient());
        // logger.info("zi " +system.getPhase(0).getComponent(solidComponent).getz());
    }

    public double calcQ() {
        Q = 0;
        double betaTotal = 0;
        dQdbeta = new double[system.getNumberOfPhases() - 1];
        Qmatrix = new double[system.getNumberOfPhases() - 1][system.getNumberOfPhases() - 1];

        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            betaTotal += system.getBeta(k);
        }

        Q = betaTotal;
        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            Q -= Math.log(E[i]) * system.getPhase(0).getComponents()[i].getz();
        }

        for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
            dQdbeta[k] = 1.0 - system.getPhases()[3].getComponents()[solidComponent].getFugasityCoeffisient()
                    / system.getPhase(k).getComponent(solidComponent).getFugasityCoeffisient();
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                if (i != solidComponent) {
                    dQdbeta[k] -= system.getPhase(0).getComponent(i).getz() * 1.0 / E[i]
                            / system.getPhase(k).getComponent(i).getFugasityCoeffisient();
                }
            }
        }

        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
            Qmatrix[i][i] = 1.0e-9;
            for (int j = 0; j < system.getNumberOfPhases() - 1; j++) {
                if (i != j) {
                    Qmatrix[i][j] = 0;
                }
                for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
                    if (k != solidComponent) {
                        Qmatrix[i][j] += system.getPhase(0).getComponent(k).getz()
                                / (E[k] * E[k] * system.getPhase(j).getComponent(k).getFugasityCoeffisient()
                                        * system.getPhase(i).getComponent(k).getFugasityCoeffisient());
                    }
                }
            }
        }
        return Q;
    }

    public void solveBeta(boolean ideal) {
        double oldBeta[] = new double[system.getNumberOfPhases() - 1];
        double newBeta[] = new double[system.getNumberOfPhases() - 1];
        int iter = 0;
        Matrix ans = new Matrix(system.getNumberOfPhases() - 1, 1);
        do {
            if (!ideal) {
                system.init(1);
            }
            calcE();
            calcQ();

            oldBeta = new double[system.getNumberOfPhases() - 1];
            newBeta = new double[system.getNumberOfPhases() - 1];
            iter++;
            for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
                oldBeta[k] = system.getBeta(k);
            }

            Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
            Matrix dQM = new Matrix(dQdbeta, 1);
            Matrix dQdBM = new Matrix(Qmatrix);
            // dQM.print(10,2);
            // dQdBM.print(10,2);
            try {
                ans = dQdBM.solve(dQM.transpose());
            } catch (Exception e) {
                // ans = dQdBM.solve(dQM.transpose());
            }

            betaMatrix.minusEquals(ans.times((iter + 1.0) / (10.0 + iter)));
            // betaMatrix.print(10, 2);
            // betaMatrix.print(10,2);

            for (int k = 0; k < system.getNumberOfPhases() - 1; k++) {
                system.setBeta(k, betaMatrix.get(k, 0));
                if (betaMatrix.get(k, 0) < 0) {
                    system.setBeta(k, 1e-9);
                }
                if (betaMatrix.get(k, 0) > 1) {
                    system.setBeta(k, 1 - 1e-9);
                }
            }

            // calcSolidBeta();
            calcSolidBeta();

            if (!hasRemovedPhase) {
                for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
                    if (Math.abs(system.getBeta(i)) < 1.01e-9) {
                        system.removePhaseKeepTotalComposition(i);
                        hasRemovedPhase = true;
                    }
                }
            }

            /*
             * for (int i = 0; i < system.getNumberOfPhases()-1; i++) { if
             * (Math.abs(system.getPhase(i).getDensity()-system.getPhase(i+1).getDensity())<
             * 1e-6 && !hasRemovedPhase) { system.removePhase(i+1);
             * doStabilityAnalysis=false; hasRemovedPhase = true; } }
             */
            if (hasRemovedPhase && !secondTime) {
                secondTime = true;
                run();
            }
            // system.init(1);
            // calcE();
            // setXY();
            // system.init(1);
            // //checkGibbs();
            // calcE();
            // setXY();
        } while ((ans.norm2() > 1e-8 && iter < 100) || iter < 2);
        // checkX();
        // calcE();
        // setXY();
        system.init(1);
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

    public void calcSolidBeta() {
        double tempVar = system.getPhase(0).getComponents()[solidComponent].getz();
        double beta = 1.0;
        for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
            tempVar -= system.getBeta(i) * system.getPhase(3).getComponent(solidComponent).getFugasityCoeffisient()
                    / system.getPhase(i).getComponent(solidComponent).getFugasityCoeffisient();
            beta -= system.getBeta(i);
        }
        if (tempVar > 0 && tempVar < 1.0) {
            system.setBeta(system.getNumberOfPhases() - 1, tempVar);
        }
        // logger.info("beta " + tempVar);
    }

    @Override
	public void run() {
        // logger.info("starting ");
        system.setNumberOfPhases(system.getNumberOfPhases());
        double oldBeta = 0.0;
        int iter = 0;
        system.init(1);
        this.solveBeta(true);

        do {
            iter++;
            oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
            setXY();
            system.init(1);

            if (system.getNumberOfPhases() > 1) {
                this.solveBeta(true);
            } else {
                // logger.info("setting beta ");
                system.setBeta(0, 1.0 - 1e-10);
                system.reset_x_y();
                system.init(1);
            }

            // system.display();
            checkX();
            // logger.info("iter " + iter);
        } while ((Math.abs(system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) > 1e-3 && !(iter > 20))
                || iter < 4);
        // checkX();
        // logger.info("iter " + iter);
        // system.setNumberOfPhases(system.getNumberOfPhases()+1);
    }
}
