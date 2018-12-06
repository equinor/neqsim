/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class Recombine {
    private static final long serialVersionUID = 1000;    
    SystemInterface gas, oil;
    private SystemInterface recombinedSystem = null;
    private double GOR = 1000.0;
    private double oilDesnity = 0.8;
    
    public Recombine(SystemInterface gas, SystemInterface oil){
        
    }
    
    public SystemInterface runRecombination(){
        return getRecombinedSystem(); 
    }

    /**
     * @return the GOR
     */
    public double getGOR() {
        return GOR;
    }

    /**
     * @param GOR the GOR to set
     */
    public void setGOR(double GOR) {
        this.GOR = GOR;
    }

    /**
     * @return the oilDesnity
     */
    public double getOilDesnity() {
        return oilDesnity;
    }

    /**
     * @param oilDesnity the oilDesnity to set
     */
    public void setOilDesnity(double oilDesnity) {
        this.oilDesnity = oilDesnity;
    }

    /**
     * @return the recombinedSystem
     */
    public SystemInterface getRecombinedSystem() {
        return recombinedSystem;
    }
    
    
}
