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
 * AtractiveTermBaseClass.java
 *
 * Created on 13. mai 2001, 21:58
 */

package neqsim.thermo.component.atractiveEosTerm;

import neqsim.thermo.component.ComponentEosInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author  esol
 * @version
 */
public class AtractiveTermBaseClass implements AtractiveTermInterface, Cloneable, java.io.Serializable{

    private static final long serialVersionUID = 1000;

    ComponentEosInterface component;
    protected double m;
    protected double parameters[] = new double[3];
    protected double parametersSolid[] = new double[3];
    
    static Logger logger = LogManager.getLogger(AtractiveTermBaseClass.class);
    
    /** Creates new AtractiveTermBaseClass */
    public AtractiveTermBaseClass(ComponentEosInterface component) {
        this.component = component;
    }
    
    public void setm(double val){
         this.m = val;
         logger.info("does not solve for accentric when new m is set... in AccentricBase class");
    }
    
    public Object clone(){
        AtractiveTermBaseClass atractiveTerm = null;
        try{
            atractiveTerm = (AtractiveTermBaseClass) super.clone();
        }
        catch(Exception e) {
            logger.error("Cloning failed.", e);
        }
        
        // atSystem.out.println("m " + m);ractiveTerm.parameters = (double[]) parameters.clone();
         //System.arraycopy(parameters,0, atractiveTerm.parameters, 0, parameters.length);
        return atractiveTerm;
    }
    
    public void init(){
    }
    
    public double diffdiffalphaT(double temperature) {
        return 0;
    }
    
    public double diffdiffaT(double temperature) {
        return 0;
    }
    
    public double aT(double temperature){
        return component.geta();
    }
    
    public double alpha(double temperature) {
        return 1.0;
    }
    
    public double diffaT(double temperature) {
        return 0.0;
    }
    
    public double diffalphaT(double temperature) {
        return 0.0;
    }
    
    public void setParameters(int i, double val){
        parameters[i] = val;
    }
    
    public double getParameters(int i){
        return parameters[i];
    }
    
}
