/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package neqsim.thermodynamicOperations.flashOps;

import Jama.Matrix;
import neqsim.MathLib.nonLinearSolver.newtonRhapson;
import neqsim.thermo.system.SystemInterface;

public class sysNewtonRhapsonTPflashNew implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    int neq = 0, iter = 0;
    int ic02p = -100, ic03p = -100, testcrit = 0, npCrit = 0;
    double beta = 0, ds = 0, dTmax = 1, dPmax = 1, avscp = 0.1, TC1 = 0, TC2 = 0, PC1 = 0, PC2 = 0;
    Matrix Jac;
    Matrix fvec;
    Matrix u;
    Matrix uold;
    Matrix Xgij;
    SystemInterface system;
    int numberOfComponents;
    int speceq = 0;
    Matrix a = new Matrix(4, 4);
    Matrix s = new Matrix(1, 4);
    Matrix xg;
    Matrix xcoef;
    newtonRhapson solver;
    boolean etterCP = false;
    boolean etterCP2 = false;

    public sysNewtonRhapsonTPflashNew() {}

    /** Creates new nonlin */
    public sysNewtonRhapsonTPflashNew(SystemInterface system, int numberOfPhases,
            int numberOfComponents) {
        this.system = system;
        this.numberOfComponents = numberOfComponents;
        neq = numberOfComponents + 1;
        Jac = new Matrix(neq, neq);
        fvec = new Matrix(neq, 1);
        u = new Matrix(neq, 1);
        Xgij = new Matrix(neq, 4);
        setu();
        uold = u.copy();
        // System.out.println("Spec : " +speceq);
        solver = new newtonRhapson();
        solver.setOrder(3);
    }

    public void setfvec() {
        for (int i = 0; i < numberOfComponents; i++) {
            fvec.set(i, 0, u.get(i, 0)
                    + Math.log(system.getPhases()[1].getComponents()[i].getFugasityCoeffisient()
                            / system.getPhases()[0].getComponents()[i].getFugasityCoeffisient()));
        }

        double fsum = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            fsum = fsum + system.getPhases()[1].getComponents()[i].getx()
                    - system.getPhases()[0].getComponents()[i].getx();
        }
        fvec.set(numberOfComponents, 0, fsum);
        // fvec.print(0,20);
    }

    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;
        double[] dxidlnk = new double[numberOfComponents];
        double[] dyidlnk = new double[numberOfComponents];

        double[] dxidbeta = new double[numberOfComponents];
        double[] dyidbeta = new double[numberOfComponents];

        double tempJ = 0.0, sumdyidbeta = 0, sumdxidbeta = 0;
        int nofc = numberOfComponents;
        for (int i = 0; i < numberOfComponents; i++) {
            dxidlnk[i] = -system.getBeta() * system.getPhases()[0].getComponents()[i].getx()
                    * system.getPhases()[1].getComponents()[i].getx()
                    / system.getPhases()[0].getComponents()[i].getz();
            dyidlnk[i] = system.getPhases()[1].getComponents()[i].getx()
                    + system.getPhases()[0].getComponents()[i].getK() * dxidlnk[i];

            dyidbeta[i] = (system.getPhases()[0].getComponents()[i].getK()
                    * system.getPhases()[0].getComponents()[i].getz()
                    * (1 - system.getPhases()[0].getComponents()[i].getK()))
                    / Math.pow(1 - system.getBeta()
                            + system.getBeta() * system.getPhases()[0].getComponents()[i].getK(),
                            2);
            dxidbeta[i] = (system.getPhases()[0].getComponents()[i].getz()
                    * (1 - system.getPhases()[0].getComponents()[i].getK()))
                    / Math.pow(1 - system.getBeta()
                            + system.getBeta() * system.getPhases()[0].getComponents()[i].getK(),
                            2);

            sumdyidbeta += dyidbeta[i];
            sumdxidbeta += dxidbeta[i];
        }

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                dij = i == j ? 1.0 : 0.0;// Kroneckers delta
                tempJ = dij + system.getPhases()[1].getComponents()[i].getdfugdx(j) * dyidlnk[j]
                        - system.getPhases()[0].getComponents()[i].getdfugdx(j) * dxidlnk[j];
                Jac.set(i, j, tempJ);
            }
            Jac.set(i, nofc, system.getPhases()[1].getComponents()[i].getdfugdx(i) * dyidbeta[i]
                    - system.getPhases()[0].getComponents()[i].getdfugdx(i) * dxidbeta[i]);
            Jac.set(nofc, i, dyidlnk[i] - dxidlnk[i]);
        }

        Jac.set(nofc, nofc, sumdyidbeta - sumdxidbeta);
    }

    public void setu() {
        for (int i = 0; i < numberOfComponents; i++) {
            u.set(i, 0, Math.log(system.getPhases()[0].getComponents()[i].getK()));
        }

        u.set(numberOfComponents, 0, system.getBeta());
    }

    public void init() {
        for (int i = 0; i < numberOfComponents; i++) {
            system.getPhases()[0].getComponents()[i].setK(Math.exp(u.get(i, 0)));
            system.getPhases()[1].getComponents()[i].setK(Math.exp(u.get(i, 0)));
        }
        system.setBeta(u.get(numberOfComponents, 0));
        system.calc_x_y();
        system.init(3);
    }

    public void solve(int np) {
        Matrix dx;
        iter = 0;
        do {
            iter++;
            init();
            setfvec();
            setJac();
            dx = Jac.solve(fvec);
            u.minusEquals(dx);
            // System.out.println("feilen: "+dx.norm2());
        } while (dx.norm2() / u.norm2() > 1.e-10);// && Double.isNaN(dx.norm2()));
        // System.out.println("iter: "+iter);
        init();
    }
}
