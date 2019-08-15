/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */
package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


/**
 *
 * @author  Even Solbraa
 * @version
 */
public class Adjuster extends ProcessEquipmentBaseClass implements ProcessEquipmentInterface {

    private static final long serialVersionUID = 1000;

    public ThermodynamicOperations testOps = null;
    
    private double tolerance = 1e-3;
    private double error = 1e6;

    /** Creates new staticMixer */
    public Adjuster() {
    }

    public Adjuster(String name) {
        super(name);
    }


    public void runTransient() {
        run();
    }

    public void run() {
       
    }

   
    
    public void displayResult() {
        
    }
  
    
    public double getTolerance() {
        return tolerance;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * @return the error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error the error to set
     */
    public void setError(double error) {
        this.error = error;
    }
    
    public static void main(String[] args) {
    	// test code for adjuster...
    }
    
}
