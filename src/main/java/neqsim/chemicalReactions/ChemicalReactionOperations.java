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

/*
 * ChemicalReactionOperations.java
 *
 * Created on 4. februar 2001, 20:06
 */

package neqsim.chemicalReactions;

import java.util.HashSet;
import java.util.Iterator;
import Jama.Matrix;
import neqsim.chemicalReactions.chemicalEquilibriaum.ChemicalEquilibrium;
import neqsim.chemicalReactions.chemicalEquilibriaum.LinearProgrammingChemicalEquilibrium;
import neqsim.chemicalReactions.chemicalReaction.ChemicalReactionList;
import neqsim.chemicalReactions.kinetics.Kinetics;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class ChemicalReactionOperations
        implements neqsim.thermo.ThermodynamicConstantsInterface, Cloneable {
    private static final long serialVersionUID = 1000;

    SystemInterface system;
    ComponentInterface[] components;
    ChemicalReactionList reactionList = new ChemicalReactionList();
    String[] componentNames;
    String[] allComponentNames;
    String[] elements;
    double[][] Amatrix;
    double[] nVector;
    int iter = 0;
    double[] bVector;
    int phase = 1;
    double[] chemRefPot;
    double[] newMoles;
    double inertMoles = 0.0;
    ChemicalEquilibrium solver;
    double deltaReactionHeat = 0.0;
    boolean firsttime = false;
    Kinetics kineticsSolver;
    LinearProgrammingChemicalEquilibrium initCalc;

    /** Creates new ChemicalReactionOperations */
    public ChemicalReactionOperations() {}

    public ChemicalReactionOperations(SystemInterface system) {
        initCalc = new LinearProgrammingChemicalEquilibrium();
        boolean newcomps = true;
        int old = system.getPhase(0).getNumberOfComponents();
        this.system = system;

        do {
            // if statement added by Procede
            if (!newcomps) {
                break;
            }
            componentNames = system.getComponentNames();
            reactionList.readReactions(system);
            reactionList.removeJunkReactions(componentNames);
            allComponentNames = reactionList.getAllComponents();
            this.addNewComponents();
            if (system.getPhase(0).getNumberOfComponents() == old) {
                newcomps = false;
            }
            old = system.getPhase(0).getNumberOfComponents();
        } while (newcomps);

        components = new ComponentInterface[allComponentNames.length];
        if (components.length > 0) {
            setReactiveComponents();
            reactionList.checkReactions(system.getPhase(1));
            chemRefPot = calcChemRefPot(1);
            elements = getAllElements();

            try {
                initCalc = new LinearProgrammingChemicalEquilibrium(chemRefPot, components,
                        elements, this, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            setComponents();
            Amatrix = initCalc.getA();
            nVector = calcNVector();
            bVector = calcBVector();
        } else {
            system.isChemicalSystem(false);
        }
        kineticsSolver = new Kinetics(this);
    }

    public void setSystem(SystemInterface system) {
        this.system = system;
    }

    @Override
    public Object clone() {
        ChemicalReactionOperations clonedSystem = null;
        try {
            clonedSystem = (ChemicalReactionOperations) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    public void setComponents() {
        for (int j = 0; j < components.length; j++) {
            system.getPhase(phase).getComponents()[components[j].getComponentNumber()] =
                    components[j];
        }
    }

    public void setComponents(int phase) {
        for (int j = 0; j < components.length; j++) {
            system.getPhase(phase).getComponents()[components[j].getComponentNumber()] =
                    components[j];
        }
    }

    public void setReactiveComponents(int phase) {
        for (int j = 0; j < components.length; j++) {
            // System.out.println("comp " + components[j].getComponentNumber());
            components[j] = system.getPhase(phase).getComponent(components[j].getComponentNumber());
        }
    }

    public void setReactiveComponents() {
        int k = 0;
        for (int j = 0; j < componentNames.length; j++) {
            // System.out.println("component " + componentNames[j]);
            String name = componentNames[j];
            for (int i = 0; i < allComponentNames.length; i++) {
                if (name.equals(allComponentNames[i])) {
                    components[k++] = system.getPhase(phase).getComponents()[j];
                    // System.out.println("reactive comp " +
                    // system.getPhases()[1].getComponents()[j].getName());
                }
            }
        }
    }

    public double calcInertMoles(int phase) {
        double reactiveMoles = 0;
        for (int j = 0; j < components.length; j++) {
            reactiveMoles += components[j].getNumberOfMolesInPhase();
        }
        inertMoles = system.getPhase(phase).getNumberOfMolesInPhase() - reactiveMoles;
        // System.out.println("inertmoles = " + inertMoles);
        if (inertMoles < 0) {
            inertMoles = 1.0e-30;
        }
        return inertMoles;
    }

    public void sortReactiveComponents() {
        ComponentInterface tempComp;
        for (int i = 0; i < components.length; i++) {
            for (int j = i + 1; j < components.length; j++) {
                if (components[j].getGibbsEnergyOfFormation() < components[i]
                        .getGibbsEnergyOfFormation()) {
                    tempComp = components[i];
                    components[i] = components[j];
                    components[j] = tempComp;
                    // System.out.println("swich : " + i + " " + j);
                }
            }
        }
    }

    public void addNewComponents() {
        boolean newComp;

        for (int i = 0; i < allComponentNames.length; i++) {
            String name = allComponentNames[i];
            newComp = true;

            for (int j = 0; j < componentNames.length; j++) {
                if (name.equals(componentNames[j])) {
                    newComp = false;
                    break;
                }
            }
            if (newComp) {
                system.addComponent(name, 1.0e-40);
                // System.out.println("new component added: " + name);
            }
        }
    }

    public String[] getAllElements() {
        HashSet<String> elementsLocal = new HashSet<String>();
        for (int j = 0; j < components.length; j++) {
            for (int i = 0; i < components[j].getElements().getElementNames().length; i++) {
                // System.out.println("elements: " +
                // components[j].getElements().getElementNames()[i]);
                elementsLocal.add(components[j].getElements().getElementNames()[i]);
            }
        }

        String[] elementList = new String[elementsLocal.size()];
        int k = 0;
        Iterator<String> newe = elementsLocal.iterator();
        while (newe.hasNext()) {
            elementList[k++] = (String) newe.next();
        }
        /*
         * for(int j=0;j<elementList.length;j++){ System.out.println("elements2: " +elementList[j]);
         * }
         */
        return elementList;
    }

    public boolean hasRections() {
        return components.length > 0;
    }

    public double[] calcNVector() {
        double[] nvec = new double[components.length];
        for (int i = 0; i < components.length; i++) {
            nvec[i] = components[i].getNumberOfMolesInPhase();
            // System.out.println("nvec: " + nvec[i]);
        }
        return nvec;
    }

    public double[] calcBVector() {
        Matrix tempA = new Matrix(Amatrix);
        Matrix tempB = new Matrix(nVector, 1);
        Matrix tempN = tempA.times(tempB.transpose()).transpose();
        // print added by Neeraj
        // System.out.println("b matrix: ");
        // tempN.print(10,2);

        return tempN.getArray()[0];
    }

    public double[] calcChemRefPot(int phase) {
        double[] referencePotentials = new double[components.length];
        reactionList.createReactionMatrix(system.getPhase(phase), components);
        double[] newreferencePotentials =
                reactionList.updateReferencePotentials(system.getPhase(phase), components);
        if (newreferencePotentials != null) {
            for (int i = 0; i < newreferencePotentials.length; i++) {
                referencePotentials[i] = newreferencePotentials[i];
                components[i].setReferencePotential(referencePotentials[i]);
            }
            return referencePotentials;
        } else {
            return null;
        }
    }

    public void updateMoles(int phase) {
        double changeMoles = 0.0;
        for (int i = 0; i < components.length; i++) {
            if (Math.abs(newMoles[i]) > 1e-45) {
                changeMoles += (newMoles[i]
                        - system.getPhase(phase).getComponents()[components[i].getComponentNumber()]
                                .getNumberOfMolesInPhase());
                // System.out.println("update moles first " + (components[i].getComponentName()
                // + " moles " + components[i].getNumberOfMolesInPhase()));
                system.getPhase(phase).addMolesChemReac(components[i].getComponentNumber(),
                        (newMoles[i] - system.getPhase(phase).getComponents()[components[i]
                                .getComponentNumber()].getNumberOfMolesInPhase()));
                // System.out.println("update moles after " + (components[i].getComponentName()
                // + " moles " + components[i].getNumberOfMolesInPhase()));
            }
        }
        // System.out.println("change " + changeMoles);
        system.initTotalNumberOfMoles(changeMoles);// x_solve.get(NELE,0)*n_t);
        system.initBeta(); // this was added for mass trans calc
        system.init_x_y();
        system.init(1);
    }

    public boolean solveChemEq(int type) {
        return solveChemEq(1, type);
    }

    public boolean solveChemEq(int phase, int type) {
        if (this.phase != phase) {
            setReactiveComponents(phase);
            chemRefPot = calcChemRefPot(phase);
        }
        this.phase = phase;
        if (!system.isChemicalSystem()) {
            System.out.println("no chemical reactions will occur...continue");
            return false;
        }

        // System.out.println("pressure1");
        calcChemRefPot(phase);
        // System.out.println("pressure2");
        if (firsttime == true || type == 0) {
            try {
                // System.out.println("Calculating initial estimates");
                nVector = calcNVector();
                bVector = calcBVector();
                calcInertMoles(phase);
                newMoles = initCalc.generateInitialEstimates(system, bVector, inertMoles, phase);
                // Print statement added by Neeraj
                // for (i=0;i<5;i++)
                // System.out.println("new moles "+newMoles[i]);
                updateMoles(phase);
                // System.out.println("finished iniT estimtes ");
                // system.display();
                firsttime = false;
                return true;
            } catch (Exception e) {
                System.out.println("error in chem eq");
                solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phase);
                return solver.solve();
            }
        } else {
            nVector = calcNVector();
            bVector = calcBVector();
            try {
                solver = new ChemicalEquilibrium(Amatrix, bVector, system, components, phase);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return solver.solve();
        }
    }

    public double solveKinetics(int phase, PhaseInterface interPhase, int component) {
        return kineticsSolver.calcReacMatrix(system.getPhase(phase), interPhase, component);
    }

    public Kinetics getKinetics() {
        return kineticsSolver;
    }

    public ChemicalReactionList getReactionList() {
        return reactionList;
    }

    public double reacHeat(int phase, String component) {
        return reactionList.reacHeat(system.getPhase(phase), component);
    }

    /**
     * Getter for property deltaReactionHeat.
     * 
     * @return Value of property deltaReactionHeat.
     */
    public double getDeltaReactionHeat() {
        return deltaReactionHeat;
    }

    /**
     * Setter for property deltaReactionHeat.
     * 
     * @param deltaReactionHeat New value of property deltaReactionHeat.
     */
    public void setDeltaReactionHeat(double deltaReactionHeat) {
        this.deltaReactionHeat = deltaReactionHeat;
    }

    // public Matrix calcReacRates(int phase){
    // // System.out.println(" vol " + system.getPhases()[0].getMolarVolume());
    // return getReactionList().calcReacRates(system.getPhase(phase), components);
    // }

    // /** Setter for property reactionList.
    // * @param reactionList New value of property reactionList.
    // */
    // public void
    // setReactionList(chemicalReactions.chemicalReaction.ChemicalReactionList
    // reactionList) {
    // this.reactionList = reactionList;
    // }
}
