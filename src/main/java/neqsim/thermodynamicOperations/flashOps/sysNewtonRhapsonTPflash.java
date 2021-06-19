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

package neqsim.thermodynamicOperations.flashOps;

import Jama.*;
import neqsim.thermo.system.SystemInterface;

public class sysNewtonRhapsonTPflash implements java.io.Serializable {

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
    Matrix dx;
    Matrix xcoef;

    public sysNewtonRhapsonTPflash() {
    }

    /**
     * Creates new nonlin
     */
    public sysNewtonRhapsonTPflash(SystemInterface system, int numberOfPhases, int numberOfComponents) {
        this.system = system;
        this.numberOfComponents = numberOfComponents;
        neq = numberOfComponents;
        Jac = new Matrix(neq, neq);
        fvec = new Matrix(neq, 1);
        u = new Matrix(neq, 1);
        Xgij = new Matrix(neq, 4);
        setu();
        uold = u.copy();
        // System.out.println("Spec : " +speceq);
    }

    public void setfvec() {
        for (int i = 0; i < numberOfComponents; i++) {

            fvec.set(i, 0,
                    Math.log(system.getPhase(0).getComponents()[i].getFugasityCoeffisient()
                            * system.getPhase(0).getComponents()[i].getx() * system.getPressure())
                            - Math.log(system.getPhase(1).getComponents()[i].getFugasityCoeffisient()
                                    * system.getPhase(1).getComponents()[i].getx() * system.getPressure()));

        }
    }

    public void setJac() {
        Jac.timesEquals(0.0);
        double dij = 0.0;

        double tempJ = 0.0, sumdyidbeta = 0, sumdxidbeta = 0;
        int nofc = numberOfComponents;

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                dij = i == j ? 1.0 : 0.0;// Kroneckers delta
                tempJ = 1.0 / system.getBeta()
                        * (dij / system.getPhase(0).getComponents()[i].getx() - 1.0
                                + system.getPhase(0).getComponents()[i].getdfugdx(j))
                        + 1.0 / (1.0 - system.getBeta()) * (dij / system.getPhase(1).getComponents()[i].getx() - 1.0
                                + system.getPhase(1).getComponents()[i].getdfugdx(j));
                Jac.set(i, j, tempJ);
            }
        }
    }

    public void setu() {
        for (int i = 0; i < numberOfComponents; i++) {
            u.set(i, 0, system.getBeta() * system.getPhase(0).getComponents()[i].getx());
        }
    }

    public void init() {
        double temp = 0;

        for (int i = 0; i < numberOfComponents; i++) {
            temp += u.get(i, 0);
        }

        system.setBeta(temp);
        for (int i = 0; i < numberOfComponents; i++) {
            system.getPhase(0).getComponents()[i].setx(u.get(i, 0) / system.getBeta());
            system.getPhase(1).getComponents()[i]
                    .setx((system.getPhase(0).getComponents()[i].getz() - u.get(i, 0)) / (1.0 - system.getBeta()));
            system.getPhase(0).getComponents()[i]
                    .setK(system.getPhase(0).getComponents()[i].getx() / system.getPhase(1).getComponents()[i].getx());
            system.getPhase(1).getComponents()[i].setK(system.getPhase(0).getComponents()[i].getK());

        }

        system.init(3);
    }

    public double solve() throws Exception {
        try {
            iter++;
            init();
            setfvec();
            setJac();
            dx = Jac.solve(fvec);
            // dx.print(10,10);
            u.minusEquals(dx);
            return (dx.norm2() / u.norm2());
        } catch (Exception e) {
            throw e;
        }
    }
}
