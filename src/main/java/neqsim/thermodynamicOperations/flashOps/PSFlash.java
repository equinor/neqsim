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
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  even solbraa
 * @version
 */
public class PSFlash extends QfuncFlash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    
    double Sspec=0;
    Flash tpFlash;
    int type=0;
    /** Creates new PHflash */
    public PSFlash() {
    }
    
    public PSFlash(SystemInterface system, double Sspec, int type) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Sspec = Sspec;
        this.type = type;
    }
    
    public double calcdQdTT(){
        double cP1=0.0, cP2=0.0;
        
        if(system.getNumberOfPhases()==1) {
            return -system.getPhases()[0].getCp()/system.getTemperature();
        }
        
        double dQdTT =  0.0;
        for(int i=0; i<system.getNumberOfPhases();i++){
            dQdTT -= system.getPhase(i).getCp()/system.getPhase(i).getTemperature();
        }
        return dQdTT;
    }
    
    public double calcdQdT(){
        double dQ = -system.getEntropy()+Sspec;
        return dQ;
    }
    
    public double solveQ(){
        double oldTemp=system.getTemperature(), nyTemp= system.getTemperature();
        double iterations=1;
        double error=1.0,erorOld=10.0e10;
        double factor = 0.5;
        do{
            if(error>erorOld){
                factor /= 2.0;
            }
            else if(error<erorOld && factor<0.5){
                factor *= 1.1;
            }
            iterations++;
            oldTemp = nyTemp;
            system.init(2);
            nyTemp = oldTemp - factor*calcdQdT()/calcdQdTT();
            if(Math.abs(nyTemp-oldTemp)>5.0) {
                nyTemp = oldTemp + Math.signum(nyTemp-oldTemp)*5.0;
            }
            system.setTemperature(nyTemp);
            tpFlash.run();
           // System.out.println("temperature " + 1.0/nyTemp + " iterations " + iterations +" error "+ error + " factor " +factor);
            erorOld = error;
            error = Math.abs((nyTemp-oldTemp)/(nyTemp));
        }
        while(error>1e-8 && iterations<500);
        return nyTemp;
    }
    
    public void onPhaseSolve(){
        
        
    }
    
    public void run(){
        tpFlash.run();
        //System.out.println("Entropy: " + system.getEntropy());
        
        if(type==0){
            solveQ();
        }
        else{
            sysNewtonRhapsonPHflash secondOrderSolver = new sysNewtonRhapsonPHflash(system, 2, system.getPhases()[0].getNumberOfComponents(),1);
            secondOrderSolver.setSpec(Sspec);
            secondOrderSolver.solve(1);
        }
        //System.out.println("Entropy: " + system.getEntropy());
        //System.out.println("Temperature: " + system.getTemperature());
    }
}
