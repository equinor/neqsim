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
 * chemicalReaction.java
 *
 * Created on 4. februar 2001, 15:32
 */

package neqsim.chemicalReactions.chemicalReaction;

import Jama.*;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public class ChemicalReaction implements neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    String[] names, reactantNames, productNames;
    String name;
    double[] stocCoefs = new double[4];
    double[] reacCoefs, prodCoefs, moles;
    boolean shiftSignK = false;
    double[] K = new double[4];
    double rateFactor = 0, activationEnergy, refT;
    double G = 0, lnK = 0;
    int numberOfReactants = 0;

    /** Creates new chemicalReaction */
    public ChemicalReaction() {
    }

    public ChemicalReaction(String name, String[] names, double[] stocCoefs, double[] K, double r,
            double activationEnergy, double refT) {

        /*
         * this.names = names; this.stocCoefs = stocCoefs; this.K = K;
         * 
         */
        this.name = name;
        this.names = new String[names.length];
        this.moles = new double[names.length];
        this.stocCoefs = new double[stocCoefs.length];
        this.K = new double[K.length];
        this.rateFactor = r;
        this.refT = refT;
        this.activationEnergy = activationEnergy;

        System.arraycopy(names, 0, this.names, 0, names.length);
        System.arraycopy(stocCoefs, 0, this.stocCoefs, 0, stocCoefs.length);
        System.arraycopy(K, 0, this.K, 0, K.length);
        numberOfReactants = 0;

        for (int i = 0; i < names.length; i++) {
            // System.out.println("stoc coef: " + this.stocCoefs[i]);
            if (stocCoefs[i] < 0) {
                numberOfReactants++;
            }
        }

        reactantNames = new String[numberOfReactants];
        productNames = new String[names.length - numberOfReactants];
        // this.reacCoefs = new double[numberOfReactants];
        // this.prodCoefs = new double[names.length - numberOfReactants];
        int k = 0, l = 0;
        for (int i = 0; i < names.length; i++) {
            if (stocCoefs[i] < 0) {
                // reacCoefs[k] = stocCoefs[i];
                reactantNames[k++] = this.names[i];
            } else {
                // prodCoefs[l] = stocCoefs[i];
                productNames[l++] = this.names[i];
            }
        }
    }

    public String[] getReactantNames() {
        return reactantNames;
    }

    /**
     * reaction constant at reference temperature
     */
    public double getRateFactor() {
        return rateFactor;
    }

    public double getRateFactor(PhaseInterface phase) {
        // return rateFactor * Math.exp(-activationEnergy/R*(1.0/phase.getTemperature()
        // - 1.0/refT));
        return 2.576e9 * Math.exp(-6024.0 / phase.getTemperature()) / 1000.0;
    }

    public double getK(PhaseInterface phase) {
        double temperature = phase.getTemperature();
        lnK = K[0] + K[1] / (temperature) + K[2] * Math.log(temperature) + K[3] * temperature;
        if (shiftSignK) {
            lnK = -lnK;
        }
        // System.out.println("K " + Math.exp(lnK));
        return Math.exp(lnK);
    }

    public double[] getStocCoefs() {
        return this.stocCoefs;
    }

    public String[] getProductNames() {
        return productNames;
    }

    public String[] getNames() {
        return names;
    }

    public double calcKx(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
        double kx = 1.0;
        for (int i = 0; i < names.length; i++) {
            // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
            kx *= Math.pow(system.getPhase(phaseNumb).getComponent(names[i]).getx(), stocCoefs[i]);
        }
        return kx;
    }

    public double calcKgamma(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
        double kgamma = 1.0;
        for (int i = 0; i < names.length; i++) {
            // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
            if (system.getPhase(phaseNumb).getComponent(names[i]).calcActivity()) {
                kgamma *= Math.pow(system.getPhase(phaseNumb).getActivityCoefficient(
                        system.getPhase(phaseNumb).getComponent(names[i]).getComponentNumber(),
                        system.getPhase(phaseNumb).getComponent("water").getComponentNumber()), stocCoefs[i]);
            }
        }
        return kgamma;
    }

    public double getSaturationRatio(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
        double ksp = 1.0;
        for (int i = 0; i < names.length; i++) {
            // System.out.println("name " + names[i] + " stcoc " + stocCoefs[i]);
            if (stocCoefs[i] < 0) {
                ksp *= Math.pow(system.getPhase(phaseNumb).getComponent(names[i]).getx(), -stocCoefs[i]);
            }
        }
        ksp /= (getK(system.getPhase(phaseNumb)));
        return ksp;
    }

    public double calcK(neqsim.thermo.system.SystemInterface system, int phaseNumb) {
        return calcKx(system, phaseNumb) * calcKgamma(system, phaseNumb);
    }

    /**
     * Generaters initial estimates for the molenumbers
     */
    public void initMoleNumbers(PhaseInterface phase, ComponentInterface[] components, double[][] Amatrix,
            double[] chemRefPot) {
        Matrix tempAmatrix = new Matrix(Amatrix.length, names.length);
        Matrix tempNmatrix = new Matrix(names.length, 1);
        Matrix tempRefPotmatrix = new Matrix(names.length, 1);

        double temp = 0, min = 0;
        for (int i = 0; i < names.length; i++) {
            for (int j = 0; j < components.length; j++) {
                // System.out.println("names: " + names[i] + " " +
                // system.getPhases()[0].getComponents()[j].getName());
                if (this.names[i].equals(components[j].getName())) {
                    for (int k = 0; k < Amatrix.length; k++) {
                        tempAmatrix.set(k, i, Amatrix[k][j]);
                    }
                    tempNmatrix.set(i, 0, components[j].getNumberOfMolesInPhase());
                    tempRefPotmatrix.set(i, 0, chemRefPot[j]);
                }
            }
        }

        Matrix tempBmatrix = tempAmatrix.times(tempNmatrix);
        // System.out.println("atemp: ");
        // tempAmatrix.print(10,2);
        // tempNmatrix.print(10,2);
        // tempBmatrix.print(10,2);
        // tempRefPotmatrix.print(10,2);

        // set AprodMetrix and setAreacMatrix

        Matrix tempAProdmatrix = new Matrix(Amatrix.length, productNames.length);
        Matrix tempAReacmatrix = new Matrix(Amatrix.length, reactantNames.length);
        // Matrix tempNProdmatrix = new Matrix(Amatrix.length, 1);
        // Matrix tempNReacmatrix = new Matrix(Amatrix.length, 1);

        for (int i = 0; i < Amatrix.length; i++) {
            for (int k = 0; k < reactantNames.length; k++) {
                tempAReacmatrix.set(i, k, tempAmatrix.get(i, k));
            }
        }

        for (int i = 0; i < Amatrix.length; i++) {
            for (int k = 0; k < productNames.length; k++) {
                tempAProdmatrix.set(i, k, tempAmatrix.get(i, names.length - 1 - k));
            }
        }

        Matrix tempNProdmatrix = tempAProdmatrix.solve(tempBmatrix);
        Matrix tempNReacmatrix = tempAReacmatrix.solve(tempBmatrix);

        // System.out.println("btemp: ");
        // tempNProdmatrix.print(10,2);
        // tempNReacmatrix.print(10,2);
        // tempAProdmatrix.print(10,2);
        // tempAReacmatrix.print(10,2);

    }

    public void init(PhaseInterface phase) {
        double temperature = phase.getTemperature();
        lnK = K[0] + K[1] / (temperature) + K[2] * Math.log(temperature) + K[3] * temperature;
        // System.out.println("K: " + Math.exp(lnK));
        for (int i = 0; i < names.length; i++) {
            for (int j = 0; j < phase.getNumberOfComponents(); j++) {
                if (this.names[i].equals(phase.getComponent(j).getName())) {
                    moles[i] = phase.getComponent(j).getNumberOfMolesInPhase();
                }
            }
        }

        double cK = lnK;
        for (int i = 0; i < names.length; i++) {
            cK -= Math.log(moles[i] / phase.getNumberOfMolesInPhase()) * stocCoefs[i];
        }

        if (Math.exp(cK) > 1) {
            for (int i = 0; i < stocCoefs.length; i++) {
                stocCoefs[i] = -stocCoefs[i];
            }
            lnK = -lnK;
            shiftSignK = !shiftSignK;
        }
    }

    public void checkK(SystemInterface system) {
        // double cK=Math.log(getK(system.getTemperature()));
        // for(int i=0;i<names.length;i++){
        // // cK -=
        // Math.log(moles[i]/system.getPhases()[0].getNumberOfMolesInPhase())*stocCoefs[i];
        // }
        // System.out.println("ck: " +cK);
    }

    public boolean reactantsContains(String[] names) {
        boolean test = false;
        /*
         * if(reactantNames.length>names.length || productNames.length>names.length ){
         * return false; }
         */

        for (int j = 0; j < reactantNames.length; j++) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(reactantNames[j])) {
                    test = true;
                    break;
                } else {
                    test = false;
                }
            }
            if (test == false) {
                break;
            }
        }

        if (test == false) {
            for (int j = 0; j < productNames.length; j++) {
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(productNames[j])) {
                        test = true;
                        break;
                    } else {
                        test = false;
                    }
                }
                if (test == false) {
                    break;
                }
            }
        }

        return test;

    }

    /**
     * Setter for property rateFactor.
     * 
     * @param rateFactor New value of property rateFactor.
     */
    public void setRateFactor(double rateFactor) {
        this.rateFactor = rateFactor;
    }

    /**
     * Getter for property activationEnergy.
     * 
     * @return Value of property activationEnergy.
     */
    public double getActivationEnergy() {
        return activationEnergy;
    }

    /**
     * Setter for property activationEnergy.
     * 
     * @param activationEnergy New value of property activationEnergy.
     */
    public void setActivationEnergy(double activationEnergy) {
        this.activationEnergy = activationEnergy;
    }

    /**
     * Getter for property reactionHeat. Van't HOffs equation dh = d lnK/dT * R *
     * T^2
     * 
     * @return Value of property reactionHeat.
     */
    public double getReactionHeat(PhaseInterface phase) {
        double diffKt = -K[1] / Math.pow(phase.getTemperature(), 2.0) + K[2] / phase.getTemperature() + K[3];
        double sign = (shiftSignK = true) ? -1.0 : 1.0;
        return sign * diffKt * Math.pow(phase.getTemperature(), 2.0) * R;
    }

    /**
     * Getter for property k.
     * 
     * @return Value of property k.
     */
    public double[] getK() {
        return this.K;
    }

    /**
     * Setter for property k.
     * 
     * @param k New value of property k.
     */
    public void setK(double[] k) {
        this.K = k;
    }

    public void setK(int i, double Kd) {
        this.K[i] = Kd;
    }

    /**
     * Getter for property name.
     * 
     * @return Value of property name.
     */
    public java.lang.String getName() {
        return name;
    }

    /**
     * Setter for property name.
     * 
     * @param name New value of property name.
     */
    public void setName(java.lang.String name) {
        this.name = name;
    }

}
