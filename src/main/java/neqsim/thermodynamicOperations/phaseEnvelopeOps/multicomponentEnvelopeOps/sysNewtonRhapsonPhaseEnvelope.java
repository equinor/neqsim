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

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import Jama.*;
import neqsim.MathLib.nonLinearSolver.newtonRhapson;
import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

public class sysNewtonRhapsonPhaseEnvelope extends Object implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(sysNewtonRhapsonPhaseEnvelope.class);

    double sumx = 0, sumy = 0;
    int neq = 0, iter = 0;
    int ic02p = -100, ic03p = -100, testcrit = 0, npCrit = 0;
    double beta = 0, ds = 0, dTmax = 100, dPmax = 100, avscp = 0.2, TC1 = 0, TC2 = 0, PC1 = 0, PC2 = 0, specVal = 0.0;
    Matrix Jac;
    Matrix fvec;
    Matrix u;
    Matrix uold;
    Matrix Xgij;
    SystemInterface system;
    int numberOfComponents;
    int speceq = 0;
    Matrix a = new Matrix(4, 4);
    Matrix dxds = null;
    Matrix s = new Matrix(1, 4);
    Matrix xg;
    Matrix xcoef;
    newtonRhapson solver;
    boolean etterCP = false;
    boolean etterCP2 = false;
    Matrix xcoefOld;
    double sign = 1.0;

    public sysNewtonRhapsonPhaseEnvelope() {
    }

    /**
     * Creates new nonlin
     */
    public sysNewtonRhapsonPhaseEnvelope(SystemInterface system, int numberOfPhases, int numberOfComponents) {
        this.system = system;
        this.numberOfComponents = numberOfComponents;
        neq = numberOfComponents + 2;
        Jac = new Matrix(neq, neq);
        fvec = new Matrix(neq, 1);
        u = new Matrix(neq, 1);
        Xgij = new Matrix(neq, 4);
        setu();
        uold = u.copy();
        findSpecEqInit();
        //   logger.info("Spec : " +speceq);
        solver = new newtonRhapson();
        solver.setOrder(3);
    }

    public void setfvec2() {
        for (int i = 0; i < numberOfComponents; i++) {
            fvec.set(i, 0,
                    Math.log(
                    system.getPhase(0).getComponents()[i].getFugasityCoeffisient() * system.getPhase(0).getComponents()[i].getx())
                    - Math.log(
                    system.getPhase(1).getComponents()[i].getFugasityCoeffisient() * system.getPhase(1).getComponents()[i].getx()));
        }
        double fsum = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            fsum += system.getPhase(0).getComponents()[i].getx() - system.getPhase(1).getComponents()[i].getx();
        }
        fvec.set(numberOfComponents, 0, fsum);
        fvec.set(numberOfComponents, 0, sumy - sumx);
        fvec.set(numberOfComponents + 1, 0, u.get(speceq, 0) - specVal);
        //  fvec.print(0,10);
    }

    public void setfvec() {
        for (int i = 0; i < numberOfComponents; i++) {
            fvec.set(i, 0, u.get(i, 0) + system.getPhase(0).getComponents()[i].getLogFugasityCoeffisient() - system.getPhase(1).getComponents()[i].getLogFugasityCoeffisient());
            //  fvec.set(i, 0, Math.log(system.getPhase(0).getComponents()[i].getK()) + system.getPhase(0).getComponents()[i].getLogFugasityCoeffisient() - system.getPhase(1).getComponents()[i].getLogFugasityCoeffisient());

        }
        // double fsum = 0.0;
        // for (int i = 0; i < numberOfComponents; i++) {
        //      fsum += system.getPhase(0).getComponents()[i].getx() - system.getPhase(1).getComponents()[i].getx();
        //  }
        //  fvec.set(numberOfComponents, 0, fsum);
        fvec.set(numberOfComponents, 0, sumy - sumx);
        fvec.set(numberOfComponents + 1, 0, u.get(speceq, 0) - specVal);
        //  fvec.print(0,10);
    }

    public void findSpecEqInit() {
        speceq = 0;
        int speceqmin = 0;

        for (int i = 0; i < numberOfComponents; i++) {
            if (system.getPhase(0).getComponents()[i].getTC() > system.getPhase(0).getComponents()[speceq].getTC()) {
                speceq = system.getPhase(0).getComponents()[i].getComponentNumber();
                specVal = u.get(i, 0);
            }
            if (system.getPhase(0).getComponents()[i].getTC() < system.getPhase(0).getComponents()[speceqmin].getTC()) {
                speceqmin = system.getPhase(0).getComponents()[i].getComponentNumber();
            }
        }
        avscp = (system.getPhase(0).getComponents()[speceq].getTC() - system.getPhase(0).getComponents()[speceqmin].getTC()) / 1500.0;
        if (avscp < 0.15) {
            avscp = 0.15;
        }
        if (avscp > 0.5) {
            avscp = 0.5;
        }
        logger.info("avscp: " + avscp);
        dTmax = 20.0;//avscp;
        dPmax = 20.0;//avscp;
        //logger.info("dTmax: " + dTmax + "  dPmax: " + dPmax);
    }

    public void findSpecEq() {
        double max = 0;
        for (int i = 0; i < numberOfComponents + 2; i++) {
            double testVal = Math.abs((Math.exp(u.get(i, 0)) - Math.exp(uold.get(i, 0))) / Math.exp(uold.get(i, 0)));
            if (testVal > max) {
                speceq = i;
                specVal = u.get(i, 0);
                max = testVal;
            }
        }
        logger.info("spec eq " + speceq);
    }

    public final void calc_x_y() {
        sumx = 0;
        sumy = 0;
        for (int j = 0; j < system.getNumberOfPhases(); j++) {
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (j == 0) {
                    sumy += system.getPhase(j).getComponents()[i].getK() * system.getPhase(j).getComponents()[i].getz() / (1.0 - system.getBeta(0) + system.getBeta(0) * system.getPhase(0).getComponents()[i].getK());
                }
                if (j == 1) {
                    sumx += system.getPhase(0).getComponents()[i].getz() / (1.0 - system.getBeta(0) + system.getBeta(0) * system.getPhase(0).getComponents()[i].getK());
                }//
                //phaseArray[j].getComponents()[i].setx(phaseArray[0].getComponents()[i].getx() / phaseArray[0].getComponents()[i].getK());
                //  logger.info("comp: " + j + i + " " + c[j][i].getx());
            }
        }
    }

    public void setJac2() {
        Jac.timesEquals(0.0);
        double dij = 0.0;
        double[] dxidlnk = new double[numberOfComponents];
        double[] dyidlnk = new double[numberOfComponents];
        double tempJ = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            dxidlnk[i] = -system.getBeta() * system.getPhase(1).getComponents()[i].getx() * system.getPhase(0).getComponents()[i].getx() / system.getPhase(0).getComponents()[i].getz();
            dyidlnk[i] = system.getPhase(0).getComponents()[i].getx() + system.getPhase(1).getComponents()[i].getK() * dxidlnk[i];
            //               logger.info("dxidlnk("+i+") "+dxidlnk[i]);
            //            logger.info("dyidlnk("+i+") "+dyidlnk[i]);
        }
        for (int i = 0; i < numberOfComponents; i++) {
            double dlnxdlnK = -1.0 / (1.0 + system.getBeta() * system.getPhase(0).getComponents()[i].getK() - system.getBeta()) * system.getBeta() * system.getPhase(0).getComponents()[i].getK();
            double dlnydlnK = 1.0 - 1.0 / (system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1 - system.getBeta()) * system.getBeta() * system.getPhase(0).getComponents()[i].getK();
            for (int j = 0; j < numberOfComponents; j++) {
                dij = i == j ? 1.0 : 0.0;//Kroneckers deltaget
                tempJ = -dij + dij * dlnydlnK - dij * dlnxdlnK;
                Jac.set(i, j, tempJ);
            }
            tempJ = system.getTemperature() * (system.getPhase(0).getComponents()[i].getdfugdt() - system.getPhase(1).getComponents()[i].getdfugdt());
            Jac.set(i, numberOfComponents, tempJ);
            tempJ = system.getPressure() * (system.getPhase(0).getComponents()[i].getdfugdp() - system.getPhase(1).getComponents()[i].getdfugdp());
            Jac.set(i, numberOfComponents + 1, tempJ);
            Jac.set(numberOfComponents, i, dyidlnk[i] - dxidlnk[i]);
        }
        Jac.set(numberOfComponents + 1, speceq, 1.0);
    }

    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;
        double[] dxidlnk = new double[numberOfComponents];
        double[] dyidlnk = new double[numberOfComponents];
        double tempJ = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            //dxidlnk[i] = -system.getBeta() * system.getPhase(1).getComponents()[i].getx() * system.getPhase(0).getComponents()[i].getx() / system.getPhase(0).getComponents()[i].getz();
            //dyidlnk[i] = system.getPhase(0).getComponents()[i].getx() + system.getPhase(1).getComponents()[i].getK() * dxidlnk[i];

            //dxidlnk[i] = -system.getPhase(1).getComponents()[i].getz() * Math.pow(system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1.0 - system.getBeta(), -2.0) * system.getBeta() * system.getPhase(1).getComponents()[i].getK();
            //dyidlnk[i] = system.getPhase(1).getComponents()[i].getz() / (system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1 - system.getBeta()) * system.getPhase(1).getComponents()[i].getK();

            dxidlnk[i] = -system.getPhase(1).getComponents()[i].getz() * Math.pow(system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1.0 - system.getBeta(), -2.0) * system.getBeta() * system.getPhase(1).getComponents()[i].getK();
            dyidlnk[i] = system.getPhase(1).getComponents()[i].getz() / (system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1.0 - system.getBeta()) * system.getPhase(1).getComponents()[i].getK()
                    - system.getPhase(0).getComponents()[i].getK() * system.getPhase(1).getComponents()[i].getz() / Math.pow(1.0 - system.getBeta() + system.getBeta() * system.getPhase(0).getComponents()[i].getK(), 2.0) * system.getBeta() * system.getPhase(0).getComponents()[i].getK();

            //       - system.getPhase(1).getComponents()[i].getz() * system.getPhase(1).getComponents()[i].getK() * Math.pow(system.getPhase(0).getComponents()[i].getK() * system.getBeta() + 1 - system.getBeta(), -2.0) * system.getBeta() * system.getPhase(1).getComponents()[i].getK();

            //     system.getPhase(0).getComponents()[i].getx() + system.getPhase(1).getComponents()[i].getK() * dxidlnk[i];
            //               logger.info("dxidlnk("+i+") "+dxidlnk[i]);
            //            logger.info("dyidlnk("+i+") "+dyidlnk[i]);
        }
        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                dij = i == j ? 1.0 : 0.0;//Kroneckers delta
                tempJ = dij + system.getPhase(0).getComponents()[i].getdfugdx(j) * dyidlnk[j] - system.getPhase(1).getComponents()[i].getdfugdx(j) * dxidlnk[j];
                Jac.set(i, j, tempJ);
            }
            tempJ = system.getTemperature() * (system.getPhase(0).getComponents()[i].getdfugdt() - system.getPhase(1).getComponents()[i].getdfugdt());
            Jac.set(i, numberOfComponents, tempJ);
            tempJ = system.getPressure() * (system.getPhase(0).getComponents()[i].getdfugdp() - system.getPhase(1).getComponents()[i].getdfugdp());
            Jac.set(i, numberOfComponents + 1, tempJ);
            Jac.set(numberOfComponents, i, dyidlnk[i] - dxidlnk[i]);
            Jac.set(numberOfComponents + 1, i, 0.0);
        }
        Jac.set(numberOfComponents + 1, speceq, 1.0);
    }

    public void setu() {
        for (int i = 0; i < numberOfComponents; i++) {
            u.set(i, 0, Math.log(system.getPhase(0).getComponents()[i].getK()));
        }
        u.set(numberOfComponents, 0, Math.log(system.getTemperature()));
        u.set(numberOfComponents + 1, 0, Math.log(system.getPressure()));
    }

    public void init() {
        for (int i = 0; i < numberOfComponents; i++) {
            system.getPhase(0).getComponents()[i].setK(Math.exp(u.get(i, 0)));
            system.getPhase(1).getComponents()[i].setK(Math.exp(u.get(i, 0)));
        }
        system.setTemperature(Math.exp(u.get(numberOfComponents, 0)));
        system.setPressure(Math.exp(u.get(numberOfComponents + 1, 0)));

        calc_x_y();
        system.calc_x_y();
        system.init(3);
        // logger.info("pressure " + system.getPressure());
        //logger.info("temperature " + system.getTemperature());
    }

    public void calcInc(int np) {
        // First we need the sensitivity vector dX/dS
        init();

        if (np < 5) {
            setu();
            speceq = numberOfComponents + 1;
            specVal = u.get(speceq, 0);
            setJac();
            fvec.timesEquals(0.0);
            fvec.set(speceq, 0, 1.0);
            dxds = Jac.solve(fvec);
            double dp = 0.1;
            ds = dp / dxds.get(numberOfComponents + 1, 0);
            Xgij.setMatrix(0, numberOfComponents + 1, np - 1, np - 1, u.copy());
            //Xgij.print(10,10);
            //dxds.timesEquals(ds);
            u.plusEquals(dxds.times(ds));
            specVal = u.get(speceq, 0);
            // logger.info("ds " + ds + "iter " +iter + "  np  " + np);
        } else {
            findSpecEq();
            //logger.info("dsfør " + ds);
            ds = dxds.get(speceq, 0) * ds;
            //logger.info("ds etter " + ds);
            //specVal = u.get(speceq, 0);
            setfvec();
            setJac();
            fvec.timesEquals(0.0);
            fvec.set(numberOfComponents + 1, 0, 1.0);
            dxds = Jac.solve(fvec);

            //logger.info("ds " + ds + "iter " + iter + "  np  " + np);
            if (iter > 6) {
                ds *= 0.5;
                //logger.info("ds > 6");
            } else {
                if (iter < 3) {
                    ds *= 1.1;
                }
                if (iter == 3) {
                    ds *= 1.0;
                }
                if (iter == 4) {
                    ds *= 0.9;
                }
                if (iter > 4) {
                    ds *= 0.7;
                }
            }
            //logger.info("dTmax " + Math.exp(dxds.get(numberOfComponents, 0) * ds));
            // Now we check wheater this ds is greater than dTmax and dPmax.
            //logger.info("dt " + Math.exp(dxds.get(numberOfComponents, 0) * ds));
            if ((1 + dTmax / system.getTemperature()) < Math.exp(dxds.get(numberOfComponents, 0) * ds)) {
                logger.info("too hig dT");
                ds = Math.log(1 + dTmax / system.getTemperature()) / dxds.get(numberOfComponents, 0);
            }
            //logger.info("dp " + Math.exp(dxds.get(numberOfComponents + 1, 0) * ds));
            if ((1 + dPmax / system.getPressure()) < Math.exp(dxds.get(numberOfComponents + 1, 0) * ds)) {
                logger.info("too hig dP - old ds" + ds);
                ds = Math.log(1 + dPmax / system.getTemperature()) / dxds.get(numberOfComponents + 1, 0);
                //logger.info(" new ds" + ds);
                // ds = sign(dPmax / system.getPressure() / Math.abs(dxds.get(numberOfComponents + 1, 0)), ds);
                //   logger.info("true P");
            }
            if (etterCP2) {
                etterCP2 = false;
            }

            logger.info("ds " + ds + " iter " + iter + "  np  " + np);
            Xgij.setMatrix(0, numberOfComponents + 1, 0, 2, Xgij.getMatrix(0, numberOfComponents + 1, 1, 3));
            Xgij.setMatrix(0, numberOfComponents + 1, 3, 3, u.copy());
            //Xgij.print(10,10);
            s.setMatrix(0, 0, 0, 3, Xgij.getMatrix(speceq, speceq, 0, 3));
            //     Xgij.print(10, 10);
            //            s.print(0,10);
            //           logger.info("ds1 : " + ds);
            calcInc2(np);
            //         logger.info("ds2 : " + ds);

            // Here we find the next point from the polynomial.

        }
    }

    public void calcInc2(int np) {
        for (int i = 0; i < 4; i++) {
            a.set(i, 0, 1.0);
            a.set(i, 1, s.get(0, i));
            a.set(i, 2, s.get(0, i) * s.get(0, i));
            a.set(i, 3, a.get(i, 2) * s.get(0, i));
        }

        double sny = ds * dxds.get(speceq, 0) + s.get(0, 3);
        specVal = sny;
        logger.info("sny " + sny + " sold " + s.get(0, 3));
        for (int j = 0; j < neq; j++) {
            xg = Xgij.getMatrix(j, j, 0, 3);
            try {
                xcoef = a.solve(xg.transpose());
            } catch (Exception e) {
                xcoef = xcoefOld.copy();
                e.printStackTrace();
            }
            // logger.info("xcoef " + j);
            //  xcoef.print(10, 10);
            //logger.info("dss: "  +ds * dxds.get(speceq, 0));
            // specVal = xcoef.get(0, 0) + sny * (xcoef.get(1, 0) + sny * (xcoef.get(2, 0) + sny * xcoef.get(3, 0)));
            u.set(j, 0, xcoef.get(0, 0) + sny * (xcoef.get(1, 0) + sny * (xcoef.get(2, 0) + sny * xcoef.get(3, 0))));
            //      logger.info("u" + j + " " + Math.exp(u.get(j, 0)));
        }
        xcoefOld = xcoef.copy();
        //specVal = u.get(speceq, 0);
        // uold = u.copy();

        double xlnkmax = 0;
        int numb = 0;

        for (int i = 0; i < numberOfComponents; i++) {
            if (Math.abs(u.get(i, 0)) > xlnkmax) {
                xlnkmax = Math.abs(u.get(i, 0));
                numb = i;
            }
        }
        //logger.info("pressure " + system.getPressure() + " new pressure guess " + Math.exp(u.get(numberOfComponents + 1, 0)) + " klnmax: " + u.get(numb, 0) + "  np " + np + " xlnmax " + xlnkmax + " avsxp " + avscp + " K " + Math.exp(u.get(numb, 0)));
        //     logger.info("np: " + np + "  ico2p: " + ic02p + "  ic03p " + ic03p);


        if ((testcrit == -3) && ic03p != np) {
            etterCP2 = true;
            etterCP = true;
            logger.info("Etter CP");
            //System.exit(0);
            ic03p = np;
            testcrit = 0;
            xg = Xgij.getMatrix(numb, numb, 0, 3);

            for (int i = 0; i < 4; i++) {
                a.set(i, 0, 1.0);
                a.set(i, 1, s.get(0, i));
                a.set(i, 2, s.get(0, i) * s.get(0, i));
                a.set(i, 3, a.get(i, 2) * s.get(0, i));
            }

            Matrix xcoef = a.solve(xg.transpose());

            double[] coefs = new double[4];
            coefs[0] = xcoef.get(3, 0);
            coefs[1] = xcoef.get(2, 0);
            coefs[2] = xcoef.get(1, 0);
            coefs[3] = xcoef.get(0, 0) - sign(avscp, ds);
            solver.setConstants(coefs);

            //  logger.info("s4: " + s.get(0,3) + "  coefs " + coefs[0] +" "+  coefs[1]+" "  + coefs[2]+" " + coefs[3]);
            double nys = solver.solve1order(s.get(0, 3));
            //ds = nys - s.get(0,3);
            ds = sign(s.get(0, 3) - nys, ds);
            logger.info("critpoint: " + ds);

            //         ds = -nys - s.get(0,3);
            calcInc2(np);

            TC2 = Math.exp(u.get(numberOfComponents, 0));
            PC2 = Math.exp(u.get(numberOfComponents + 1, 0));
            system.setTC((TC1 + TC2) * 0.5);
            system.setPC((PC1 + PC2) * 0.5);
            system.invertPhaseTypes();
            system.init(3);
            //logger.info("invert phases....");
            //            system.setPhaseType(0,0);
            //            system.setPhaseType(1,1);
            return;
        } else if ((xlnkmax < avscp && testcrit != 1) && (np != ic03p && !etterCP)) {

            logger.info("hei fra her");
            testcrit = 1;
            xg = Xgij.getMatrix(numb, numb, 0, 3);

            for (int i = 0; i < 4; i++) {
                a.set(i, 0, 1.0);
                a.set(i, 1, s.get(0, i));
                a.set(i, 2, s.get(0, i) * s.get(0, i));
                a.set(i, 3, a.get(i, 2) * s.get(0, i));
            }
            //    a.print(0,10);
            //    xg.print(0,10);

            Matrix xcoef = a.solve(xg.transpose());
            //       xcoef.print(0,10);

            double[] coefs = new double[4];
            coefs[0] = xcoef.get(3, 0);
            coefs[1] = xcoef.get(2, 0);
            coefs[2] = xcoef.get(1, 0);
            coefs[3] = xcoef.get(0, 0) - sign(avscp, uold.get(numb, 0));
            solver.setConstants(coefs);
            //logger.info("s4: " + s.get(0, 3) + "  coefs " + coefs[0] + " " + coefs[1] + " " + coefs[2] + " " + coefs[3]);
            double nys = solver.solve1order(s.get(0, 3));

            ds = nys - s.get(0, 3);
            //logger.info("critpoint ds: " + ds);
            npCrit = np;

            calcInc2(np);

            TC1 = Math.exp(u.get(numberOfComponents, 0));
            PC1 = Math.exp(u.get(numberOfComponents + 1, 0));
            return;
        }

        if (testcrit == 1) {
            testcrit = -3;
        }

    }

    public int getNpCrit() {
        return npCrit;
    }

    public double sign(double a, double b) {
        a = Math.abs(a);
        b = b >= 0 ? 1.0 : -1.0;
        return a * b;
    }

    public void solve(int np) {
        Matrix dx;
        iter = 0;
        double dxOldNorm = 1e10;

        do {
            iter++;
            init();
            setfvec();
            setJac();
            try {

                dx = Jac.solve(fvec);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            u.minusEquals(dx);

            if (iter > 10 || dxOldNorm < dx.norm2() || Double.isNaN(dx.norm2())) {
                if (dxOldNorm < dx.norm2() || Double.isNaN(dx.norm2())) {
                    if (Double.isNaN(dx.norm2())) {
                        ds *= 0.5;
                        logger.info("Double.isNaN(dx.norm2())........");
                        break;
                    }
                    if (dxOldNorm < dx.norm2()) {
                        logger.info("dxOldNorm < dx.norm2()........");
                    }
                    //  system.invertPhaseTypes();
                    // break;
                }
                u = uold.copy();
                init();
                ds *= 0.5;
                calcInc2(np);
                logger.info("iter > " + iter);
                iter = 0;
                //calcInc(np);
                try {
                   solve(np);
                }
                 catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                return;
            }
            dxOldNorm = dx.norm2();

            //   logger.info("feilen: " + dx.norm2());
        } while (dx.norm2() > 1.e-10 && !Double.isNaN(dx.norm2()));
        // logger.info("iter: " + iter + " err " + (dx.norm2() / u.norm2()));
        init();
        findSpecEq();
        uold = u.copy();

    }

    public static void main(String args[]) {
        /*
         * sysNewtonRhapson test=new sysNewtonRhapson(); double[] constants =
         * new double[]{0.4,0.4}; test.setx(constants); while
         * (test.nonsol()>1.0e-8) { constants=test.getx();
         * logger.info(constants[0]+" "+constants[1]); } test.nonsol();
         * constants=test.getf(); logger.info(constants[0]+"
         * "+constants[1]); System.exit(0);
         */ }
}
