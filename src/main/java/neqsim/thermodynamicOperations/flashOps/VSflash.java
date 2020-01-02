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
 * VUflash.java
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
public class VSflash  extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;
    
    double Sspec=0;
    double Vspec=0;
    Flash pHFlash;
    /** Creates new PHflash */
    public VSflash() {
    }
    
    public VSflash(SystemInterface system, double Vspec, double Sspec) {
        this.system = system;
        this.Sspec = Sspec;
        this.Vspec = Vspec;
//        System.out.println("entalpy " + Hspec);
//        System.out.println("volume " + Vspec);
    }
    
    public void run(){
       
    }
    
    public org.jfree.chart.JFreeChart getJFreeChart(String name){
        return null;
    }
    
}
