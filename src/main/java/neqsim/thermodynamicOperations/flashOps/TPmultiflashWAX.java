package neqsim.thermodynamicOperations.flashOps;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TPmultiflashWAX class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TPmultiflashWAX extends TPflash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TPmultiflashWAX.class);

    // SystemInterface clonedSystem;
    boolean multiPhaseTest = false;
    double dQdbeta[];
    double Qmatrix[][];
    double E[];
    double Q = 0;
    boolean doStabilityAnalysis = true;

    /**
     * <p>
     * Constructor for TPmultiflashWAX.
     * </p>
     */
    public TPmultiflashWAX() {}

    /**
     * <p>
     * Constructor for TPmultiflashWAX.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public TPmultiflashWAX(SystemInterface system) {
        super(system);
    }

    /**
     * <p>
     * Constructor for TPmultiflashWAX.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param check a boolean
     */
    public TPmultiflashWAX(SystemInterface system, boolean check) {
        super(system, check);
    }

    /**
     * <p>
     * calcMultiPhaseBeta.
     * </p>
     */
    public void calcMultiPhaseBeta() {}

    /**
     * <p>
     * setXY.
     * </p>
     */
    public void setXY() {
        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).getz() > 1e-100) {
                    system.getPhase(k).getComponents()[i].setx(system.getPhase(0).getComponents()[i]
                            .getz() / E[i]
                            / system.getPhase(k).getComponents()[i].getFugacityCoefficient());
                }
                if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                        && !system.getPhase(k).getPhaseTypeName().equals("aqueous")) {
                    system.getPhase(k).getComponents()[i].setx(1e-50);
                }
                if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
                        && system.getPhase(k).getPhaseTypeName().equals("aqueous")) {
                    system.getPhase(k).getComponents()[i]
                            .setx(system.getPhase(k).getComponents()[i].getNumberOfmoles()
                                    / system.getPhase(k).getNumberOfMolesInPhase());
                }
                if (system.hasPhaseType("wax")) {
                    system.getPhaseOfType("wax").getComponents()[i].setx(0);
                }
            }
            system.getPhase(k).normalize();
        }
    }

    /**
     * <p>
     * calcE.
     * </p>
     */
    public void calcE() {
        E = new double[system.getPhase(0).getNumberOfComponents()];

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                E[i] += system.getPhase(k).getBeta()
                        / system.getPhase(k).getComponents()[i].getFugacityCoefficient();
            }
        }
    }

    /**
     * <p>
     * calcQ.
     * </p>
     *
     * @return a double
     */
    public double calcQ() {
        Q = 0;
        double betaTotal = 0;
        dQdbeta = new double[system.getNumberOfPhases()];
        Qmatrix = new double[system.getNumberOfPhases()][system.getNumberOfPhases()];

        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            betaTotal += system.getPhase(k).getBeta();
        }

        Q = betaTotal;
        this.calcE();

        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            Q -= Math.log(E[i]) * system.getPhase(0).getComponents()[i].getz();
        }

        for (int k = 0; k < system.getNumberOfPhases(); k++) {
            dQdbeta[k] = 1.0;
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                dQdbeta[k] -= system.getPhase(0).getComponents()[i].getz() * 1.0 / E[i]
                        / system.getPhase(k).getComponents()[i].getFugacityCoefficient();
            }
        }

        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            for (int j = 0; j < system.getNumberOfPhases(); j++) {
                Qmatrix[i][j] = 0;
                for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
                    Qmatrix[i][j] += system.getPhase(0).getComponents()[k].getz() / (E[k] * E[k]
                            * system.getPhase(j).getComponents()[k].getFugacityCoefficient()
                            * system.getPhase(i).getComponents()[k].getFugacityCoefficient());
                }
                if (i == j) {
                    Qmatrix[i][j] += 1e-10;
                }
            }
        }
        return Q;
    }

    /**
     * <p>
     * solveBeta.
     * </p>
     *
     * @param updateFugacities a boolean
     */
    public void solveBeta(boolean updateFugacities) {
        double oldBeta[] = new double[system.getNumberOfPhases()];
        // double newBeta[] = new double[system.getNumberOfPhases()];

        Matrix ans = new Matrix(system.getNumberOfPhases() - 1, 1);

        int iter = 1;
        do {
            iter++;
            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                oldBeta[k] = system.getPhase(k).getBeta();
            }

            calcQ();

            Matrix betaMatrix = new Matrix(oldBeta, 1).transpose();
            Matrix dQM = new Matrix(dQdbeta, 1);
            Matrix dQdBM = new Matrix(Qmatrix);
            try {
                ans = dQdBM.solve(dQM.transpose());
            } catch (Exception e) {
            }
            betaMatrix.minusEquals(ans.times(iter / (iter + 2.0)));
            // ans.print(10,2);
            // betaMatrix.print(10,2);

            for (int k = 0; k < system.getNumberOfPhases(); k++) {
                system.setBeta(k, betaMatrix.get(k, 0));
                if (betaMatrix.get(k, 0) < 0) {
                    system.setBeta(k, 1.0e-9);
                }
                if (betaMatrix.get(k, 0) > 1) {
                    system.setBeta(k, 1.0 - 1e-9);
                }
            }

            if (updateFugacities) {
                // system.init(1);
            }
            calcE();
            setXY();
            if (updateFugacities) {
                system.init(1);
            }
        } while ((ans.norm2() > 1e-6 && iter < 20) || iter < 3);
    }

    /** {@inheritDoc} */
    @Override
    public void stabilityAnalysis() {
        double[] logWi = new double[system.getPhase(0).getNumberOfComponents()];
        double[][] Wi = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
                .getNumberOfComponents()];
        double[] sumw = new double[system.getPhase(0).getNumberOfComponents()];
        double sumz = 0, err = 0;
        double[] oldlogw = new double[system.getPhase(0).getNumberOfComponents()];
        double[] d = new double[system.getPhase(0).getNumberOfComponents()];
        double[][] x = new double[system.getPhase(0).getNumberOfComponents()][system.getPhase(0)
                .getNumberOfComponents()];
        tm = new double[system.getPhase(0).getNumberOfComponents()];

        SystemInterface minimumGibbsEnergySystem;
        ArrayList<SystemInterface> clonedSystem = new ArrayList<SystemInterface>(1);

        int waxphasenumber = 5;
        minimumGibbsEnergySystem = system.clone();
        // minimumGibbsEnergySystem.init(1);
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            double numb = 0;
            if (system.getPhase(0).getComponent(i).getx() < 1e-100) {
                clonedSystem.add(null);
                continue;
            }
            clonedSystem.add(system.clone());
            // (clonedSystem.get(i)).init(0); commented out sept 2005, Even
            // S.
            for (int j = 0; j < system.getPhase(0).getNumberOfComponents(); j++) {
                numb = i == j ? 1.0 : 1.0e-12; // set to 0 by Even Solbraa 23.01.2013 - changed back
                                               // to 1.0e-12 27.04.13
                if (system.getPhase(0).getComponent(j).getz() < 1e-100) {
                    numb = 0;
                }
                (clonedSystem.get(i)).getPhase(waxphasenumber).getComponents()[j].setx(numb);
            }
            if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
                (clonedSystem.get(i)).init(1);
            }
        }

        lowestGibbsEnergyPhase = 0;
        // logger.info("low gibbs phase " + lowestGibbsEnergyPhase);

        for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
            sumz += minimumGibbsEnergySystem.getPhase(0).getComponents()[k].getz();
            for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
                if (!((clonedSystem.get(k)) == null)) {
                    sumw[k] += (clonedSystem.get(k)).getPhase(waxphasenumber).getComponents()[i]
                            .getx();
                }
            }
        }

        for (int k = 0; k < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); k++) {
            for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
                if (!((clonedSystem.get(k)) == null)
                        && system.getPhase(0).getComponent(k).getx() > 1e-100) {
                    (clonedSystem.get(k)).getPhase(waxphasenumber).getComponents()[i].setx(
                            (clonedSystem.get(k)).getPhase(waxphasenumber).getComponents()[i].getx()
                                    / sumw[0]);
                    // logger.info("x: " + (
                    // clonedSystem.get(k)).getPhase(0).getComponents()[i].getx());
                }
            }

            if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
                d[k] = Math
                        .log(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase]
                                .getComponents()[k].getx())
                        + minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase]
                                .getComponents()[k].getLogFugacityCoefficient();
            }
        }

        for (int j = 0; j < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); j++) {
            if (system.getPhase(0).getComponent(j).getz() > 1e-100) {
                logWi[j] = 1.0;
            } else {
                logWi[j] = -10000.0;
            }
        }

        int hydrocarbonTestCompNumb = 0, lightTestCompNumb = 0;
        double Mmax = 0, Mmin = 1e10;
        for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
            if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
                if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) > Mmax) {
                    Mmax = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
                }
                if ((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass()) < Mmin) {
                    Mmin = minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass();
                }
            }
        }
        for (int i = 0; i < minimumGibbsEnergySystem.getPhase(0).getNumberOfComponents(); i++) {
            if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
                if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass())
                        - Mmax) < 1e-5) {
                    hydrocarbonTestCompNumb = i;
                    // logger.info("CHECKING heavy component " + hydrocarbonTestCompNumb);
                }
            }

            if (minimumGibbsEnergySystem.getPhase(0).getComponent(i).isHydrocarbon()) {
                if (Math.abs((minimumGibbsEnergySystem.getPhase(0).getComponent(i).getMolarMass())
                        - Mmin) < 1e-5) {
                    lightTestCompNumb = i;
                    // logger.info("CHECKING light component " + lightTestCompNumb);
                }
            }
        }

        for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
            if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100
                    || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
                    || (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
                            && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)) {
                continue;
            }
            // logger.info("STAB CHECK COMP " +
            // system.getPhase(0).getComponent(j).getComponentName());
            // if(minimumGibbsEnergySystem.getPhase(0).getComponent(j).isInert()) break;
            int iter = 0;
            do {
                iter++;
                err = 0;
                (clonedSystem.get(j)).init(1, waxphasenumber);

                for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    oldlogw[i] = logWi[i];
                    if (!Double.isInfinite(Math
                            .log((clonedSystem.get(j)).getPhase(waxphasenumber).getComponents()[i]
                                    .getLogFugacityCoefficient()))
                            && system.getPhase(0).getComponent(i).getx() > 1e-100) {
                        logWi[i] = d[i] - Math.log(
                                (clonedSystem.get(j)).getPhase(waxphasenumber).getComponents()[i]
                                        .getFugacityCoefficient());
                        if ((clonedSystem.get(j)).getPhase(1).getComponents()[i]
                                .getIonicCharge() != 0) {
                            logWi[i] = -1000.0;
                        }
                    }
                    err += Math.abs(logWi[i] - oldlogw[i]);
                    Wi[j][i] = Math.exp(logWi[i]);
                }
                // logger.info("err: " + err);
                sumw[j] = 0;

                for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    sumw[j] += Math.exp(logWi[i]);
                }

                for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    (clonedSystem.get(j)).getPhase(waxphasenumber).getComponents()[i]
                            .setx(Math.exp(logWi[i]) / sumw[j]);
                }
            } while (Math.abs(err) > 1e-9 && iter < 100);

            tm[j] = 1.0;

            for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
                tm[j] -= Math.exp(logWi[i]);
                x[j][i] = (clonedSystem.get(j)).getPhase(waxphasenumber).getComponents()[i].getx();
                // logger.info("txji: " + x[j][i]);
            }
            if (iter >= 99) {
                logger.info("iter > maxiter multiphase stability ");
                logger.info("error " + Math.abs(err));
                logger.info("tm: " + tm[j]);
            }
            if (tm[j] < -1e-8) {
                break;
            }
        }

        int unstabcomp = 0;
        for (int k = system.getPhase(0).getNumberOfComponents() - 1; k >= 0; k--) {
            if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
                system.addPhase();
                system.setPhaseIndex(system.getNumberOfPhases() - 1, waxphasenumber);
                unstabcomp = k;
                for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
                    system.getPhase(system.getNumberOfPhases() - 1).getComponents()[i]
                            .setx(x[k][i]);
                }
                multiPhaseTest = true;
                system.setBeta(system.getNumberOfPhases() - 1,
                        system.getPhase(0).getComponent(unstabcomp).getz());

                system.init(1);
                system.normalizeBeta();
                return;
            }
        }

        system.normalizeBeta();

        logger.info("STABILITY ANALYSIS: ");
        logger.info("tm1: " + tm[0] + "  tm2: " + tm[1]);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        // logger.info("Starting multiphase-flash....");

        // system.setNumberOfPhases(system.getNumberOfPhases()+1);
        // system.display();
        if (doStabilityAnalysis) {
            stabilityAnalysis();
        }
        doStabilityAnalysis = true;
        // system.init(1);
        // system.display();
        int iterations = 0;
        if (multiPhaseTest && !system.isChemicalSystem()) {
            double oldBeta = 1.0;
            double diff = 1.0, oldDiff = 1.0e10;

            do {
                iterations++;
                oldBeta = system.getBeta(system.getNumberOfPhases() - 1);
                // system.init(1);
                this.solveBeta(true);
                oldDiff = diff;
                diff = Math
                        .abs((system.getBeta(system.getNumberOfPhases() - 1) - oldBeta) / oldBeta);
                // logger.info("diff multiphase " + diff);
            } while (diff > 1e-5 && iterations < 50);
            // this.solveBeta(true);
            if (iterations >= 49) {
                logger.error("error in multiphase flash..did not solve in 50 iterations");
            }
        }

        double chemdev = 0;
        if (system.isChemicalSystem()) {
            for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
                chemdev = 0.0;
                double xchem[] = new double[system.getPhase(phase).getNumberOfComponents()];

                for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    xchem[i] = system.getPhase(phase).getComponents()[i].getx();
                }

                system.init(1);
                system.getChemicalReactionOperations().solveChemEq(phase, 1);

                for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                    chemdev +=
                            Math.abs(xchem[i] - system.getPhase(phase).getComponents()[i].getx());
                }
                logger.info("chemdev: " + chemdev);
            }
        }

        boolean hasRemovedPhase = false;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            if (system.getBeta(i) < 1.1e-9) {
                system.removePhaseKeepTotalComposition(i);
                doStabilityAnalysis = false;
                hasRemovedPhase = true;
            }
        }
        if (!hasRemovedPhase) {
            for (int i = 0; i < system.getNumberOfPhases() - 1; i++) {
                if (Math.abs(system.getPhase(i).getDensity()
                        - system.getPhase(i + 1).getDensity()) < 1.1e-4) {
                    system.removePhaseKeepTotalComposition(i + 1);
                    doStabilityAnalysis = false;
                    hasRemovedPhase = true;
                }
            }
        }
        /*
         * for (int i = 0; i < system.getNumberOfPhases()-1; i++) { if
         * (Math.abs(system.getPhase(i).getDensity()-system.getPhase(i+1).getDensity())< 1e-6 &&
         * !hasRemovedPhase) { system.removePhase(i+1); doStabilityAnalysis=false; hasRemovedPhase =
         * true; } }
         */
        if (hasRemovedPhase) {
            run();
        }
        system.orderByDensity();
    }
}
