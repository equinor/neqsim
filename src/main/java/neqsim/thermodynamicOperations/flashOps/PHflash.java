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
public class PHflash  extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    
    double Hspec=0;
    Flash tpFlash;
    int type=0;
    /** Creates new PHflash */
    public PHflash() {
    }
    
    public PHflash(SystemInterface system, double Hspec, int type) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Hspec = Hspec;
        this.type = type;
    }
    
    public double calcdQdTT(){
        double dQdTT = - system.getTemperature() * system.getTemperature() * system.getCp();
        return dQdTT;
    }
    
    public double calcdQdT(){
        double dQ = system.getEnthalpy()-Hspec;
        return dQ;
    }
    
    public double solveQ(){
        double oldTemp=1.0/system.getTemperature(), nyTemp=1.0/system.getTemperature();
        double iterations=1;
        double error=1.0,erorOld=10.0e10;
        double factor = 0.8;
        do{
         if(error>erorOld){
                factor /= 2.0;
            }
            else if(error<erorOld && factor<0.8){
                factor *= 1.1;
            }
            iterations++;
            oldTemp = nyTemp;
            system.init(2);
            nyTemp = oldTemp - factor*calcdQdT()/calcdQdTT();
           //f(Math.abs(1.0/nyTemp-1.0/oldTemp)>5.0) nyTemp = 1.0/(1.0/oldTemp + Math.signum(1.0/nyTemp-1.0/oldTemp)*5.0);
            if(Double.isNaN(nyTemp)) {
                nyTemp = oldTemp+1.0;
         }
            system.setTemperature(1.0/nyTemp);
            tpFlash.run();
            erorOld = error;
            error = Math.abs((1.0/nyTemp-1.0/oldTemp)/(1.0/oldTemp));
        }
        while(error>1e-8 && iterations<500);
        
        return 1.0/nyTemp;
    }
    
    public void run(){
        tpFlash.run();
        //System.out.println("enthalpy start: " + system.getEnthalpy());
        if(type==0){
            solveQ();
        } else{
            sysNewtonRhapsonPHflash secondOrderSolver = new sysNewtonRhapsonPHflash(system, 2, system.getPhases()[0].getNumberOfComponents(),0);
            secondOrderSolver.setSpec(Hspec);
            secondOrderSolver.solve(1);
            
        }
        //System.out.println("enthalpy: " + system.getEnthalpy());
//        System.out.println("Temperature: " + system.getTemperature());
    }
    
    public org.jfree.chart.JFreeChart getJFreeChart(String name){
        return null;
    }
    
}
