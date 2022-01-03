package neqsim.thermo.system;

import neqsim.chemicalReactions.ChemicalReactionOperations;
import neqsim.physicalProperties.interfaceProperties.InterphasePropertiesInterface;
import neqsim.thermo.characterization.WaxModelInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/*
 * Component_Interface.java
 *
 * Created on 8. april 2000, 21:35
 */
public interface SystemInterface extends Cloneable, java.io.Serializable {

    public void saveFluid(int ID);

    public String getComponentNameTag();

    public void setComponentNameTagOnNormalComponents(String nameTag);

    public void addPhaseFractionToPhase(double fraction, String specification, String fromPhaseName,
            String toPhaseName);

    public void addPhaseFractionToPhase(double fraction, String specification,
            String specifiedStream, String fromPhaseName, String toPhaseName);

    public void renameComponent(String oldName, String newName);

    public void setComponentNameTag(String nameTag);

    /**
     * add components to a fluid. If component already exists, it will be added to the component
     *
     * @param names Names of the components to be added. See NeqSim database for available
     *        components in the database.
     */
    default public void addComponents(String[] names) {
        for (int i = 0; i < names.length; i++) {
            addComponent(names[i], 0.0);
        }
    }

    default public String[][] calcResultTable() {
        return createTable("");
    }

    /**
     * method to return kinematic viscosity in a given unit
     *
     * @param unit The unit as a string. Supported units are m2/sec
     * @return kinematic viscosity in specified unit
     */
    public double getKinematicViscosity(String unit);

    public int getNumberOfComponents();

    /**
     * method to get molar mass of a fluid phase
     * 
     * @param unit The unit as a string. Supported units are kg/mol, gr/mol
     * @return molar mass in given unit
     */
    public double getMolarMass(String unit);

    /**
     * This method is used to set the total molar composition of a plus fluid. The total flow rate
     * will be kept constant. The input mole fractions will be normalized.
     *
     * @param molefractions is a double array taking the molar fraction of the components in the
     *        fluid. THe last molfraction is the mole fraction of the plus component
     * @return Nothing.
     */
    public void setMolarCompositionPlus(double[] molefractions);

    public void saveFluid(int ID, String text);

    /**
     * This method is used to set the total molar composition of a characterized fluid. The total
     * flow rate will be kept constant. The input mole fractions will be normalized.
     *
     * @param molefractions is a double array taking the molar fraction of the components in the
     *        fluid. THe last fraction in the array is the total molefraction of the characterized
     *        components.
     * @return Nothing.
     */
    public void setMolarCompositionOfPlusFluid(double[] molefractions);

    /**
     * method to get the Joule Thomson Coefficient of a system. Based on a phase mole fraction basis
     * average
     * 
     * @param unit The unit as a string. Supported units are K/bar, C/bar
     * @return Joule Thomson coefficient in given unit
     */
    public double getJouleThomsonCoefficient(String unit);

    /**
     * method to return exergy in a given unit
     * 
     * @param unit The unit as a string. Supported units are J, J/mol, J/kg and kJ/kg
     * @param temperatureOfSurroundings in Kelvin
     * @return exergy in specified unit
     */
    public double getExergy(double temperatureOfSurroundings, String exergyUnit);

    /**
     * method to return exergy defined as (h1-T0*s1) in a unit Joule
     * 
     * @param temperatureOfSurroundings in Kelvin
     */
    public double getExergy(double temperatureOfSurroundings);

    /**
     * method to get the Joule Thomson Coefficient of a system. Based on a phase mole fraction basis
     * average
     *
     * @return Joule Thomson coefficient in K/bar
     */
    public double getJouleThomsonCoefficient();

    /**
     * method to return mass of fluid
     *
     * @param unit The unit as a string. Supported units are kg, gr, tons
     * @return volume in specified unit
     */
    public double getMass(String unit);

    public double getMoleFractionsSum();

