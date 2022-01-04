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
/**
 * <p>
 * SystemInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SystemInterface extends Cloneable, java.io.Serializable {
    /**
     * <p>
     * saveFluid.
     * </p>
     *
     * @param ID a int
     */
    public void saveFluid(int ID);

    /**
     * <p>
     * getComponentNameTag.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getComponentNameTag();

    /**
     * <p>
     * setComponentNameTagOnNormalComponents.
     * </p>
     *
     * @param nameTag a {@link java.lang.String} object
     */
    public void setComponentNameTagOnNormalComponents(String nameTag);

    /**
     * <p>
     * addPhaseFractionToPhase.
     * </p>
     *
     * @param fraction a double
     * @param specification a {@link java.lang.String} object
     * @param fromPhaseName a {@link java.lang.String} object
     * @param toPhaseName a {@link java.lang.String} object
     */
    public void addPhaseFractionToPhase(double fraction, String specification, String fromPhaseName,
            String toPhaseName);

    /**
     * <p>
     * addPhaseFractionToPhase.
     * </p>
     *
     * @param fraction a double
     * @param specification a {@link java.lang.String} object
     * @param specifiedStream a {@link java.lang.String} object
     * @param fromPhaseName a {@link java.lang.String} object
     * @param toPhaseName a {@link java.lang.String} object
     */
    public void addPhaseFractionToPhase(double fraction, String specification,
            String specifiedStream, String fromPhaseName, String toPhaseName);

    /**
     * <p>
     * renameComponent.
     * </p>
     *
     * @param oldName a {@link java.lang.String} object
     * @param newName a {@link java.lang.String} object
     */
    public void renameComponent(String oldName, String newName);

    /**
     * <p>
     * setComponentNameTag.
     * </p>
     *
     * @param nameTag a {@link java.lang.String} object
     */
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

    /**
     * <p>
     * calcResultTable.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
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

    /**
     * <p>
     * getNumberOfComponents.
     * </p>
     *
     * @return a int
     */
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
     */
    public void setMolarCompositionPlus(double[] molefractions);

    /**
     * <p>
     * saveFluid.
     * </p>
     *
     * @param ID a int
     * @param text a {@link java.lang.String} object
     */
    public void saveFluid(int ID, String text);

    /**
     * This method is used to set the total molar composition of a characterized fluid. The total
     * flow rate will be kept constant. The input mole fractions will be normalized.
     *
     * @param molefractions is a double array taking the molar fraction of the components in the
     *        fluid. THe last fraction in the array is the total molefraction of the characterized
     *        components.
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
     * @param temperatureOfSurroundings in Kelvin
     * @return exergy in specified unit
     * @param exergyUnit a {@link java.lang.String} object
     */
    public double getExergy(double temperatureOfSurroundings, String exergyUnit);

    /**
     * method to return exergy defined as (h1-T0*s1) in a unit Joule
     *
     * @param temperatureOfSurroundings in Kelvin
     * @return a double
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

    /**
     * <p>
     * getMoleFractionsSum.
     * </p>
     *
     * @return a double
     */
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

    /**
     * <p>
     * removePhaseKeepTotalComposition.
     * </p>
     *
     * @param specPhase a int
     */
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

    /**
     * <p>
     * getInterfacialTension.
     * </p>
     *
     * @param phase1 a int
     * @param phase2 a int
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getInterfacialTension(int phase1, int phase2, String unit);

    /**
     * Calculates physical properties of type propertyName
     *
     * @param propertyName a {@link java.lang.String} object
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

    /**
     * <p>
     * getHeatOfVaporization.
     * </p>
     *
     * @return a double
     */
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

    /**
     * <p>
     * isForcePhaseTypes.
     * </p>
     *
     * @return a boolean
     */
    public boolean isForcePhaseTypes();

    /**
     * <p>
     * setForcePhaseTypes.
     * </p>
     *
     * @param forcePhaseTypes a boolean
     */
    public void setForcePhaseTypes(boolean forcePhaseTypes);

    /**
     * <p>
     * setEmptyFluid.
     * </p>
     */
    public void setEmptyFluid();

    /**
     * <p>
     * setMolarFlowRates.
     * </p>
     *
     * @param moles an array of {@link double} objects
     */
    public void setMolarFlowRates(double[] moles);

    /**
     * <p>
     * setComponentNames.
     * </p>
     *
     * @param componentNames an array of {@link java.lang.String} objects
     */
    public void setComponentNames(String[] componentNames);

    /**
     * <p>
     * calc_x_y_nonorm.
     * </p>
     */
    public void calc_x_y_nonorm();

    /**
     * <p>
     * saveObjectToFile.
     * </p>
     *
     * @param filePath a {@link java.lang.String} object
     * @param fluidName a {@link java.lang.String} object
     */
    public void saveObjectToFile(String filePath, String fluidName);

    /**
     * <p>
     * readObjectFromFile.
     * </p>
     *
     * @param filePath a {@link java.lang.String} object
     * @param fluidName a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface readObjectFromFile(String filePath, String fluidName);

    /**
     * <p>
     * getLiquidVolume.
     * </p>
     *
     * @return a double
     */
    public double getLiquidVolume();

    /**
     * <p>
     * resetPhysicalProperties.
     * </p>
     */
    public void resetPhysicalProperties();

    /**
     * <p>
     * phaseToSystem.
     * </p>
     *
     * @param phaseNumber1 a int
     * @param phaseNumber2 a int
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface phaseToSystem(int phaseNumber1, int phaseNumber2);

    /**
     * <p>
     * changeComponentName.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param newName a {@link java.lang.String} object
     */
    public void changeComponentName(String name, String newName);

    /**
     * <p>
     * getWaxModel.
     * </p>
     *
     * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
     */
    public WaxModelInterface getWaxModel();

    /**
     * <p>
     * getWaxCharacterisation.
     * </p>
     *
     * @return a {@link neqsim.thermo.characterization.WaxCharacterise} object
     */
    public neqsim.thermo.characterization.WaxCharacterise getWaxCharacterisation();

    /**
     * <p>
     * phaseToSystem.
     * </p>
     *
     * @param phaseName a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface phaseToSystem(String phaseName);

    /**
     * method to get the total molar flow rate of individual components in a fluid
     *
     * @return molar flow of individual components in unit mol/sec
     */
    public double[] getMolarRate();

    /**
     * <p>
     * getPhase.
     * </p>
     *
     * @param phaseTypeName a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public PhaseInterface getPhase(String phaseTypeName);

    /**
     * <p>
     * getPhaseIndexOfPhase.
     * </p>
     *
     * @param phaseTypeName a {@link java.lang.String} object
     * @return a int
     */
    public int getPhaseIndexOfPhase(String phaseTypeName);

    /**
     * <p>
     * setTotalFlowRate.
     * </p>
     *
     * @param flowRate a double
     * @param flowunit a {@link java.lang.String} object
     */
    public void setTotalFlowRate(double flowRate, String flowunit);

    /**
     * <p>
     * getMolarComposition.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getMolarComposition();

    /**
     * <p>
     * getNumberOfOilFractionComponents.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfOilFractionComponents();

    /**
     * <p>
     * setHeavyTBPfractionAsPlusFraction.
     * </p>
     *
     * @return a boolean
     */
    public boolean setHeavyTBPfractionAsPlusFraction();

    /**
     * <p>
     * getCapeOpenProperties11.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCapeOpenProperties11();

    /**
     * <p>
     * getCapeOpenProperties10.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCapeOpenProperties10();

    /**
     * <p>
     * getLowestGibbsEnergyPhase.
     * </p>
     *
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public PhaseInterface getLowestGibbsEnergyPhase();

    /**
     * <p>
     * getOilFractionNormalBoilingPoints.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getOilFractionNormalBoilingPoints();

    /**
     * <p>
     * getOilFractionLiquidDensityAt25C.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getOilFractionLiquidDensityAt25C();

    /**
     * <p>
     * getOilFractionMolecularMass.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getOilFractionMolecularMass();

    /**
     * <p>
     * getOilFractionIDs.
     * </p>
     *
     * @return an array of {@link int} objects
     */
    public int[] getOilFractionIDs();

    /**
     * <p>
     * getMoleFraction.
     * </p>
     *
     * @param phaseNumber a int
     * @return a double
     */
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

    /**
     * <p>
     * getCharacterization.
     * </p>
     *
     * @return a {@link neqsim.thermo.characterization.Characterise} object
     */
    public neqsim.thermo.characterization.Characterise getCharacterization();

    /**
     * add a component to a fluid. If component already exists, it will be added to the component
     *
     * @param name a {@link java.lang.String} object
     */
    public void addComponent(String name);

    /**
     * add a component to a fluid. If component already exists, it will be added to the component
     *
     * @param moles number of moles (per second) of the component to be added to the fluid
     * @param componenName a {@link java.lang.String} object
     */
    public void addComponent(String componenName, double moles);

    /**
     * <p>
     * readObject.
     * </p>
     *
     * @param ID a int
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface readObject(int ID);

    /**
     * <p>
     * getCompIDs.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCompIDs();

    /**
     * <p>
     * isImplementedCompositionDeriativesofFugacity.
     * </p>
     *
     * @param isImpl a boolean
     */
    public void isImplementedCompositionDeriativesofFugacity(boolean isImpl);

    /**
     * <p>
     * saveObject.
     * </p>
     *
     * @param ID a int
     * @param text a {@link java.lang.String} object
     */
    public void saveObject(int ID, String text);

    /**
     * <p>
     * reset.
     * </p>
     */
    public void reset();

    /**
     * <p>
     * getCASNumbers.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCASNumbers();

    /**
     * <p>
     * getMolecularWeights.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getMolecularWeights();

    /**
     * <p>
     * getNormalBoilingPointTemperatures.
     * </p>
     *
     * @return an array of {@link double} objects
     */
    public double[] getNormalBoilingPointTemperatures();

    /**
     * <p>
     * getCompNames.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCompNames();

    /**
     * <p>
     * getCompFormulaes.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCompFormulaes();

    /**
     * <p>
     * getWtFraction.
     * </p>
     *
     * @param phaseNumber a int
     * @return a double
     */
    public double getWtFraction(int phaseNumber);

    /**
     * <p>
     * isMultiphaseWaxCheck.
     * </p>
     *
     * @return a boolean
     */
    public boolean isMultiphaseWaxCheck();

    /**
     * <p>
     * setMultiphaseWaxCheck.
     * </p>
     *
     * @param multiphaseWaxCheck a boolean
     */
    public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck);

    /**
     * <p>
     * setMolarComposition.
     * </p>
     *
     * @param moles an array of {@link double} objects
     */
    public void setMolarComposition(double[] moles);

    /**
     * return the phase of to specified type if the phase does not exist, the method will return
     * null
     *
     * @param phaseTypeName the phase type to be returned (gas, oil, aqueous, wax, hydrate are
     *        supported)
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
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
     * @param unitName the unit of rate (sported units are kg/sec, mol/sec, Nlitre/min, kg/hr,
     *        Sm^3/hr, Sm^3/day, MSm^3/day ..
     * @param value a double
     */
    public void addComponent(String componentName, double value, String unitName);

    /**
     * <p>
     * setUseTVasIndependentVariables.
     * </p>
     *
     * @param useTVasIndependentVariables a boolean
     */
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

    /**
     * <p>
     * addTBPfraction.
     * </p>
     *
     * @param componentName a {@link java.lang.String} object
     * @param numberOfMoles a double
     * @param molarMass a double
     * @param density a double
     * @param criticalTemperature a double
     * @param criticalPressure a double
     * @param acentricFactor a double
     */
    public void addTBPfraction(String componentName, double numberOfMoles, double molarMass,
            double density, double criticalTemperature, double criticalPressure,
            double acentricFactor);

    /**
     * <p>
     * addPlusFraction.
     * </p>
     *
     * @param componentName a {@link java.lang.String} object
     * @param numberOfMoles a double
     * @param molarMass a double
     * @param density a double
     */
    public void addPlusFraction(String componentName, double numberOfMoles, double molarMass,
            double density);

    /**
     * <p>
     * addSalt.
     * </p>
     *
     * @param componentName a {@link java.lang.String} object
     * @param value a double
     */
    public void addSalt(String componentName, double value);

    /**
     * <p>
     * deleteFluidPhase.
     * </p>
     *
     * @param phase a int
     */
    public void deleteFluidPhase(int phase);

    /**
     * <p>
     * setBmixType.
     * </p>
     *
     * @param bmixType a int
     */
    public void setBmixType(int bmixType);

    /**
     * <p>
     * hasSolidPhase.
     * </p>
     *
     * @return a boolean
     */
    public boolean hasSolidPhase();

    /**
     * <p>
     * addSolidComplexPhase.
     * </p>
     *
     * @param type a {@link java.lang.String} object
     */
    public void addSolidComplexPhase(String type);

    /**
     * method to calculate thermodynamic properties of the fluid. The temperature, pressure, number
     * of phases and composition of the phases will be used as basis for calculation.
     *
     * @param number - The number can be 0, 1, 2 or 3. 0: Initialization of a fluid (feed
     *        composition will be set for all phases). 1: Calculation of density and fugacities,
     *        Z-factor 2: 1 + calculation of enthalpy, entropy, Cp, Cv, and most other thermodynamic
     *        properties 3 - 1+2 + Calculation of composition derivatives of fugacity coefficients
     *        init(1) is faster than init(2). init(2) faster than init(3).Which init to use is
     *        dependent on what properties you need.
     */
    public void init(int number);

    /**
     * <p>
     * resetCharacterisation.
     * </p>
     */
    public void resetCharacterisation();

    /**
     * <p>
     * getMaxNumberOfPhases.
     * </p>
     *
     * @return a int
     */
    public int getMaxNumberOfPhases();

    /**
     * <p>
     * setMaxNumberOfPhases.
     * </p>
     *
     * @param maxNumberOfPhases a int
     */
    public void setMaxNumberOfPhases(int maxNumberOfPhases);

    /**
     * <p>
     * getMixingRuleName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public java.lang.String getMixingRuleName();

    /**
     * <p>
     * getModelName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public java.lang.String getModelName();

    /**
     * <p>
     * tuneModel.
     * </p>
     *
     * @param model a {@link java.lang.String} object
     * @param val a double
     * @param phase a int
     */
    public void tuneModel(String model, double val, int phase);

    /**
     * <p>
     * addComponent.
     * </p>
     *
     * @param componentName a {@link java.lang.String} object
     * @param moles a double
     * @param TC a double
     * @param PC a double
     * @param acs a double
     */
    public void addComponent(String componentName, double moles, double TC, double PC, double acs);

    /**
     * <p>
     * getBeta.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getBeta(int phase);

    /**
     * <p>
     * save.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void save(String name);

    /**
     * <p>
     * setModel.
     * </p>
     *
     * @param model a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface setModel(String model);

    /**
     * <p>
     * removeComponent.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void removeComponent(String name);

    /**
     * <p>
     * setMixingRule.
     * </p>
     *
     * @param typename a {@link java.lang.String} object
     * @param GEmodel a {@link java.lang.String} object
     */
    public void setMixingRule(String typename, String GEmodel);

    /**
     * <p>
     * normalizeBeta.
     * </p>
     */
    public void normalizeBeta();

    /**
     * <p>
     * getPhaseIndex.
     * </p>
     *
     * @param index a int
     * @return a int
     */
    public int getPhaseIndex(int index);

    /**
     * <p>
     * setInitType.
     * </p>
     *
     * @param initType a int
     */
    public void setInitType(int initType);

    /**
     * <p>
     * checkStability.
     * </p>
     *
     * @param val a boolean
     */
    public void checkStability(boolean val);

    /**
     * <p>
     * hasPlusFraction.
     * </p>
     *
     * @return a boolean
     */
    public boolean hasPlusFraction();

    /**
     * <p>
     * checkStability.
     * </p>
     *
     * @return a boolean
     */
    public boolean checkStability();

    /**
     * <p>
     * getInitType.
     * </p>
     *
     * @return a int
     */
    public int getInitType();

    /**
     * <p>
     * invertPhaseTypes.
     * </p>
     */
    public void invertPhaseTypes();

    /**
     * method to return fluid volume with Peneloux volume correction
     *
     * @return volume in unit m3
     */
    public double getCorrectedVolume();

    /**
     * <p>
     * readFluid.
     * </p>
     *
     * @param fluidName a {@link java.lang.String} object
     */
    public void readFluid(String fluidName);

    /**
     * <p>
     * calcKIJ.
     * </p>
     *
     * @param ok a boolean
     */
    public void calcKIJ(boolean ok);

    /**
     * <p>
     * write.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param filename a {@link java.lang.String} object
     * @param newfile a boolean
     */
    public void write(String name, String filename, boolean newfile);

    /**
     * <p>
     * useVolumeCorrection.
     * </p>
     *
     * @param volcor a boolean
     */
    public void useVolumeCorrection(boolean volcor);

    /**
     * method to set the mixing rule for the fluid
     *
     * @param typename a {@link java.lang.String} object
     */
    public void setMixingRule(String typename);

    /**
     * <p>
     * isNumericDerivatives.
     * </p>
     *
     * @return a boolean
     */
    public boolean isNumericDerivatives();

    /**
     * <p>
     * setNumericDerivatives.
     * </p>
     *
     * @param numericDerivatives a boolean
     */
    public void setNumericDerivatives(boolean numericDerivatives);

    /**
     * <p>
     * init.
     * </p>
     */
    public void init();

    /**
     * <p>
     * getFluidInfo.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public java.lang.String getFluidInfo();

    /**
     * <p>
     * setFluidInfo.
     * </p>
     *
     * @param info a {@link java.lang.String} object
     */
    public void setFluidInfo(java.lang.String info);

    /**
     * <p>
     * setPhaseIndex.
     * </p>
     *
     * @param index a int
     * @param phaseIndex a int
     */
    public void setPhaseIndex(int index, int phaseIndex);

    /**
     * <p>
     * setPhase.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param numb a int
     */
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

    /**
     * <p>
     * resetDatabase.
     * </p>
     */
    public void resetDatabase();

    /**
     * <p>
     * setSolidPhaseCheck.
     * </p>
     *
     * @param test a boolean
     */
    public void setSolidPhaseCheck(boolean test);

    /**
     * <p>
     * doSolidPhaseCheck.
     * </p>
     *
     * @return a boolean
     */
    public boolean doSolidPhaseCheck();

    /**
     * <p>
     * doMultiPhaseCheck.
     * </p>
     *
     * @return a boolean
     */
    public boolean doMultiPhaseCheck();

    /**
     * method to specify if calculations should check for more than two fluid phases.
     *
     * @param doMultiPhaseCheck Specify if the calculations should check for more than two fluid
     *        phases. Default is two fluid phases (gas and liquid). If set to true the program will
     *        check for gas and multiple liquid phases (eg. gas-oil-aqueous).
     */
    public void setMultiPhaseCheck(boolean doMultiPhaseCheck);

    /**
     * <p>
     * init.
     * </p>
     *
     * @param number a int
     * @param phase a int
     */
    public void init(int number, int phase);

    /**
     * <p>
     * initNumeric.
     * </p>
     */
    public void initNumeric();

    /**
     * <p>
     * display.
     * </p>
     */
    public void display();

    /**
     * <p>
     * addFluid.
     * </p>
     *
     * @param addSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void addFluid(SystemInterface addSystem);

    /**
     * <p>
     * display.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void display(String name);

    /**
     * <p>
     * doHydrateCheck.
     * </p>
     *
     * @return a boolean
     */
    public boolean doHydrateCheck();

    /**
     * <p>
     * createTable.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] createTable(String name);

    /**
     * <p>
     * setHydrateCheck.
     * </p>
     *
     * @param hydrateCheck a boolean
     */
    public void setHydrateCheck(boolean hydrateCheck);

    /**
     * <p>
     * calcBeta.
     * </p>
     *
     * @return a double
     * @throws neqsim.util.exception.IsNaNException if any.
     * @throws neqsim.util.exception.TooManyIterationsException if any.
     */
    public double calcBeta() throws neqsim.util.exception.IsNaNException,
            neqsim.util.exception.TooManyIterationsException;

    /**
     * <p>
     * setAllComponentsInPhase.
     * </p>
     *
     * @param phase a int
     */
    public void setAllComponentsInPhase(int phase);

    /**
     * <p>
     * initTotalNumberOfMoles.
     * </p>
     *
     * @param change a double
     */
    public void initTotalNumberOfMoles(double change);

    /**
     * <p>
     * calc_x_y.
     * </p>
     */
    public void calc_x_y();

    /**
     * <p>
     * getPhase.
     * </p>
     *
     * @param i a int
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public PhaseInterface getPhase(int i);

    /**
     * <p>
     * reset_x_y.
     * </p>
     */
    public void reset_x_y();

    /**
     * <p>
     * isChemicalSystem.
     * </p>
     *
     * @param temp a boolean
     */
    public void isChemicalSystem(boolean temp);

    /**
     * <p>
     * addComponent.
     * </p>
     *
     * @param index a int
     * @param moles a double
     * @param phaseNumber a int
     */
    public void addComponent(int index, double moles, int phaseNumber);

    /**
     * <p>
     * addPhase.
     * </p>
     */
    public void addPhase();

    /**
     * <p>
     * setAtractiveTerm.
     * </p>
     *
     * @param i a int
     */
    public void setAtractiveTerm(int i);

    /**
     * <p>
     * setBeta.
     * </p>
     *
     * @param phase a int
     * @param b a double
     */
    public void setBeta(int phase, double b);

    /**
     * <p>
     * removePhase.
     * </p>
     *
     * @param specPhase a int
     */
    public void removePhase(int specPhase);

    /**
     * <p>
     * phaseToSystem.
     * </p>
     *
     * @param newPhase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface phaseToSystem(PhaseInterface newPhase);

    /**
     * <p>
     * setTemperature.
     * </p>
     *
     * @param temp a double
     */
    public void setTemperature(double temp);

    /**
     * <p>
     * setTemperature.
     * </p>
     *
     * @param newTemperature a double
     * @param phaseNumber a int
     */
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

    /**
     * <p>
     * reInitPhaseType.
     * </p>
     */
    public void reInitPhaseType();

    /**
     * <p>
     * setPhysicalPropertyModel.
     * </p>
     *
     * @param type a int
     */
    public void setPhysicalPropertyModel(int type);

    /**
     * <p>
     * clearAll.
     * </p>
     */
    public void clearAll();

    /**
     * <p>
     * getPressure.
     * </p>
     *
     * @param phaseNumber a int
     * @return a double
     */
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

    /**
     * <p>
     * getChemicalReactionOperations.
     * </p>
     *
     * @return a {@link neqsim.chemicalReactions.ChemicalReactionOperations} object
     */
    public ChemicalReactionOperations getChemicalReactionOperations();

    /**
     * <p>
     * isChemicalSystem.
     * </p>
     *
     * @return a boolean
     */
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

    /**
     * <p>
     * calcInterfaceProperties.
     * </p>
     */
    public void calcInterfaceProperties();

    /**
     * <p>
     * getInterphaseProperties.
     * </p>
     *
     * @return a {@link neqsim.physicalProperties.interfaceProperties.InterphasePropertiesInterface}
     *         object
     */
    public InterphasePropertiesInterface getInterphaseProperties();

    /**
     * <p>
     * initBeta.
     * </p>
     *
     * @return a double
     */
    public double initBeta();

    /**
     * <p>
     * init_x_y.
     * </p>
     */
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

    /**
     * <p>
     * getTemperature.
     * </p>
     *
     * @param phaseNumber a int
     * @return a double
     */
    public double getTemperature(int phaseNumber);

    /**
     * <p>
     * getBeta.
     * </p>
     *
     * @return a double
     */
    public double getBeta();

    /**
     * <p>
     * chemicalReactionInit.
     * </p>
     */
    public void chemicalReactionInit();

    /**
     * <p>
     * initPhysicalProperties.
     * </p>
     */
    public void initPhysicalProperties();

    /**
     * <p>
     * setBeta.
     * </p>
     *
     * @param b a double
     */
    public void setBeta(double b);
    // public double getdfugdt(int i, int j);

    /**
     * <p>
     * setPhaseType.
     * </p>
     *
     * @param phaseToChange a int
     * @param newPhaseType a int
     */
    public void setPhaseType(int phaseToChange, int newPhaseType);

    /**
     * <p>
     * setNumberOfPhases.
     * </p>
     *
     * @param number a int
     */
    public void setNumberOfPhases(int number);

    /**
     * <p>
     * getTC.
     * </p>
     *
     * @return a double
     */
    public double getTC();

    /**
     * <p>
     * getPC.
     * </p>
     *
     * @return a double
     */
    public double getPC();

    /**
     * <p>
     * setTC.
     * </p>
     *
     * @param TC a double
     */
    public void setTC(double TC);

    /**
     * <p>
     * setPC.
     * </p>
     *
     * @param PC a double
     */
    public void setPC(double PC);

    /**
     * <p>
     * getPhases.
     * </p>
     *
     * @return an array of {@link neqsim.thermo.phase.PhaseInterface} objects
     */
    public PhaseInterface[] getPhases();

    /**
     * <p>
     * getNumberOfPhases.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfPhases();

    /**
     * <p>
     * getGibbsEnergy.
     * </p>
     *
     * @return a double
     */
    public double getGibbsEnergy();

    /**
     * method to return internal energy (U) in unit J
     *
     * @return internal energy in unit Joule (J)
     */
    public double getInternalEnergy();

    /**
     * <p>
     * getHelmholtzEnergy.
     * </p>
     *
     * @return a double
     */
    public double getHelmholtzEnergy();

    /**
     * <p>
     * getComponent.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.component.ComponentInterface} object
     */
    public ComponentInterface getComponent(String name);

    /**
     * <p>
     * getComponent.
     * </p>
     *
     * @param number a int
     * @return a {@link neqsim.thermo.component.ComponentInterface} object
     */
    public ComponentInterface getComponent(int number);

    /**
     * <p>
     * getNumberOfMoles.
     * </p>
     *
     * @return a double
     */
    public double getNumberOfMoles();

    /**
     * <p>
     * clone.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
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

    /**
     * <p>
     * getComponentNames.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getComponentNames();

    /**
     * <p>
     * getdVdPtn.
     * </p>
     *
     * @return a double
     */
    public double getdVdPtn();

    /**
     * <p>
     * getdVdTpn.
     * </p>
     *
     * @return a double
     */
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

    /**
     * <p>
     * replacePhase.
     * </p>
     *
     * @param repPhase a int
     * @param newPhase a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public void replacePhase(int repPhase, PhaseInterface newPhase);

    /**
     * <p>
     * getGasPhase.
     * </p>
     *
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
    public PhaseInterface getGasPhase();

    /**
     * <p>
     * getLiquidPhase.
     * </p>
     *
     * @return a {@link neqsim.thermo.phase.PhaseInterface} object
     */
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

    /**
     * <p>
     * getKinematicViscosity.
     * </p>
     *
     * @return a double
     */
    public double getKinematicViscosity();

    /**
     * <p>
     * initRefPhases.
     * </p>
     */
    public void initRefPhases();

    /**
     * <p>
     * getFluidName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public java.lang.String getFluidName();

    /**
     * <p>
     * setFluidName.
     * </p>
     *
     * @param fluidName a {@link java.lang.String} object
     */
    public void setFluidName(java.lang.String fluidName);

    /**
     * <p>
     * setSolidPhaseCheck.
     * </p>
     *
     * @param solidComponent a {@link java.lang.String} object
     */
    public void setSolidPhaseCheck(String solidComponent);

    /**
     * <p>
     * allowPhaseShift.
     * </p>
     *
     * @return a boolean
     */
    public boolean allowPhaseShift();

    /**
     * <p>
     * allowPhaseShift.
     * </p>
     *
     * @param allowPhaseShift a boolean
     */
    public void allowPhaseShift(boolean allowPhaseShift);

    /**
     * method to return phase fraction of selected phase
     *
     * @param phaseTypeName: gas/oil/aqueous
     * @param unit: mole/volume/weight
     * @return phase: fraction in given unit
     */
    public double getPhaseFraction(String phaseTypeName, String unit);

    /**
     * <p>
     * setPhaseType.
     * </p>
     *
     * @param phases a {@link java.lang.String} object
     * @param newPhaseType a int
     */
    public void setPhaseType(String phases, int newPhaseType);

    /**
     * method to set the phase type of a given phase
     *
     * @param phaseToChange the phase number of the phase to set phase type
     * @param phaseTypeName the phase type name (valid names are gas or liquid)
     */
    public void setPhaseType(int phaseToChange, String phaseTypeName);

    /**
     * <p>
     * removeMoles.
     * </p>
     */
    public void removeMoles();

    /**
     * <p>
     * getProperty.
     * </p>
     *
     * @param prop a {@link java.lang.String} object
     * @param compName a {@link java.lang.String} object
     * @param phase a int
     * @return a double
     */
    public double getProperty(String prop, String compName, int phase);

    /**
     * <p>
     * getProperty.
     * </p>
     *
     * @param prop a {@link java.lang.String} object
     * @param phase a int
     * @return a double
     */
    public double getProperty(String prop, int phase);

    /**
     * <p>
     * getProperty.
     * </p>
     *
     * @param prop a {@link java.lang.String} object
     * @return a double
     */
    public double getProperty(String prop);

    /**
     * <p>
     * getStandard.
     * </p>
     *
     * @return a {@link neqsim.standards.StandardInterface} object
     */
    public neqsim.standards.StandardInterface getStandard();

    /**
     * <p>
     * getStandard.
     * </p>
     *
     * @param standardName a {@link java.lang.String} object
     * @return a {@link neqsim.standards.StandardInterface} object
     */
    public neqsim.standards.StandardInterface getStandard(String standardName);

    /**
     * <p>
     * setStandard.
     * </p>
     *
     * @param standardName a {@link java.lang.String} object
     */
    public void setStandard(String standardName);

    /**
     * <p>
     * saveToDataBase.
     * </p>
     */
    public void saveToDataBase();

    /**
     * <p>
     * generatePDF.
     * </p>
     */
    public void generatePDF();

    /**
     * <p>
     * displayPDF.
     * </p>
     */
    public void displayPDF();

    /**
     * <p>
     * getMixingRule.
     * </p>
     *
     * @return a int
     */
    public int getMixingRule();

    /**
     * <p>
     * getResultTable.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] getResultTable();

    /**
     * <p>
     * autoSelectModel.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface autoSelectModel();

    /**
     * <p>
     * autoSelectMixingRule.
     * </p>
     */
    public void autoSelectMixingRule();

    /**
     * <p>
     * orderByDensity.
     * </p>
     */
    public void orderByDensity();

    /**
     * <p>
     * addLiquidToGas.
     * </p>
     *
     * @param fraction a double
     */
    public void addLiquidToGas(double fraction);

    /**
     * <p>
     * addGasToLiquid.
     * </p>
     *
     * @param fraction a double
     */
    public void addGasToLiquid(double fraction);

    /**
     * method to get the total molar flow rate of a fluid
     *
     * @return molar flow in unit mol/sec
     */
    public double getTotalNumberOfMoles();

    /**
     * <p>
     * setTotalNumberOfMoles.
     * </p>
     *
     * @param totalNumberOfMoles a double
     */
    public void setTotalNumberOfMoles(double totalNumberOfMoles);

    /**
     * <p>
     * phaseToSystem.
     * </p>
     *
     * @param phaseNumber a int
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface phaseToSystem(int phaseNumber);

    /**
     * <p>
     * hasPhaseType.
     * </p>
     *
     * @param phaseTypeName a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean hasPhaseType(String phaseTypeName);

    /**
     * <p>
     * getPhaseNumberOfPhase.
     * </p>
     *
     * @param phaseTypeName a {@link java.lang.String} object
     * @return a int
     */
    public int getPhaseNumberOfPhase(String phaseTypeName);

    /**
     * <p>
     * getEmptySystemClone.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getEmptySystemClone();

    /**
     * <p>
     * calcHenrysConstant.
     * </p>
     *
     * @param component a {@link java.lang.String} object
     * @return a double
     */
    public double calcHenrysConstant(String component);

    /**
     * <p>
     * isImplementedTemperatureDeriativesofFugacity.
     * </p>
     *
     * @return a boolean
     */
    public boolean isImplementedTemperatureDeriativesofFugacity();

    /**
     * <p>
     * setImplementedTemperatureDeriativesofFugacity.
     * </p>
     *
     * @param implementedTemperatureDeriativesofFugacity a boolean
     */
    public void setImplementedTemperatureDeriativesofFugacity(
            boolean implementedTemperatureDeriativesofFugacity);

    /**
     * <p>
     * isImplementedPressureDeriativesofFugacity.
     * </p>
     *
     * @return a boolean
     */
    public boolean isImplementedPressureDeriativesofFugacity();

    /**
     * <p>
     * setImplementedPressureDeriativesofFugacity.
     * </p>
     *
     * @param implementedPressureDeriativesofFugacity a boolean
     */
    public void setImplementedPressureDeriativesofFugacity(
            boolean implementedPressureDeriativesofFugacity);

    /**
     * <p>
     * isImplementedCompositionDeriativesofFugacity.
     * </p>
     *
     * @return a boolean
     */
    public boolean isImplementedCompositionDeriativesofFugacity();

    /**
     * <p>
     * setImplementedCompositionDeriativesofFugacity.
     * </p>
     *
     * @param implementedCompositionDeriativesofFugacity a boolean
     */
    public void setImplementedCompositionDeriativesofFugacity(
            boolean implementedCompositionDeriativesofFugacity);

    /**
     * <p>
     * addComponent.
     * </p>
     *
     * @param componentIndex a int
     * @param moles a double
     */
    public void addComponent(int componentIndex, double moles);

    /**
     * <p>
     * addCapeOpenProperty.
     * </p>
     *
     * @param propertyName a {@link java.lang.String} object
     */
    public void addCapeOpenProperty(String propertyName);
}
