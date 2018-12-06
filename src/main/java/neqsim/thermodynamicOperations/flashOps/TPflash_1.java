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
 * TPflash.java
 *
 * Created on 2. oktober 2000, 22:26
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TPflash_1 extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    
    
    SystemInterface clonedSystem;
    
    /** Creates new TPflash */
    public TPflash_1() {
    }
    
    public TPflash_1(SystemInterface system) {
        this.system = system;
        lnOldOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
        lnK = new double[system.getPhases()[0].getNumberOfComponents()];
        oldoldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
        oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
        deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    }
    
    public TPflash_1(SystemInterface system, boolean solCheck) {
        this(system);
        solidCheck = solCheck;
    }
    
    
    
    public void sucsSubs(){
        deviation = 0;
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            if(system.getPhase(0).getComponent(i).getIonicCharge()!=0){
                Kold = system.getPhase(0).getComponent(i).getK();
                system.getPhase(0).getComponent(i).setK(1.0e-40);
                system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
            }
            else{
                Kold = system.getPhase(0).getComponent(i).getK();
                system.getPhase(0).getComponent(i).setK(Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugasityCoeffisient()) - Math.log(system.getPhase(0).getComponent(i).getFugasityCoeffisient()))*system.getPhase(1).getPressure()/system.getPhase(0).getPressure());
                system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
                deviation += Math.abs(Math.log(system.getPhase(0).getComponent(i).getK()) - Math.log(Kold));
            }
        }
        
        
        try{
            system.calcBeta();
        }
        catch(Exception e){
            e.printStackTrace(System.err);
            System.out.println("error in beta calc" + e.toString());
        }
        if((system.getBeta()>1.0-1e-9 || system.getBeta()<1e-9)) {
            return; //{System.out.println("beta outside range in TPflash-sucsSubs..returning ");};return;stabilityCheck();
        }
        system.calc_x_y();
        system.init(1);
        setNewK();
        
    }
    
    
    
    
    public void accselerateSucsSubs(){
        double vec1=0.0, vec2=0.0, prod1=0.0, prod2=0.0;
        
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            vec1 =  oldDeltalnK[i]*oldoldDeltalnK[i];
            vec2 = Math.pow(oldoldDeltalnK[i],2.0);
            prod1+=vec1;
            prod2+=vec2;
        }
        
        double lambda = prod1/prod2;
        
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            //lnK[i] = lnK[i] + lambda*lambda*oldoldDeltalnK[i]/(1.0-lambda); //  byttet + til -
            lnK[i] +=  lambda/(1.0-lambda)*deltalnK[i];
            system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
            system.getPhase(1).getComponent(i).setK(Math.exp(lnK[i]));
        }
        try{
            system.calcBeta();
        }
        catch(Exception e){
            e.printStackTrace(System.err);
        }
        
        system.calc_x_y();
        system.init(1);
        sucsSubs();
    }
    
    public void setNewK(){
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            lnOldOldOldK[i] = lnOldOldK[i];
            lnOldOldK[i] = lnOldK[i];
            lnOldK[i] =  lnK[i];
            lnK[i] =  Math.log(system.getPhase(1).getComponent(i).getFugasityCoeffisient()) - Math.log(system.getPhase(0).getComponents()[i].getFugasityCoeffisient());
            
            
            oldoldDeltalnK[i] =  lnOldOldK[i] - lnOldOldOldK[i];
            oldDeltalnK[i] = lnOldK[i] - lnOldOldK[i];
            deltalnK[i] = lnK[i] - lnOldK[i];
        }
    }
    
    
    public void resetK(){
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            lnK[i] = lnOldK[i];
            system.getPhase(0).getComponents()[i].setK(Math.exp(lnK[i]));
            system.getPhase(1).getComponents()[i].setK(Math.exp(lnK[i]));
        }
        try{
            system.calcBeta();
            system.calc_x_y();
            system.init(1);
            setNewK();
        }
        catch(Exception e){
            e.printStackTrace(System.err);
        }
        
    }
    
    
    public void run(){
        system.init(0);
        system.setNumberOfPhases(2);
        system.setPhaseType(0,1);
        system.setPhaseType(1,0);
        
        double minimumGibbsEnergy = 0;
        system.init(1);
        if(system.getPhase(0).getGibbsEnergy() < system.getPhase(1).getGibbsEnergy()){
            minimumGibbsEnergy = system.getPhase(0).getGibbsEnergy() ;
        }
        else{
            minimumGibbsEnergy = system.getPhase(1).getGibbsEnergy() ;
        }
        
        for (i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            system.getPhase(0).getComponent(i).setK(system.getPhase(0).getComponent(i).getK()*system.getPhase(1).getPressure()/system.getPhase(0).getPressure());
            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
        }
        
        if(system.isChemicalSystem()){
            system.getChemicalReactionOperations().solveChemEq(0);
            system.getChemicalReactionOperations().solveChemEq(1,1);
        }
        try{
            system.calcBeta();
        }
        catch(Exception e){
            e.printStackTrace(System.err);
        }
        
//        if(system.getBeta()<1e-9){
//            double err=0.0;
//            double temp=0.0;
//            int it=0;
//            
//            do{
//                it++;
//                temp = 0.0; err=0.0;
//                for(int i=0;i<system.getPhase(0).getNumberOfComponents();i++){
//                    temp += system.getPhase(0).getComponent(i).getz()*system.getPhase(0).getComponent(i).getK();
//                }
//                for(int i=0;i<system.getPhase(0).getNumberOfComponents();i++){
//                    double old = system.getPhase(0).getComponents()[i].getx();
//                    system.getPhase(1).getComponent(i).setx(system.getPhase(1).getComponent(i).getz());
//                    system.getPhase(0).getComponent(i).setx(system.getPhase(0).getComponent(i).getz()*system.getPhase(0).getComponent(i).getK()/temp);
//                    err += Math.abs(system.getPhase(0).getComponents()[i].getx() - old);
//                }
//                system.init(1);
//                for(int i=0;i<system.getPhase(0).getNumberOfComponents();i++){
//                    system.getPhase(0).getComponents()[i].setK(Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugasityCoeffisient()) - Math.log(system.getPhase(0).getComponent(i).getFugasityCoeffisient()))*system.getPhase(1).getPressure()/system.getPhase(0).getPressure());
//                    system.getPhase(1).getComponents()[i].setK(system.getPhase(0).getComponent(i).getK());
//                }
//                
//                try{
//                    system.calcBeta();
//                }
//                catch(Exception e){
//                    e.printStackTrace(System.err);
//                }
//            }
//            while(err>1e-6 && it<5);
//        }
        
        system.calc_x_y();
        system.init(1);
        
        int totiter=0;
        double tpdx = 1.0;
        double tpdy = 1.0;
        double dgonRT=1.0;
        boolean passedTests = false;
        
//        if((system.getBeta()<1.0-1e-9 && system.getBeta()>1e-9)){
//            
//            for(int k=0;k<2;k++){
//                sucsSubs();
//            }
//            
//            if((system.getBeta()>1.0-1e-9 || system.getBeta()<1e-9)){
//                tpdx=1.0;tpdy=1.0;dgonRT=1.0;
//            }
//            else if(system.getGibbsEnergy()<(minimumGibbsEnergy*(1.0-1.0e-10))) {
//                tpdx=-1.0;tpdy=-1.0;dgonRT=-1.0;
//            }
//            else{
//                lowestGibbsEnergyPhase = findLowestGibbsEnergyPhase();
//                for(i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
//                    tpdy += system.getPhase(0).getComponent(i).getx()*(Math.log(system.getPhase(0).getComponent(i).getFugasityCoeffisient())+Math.log(system.getPhase(0).getComponents()[i].getx())-Math.log(minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponents()[i].getz())-Math.log(minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponents()[i].getFugasityCoeffisient()));
//                    tpdx += system.getPhase(1).getComponent(i).getx()*(Math.log(system.getPhase(1).getComponent(i).getFugasityCoeffisient())+Math.log(system.getPhase(1).getComponents()[i].getx())-Math.log(minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponents()[i].getz())-Math.log(minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponents()[i].getFugasityCoeffisient()));
//                }
//                
//                dgonRT = system.getPhase(0).getBeta()*tpdy + (1.0-system.getPhase(0).getBeta())*tpdx;
//                
//                if(dgonRT>0){
//                    if(tpdx<0){
//                        for(i=0;i<system.getPhases()[0].getNumberOfComponents();i++){
//                            system.getPhase(0).getComponent(i).setK(Math.exp(Math.log(system.getPhase(1).getComponent(i).getFugasityCoeffisient()) - Math.log(minimumGibbsEnergySystem.getPhases()[lowestGibbsEnergyPhase].getComponents()[i].getFugasityCoeffisient()))*system.getPhase(1).getPressure()/system.getPhase(0).getPressure());
//                            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
//                        }
//                    }
//                    else if(tpdy<0){
//                        for(i=0;i<system.getPhase(0).getNumberOfComponents();i++){
//                            system.getPhase(0).getComponents()[i].setK(Math.exp(Math.log(minimumGibbsEnergySystem.getPhase(lowestGibbsEnergyPhase).getComponent(i).getFugasityCoeffisient())-Math.log(system.getPhase(0).getComponent(i).getFugasityCoeffisient()))*system.getPhase(1).getPressure()/system.getPhase(0).getPressure());
//                            system.getPhase(1).getComponent(i).setK(system.getPhase(0).getComponent(i).getK());
//                        }
//                    }
//                    else{
//                        passedTests = true;
//                    }
//                }
//            }
//        }
        
        
        
        if(passedTests || (dgonRT>0 && tpdx>0 && tpdy>0) || (system.getBeta()>1.0-1e-9 || system.getBeta()<1e-9 || Double.isNaN(system.getBeta()))){
            if(system.checkStability()){
                if(stabilityCheck()){
                    return;
                }
            }
        }
        
        if(!system.doSolidPhaseCheck()){
            system.init(1);
            double gasgib = system.getPhase(0).getGibbsEnergy();
            system.setPhaseType(0,0);
            system.init(1);
            double liqgib = system.getPhase(0).getGibbsEnergy();
            if(gasgib*(1.0-1e-8)<liqgib) {
                system.setPhaseType(0,1);
            }
        }
        
        system.calc_x_y();
        system.init(1);
        gibbsEnergy=system.getGibbsEnergy();
        gibbsEnergyOld = gibbsEnergy;
        
        double chemdev=0;
        
        int accelerateInterval = 7;
        int newtonLimit = 50000;
        do{
            iterations=0;
            do{
                iterations++;
                
                if(iterations<newtonLimit || system.isChemicalSystem()){
                    sucsSubs();
                }
                else if (iterations>=newtonLimit && Math.abs(system.getPhase(0).getPressure()-system.getPhase(1).getPressure())<1e-5){
                    if(iterations==newtonLimit) {
                        secondOrderSolver = new sysNewtonRhapsonTPflash(system, 2, system.getPhases()[0].getNumberOfComponents());
                    }
                    deviation = secondOrderSolver.solve();
                }
                else{
                    sucsSubs();
                }
                
                if((iterations%accelerateInterval)==0 && !system.isChemicalSystem()){
                    accselerateSucsSubs();
                }
                
                gibbsEnergyOld = gibbsEnergy;
                gibbsEnergy=system.getGibbsEnergy();
                
                if((gibbsEnergy-gibbsEnergyOld)/Math.abs(gibbsEnergy)>1e-6 && !system.isChemicalSystem()){
                    resetK();
                    System.out.println("reset K..");
                }
                
                //System.out.println("deviation: " + deviation);
            }
            while ((deviation>1e-8) && (iterations < maxNumberOfIterations));
            //System.out.println("iterations " + iterations);
            if(system.isChemicalSystem()){
                chemdev=0.0;
                
                double xchem[] = new double[system.getPhase(0).getNumberOfComponents()];
                
                for(int phase=1;phase<system.getNumberOfPhases();phase++){
                    for (i=0;i<system.getPhases()[phase].getNumberOfComponents();i++){
                        xchem[i] = system.getPhase(phase).getComponent(i).getx();
                    }
                    
                    system.init(1);
                    system.getChemicalReactionOperations().solveChemEq(phase,1);
                    
                    for (i=0;i<system.getPhases()[phase].getNumberOfComponents();i++){
                        chemdev += Math.abs(xchem[i]-system.getPhase(phase).getComponent(i).getx());
                    }
                }
            }
            //System.out.println("chemdev: " + chemdev + "  iter: " + totiter);
            totiter++;
        }
        while((chemdev>1e-6 && totiter<100) || totiter<2);
        
//        System.out.println("iterations : " + totiter);
//        System.out.println("clonedSystem G : " +clonedSystem.calcGibbsEnergy());
//        System.out.println("system G : " + system.calcGibbsEnergy());
        
        system.init(3);
        if(system.doMultiPhaseCheck()){
            TPmultiflash operation = new TPmultiflash(system, true);
            operation.run();
        }
        if(system.doSolidPhaseCheck()){
            this.solidPhaseFlash();
        }
    }
    
    
     public org.jfree.chart.JFreeChart getJFreeChart(String name){
        return null;
    }
    
    
}
