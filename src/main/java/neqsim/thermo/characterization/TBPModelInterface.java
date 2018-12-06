/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public interface TBPModelInterface {
  
    public double calcTC(double molarMass, double density);

    public double calcPC(double molarMass, double density);

    public double calcm(double molarMass, double density);

    public double calcTB(double molarMass, double density);

    public double calcAcentricFactorKeslerLee(double molarMass, double density);

    public double calcAcentricFactor(double molarMass, double density);

    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density);

    public double calcCriticalVolume(double molarMass, double density);

    public double calcParachorParameter(double molarMass, double density);

    public double calcCriticalViscosity(double molarMass, double density);

    public boolean isCalcm();

    public double calcWatsonCharacterizationFactor(double molarMass, double density);

    public String getName();

}
