/*
 * PhaseInterface.java
 *
 * Created on 3. juni 2000, 14:45
 */
package neqsim.thermo.phase;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @author  Even Solbraa
 * @version
 */
public interface PhaseInterface extends ThermodynamicConstantsInterface, Cloneable, java.io.Serializable {

        public void addcomponent(String componentName, double molesInPhase, double moles, int compNumber);

        public void setMoleFractions(double[] x);

        public double getPhaseFraction();

        /**
         * @param  unit The unit as a string -
         *              molefraction/wtfraction/molespersec/volumefraction
         * @return      composition array with unit
         */
        public double[] getComposition(String unit);

        public double getCp0();

        /**
         * method to get density of a phase using the AGA8-Detail EoS
         *
         * @return density with unit kg/m3
         */
        public double getDensity_AGA8();

        /**
         * method to get the Joule Thomson Coefficient of a phase note: implemented in
         * phaseEos
         * 
         * @param  unit The unit as a string. Supported units are K/bar, C/bar
         * @return      Joule Thomson coefficient in given unit
         */
        public double getJouleThomsonCoefficient(String unit);

        /**
         * Returns the mole composition vector in unit mole fraction
         */
        public double[] getMolarComposition();

        public void resetPhysicalProperties();

        /**
         * method to return fluid volume
         *
         * @param  unit The unit as a string. Supported units are m3, litre
         * @return      volume in specified unit
         */
        public double getVolume(String unit);

        /**
         * method to return heat capacity ratio calculated as Cp/(Cp-R)
         *
         * @return kappa
         */
        public double getGamma2();

        /**
         * method to return heat capacity ratio/adiabatic index/Poisson constant. The
         * method calculates it as Cp (real) /Cv (real)
         *
         * @return gamma
         */
        public double getGamma();

        public ComponentInterface getComponentWithIndex(int index);

        public double getWtFractionOfWaxFormingComponents();

        public void init();

        public double getdrhodN();

        public void initPhysicalProperties(String type);

        public double getdrhodP();

        public double getdrhodT();

        public double getEnthalpydP();

        public double getEnthalpydT();

        public double getEntropydP();

        public double getEntropydT();

        public double getMoleFraction();

