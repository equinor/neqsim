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
 * Kinetics.java
 *
 * Created on 3. august 2001, 23:05
 */

package neqsim.chemicalReactions.kinetics;
import java.util.*;
import neqsim.chemicalReactions.ChemicalReactionOperations;
import neqsim.chemicalReactions.chemicalReaction.ChemicalReaction;
import neqsim.thermo.phase.PhaseInterface;
/**
 *
 * @author  esol
 * @version
 */
public class Kinetics implements java.io.Serializable{

    private static final long serialVersionUID = 1000;
    protected ChemicalReactionOperations operations;
    double phiInfinite = 0.0;
    boolean isIrreversible;
    /** Creates new Kinetics */
    public Kinetics() {
    }
    
    public Kinetics(ChemicalReactionOperations operations){
        this.operations = operations;
    }
    
    public void calcKinetics(){
    }
    
    public double calcReacMatrix(PhaseInterface phase, PhaseInterface interPhase, int comp){
        ChemicalReaction reaction;
        double reacCoef = 0.0, irr=0.0, ktemp=0.0, exponent=0.0;
        Iterator e = operations.getReactionList().getChemicalReactionList().iterator();
        phase.getPhysicalProperties().calcEffectiveDiffusionCoefficients();

        while(e.hasNext()){
            reaction = (ChemicalReaction)e.next();
            ktemp = reaction.getRateFactor(interPhase);
            irr = 1.0/reaction.getK(phase);
            //System.out.println("reaction heat " + reaction.getReactionHeat(phase));
            for (int j=0; j<reaction.getNames().length; j++){
                irr *= Math.pow(interPhase.getComponent(reaction.getNames()[j]).getx()*phase.getPhysicalProperties().getDensity()/phase.getComponent(reaction.getNames()[j]).getMolarMass(), -reaction.getStocCoefs()[j]);
//                 System.out.println("reac names " + reaction.getNames()[j]);
//                 System.out.println("stoc coefs " + reaction.getStocCoefs()[j]);
                if(phase.getComponents()[comp].getName().equals(reaction.getNames()[j])){
                    for (int k=0; k<reaction.getNames().length; k++){
                        if(reaction.getStocCoefs()[k]*reaction.getStocCoefs()[j]>0 && !(k==j) && !(phase.getComponent(reaction.getNames()[k]).getName().equals("water"))){
                            exponent = reaction.getStocCoefs()[k]/reaction.getStocCoefs()[j];
                            double molConsAint = interPhase.getComponent(comp).getx()*interPhase.getPhysicalProperties().getDensity()/phase.getComponent(comp).getMolarMass();
                            double molConsB = phase.getComponent(reaction.getNames()[k]).getx()*phase.getPhysicalProperties().getDensity()/phase.getComponent(reaction.getNames()[k]).getMolarMass();
                            ktemp *= Math.pow(molConsB, exponent);
                            phiInfinite = Math.sqrt(phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(comp)/phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(phase.getComponent(reaction.getNames()[k]).getComponentNumber()))
                            + Math.sqrt(phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(phase.getComponent(reaction.getNames()[k]).getComponentNumber())/phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(comp))*molConsB/(exponent*molConsAint);
//                            System.out.println("reac names " + reaction.getNames()[k]);
//                            System.out.println("phi inf " + phiInfinite);
                        }
                    }
                }
            }
            reacCoef += ktemp;
            //System.out.println("irr " + irr);
            if(Math.abs(irr)<1e-3) {
                isIrreversible = true;
            } 
        }
        return reacCoef;
    }
    
    
    public double getPhiInfinite(){
        return phiInfinite;
    }
    
    public double getPseudoFirstOrderCoef(PhaseInterface phase, PhaseInterface interPhase, int comp){
        return calcReacMatrix(phase, interPhase,comp);
    }
    
    /** Getter for property isIrreversible.
     * @return Value of property isIrreversible.
     */
    public boolean isIrreversible() {
        return isIrreversible;
    }
}