    /**
     * method to get the speed of sound of a system. THe sound speed is implemented based on a molar
     * average over the phases
     * 
     * @param unit The unit as a string. Supported units are m/s, km/h
     * @return speed of sound in m/s
     */
    public double getSoundSpeed(String unit);

    /**
     * method to get the speed of sound of a system. THe sound speed is implemented based on a molar
     * average over the phases
     *
     * @return speed of sound in m/s
     */
    public double getSoundSpeed();

    public void removePhaseKeepTotalComposition(int specPhase);

    /**
     * Calculates thermodynamic and physical properties of a fluid using initThermoProperties() and
     * initPhysicalProperties();
     */
    public void initProperties();

    /**
     * return two fluid added as a new fluid
     *
     * @param addFluid1 first fluid to add
     * @param addFluid2 second fluid o add
     * @return new fluid
     */
    public static SystemInterface addFluids(SystemInterface addFluid1, SystemInterface addFluid2) {
        SystemInterface newFluid = (SystemInterface) addFluid1.clone();
        newFluid.addFluid(addFluid2);
        return newFluid;
    }

    /**
     * Calculates thermodynamic properties of a fluid using the init(2) method
     */
    public void initThermoProperties();

    public double getInterfacialTension(int phase1, int phase2, String unit);

    /**
     * Calculates physical properties of type propertyName
     */
    public void initPhysicalProperties(String propertyName);

    /**
     * method to return heat capacity ratio calculated as Cp/(Cp-R)
     *
     * @return kappa
     */
    public double getGamma2();

    /**
     * method to return heat capacity ratio/adiabatic index/Poisson constant
     *
     * @return kappa
     */
    public double getGamma();

    /**
     * method to return fluid volume
     *
     * @param unit The unit as a string. Supported units are m3, litre, m3/kg, m3/mol
     * @return volume in specified unit
     */
    public double getVolume(String unit);

    /**
     * method to return flow rate of fluid
     *
     * @param flowunit The unit as a string. Supported units are kg/sec, kg/min, kg/hr m3/sec,
     *        m3/min, m3/hr, mole/sec, mole/min, mole/hr, Sm3/hr, Sm3/day
     * @return flow rate in specified unit
     */
    public double getFlowRate(String flowunit);

    /**
     * method to set the pressure of a fluid (same temperature for all phases)
     *
     * @param newPressure in specified unit
     * @param unit unit can be bar, bara, barg or atm
     */
    public void setPressure(double newPressure, String unit);

    /**
     * method to set the temperature of a fluid (same temperature for all phases)
     *
     * @param newTemperature in specified unit
     * @param unit unit can be C or K (Celcius of Kelvin)
     */
    public void setTemperature(double newTemperature, String unit);

    /**
     * method to return the volume fraction of a phase note: without Peneloux volume correction
     *
     * @param phaseNumber number of the phase to get volume fraction for
     * @return volume fraction
     */
    public double getVolumeFraction(int phaseNumber);

    /**
     * method to return the volume fraction of a phase note: with Peneloux volume correction
     *
     * @param phaseNumber number of the phase to get volume fraction for
     * @return volume fraction
     */
    public double getCorrectedVolumeFraction(int phaseNumber);

    public double getHeatOfVaporization();

    /**
     * method to return total enthalpy
     *
     * @param unit The unit as a string. unit supported units are J, J/mol, J/kg and kJ/kg
     * @return enthalpy in specified unit
     */
    public double getEnthalpy(String unit);

    /**
     * method to return internal energy (U) in a given unit
     *
     * @param unit The unit as a string. unit supported units are J, J/mol, J/kg and kJ/kg
     * @return enthalpy in unit Joule (J)
     */
    public double getInternalEnergy(String unit);

    public boolean isForcePhaseTypes();

    public void setForcePhaseTypes(boolean forcePhaseTypes);

    public void setEmptyFluid();

    public void setMolarFlowRates(double[] moles);

    public void setComponentNames(String[] componentNames);

    public void calc_x_y_nonorm();

    public void saveObjectToFile(String filePath, String fluidName);

