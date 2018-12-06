/*
 * PhaseGEInterface.java
 *
 * Created on 12. juli 2000, 00:26
 */

package neqsim.thermo.phase;

/**
 *
 * @author  Even Solbraa
 * @version 
 */
public interface PhaseGEInterface{
    
     public double getExessGibbsEnergy(PhaseInterface phase, int numberOfComponents, double temperature, double pressure, int phasetype);

}
