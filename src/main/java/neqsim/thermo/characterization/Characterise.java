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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author esol
 */
public class Characterise extends Object implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = 1000;    
    transient SystemInterface system = null;
     TBPCharacterize TBPCharacterise = null;
     private TBPModelInterface TBPfractionModel = null;
     private PlusFractionModel plusFractionModelSelector = null;
     private PlusFractionModelInterface plusFractionModel = null;
     private LumpingModelInterface lumpingModel = null;
    protected String TBPFractionModelName = "PedersenSRK";
    protected LumpingModel lumpingModelSelector = null;
    protected TBPfractionModel TBPfractionModelSelector;

    /**
     * Creates a new instance of TBPCharacterize
     */
    public Characterise() {
    }

    public Characterise(SystemInterface system) {
        this.system = system;

        TBPCharacterise = new neqsim.thermo.characterization.TBPCharacterize(system);

        TBPfractionModelSelector = new TBPfractionModel();
        TBPfractionModel = TBPfractionModelSelector.getModel("");

        lumpingModelSelector = new LumpingModel(system);
        lumpingModel = lumpingModelSelector.getModel("");

        plusFractionModelSelector = new PlusFractionModel(system);
        plusFractionModel = plusFractionModelSelector.getModel("");
    }

    public void setThermoSystem(SystemInterface system) {
        this.system = system;
    }

    public Object clone() {
        Characterise clonedSystem = null;
        try {
            clonedSystem = (Characterise) super.clone();
            //clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations) chemicalReactionOperations.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    public TBPModelInterface getTBPModel() {
        return TBPfractionModel;
    }

    public void setTBPModel(String name) {
        TBPfractionModel = TBPfractionModelSelector.getModel(name);
    }

    public void setLumpingModel(String name) {
        lumpingModel = lumpingModelSelector.getModel(name);
    }

    public void setPlusFractionModel(String name) {
        plusFractionModel = plusFractionModelSelector.getModel(name);
    }

    public PlusFractionModelInterface getPlusFractionModel() {
        return plusFractionModel;
    }

    public LumpingModelInterface getLumpingModel() {
        return lumpingModel;
    }

    public void characterisePlusFraction() {
        system.init(0);
        if (plusFractionModel.hasPlusFraction()) {

            if (plusFractionModel.getMPlus() > plusFractionModel.getMaxPlusMolarMass()) {
                System.out.println("plus fraction molar mass too heavy for " + plusFractionModel.getName());
                plusFractionModel = plusFractionModelSelector.getModel("heavyOil");
                System.out.println("changing to " + plusFractionModel.getName());
            }
            plusFractionModel.characterizePlusFraction(TBPfractionModel);
            lumpingModel.generateLumpedComposition(this);
        }
    }

    /*
     *
     * public boolean addPlusFraction(int start, int end) { plusFractionModel =
     * new PlusCharacterize(system); if (TBPCharacterise.hasPlusFraction()) {
     * TBPCharacterise.groupTBPfractions();
     * TBPCharacterise.generateTBPFractions(); return true; } else {
     * System.out.println("not able to generate pluss fraction"); return false;
     * } }
     *
     *
     * public boolean characterize2() { if (TBPCharacterise.groupTBPfractions())
     * { TBPCharacterise.solve(); return true; } else { System.out.println("not
     * able to generate pluss fraction"); return false; } }
     *
     *
     */
}