        public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta);

        public void initPhysicalProperties();

        public void setInitType(int initType);

        public ComponentInterface[] getcomponentArray();

        public double getMass();

        public double getWtFraction(SystemInterface system);

        /**
         * method to return molar volume of the phase note: without Peneloux volume
         * correction
         *
         * @return molar volume volume in unit m3/mol*1e5
         */
        public double getMolarVolume();

        /**
         * method to return flow rate of a phase
         *
         * @param  flowunit The unit as a string. Supported units are kg/sec, kg/min,
         *                  m3/sec, m3/min, m3/hr, mole/sec, mole/min, mole/hr
         * @return          flow rate in specified unit
         */
        public double getFlowRate(String flowunit);

        public void setComponentArray(ComponentInterface[] components);

        /**
         * method to get density of a phase using the GERG-2008 EoS
         *
         * @return density with unit kg/m3
         */
        public double getDensity_GERG2008();

        /**
         * method to get density of a phase note: does not use Peneloux volume
         * correction
         *
         * @return density with unit kg/m3
         */
        public double getDensity();

        /**
         * method to get density of a fluid note: with Peneloux volume correction
         *
         * @param  unit The unit as a string. Supported units are kg/m3, mol/m3
         * @return      density in specified unit
         */
        public double getDensity(String unit);

        public void removeComponent(String componentName, double moles, double molesInPhase, int compNumber);

        public double getFugacity(int compNumb);

        /**
         * method to return phase volume note: without Peneloux volume correction
         *
         * @return volume in unit m3*1e5
         */
        public double getTotalVolume();

        /**
         * method to return phase volume with Peneloux volume correction need to call
         * initPhysicalProperties() before this method is called
         *
         * @return volume in unit m3
         */
        public double getCorrectedVolume();

        public boolean hasTBPFraction();

        public double getMolalMeanIonicActivity(int comp1, int comp2);

        public double getMixGibbsEnergy();

        public boolean hasPlusFraction();

        public double getExessGibbsEnergy();

        public double getSresTP();

        public void setPhaseType(int phaseType);

        public void setBeta(double beta);

        /**
         * method to return phase volume note: without Peneloux volume correction
         *
         * @return volume in unit m3*1e5
         */
        public double getVolume();

        public void setProperties(PhaseInterface phase);

        public void useVolumeCorrection(boolean volcor);

        public boolean useVolumeCorrection();

        public double getBeta();

        public double getWtFrac(int component);

        public double getWtFrac(String componentName);

        public void setMixingRuleGEModel(String name);

        public ComponentInterface getComponent(int i);

        public double getActivityCoefficient(int k, int p);

        public void setPressure(double pres);

        public double getpH();

        public void normalize();

        public double getLogPureComponentFugacity(int k);

        public double getPureComponentFugacity(int k, boolean pure);

        public void addMolesChemReac(int component, double dn, double totdn);

        public void setEmptyFluid();

        public void setPhysicalProperties();

        public ComponentInterface getComponent(String name);

        public int getInitType();

        public void setAtractiveTerm(int i);

        public void resetMixingRule(int type);

        /**
         * method to set the temperature of a phase
         *
         * @param temperature in unit Kelvin
         */
        public void setTemperature(double temperature);

        public void addMolesChemReac(int component, double dn);

        public neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface getPhysicalProperties();

        double molarVolume(double pressure, double temperature, double A, double B, int phase)
                        throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException;

        public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp);

        public double getAntoineVaporPressure(double temp);

        double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcAij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcBij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcAT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

        double calcR();

        // double getf();

        double getg();

        public void addMoles(int component, double dn);

        /**
         * method to return enthalpy of a phase in unit Joule
         */
        public double getEnthalpy();

        /**
         * method to return phase enthalpy in a given unit
         *
         * @param  unit The unit as a string. Supported units are J, J/mol, J/kg and
         *              kJ/kg
         * @return      enthalpy in specified unit
         */
        public double getEnthalpy(String unit);

        /**
         * method to return entropy of the phase
         */
        public double getEntropy();

        /**
         * method to return entropy of the phase
         *
         * @param  unit The unit as a string. Supported units are J/K, J/moleK, J/kgK
         *              and kJ/kgK
         * @return      entropy in specified unit
         */
        public double getEntropy(String unit);

        /**
         * method to return viscosity of the phase
         *
         * @return viscosity in unit kg/msec
         */
        public double getViscosity();

        /**
         * method to return viscosity og the phase in a given unit
         *
         * @param  unit The unit as a string. Supported units are kg/msec, cP
         *              (centipoise)
         * @return      viscosity in specified unit
         */
        public double getViscosity(String unit);

        /**
         * method to return conductivity of a phase
         *
         * @return conductivity in unit W/m*K
         */
        public double getConductivity();

        /**
         * method to return conductivity in a given unit
         *
         * @param  unit The unit as a string. Supported units are W/mK, W/cmK
         * @return      conductivity in specified unit
         */
        public double getConductivity(String unit);

        /**
         * method to return conductivity of a phase
         *
         * @return conductivity in unit W/m*K
         */
        public double getThermalConductivity();

        /**
         * method to return conductivity in a given unit
         *
         * @param  unit The unit as a string. Supported units are W/mK, W/cmK
         * @return      conductivity in specified unit
         */
        public double getThermalConductivity(String unit);

        /**
         * method to return specific heat capacity (Cp)
         *
         * @return Cp in unit J/K
         */
        public double getCp();

        /**
         * method to return specific heat capacity (Cp) in a given unit
         *
         * @param  unit The unit as a string. Supported units are J/K, J/molK, J/kgK and
         *              kJ/kgK
         * @return      Cp in specified unit
         */
        public double getCp(String unit);

        public double getHresTP();

        public double getGresTP();

        /**
         * method to return specific heat capacity (Cv)
         *
         * @return Cv in unit J/K
         */
        public double getCv();

        /**
         * method to return specific heat capacity (Cv) in a given unit
         *
         * @param  unit The unit as a string. Supported units are J/K, J/molK, J/kgK and
         *              kJ/kgK
         * @return      Cv in specified unit
         */
        public double getCv(String unit);

        /**
         * method to return real gas isentropic exponent (kappa = - Cp/Cv*(v/p)*dp/dv
         *
         * @return kappa
         */
        public double getKappa();

        public double getCpres();

        public double getZ();

        public void setPhysicalProperties(int type);

        public double getPseudoCriticalPressure();

        public double getPseudoCriticalTemperature();

        PhaseInterface getPhase();

        public int getNumberOfComponents();

        public void setNumberOfComponents(int k);

        /**
         * method to get the Joule Thomson Coefficient of a phase note: implemented in
         * phaseEos
         *
         * @return Joule Thomson coefficient in K/bar
         */
        public double getJouleThomsonCoefficient();

        public void setMixingRule(int type);

        ComponentInterface[] getComponents();

        public double getNumberOfMolesInPhase();

        public int getPhaseType();

        public void calcMolarVolume(boolean test);

        public void setTotalVolume(double volume);

        public void setMolarVolume(double molarVolume);

        public double getPureComponentFugacity(int k);
        // public double getInfiniteDiluteFugacity(int k);

        public double getInfiniteDiluteFugacity(int k, int p);

        public double getHelmholtzEnergy();

        public int getNumberOfMolecularComponents();

        public int getNumberOfIonicComponents();

        public double getFugacity(String compName);
        // double calcA2(PhaseInterface phase, double temperature, double pressure, int
        // numbcomp);
        // double calcB2(PhaseInterface phase, double temperature, double pressure, int
        // numbcomp);

        public double getA();

        public double getB();
        // public double getBi();

        public double getAT();

        public double getATT();
        // public double getAiT();

        public double getGibbsEnergy();

        public Object clone();

        /**
         * method to get temperature
         *
         * @return temperature in unit K
         */
        public double getTemperature();

        /**
         * method to get pressure
         *
         * @return pressure in unit bara
         */
        public double getPressure();

        /**
         * method to return pressure in a given unit
         *
         * @param  unit The unit as a string. Supported units are bara, barg, Pa and MPa
         * @return      pressure in specified unit
         */
        public double getPressure(String unit);

        /**
         * method to get molar mass of a fluid phase
         *
         * @return molar mass in unit kg/mol
         */
        public double getMolarMass();

        public double getInternalEnergy();

        public double getdPdrho();

        public double getdPdTVn();

        public double getdPdVTn();

        public double Fn();

        public double FT();

        public double FV();

        public double FD();

        public double FB();

        public double gb();

        public double fb();

        public double gV();

        public double fv();

        public double FnV();

        public double FnB();

        public double FTT();

        public double FBT();

        public double FDT();

        public double FBV();

        public double FBB();

        public double FDV();

        public double FBD();

        public double FTV();

        public double FVV();

        public double gVV();

        public double gBV();

        public double gBB();

        public double fVV();

        public double fBV();

        public double fBB();

        public double dFdT();

        public double dFdV();

        public double dFdTdV();

        public double dFdVdV();

        public double dFdTdT();

        public double getOsmoticCoefficientOfWater();

        public double getOsmoticCoefficient(int watNumb);

        public double getMeanIonicActivity(int comp1, int comp2);

        public double getLogInfiniteDiluteFugacity(int k, int p);

        public double getLogInfiniteDiluteFugacity(int k);

        public double getActivityCoefficient(int k);

        public int getMixingRuleNumber();

        public void initRefPhases(boolean onlyPure);

        public neqsim.thermo.phase.PhaseInterface getRefPhase(int index);

        public neqsim.thermo.phase.PhaseInterface[] getRefPhase();

        public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase);

        public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase);

        public int getPhysicalPropertyType();

        public void setPhysicalPropertyType(int physicalPropertyType);

        public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
                        String[][] mixRule, double[][] intparam);

        public java.lang.String getPhaseTypeName();

        public void setPhaseTypeName(java.lang.String phaseTypeName);

        public boolean isMixingRuleDefined();

        public void setMixingRuleDefined(boolean mixingRuleDefined);

        public double getActivityCoefficientSymetric(int k);

        public double getActivityCoefficientUnSymetric(int k);

        public double getExessGibbsEnergySymetric();

        public boolean hasComponent(String name);

        public double getLogActivityCoefficient(int k, int p);

        public boolean isConstantPhaseVolume();

        public void setConstantPhaseVolume(boolean constantPhaseVolume);

        /**
         * method to get the speed of sound of a phase note: implemented in phaseEos
         *
         * @return speed of sound in m/s
         */
        public double getSoundSpeed();
}