    public SystemInterface readObjectFromFile(String filePath, String fluidName);

    public double getLiquidVolume();

    public void resetPhysicalProperties();

    public SystemInterface phaseToSystem(int phaseNumber1, int phaseNumber2);

    public void changeComponentName(String name, String newName);

    public WaxModelInterface getWaxModel();

    public neqsim.thermo.characterization.WaxCharacterise getWaxCharacterisation();

    public SystemInterface phaseToSystem(String phaseName);

    /**
     * method to get the total molar flow rate of individual components in a fluid
     *
     * @return molar flow of individual components in unit mol/sec
     */
    public double[] getMolarRate();

    public PhaseInterface getPhase(String phaseTypeName);

    public int getPhaseIndexOfPhase(String phaseTypeName);

    public void setTotalFlowRate(double flowRate, String flowunit);

    public double[] getMolarComposition();

    public int getNumberOfOilFractionComponents();

    public boolean setHeavyTBPfractionAsPlusFraction();

    public String[] getCapeOpenProperties11();

    public String[] getCapeOpenProperties10();

    public PhaseInterface getLowestGibbsEnergyPhase();

    public double[] getOilFractionNormalBoilingPoints();

    public double[] getOilFractionLiquidDensityAt25C();

    public double[] getOilFractionMolecularMass();

    public int[] getOilFractionIDs();

    public double getMoleFraction(int phaseNumber);

    /**
     * method to return specific heat capacity (Cv)
     *
     * @return Cv in unit J/K
     */
    public double getCv();

    /**
     * method to return specific heat capacity (Cp) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and kJ/kgK
     * @return Cp in specified unit
     */
    public double getCv(String unit);

    public neqsim.thermo.characterization.Characterise getCharacterization();

    /**
     * add a component to a fluid. If component already exists, it will be added to the component
     *
     * @param componentName Name of the component to be added. See NeqSim database for available
     *        components in the database.
     * @param moles number of moles (per second) of the component to be added to the fluid
     */
    public void addComponent(String name);

    /**
     * add a component to a fluid. If component already exists, it will be added to the component
     *
     * @param componentName Name of the component to be added. See NeqSim database for available
     *        components in the database.
     * @param moles number of moles (per second) of the component to be added to the fluid
     */
    public void addComponent(String componenName, double moles);

    public SystemInterface readObject(int ID);

    public String[] getCompIDs();

    public void isImplementedCompositionDeriativesofFugacity(boolean isImpl);

    public void saveObject(int ID, String text);

    public void reset();

    public String[] getCASNumbers();

    public double[] getMolecularWeights();

    public double[] getNormalBoilingPointTemperatures();

    public String[] getCompNames();

    public String[] getCompFormulaes();

    public double getWtFraction(int phaseNumber);

    public boolean isMultiphaseWaxCheck();

