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
 * chemicalReactionList.java
 *
 * Created on 4. februar 2001, 15:32
 */
package neqsim.chemicalReactions.chemicalReaction;

import Jama.*;
import java.util.*;
import neqsim.thermo.ThermodynamicConstantsInterface;
import static neqsim.thermo.ThermodynamicConstantsInterface.R;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ChemicalReactionList extends Object implements ThermodynamicConstantsInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;

    ArrayList chemicalReactionList = new ArrayList();
    String[] reactiveComponentList;
    double[][] reacMatrix;
    double[][] reacGMatrix;
    double[][] tempReacMatrix;
    double[][] tempStocMatrix;

    /** Creates new chemicalReactionList */
    public ChemicalReactionList() {
    }

    public void readReactions(SystemInterface system) {
        chemicalReactionList.clear();
        StringTokenizer tokenizer;
        String token;
        ArrayList names = new ArrayList();
        ArrayList stocCoef = new ArrayList();
        ArrayList referenceType = new ArrayList();
        double r = 0, refT = 0, actH;
        double[] K = new double[4];
        boolean useReaction = false;
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        try {

            if (system.getModelName().equals("Kent Eisenberg-model")) {
                // System.out.println("selecting Kent-Eisenberg reaction set");
                dataSet = database.getResultSet("SELECT * FROM reactiondatakenteisenberg");
            } else {
                // System.out.println("selecting standard reaction set");
                dataSet = database.getResultSet("SELECT * FROM reactiondata");
            }

            double[] coefArray;
            String[] nameArray;
            dataSet.next();
            do {
                useReaction = Integer.parseInt(dataSet.getString("usereaction")) == 1;
                if (useReaction) {
                    names.clear();
                    stocCoef.clear();
                    String reacname = dataSet.getString("NAME");
                    // System.out.println("name " +reacname );
                    K[0] = Double.parseDouble(dataSet.getString("K1"));
                    K[1] = Double.parseDouble(dataSet.getString("K2"));
                    K[2] = Double.parseDouble(dataSet.getString("K3"));
                    K[3] = Double.parseDouble(dataSet.getString("K4"));
                    refT = Double.parseDouble(dataSet.getString("Tref"));
                    r = Double.parseDouble(dataSet.getString("r"));
                    actH = Double.parseDouble(dataSet.getString("ACTENERGY"));

                    java.sql.ResultSet dataSet2 = null;
                    try {
                        neqsim.util.database.NeqSimDataBase database2 = new neqsim.util.database.NeqSimDataBase();
                        dataSet2 = database2
                                .getResultSet("SELECT * FROM stoccoefdata where REACNAME='" + reacname + "'");
                        dataSet2.next();
                        do {
                            // System.out.println("name of cop " +dataSet2.getString("compname").trim());
                            names.add(dataSet2.getString("compname").trim());
                            stocCoef.add((dataSet2.getString("stoccoef")).trim());
                        } while (dataSet2.next());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            dataSet2.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    // System.out.println(names);
                    // System.out.println(stocCoef);
                    nameArray = new String[names.size()];
                    coefArray = new double[nameArray.length];
                    for (int i = 0; i < nameArray.length; i++) {
                        coefArray[i] = Double.parseDouble((String) stocCoef.get(i));
                        nameArray[i] = (String) names.get(i);
                    }

                    ChemicalReaction reaction = new ChemicalReaction(reacname, nameArray, coefArray, K, r, actH, refT);
                    chemicalReactionList.add(reaction);
                    // System.out.println("reaction added ok...");
                }
            } while (dataSet.next());

        } catch (Exception e) {
            String err = e.toString();
            System.out.println("could not add reacton: " + err);
        } finally {
            try {
                dataSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            database.getConnection().close();
        } catch (Exception e) {
            System.out.println("err closing database");
        }
    }

    public ChemicalReaction getReaction(int i) {
        return (ChemicalReaction) chemicalReactionList.get(i);
    }

    public ChemicalReaction getReaction(String name) {
        for (int i = 0; i < chemicalReactionList.size(); i++) {
            if (((ChemicalReaction) chemicalReactionList.get(i)).getName().equals(name)) {
                return (ChemicalReaction) chemicalReactionList.get(i);
            }
        }
        System.out.println("did not find reaction: " + name);
        return null;

    }

    public void removeJunkReactions(String[] names) {
        Iterator e = chemicalReactionList.iterator();
        while (e.hasNext()) {
            // System.out.println("reaction name " +((ChemicalReaction)
            // e.next()).getName());
            if (!((ChemicalReaction) e.next()).reactantsContains(names)) {
                e.remove();
            }
        }
    }

    public void checkReactions(PhaseInterface phase) {
        Iterator e = chemicalReactionList.iterator();
        while (e.hasNext()) {
            ((ChemicalReaction) e.next()).init(phase);
        }
    }

    public void initMoleNumbers(PhaseInterface phase, ComponentInterface[] components, double[][] Amatrix,
            double[] chemRefPot) {
        Iterator e = chemicalReactionList.iterator();
        while (e.hasNext()) {
            ((ChemicalReaction) e.next()).initMoleNumbers(phase, components, Amatrix, chemRefPot);
            // ((ChemicalReaction)e).checkK(system);
        }

    }

    public String[] getAllComponents() {
        HashSet components = new HashSet();
        Iterator e = chemicalReactionList.iterator();
        ChemicalReaction reaction;
        while (e.hasNext()) {
            reaction = (ChemicalReaction) e.next();
            components.addAll(Arrays.asList(reaction.getNames()));
        }
        String[] componentList = new String[components.size()];
        int k = 0;
        Iterator newe = components.iterator();
        while (newe.hasNext()) {
            componentList[k++] = (String) newe.next();
        }
        reactiveComponentList = componentList;
        return componentList;
    }

    public double[][] createReactionMatrix(PhaseInterface phase, ComponentInterface[] components) {
        Iterator e = chemicalReactionList.iterator();
        ChemicalReaction reaction;
        int reactionNumber = 0;
        reacMatrix = new double[chemicalReactionList.size()][reactiveComponentList.length];
        reacGMatrix = new double[chemicalReactionList.size()][reactiveComponentList.length + 1];
        try {
            while (e.hasNext()) {
                reaction = (ChemicalReaction) e.next();
                for (int i = 0; i < components.length; i++) {
                    reacMatrix[reactionNumber][i] = 0;
                    // System.out.println("Component List loop "+components[i].getComponentName());
                    for (int j = 0; j < reaction.getNames().length; j++) {
                        if (components[i].getName().equals(reaction.getNames()[j])) {
                            reacMatrix[reactionNumber][i] = reaction.getStocCoefs()[j];
                            reacGMatrix[reactionNumber][i] = reaction.getStocCoefs()[j];
                        }
                    }
                }
                reacGMatrix[reactionNumber][components.length] = R * phase.getTemperature()
                        * Math.log(reaction.getK(phase));
                reactionNumber++;

            }
        } catch (Exception er) {
            er.printStackTrace();
        }
        Matrix reacMatr;
        if (reacGMatrix.length > 0) {
            reacMatr = new Matrix(reacGMatrix);
        }
        // System.out.println("reac matrix: ");
        // reacMatr.print(10,3);

        return reacMatrix;
    }

    public double[] updateReferencePotentials(PhaseInterface phase, ComponentInterface[] components) {
        for (int i = 0; i < chemicalReactionList.size(); i++) {
            reacGMatrix[i][components.length] = R * phase.getTemperature()
                    * Math.log(((ChemicalReaction) chemicalReactionList.get(i)).getK(phase));
        }
        return calcReferencePotentials();
    }

    public double[][] getReactionGMatrix() {
        return reacGMatrix;
    }

    public double[][] getReactionMatrix() {
        return reacMatrix;
    }

    public double[] calcReferencePotentials() {

        Matrix reacMatr = new Matrix(reacGMatrix);
        Matrix Amatrix = reacMatr.copy().getMatrix(0, chemicalReactionList.size() - 1, 0,
                chemicalReactionList.size() - 1);// new Matrix(reacGMatrix);
        Matrix Bmatrix = reacMatr.copy().getMatrix(0, chemicalReactionList.size() - 1, reacGMatrix[0].length - 1,
                reacGMatrix[0].length - 1);// new Matrix(reacGMatrix);

        if (Amatrix.rank() < chemicalReactionList.size()) {
            System.out.println("rank of A matrix too low !!" + Amatrix.rank());
            return null;
        } else {
            Matrix solv = Amatrix.solve(Bmatrix.timesEquals(-1.0)); // Solves for A*X = -B
            // System.out.println("ref pots");
            // solv.print(10,3);
            return solv.transpose().getArrayCopy()[0];
        }
    }

    public void calcReacMatrix(PhaseInterface phase) {
        tempReacMatrix = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
        tempStocMatrix = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
        ChemicalReaction reaction;

        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
            Iterator e = chemicalReactionList.iterator();
            while (e.hasNext()) {
                reaction = (ChemicalReaction) e.next();
                for (int j = 0; j < reaction.getNames().length; j++) {
                    if (phase.getComponents()[i].getName().equals(reaction.getNames()[j])) {
                        for (int k = 0; k < phase.getNumberOfComponents(); k++) {
                            for (int o = 0; o < reaction.getNames().length; o++) {
                                if (phase.getComponents()[k].getName().equals(reaction.getNames()[o])) {
                                    // System.out.println("comp1 " +
                                    // system.getPhases()[1].getComponents()[i].getComponentName() + " comp2 "
                                    // +system.getPhases()[1].getComponents()[k].getComponentName() );
                                    tempReacMatrix[i][k] = reaction.getRateFactor(phase);
                                    tempStocMatrix[i][k] = -reaction.getStocCoefs()[o];
                                }
                            }
                        }
                    }
                }
            }
        }
        Matrix temp = new Matrix(tempReacMatrix);
        Matrix temp2 = new Matrix(tempStocMatrix);
        // temp.print(10,10);
        // temp2.print(10,10);
    }

    public double[][] getReacMatrix() {
        return tempReacMatrix;
    }

    public double[][] getStocMatrix() {
        return tempStocMatrix;
    }

    public Matrix calcReacRates(PhaseInterface phase, ComponentInterface[] components) {
        Matrix modReacMatrix = new Matrix(reacMatrix).copy();
        // System.out.println(" vol " + system.getPhases()[1].getMolarVolume());

        for (int i = 0; i < chemicalReactionList.size(); i++) {
            for (int j = 0; j < components.length; j++) {
                // System.out.println("mol cons " +
                // components[j].getx()/system.getPhases()[1].getMolarMass());
                modReacMatrix.set(i, j, Math.pow(components[j].getx() * phase.getDensity() / phase.getMolarMass(),
                        Math.abs(reacMatrix[i][j])));
            }
        }
        // modReacMatrix.print(10,10);
        double[] tempForward = new double[chemicalReactionList.size()];
        double[] tempBackward = new double[chemicalReactionList.size()];
        double[] reacVec = new double[chemicalReactionList.size()];

        for (int i = 0; i < chemicalReactionList.size(); i++) {
            tempForward[i] = ((ChemicalReaction) chemicalReactionList.get(i)).getRateFactor();
            tempBackward[i] = ((ChemicalReaction) chemicalReactionList.get(i)).getK(phase)
                    / ((ChemicalReaction) chemicalReactionList.get(i)).getRateFactor();
            for (int j = 0; j < components.length; j++) {
                if (reacMatrix[i][j] > 0) {
                    tempForward[i] *= modReacMatrix.get(i, j);
                }
                if (reacMatrix[i][j] < 0) {
                    tempBackward[i] *= modReacMatrix.get(i, j);
                }
            }
            reacVec[i] = tempForward[i] - tempBackward[i];
        }

        Matrix reacMatVec = new Matrix(reacVec, 1);
        Matrix reacMat = new Matrix(reacMatrix).transpose().times(reacMatVec);

        double[] reactRates = new double[phase.getComponents().length];
        for (int j = 0; j < components.length; j++) {
            reactRates[components[j].getComponentNumber()] = reacMat.get(j, 0);
        }

        for (int j = 0; j < phase.getNumberOfComponents(); j++) {
            // System.out.println("reac " +j + " " + reactRates[j] );
        }

        // System.out.println("reac matrix ");
        // reacMat.print(10,10);
        return reacMat;
    }

    public static void main(String[] args) {
        ChemicalReactionList test = new ChemicalReactionList();
        // test.readReactions();
        // String[] test2 = {"water","MDEA"};
        // test.removeJunkReactions(test2);
        // String[] comp = test.getAllComponents();
        // System.out.println("components: " + comp.length);
    }

    /**
     * Getter for property chemicalReactionList.
     * 
     * @return Value of property chemicalReactionList.
     */
    public java.util.ArrayList getChemicalReactionList() {
        return chemicalReactionList;
    }

    /**
     * Setter for property chemicalReactionList.
     * 
     * @param chemicalReactionList New value of property chemicalReactionList.
     */
    public void setChemicalReactionList(java.util.ArrayList chemicalReactionList) {
        this.chemicalReactionList = chemicalReactionList;
    }

    public double reacHeat(PhaseInterface phase, String comp) {
        ChemicalReaction reaction;
        double heat = 0.0;
        Iterator e = chemicalReactionList.iterator();
        while (e.hasNext()) {
            reaction = (ChemicalReaction) e.next();
            heat += phase.getComponent(comp).getNumberOfmoles() * reaction.getReactionHeat(phase);
            // System.out.println("moles " + phase.getComponent(comp).getNumberOfmoles());
            // System.out.println("reac heat 2 " + heat);
        }
        return heat;
    }
}
