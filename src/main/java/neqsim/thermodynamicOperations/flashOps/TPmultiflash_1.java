

/*
 * TPmultiflash_1.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicOperations.flashOps;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class TPmultiflash_1 extends TPflash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TPmultiflash_1.class);

    // SystemInterface clonedSystem;
    boolean multiPhaseTest = false;
    double dQdbeta[];
    double Qmatrix[][];
    double E[];
    double Q = 0;

    /** Creates new TPmultiflash_1 */
    public TPmultiflash_1() {}

    public TPmultiflash_1(SystemInterface system) {
        super(system);
    }

    public TPmultiflash_1(SystemInterface system, boolean check) {
        super(system, check);
    }

    public void calcMultiPhaseBeta() {}

    public void setXY() {
        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                system.getPhases()[k].getComponents()[i]
                        .setx(system.getPhases()[k].getComponents()[i].getz() / E[i]
                                / system.getPhases()[k].getComponents()[i]
                                        .getFugasityCoeffisient());
            }
        }
    }

    public void calcE() {
        E = new double[system.getPhases()[0].getNumberOfComponents()];

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                E[i] += system.getPhases()[k].getBeta()
                        / system.getPhases()[k].getComponents()[i].getFugasityCoeffisient();
            }
        }
    }

    public double calcQ() {
        Q = 0;
        double betaTotal = 0;
        dQdbeta = new double[system.getNumberOfPhases()];
        Qmatrix = new double[system.getNumberOfPhases()][system.getNumberOfPhases()];

        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            betaTotal += system.getPhases()[k].getBeta();
        }

        Q = betaTotal;
        this.calcE();

        for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
            Q -= Math.log(E[i]) * system.getPhases()[0].getComponents()[i].getz();
        }

        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            dQdbeta[k] = 1.0;
            for (int i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                dQdbeta[k] -= system.getPhases()[0].getComponents()[i].getz() * 1.0 / E[i]
                        / system.getPhases()[k].getComponents()[i].getFugasityCoeffisient();
            }
        }

        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            for (int j = 0; j < system.getNumberOfPhases(); j++) {
                Qmatrix[i][j] = 0;
                for (int k = 0; k < system.getPhases()[0].getNumberOfComponents(); k++) {
                    Qmatrix[i][j] += system.getPhases()[0].getComponents()[k].getz() / (E[k] * E[k]
                            * system.getPhases()[j].getComponents()[k].getFugasityCoeffisient()
                            * system.getPhases()[i].getComponents()[k].getFugasityCoeffisient());
                }
            }
        }
        return Q;
    }

    public void solveBeta() {
        double oldBeta[] = new double[system.getNumberOfPhases()];
        double newBeta[] = new double[system.getNumberOfPhases()];

        Matrix ans;
        int iter = 1;
        do {
            iter++;
            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                oldBeta[k] = system.getPhases()[k].getBeta();
            }

            calcQ();

            Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
            Matrix dQM = new Matrix(dQdbeta, 1);
            Matrix dQdBM = new Matrix(Qmatrix);

            ans = dQdBM.solve(dQM.transpose());
            betaMatrix.minusEquals(ans.times(iter / (iter + 20.0)));
            // ans.print(10,2);
            betaMatrix.print(10, 2);

            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                system.setBeta(k, betaMatrix.get(k, 0));
                if (betaMatrix.get(k, 0) < 0) {
                    system.setBeta(k, 1.0e-9);
                }
                if (betaMatrix.get(k, 0) > 1) {
                    system.setBeta(k, 1.0 - 1e-9);
                }
            }

            calcE();
            setXY();
            system.init(1);
        } while (ans.norm2() > 1e-6);
    }

    @Override
    public void stabilityAnalysis() {
        double[] logWi = new double[system.getPhases()[1].getNumberOfComponents()];
        double[][] Wi = new double[system.getPhases()[1]
                .getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];
        double[] sumw = new double[system.getPhases()[1].getNumberOfComponents()];
        double sumz = 0, err = 0;
        double[] oldlogw = new double[system.getPhases()[1].getNumberOfComponents()];
        double[] d = new double[system.getPhases()[1].getNumberOfComponents()];
        double[][] x = new double[system.getPhases()[1]
                .getNumberOfComponents()][system.getPhases()[0].getNumberOfComponents()];

        SystemInterface minimumGibbsEnergySystem;
        ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);

        minimumGibbsEnergySystem = (SystemInterface) system.clone();

        for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
            double numb = 0;
            clonedSystem.add((SystemInterface) system.clone());
            ((SystemInterface) clonedSystem.get(i)).init(0);
            for (int j = 0; j < system.getPhases()[1].getNumberOfComponents(); j++) {
                numb = i == j ? 1.0 : 1.0e-3;
                ((SystemInterface) clonedSystem.get(i)).getPhases()[1].getComponents()[j]
                        .setx(numb);
            }
            ((SystemInterface) clonedSystem.get(i)).init(1);
        }

        lowestGibbsEnergyPhase = 0;

        // logger.info("low gibbs phase " + lowestGibbsEnergyPhase);

        for (int k = 0; k < minimumGibbsEnergySystem.getPhases()[1].getNumberOfComponents(); k++) {
            sumz += minimumGibbsEnergySystem.getPhases()[1].getComponents()[k].getz();
            for (int i = 0; i < minimumGibbsEnergySystem.getPhases()[1]
                    .getNumberOfComponents(); i++) {
                sumw[k] += ((SystemInterface) clonedSystem.get(k)).getPhases()[1].getComponents()[i]
                        .getx();
            }
        }

        for (int k = 0; k < minimumGibbsEnergySystem.getPhases()[1].getNumberOfComponents(); k++) {
            for (int i = 0; i < minimumGibbsEnergySystem.getPhases()[1]
                    .getNumberOfComponents(); i++) {
                ((SystemInterface) clonedSystem.get(k)).getPhases()[1].getComponents()[i].setx(
                        ((SystemInterface) clonedSystem.get(k)).getPhases()[1].getComponents()[i]
                                .getx() / sumw[0]);
                // logger.info("x: " + ((SystemInterface)
                // clonedSystem.get(k)).getPhases()[0].getComponents()[i].getx());
            }
            d[k] = Math.log(
                    minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents()[k]
                            .getx())
                    + Math.log(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase]
                            .getComponents()[k].getFugasityCoeffisient());
            // logger.info("dk: " + d[k]);
        }

        for (int j = 0; j < minimumGibbsEnergySystem.getPhases()[1].getNumberOfComponents(); j++) {
            logWi[j] = 1;
        }

        for (int j = 0; j < system.getPhases()[1].getNumberOfComponents(); j++) {
            do {
                err = 0;
                ((SystemInterface) clonedSystem.get(j)).init(1);
                for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                    oldlogw[i] = logWi[i];
                    logWi[i] =
                            d[i] - Math.log(((SystemInterface) clonedSystem.get(j)).getPhases()[1]
                                    .getComponents()[i].getFugasityCoeffisient());
                    err += Math.abs(logWi[i] - oldlogw[i]);
                    Wi[j][i] = Math.exp(logWi[i]);
                }
                // logger.info("err: " + err);
                sumw[j] = 0;

                for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                    sumw[j] += Math.exp(logWi[i]);
                }

                for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                    ((SystemInterface) clonedSystem.get(j)).getPhases()[1].getComponents()[i]
                            .setx(Math.exp(logWi[i]) / sumw[j]);
                }
            } while (Math.abs(err) > 1e-9);

            tm[j] = 1.0;

            for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                tm[j] -= Math.exp(logWi[i]);
                x[j][i] = ((SystemInterface) clonedSystem.get(j)).getPhases()[1].getComponents()[i]
                        .getx();
                // logger.info("txji: " + x[j][i]);
            }
            logger.info("tm: " + tm[j]);
        }
        int unstabcomp = 0;
        for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
            if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
                system.addPhase();
                unstabcomp = k;
                for (int i = 0; i < system.getPhases()[1].getNumberOfComponents(); i++) {
                    system.getPhases()[2].getComponents()[i].setx(x[k][i]);
                }
                multiPhaseTest = true;
                system.setBeta(system.getNumberOfPhases() - 1,
                        system.getPhase(0).getComponent(unstabcomp).getz());
                return;
            }
        }

        // logger.info("STABILITY ANALYSIS: ");
        // logger.info("tm1: " + tm[0] + " tm2: " + tm[1]);*/
    }

    @Override
    public void run() {
        logger.info("Starting multiphase-flash....");
        stabilityAnalysis();
        system.init(1);

        if (multiPhaseTest && !system.isChemicalSystem()) {
            this.solveBeta();
        }

        double chemdev = 0;
        if (system.isChemicalSystem()) {
            for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
                chemdev = 0.0;
                double xchem[] = new double[system.getPhases()[phase].getNumberOfComponents()];

                for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                    xchem[i] = system.getPhases()[phase].getComponents()[i].getx();
                }

                system.init(1);
                system.getChemicalReactionOperations().solveChemEq(phase, 1);

                for (i = 0; i < system.getPhases()[0].getNumberOfComponents(); i++) {
                    chemdev += Math
                            .abs(xchem[i] - system.getPhases()[phase].getComponents()[i].getx());
                }
                logger.info("chemdev: " + chemdev);
            }
        }
    }
}