    public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck);

    public void setMolarComposition(double[] moles);

    /**
     * return the phase of to specified type if the phase does not exist, the method will return
     * null
     *
     * @param phaseTypeName the phase type to be returned (gas, oil, aqueous, wax, hydrate are
     *        supported)
     */
    public PhaseInterface getPhaseOfType(String phaseTypeName);

    /**
     * add a component to a fluid. I component already exists, it will be added to the component
     *
     * @param componentName Name of the component to be added. See NeqSim database for component in
     *        the database.
     * @param moles number of moles (per second) of the component to be added to the fluid
     * @param phaseNumber the phase number of the phase to add the component to
     */
    public void addComponent(String componentName, double moles, int phaseNumber);

    /**
     * add a component to a fluid. I component already exists, it will be added to the component
     *
     * @param componentName Name of the component to be added. See NeqSim database for component in
     *        the database.
     * @param value rate of the component to be added to the fluid
     * @param unitName the unit of the flow rate (eg. mol/sec, kg/sec, etc.)
     * @param phaseNumber the phase number of the phase to add the component to
     */
    public void addComponent(String componentName, double value, String unitName, int phaseNumber);

    /**
     * add a component to a fluid. I component already exists, it will be added to the component
     *
     * @param componentName Name of the component to be added. See NeqSim database for component in
     *        the database.
     * @param moles rate of the component to be added to the fluid in the specified unit
     * @param unitName the unit of rate (sported units are kg/sec, mol/sec, Nlitre/min, kg/hr,
     *        Sm^3/hr, Sm^3/day, MSm^3/day ..
     */
    public void addComponent(String componentName, double value, String unitName);

    public void setUseTVasIndependentVariables(boolean useTVasIndependentVariables);

    /**
     * method to add true boiling point fraction
     *
     * @param componentName selected name of the component to be added
     * @param numberOfMoles number of moles to be added
     * @param molarMass molar mass of the component in kg/mol
     * @param density density of the component in g/cm3
     */
    public void addTBPfraction(String componentName, double numberOfMoles, double molarMass,
            double density);

    public void addTBPfraction(String componentName, double numberOfMoles, double molarMass,
            double density, double criticalTemperature, double criticalPressure,
            double acentricFactor);

    public void addPlusFraction(String componentName, double numberOfMoles, double molarMass,
            double density);

    public void addSalt(String componentName, double value);

    public void deleteFluidPhase(int phase);

    public void setBmixType(int bmixType);

    public boolean hasSolidPhase();

    public void addSolidComplexPhase(String type);

    /**
     * method to calculate thermodynamic properties of the fluid. The temperature, pressure, number
     * of phases and composition of the phases will be used as basis for calculation.
     *
     * @param number - The number can be 0, 1, 2 or 3. <br>
     *        0: Initialization of a fluid (feed composition will be set for all phases). <br>
     *        1: Calculation of density, fugacities and Z-factor <br>
     *        2: 1 + calculation of enthalpy, entropy, Cp, Cv, and most other thermodynamic
     *        properties <br>
     *        3 - 1+2 + Calculation of composition derivatives of fugacity coefficients <br>
     *        init(1) is faster than init(2). init(2) faster than init(3). <br>
     *        Which init to use is dependent on what properties you need.
     */
    public void init(int number);

    public void resetCharacterisation();

    public int getMaxNumberOfPhases();

    public void setMaxNumberOfPhases(int maxNumberOfPhases);

    public java.lang.String getMixingRuleName();

    public java.lang.String getModelName();

    public void tuneModel(String model, double val, int phase);

    public void addComponent(String componentName, double moles, double TC, double PC, double acs);

    public double getBeta(int phase);

    public void save(String name);

    public SystemInterface setModel(String model);

    public void removeComponent(String name);

    public void setMixingRule(String typename, String GEmodel);

    public void normalizeBeta();

    public int getPhaseIndex(int index);

    public void setInitType(int initType);

    public void checkStability(boolean val);

    public boolean hasPlusFraction();

    public boolean checkStability();

    public int getInitType();

    public void invertPhaseTypes();

    /**
     * method to return fluid volume with Peneloux volume correction
     *
     * @return volume in unit m3
     */
    public double getCorrectedVolume();

    public void readFluid(String fluidName);

    public void calcKIJ(boolean ok);

    public void write(String name, String filename, boolean newfile);

    public void useVolumeCorrection(boolean volcor);

    /**
     * method to set the mixing rule for the fluid
     *
     * @param mixingRuleName the name of the mixing rule. The name can be 'no','classic',
     *        'Huron-Vidal'/'HV', 'Huron-Vidal-T', 'WS'/'Wong-Sandler' , 'classic-CPA', 'classic-T',
     *        'classic-CPA-T', 'classic-Tx'
     */
    public void setMixingRule(String typename);

    public boolean isNumericDerivatives();

    public void setNumericDerivatives(boolean numericDerivatives);

    public void init();

    public java.lang.String getFluidInfo();

    public void setFluidInfo(java.lang.String info);

    public void setPhaseIndex(int index, int phaseIndex);

    public void setPhase(PhaseInterface phase, int numb);

    /**
     * method to read pure component and interaction parameters from the NeqSim database and create
     * temporary tables with parameters for active fluid.
     *
     * @param reset If reset is set to true, new temporary tables with parameters for the added
     *        components will be created. When parameters are needed (eg. when adding components or
     *        when setting a mixing rule) it will try to find them in the temporary tables first eg.
     *        COMPTEMP (for pure component parameters) and INTERTEMP (for interaction parameters).
     *        If reset is set to false it will not create new temporary tables. If a fluid is
     *        created with the same components many times, performance improvements will be
     *        obtained, if temporary tables are created the first time (reset=true), and then the
     *        same tables is used when creating new fluids with the same temporary tables
     *        (reset=false)
     */
    public void createDatabase(boolean reset);

    public void resetDatabase();

    public void setSolidPhaseCheck(boolean test);

    public boolean doSolidPhaseCheck();

    public boolean doMultiPhaseCheck();

    /**
     * method to specify if calculations should check for more than two fluid phases.
     *
     * @param doMultiPhaseCheck Specify if the calculations should check for more than two fluid
     *        phases. Default is two fluid phases (gas and liquid). If set to true the program will
     *        check for gas and multiple liquid phases (eg. gas-oil-aqueous).
     */
    public void setMultiPhaseCheck(boolean doMultiPhaseCheck);

    public void init(int number, int phase);

    public void initNumeric();

    public void display();

    public void addFluid(SystemInterface addSystem);

    public void display(String name);

    public boolean doHydrateCheck();

    public String[][] createTable(String name);

    public void setHydrateCheck(boolean hydrateCheck);

    public double calcBeta() throws neqsim.util.exception.IsNaNException,
            neqsim.util.exception.TooManyIterationsException;

    public void setAllComponentsInPhase(int phase);

    public void initTotalNumberOfMoles(double change);

    public void calc_x_y();

    public PhaseInterface getPhase(int i);

    public void reset_x_y();

    public void isChemicalSystem(boolean temp);

    public void addComponent(int index, double moles, int phaseNumber);

    public void addPhase();

    public void setAtractiveTerm(int i);

    public void setBeta(int phase, double b);

    public void removePhase(int specPhase);

    public SystemInterface phaseToSystem(PhaseInterface newPhase);

    public void setTemperature(double temp);

    public void setTemperature(double newTemperature, int phaseNumber);
    // public void setPressure(double newPressure, int phaseNumber);

    /**
     * method to set the pressure
     *
     * @param pres pressure in unit bara (absolute pressure in bar)
     */
    public void setPressure(double pres);

    /**
     * method to return pressure
     *
     * @return pressure in unit bara
     */
    public double getPressure();

    /**
     * method to return pressure in a given unit
     *
     * @param unit The unit as a string. Supported units are bara, barg, Pa and MPa
     * @return pressure in specified unit
     */
    public double getPressure(String unit);

    public void reInitPhaseType();

    public void setPhysicalPropertyModel(int type);

    public void clearAll();

    public double getPressure(int phaseNumber);

    /**
     * method to get density of a fluid note: without Peneloux volume correction
     *
     * @return density with unit kg/m3
     */
    public double getDensity();

    /**
     * method to get density of a fluid note: with Peneloux volume correction
     *
     * @param unit The unit as a string. Supported units are kg/m3, mol/m3
     * @return density in specified unit
     */
    public double getDensity(String unit);

    /**
     * method to return fluid volume
     *
     * @return volume in unit m3*1e5
     */
    public double getVolume();

    public ChemicalReactionOperations getChemicalReactionOperations();

    public boolean isChemicalSystem();

    /**
     * method to return molar volume of the fluid note: without Peneloux volume correction
     *
     * @return molar volume volume in unit m3/mol*1e5
     */
    public double getMolarVolume();

    /**
     * method to get the total molar mass of a fluid
     *
     * @return molar mass in unit kg/mol
     */
    public double getMolarMass();

    /**
     * method to get the total enthalpy of a fluid
     *
     * @return molar mass in unit J (Joule)
     */
    public double getEnthalpy();

    public void calcInterfaceProperties();

    public InterphasePropertiesInterface getInterphaseProperties();

    public double initBeta();

    public void init_x_y();

    /**
     * method to return total entropy of the fluid
     *
     * @return entropy in unit J/K (Joule/Kelvin)
     */
    public double getEntropy();

    /**
     * method to return total entropy of the fluid
     *
     * @param unit The unit as a string. unit supported units are J/K, J/molK, J/kgK and kJ/kgK
     * @return entropy in specified unit
     */
    public double getEntropy(String unit);

    /**
     * method to return temperature
     *
     * @return temperature in unit Kelvin
     */
    public double getTemperature();

    /**
     * method to return temperature in a given unit
     *
     * @param unit The unit as a string. Supported units are K, C, R
     * @return temperature in specified unit
     */
    public double getTemperature(String unit);

    public double getTemperature(int phaseNumber);

    public double getBeta();

    public void chemicalReactionInit();

    public void initPhysicalProperties();

    public void setBeta(double b);
    // public double getdfugdt(int i, int j);

    public void setPhaseType(int phaseToChange, int newPhaseType);

    public void setNumberOfPhases(int number);

    public double getTC();

    public double getPC();

    public void setTC(double TC);

    public void setPC(double PC);

    public PhaseInterface[] getPhases();

    public int getNumberOfPhases();

    public double getGibbsEnergy();

    /**
     * method to return internal energy (U) in unit J
     *
     * @return internal energy in unit Joule (J)
     */
    public double getInternalEnergy();

    public double getHelmholtzEnergy();

    public ComponentInterface getComponent(String name);

    public ComponentInterface getComponent(int number);

    public double getNumberOfMoles();

    public SystemInterface clone();

    /**
     * method to set mixing rule used for the fluid
     *
     * @param type The type of mixing rule to be used for the fluid. 1 - classic mixing rule with
     *        all kij set to zero 2 -classic mixing rule with kij from NeqSim database 3- classic
     *        mixing rule with temperature dependent kij 4- Huron Vidal mixing rule with parameters
     *        from NeqSim database 7 -classic mixing rule with kij of CPA from NeqSim Database 9
     *        -classicmixing rule with temperature dependent kij of CPA from NeqSim database
     *        10-classic mixing rule with temperature and composition dependent kij of CPA from
     *        NeqSim database
     */
    public void setMixingRule(int type);

    public String[] getComponentNames();

    public double getdVdPtn();

    public double getdVdTpn();

    /**
     * method to return specific heat capacity (Cp)
     *
     * @return Cp in unit J/K
     */
    public double getCp();

    /**
     * method to return specific heat capacity (Cp) in a given unit
     *
     * @param unit The unit as a string. Supported units are J/K, J/molK, J/kgK and kJ/kgK
     * @return Cp in specified unit
     */
    public double getCp(String unit);

    /**
     * method to return heat capacity ratio/adiabatic index/Poisson constant
     *
     * @return kappa
     */
    public double getKappa();

    public void replacePhase(int repPhase, PhaseInterface newPhase);

    public PhaseInterface getGasPhase();

    public PhaseInterface getLiquidPhase();

    /**
     * method to return compressibility factor of a fluid compressibility factor is defined in EoS
     * from PV=ZnRT where V is total volume of fluid
     *
     * @return compressibility factor Z
     */
    public double getZ();

    /**
     * method to return viscosity of a fluid
     *
     * @return viscosity in unit kg/msec
     */
    public double getViscosity();

    /**
     * method to return viscosity in a given unit
     *
     * @param unit The unit as a string. Supported units are kg/msec, cP (centipoise)
     * @return viscosity in specified unit
     */
    public double getViscosity(String unit);

    /**
     * method to return thermal conductivity
     *
     * @return conductivity in unit W/mK
     * @deprecated use {@link #getThermalConductivity()} instead.
     */
    @Deprecated
    public double getConductivity();

    /**
     * method to return thermal conductivity in a given unit
     *
     * @param unit The unit as a string. Supported units are W/mK, W/cmK
     * @return conductivity in specified unit
     * @deprecated use {@link #getThermalConductivity(String unit)} instead.
     */
    @Deprecated
    public double getConductivity(String unit);

    /**
     * method to return thermal conductivity
     *
     * @return conductivity in unit W/mK
     */
    public double getThermalConductivity();

    /**
     * method to return thermal conductivity in a given unit
     *
     * @param unit The unit as a string. Supported units are W/mK, W/cmK
     * @return conductivity in specified unit
     */
    public double getThermalConductivity(String unit);

    /**
     * method to return interfacial tension between two phases
     *
     * @param phase1 phase type of phase1 as string (valid phases are gas, oil, aqueous)
     * @param phase2 phase type of phase2 as string (valid phases are gas, oil, aqueous)
     * @return interfacial tension with unit N/m. If one or both phases does not exist - the method
     *         will return NaN
     */
    public double getInterfacialTension(String phase1, String phase2);

    /**
     * method to return interfacial tension between two phases
     *
     * @param phase1 phase number of phase1
     * @param phase2 phase number of phase2
     * @return interfacial tension with unit N/m
     */
    public double getInterfacialTension(int phase1, int phase2);

    public double getKinematicViscosity();

    public void initRefPhases();

    public java.lang.String getFluidName();

    public void setFluidName(java.lang.String fluidName);

    public void setSolidPhaseCheck(String solidComponent);

    public boolean allowPhaseShift();

    public void allowPhaseShift(boolean allowPhaseShift);

    /**
     * method to return phase fraction of selected phase
     *
     * @param phaseTypeName: gas/oil/aqueous
     * @param unit: mole/volume/weight
     * @return phase: fraction in given unit
     */
    public double getPhaseFraction(String phaseTypeName, String unit);

    public void setPhaseType(String phases, int newPhaseType);

    /**
     * method to set the phase type of a given phase
     *
     * @param phaseToChange the phase number of the phase to set phase type
     * @param phaseTypeName the phase type name (valid names are gas or liquid)
     */
    public void setPhaseType(int phaseToChange, String phaseTypeName);

    public void removeMoles();

    public double getProperty(String prop, String compName, int phase);

    public double getProperty(String prop, int phase);

    public double getProperty(String prop);

    public neqsim.standards.StandardInterface getStandard();

    public neqsim.standards.StandardInterface getStandard(String standardName);

    public void setStandard(String standardName);

    public void saveToDataBase();

    public void generatePDF();

    public void displayPDF();

    public int getMixingRule();

    public String[][] getResultTable();

    public SystemInterface autoSelectModel();

    public void autoSelectMixingRule();

    public void orderByDensity();

    public void addLiquidToGas(double fraction);

    public void addGasToLiquid(double fraction);

    /**
     * method to get the total molar flow rate of a fluid
     *
     * @return molar flow in unit mol/sec
     */
    public double getTotalNumberOfMoles();

    public void setTotalNumberOfMoles(double totalNumberOfMoles);

    public SystemInterface phaseToSystem(int phaseNumber);

    public boolean hasPhaseType(String phaseTypeName);

    public int getPhaseNumberOfPhase(String phaseTypeName);

    public SystemInterface getEmptySystemClone();

    public double calcHenrysConstant(String component);

    public boolean isImplementedTemperatureDeriativesofFugacity();

    public void setImplementedTemperatureDeriativesofFugacity(
            boolean implementedTemperatureDeriativesofFugacity);

    public boolean isImplementedPressureDeriativesofFugacity();

    public void setImplementedPressureDeriativesofFugacity(
            boolean implementedPressureDeriativesofFugacity);

    public boolean isImplementedCompositionDeriativesofFugacity();

    public void setImplementedCompositionDeriativesofFugacity(
            boolean implementedCompositionDeriativesofFugacity);

    public void addComponent(int componentIndex, double moles);

    public void addCapeOpenProperty(String propertyName);
}
