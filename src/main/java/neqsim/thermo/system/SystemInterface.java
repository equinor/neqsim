package neqsim.thermo.system;

import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.PseudoComponentCombiner;
import neqsim.thermo.characterization.WaxModelInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SystemInterface interface.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface SystemInterface extends Cloneable, java.io.Serializable {
  /**
   * return two fluid added as a new fluid.
   *
   * @param addFluid1 first fluid to add
   * @param addFluid2 second fluid o add
   * @return new fluid
   */
  public static SystemInterface addFluids(SystemInterface addFluid1, SystemInterface addFluid2) {
    SystemInterface newFluid = addFluid1.clone();
    newFluid.addFluid(addFluid2);
    return newFluid;
  }

  /**
   * Combine fluids and redistribute pseudo components to a requested number.
   *
   * @param numberOfPseudoComponents target number of pseudo components
   * @param fluids fluids to combine
   * @return combined fluid
   */
  public static SystemInterface combineReservoirFluids(int numberOfPseudoComponents,
      SystemInterface... fluids) {
    return PseudoComponentCombiner.combineReservoirFluids(numberOfPseudoComponents, fluids);
  }

  /**
   * Characterize a fluid to match the pseudo component definition of a reference fluid.
   *
   * @param source fluid to characterize
   * @param reference fluid providing the pseudo component definition
   * @return characterized fluid with pseudo components aligned to the reference
   */
  public static SystemInterface characterizeToReference(SystemInterface source,
      SystemInterface reference) {
    return PseudoComponentCombiner.characterizeToReference(source, reference);
  }

  /**
   * <p>
   * addCapeOpenProperty.
   * </p>
   *
   * @param propertyName a {@link java.lang.String} object
   */
  public void addCapeOpenProperty(String propertyName);

  /**
   * <p>
   * addCharacterized.
   * </p>
   *
   * @param charNames an array of {@link java.lang.String} objects
   * @param charFlowrate an array of type double
   * @param molarMass an array of type double
   * @param relativedensity an array of type double
   */
  public void addCharacterized(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity);

  /**
   * add a component to a fluid. If component already exists, the moles will be added to the
   * existing component.
   *
   * @param inComponent Component object to add.
   */
  public void addComponent(ComponentInterface inComponent);

  /**
   * <p>
   * addComponent.
   * </p>
   *
   * @param index Component number to add
   * @param moles number of moles (per second) of the component to be added to the fluid
   */
  public void addComponent(int index, double moles);

  /**
   * <p>
   * addComponent.
   * </p>
   *
   * @param index Component number to add
   * @param moles number of moles (per second) of the component to be added to the fluid
   * @param phaseNumber Number of the phase to add the component to
   */
  public void addComponent(int index, double moles, int phaseNumber);

  /**
   * add a component to a fluid with no moles.
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   */
  public default void addComponent(String name) {
    addComponent(name, 0);
  }

  /**
   * add a component to a fluid. If component already exists, the moles will be added to the
   * existing component.
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   * @param moles number of moles (per second) of the component to be added to the fluid
   */
  public void addComponent(String name, double moles);

  /**
   * <p>
   * addComponent.
   * </p>
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   * @param moles number of moles (per second) of the component to be added to the fluid
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param acs a double
   */
  public void addComponent(String name, double moles, double TC, double PC, double acs);

  /**
   * add a component to a fluid. If component already exists, the moles will be added to the
   * existing component.
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   * @param moles number of moles (per second) of the component to be added to the fluid
   * @param phaseNumber Number of the phase to add the component to
   */
  public void addComponent(String name, double moles, int phaseNumber);

  /**
   * add a component to a fluid. If component already exists, the amount will be added to the
   * existing component.
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   * @param value The amount.
   * @param unitName the unit of rate (sported units are kg/sec, mol/sec, Nlitre/min, kg/hr,
   *        Sm^3/hr, Sm^3/day, MSm^3/day ..
   */
  public void addComponent(String name, double value, String unitName);

  /**
   * add a component to a fluid. I component already exists, it will be added to the component
   *
   * @param name Name of the component to add. See NeqSim database for component in the database.
   * @param value rate of the component to add to the fluid
   * @param unitName the unit of the flow rate (eg. mol/sec, kg/sec, etc.)
   * @param phaseNumber Number of the phase to add the component to
   */
  public void addComponent(String name, double value, String unitName, int phaseNumber);

  /**
   * Add named components to a System. Does nothing if components already exist in System.
   *
   * @param names Names of the components to be added. See NeqSim database for available components
   *        in the database.
   */
  public default void addComponents(String[] names) {
    for (int i = 0; i < names.length; i++) {
      addComponent(names[i], 0.0);
    }
  }

  /**
   * Add named components to a System with a number of moles. If component already exists, the moles
   * will be added to the component.
   *
   * @param names Names of the components to be added. See NeqSim database for available components
   *        in the database.
   * @param moles Number of moles to add per component.
   */
  public default void addComponents(String[] names, double[] moles) {
    for (int i = 0; i < names.length; i++) {
      addComponent(names[i], moles[i]);
    }
  }

  /**
   * <p>
   * addFluid.
   * </p>
   *
   * @param addSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @return SystemInterface
   */
  public SystemInterface addFluid(SystemInterface addSystem);

  /**
   * <p>
   * addFluid.
   * </p>
   *
   * @param addSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param phaseNum phase number of phase to add fluid to
   * @return SystemInterface
   */
  public SystemInterface addFluid(SystemInterface addSystem, int phaseNum);

  /**
   * <p>
   * addGasToLiquid.
   * </p>
   *
   * @param fraction a double
   */
  public void addGasToLiquid(double fraction);

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
   * addCharacterized.
   * </p>
   *
   * @param charNames an array of {@link java.lang.String} objects
   * @param charFlowrate an array of type double
   * @param molarMass an array of type double
   * @param relativedensity an array of type double
   * @param lastIsPlusFraction True if last fraction is a Plus fraction
   */
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity, boolean lastIsPlusFraction);

  /**
   * <p>
   * addCharacterized.
   * </p>
   *
   * @param charNames an array of {@link java.lang.String} objects
   * @param charFlowrate an array of type double
   * @param molarMass an array of type double
   * @param relativedensity an array of type double
   * @param lastIsPlusFraction True if last fraction is a Plus fraction
   * @param lumpComponents True if component should be lumped
   * @param numberOfPseudoComponents number of pseudo components
   */
  public void addOilFractions(String[] charNames, double[] charFlowrate, double[] molarMass,
      double[] relativedensity, boolean lastIsPlusFraction, boolean lumpComponents,
      int numberOfPseudoComponents);

  /**
   * Add phase to SystemInterface object.
   */
  public void addPhase();

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
  public void addPhaseFractionToPhase(double fraction, String specification, String specifiedStream,
      String fromPhaseName, String toPhaseName);

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
   * addSolidComplexPhase.
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public void addSolidComplexPhase(String type);

  /**
   * method to add true boiling point fraction.
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
      double density, double criticalTemperature, double criticalPressure, double acentricFactor);

  /**
   * <p>
   * addTBPfraction2.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   * @param numberOfMoles a double
   * @param molarMass a double
   * @param boilingPoint a double
   */
  public void addTBPfraction2(String componentName, double numberOfMoles, double molarMass,
      double boilingPoint);

  /**
   * Calculate density from boiling point and molar mass using TBP correlation.
   *
   * @param molarMass molar mass in kg/mol
   * @param boilingPoint boiling point in K
   * @return density in g/cm3
   */
  public double calculateDensityFromBoilingPoint(double molarMass, double boilingPoint);

  /**
   * Add to component names.
   *
   * @param name Component name to add
   */
  public void addToComponentNames(String name);

  /**
   * <p>
   * Getter for property allowPhaseShift.
   * </p>
   *
   * @return a boolean
   */
  public boolean allowPhaseShift();

  /**
   * <p>
   * Setter for property allowPhaseShift.
   * </p>
   *
   * @param allowPhaseShift a boolean
   */
  public void allowPhaseShift(boolean allowPhaseShift);

  /**
   * <p>
   * autoSelectMixingRule.
   * </p>
   */
  public void autoSelectMixingRule();

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
   * calc_x_y.
   * </p>
   */
  public void calc_x_y();

  /**
   * <p>
   * calc_x_y_nonorm.
   * </p>
   */
  public void calc_x_y_nonorm();

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
   * calcInterfaceProperties.
   * </p>
   */
  public void calcInterfaceProperties();

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
   * calcResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public default String[][] calcResultTable() {
    return createTable("");
  }

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
   * checkStability.
   * </p>
   *
   * @return a boolean
   */
  public boolean checkStability();

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
   * chemicalReactionInit.
   * </p>
   */
  public void chemicalReactionInit();

  /**
   * <p>
   * clearAll.
   * </p>
   */
  public void clearAll();

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface clone();

  /**
   * method to read pure component and interaction parameters from the NeqSim database and create
   * temporary tables with parameters for active fluid.
   *
   * <p>
   * NB! Resets the mixing rule of each phase.
   * </p>
   *
   * @param reset If reset is set to true, new temporary tables with parameters for the added
   *        components will be created. When parameters are needed (eg. when adding components or
   *        when setting a mixing rule) it will try to find them in the temporary tables first eg.
   *        COMPTEMP (for pure component parameters) and INTERTEMP (for interaction parameters). If
   *        reset is set to false it will not create new temporary tables. If a fluid is created
   *        with the same components many times, performance improvements will be obtained, if
   *        temporary tables are created the first time (reset=true), and then the same tables is
   *        used when creating new fluids with the same temporary tables (reset=false)
   */
  public void createDatabase(boolean reset);

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
   * deleteFluidPhase.
   * </p>
   *
   * @param phaseNum a int
   */
  public void deleteFluidPhase(int phaseNum);

  /**
   * <p>
   * display.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public default void display() {
    display(this.getFluidName());
  }

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
   * Getter for property <code>multiPhaseCheck</code>.
   * </p>
   *
   * @return a boolean
   */
  public boolean doMultiPhaseCheck();

  /**
   * <p>
   * doSolidPhaseCheck.
   * </p>
   *
   * @return a boolean
   */
  public boolean doSolidPhaseCheck();

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /**
   * <p>
   * Getter for property <code>beta</code>.
   *
   * Gets value for heaviest phase.
   * </p>
   *
   * @return Beta value
   */
  public double getBeta();

  /**
   * <p>
   * Getter for property <code>beta</code> for a specific phase.
   * </p>
   *
   * @param phaseNum Number of phase to get beta for.
   * @return Beta value for
   */
  public double getBeta(int phaseNum);

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
   * getCapeOpenProperties11.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getCapeOpenProperties11();

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
   * Getter for property characterization.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.Characterise} object
   */
  public neqsim.thermo.characterization.Characterise getCharacterization();

  /**
   * <p>
   * getChemicalReactionOperations.
   * </p>
   *
   * @return a {@link neqsim.chemicalreactions.ChemicalReactionOperations} object
   */
  public ChemicalReactionOperations getChemicalReactionOperations();

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
   * getCompIDs.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getCompIDs();

  /**
   * Get normalized names of all components in System.
   *
   * @return Array of names of components in System.
   */
  public String[] getCompNames();

  /**
   * <p>
   * Get component by index.
   * </p>
   *
   * @param i Component index
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public default ComponentInterface getComponent(int i) {
    return getPhase(0).getComponent(i);
  }

  /**
   * <p>
   * Get component by name.
   * </p>
   *
   * @param name Name of component
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public default ComponentInterface getComponent(String name) {
    return getPhase(0).getComponent(name);
  }

  /**
   * Get normalized names of components in System.
   *
   * @return Array of names of components in system.
   */
  public default String[] getComponentNames() {
    return getPhase(0).getComponentNames();
  }

  /**
   * <p>
   * getComponentNameTag.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getComponentNameTag();

  /**
   * method to return fluid volume with Peneloux volume correction.
   *
   * @return volume in unit m3
   */
  public double getCorrectedVolume();

  /**
   * method to return the volume fraction of a phase note: with Peneloux volume correction.
   *
   * @param phaseNumber number of the phase to get volume fraction for
   * @return volume fraction
   */
  public double getCorrectedVolumeFraction(int phaseNumber);

  /**
   * method to return specific heat capacity (Cp).
   *
   * @return Cp in unit J/K
   */
  public double getCp();

  /**
   * method to return specific heat capacity (Cp) in a specified unit.
   *
   * @param unit Supported units are J/K, J/molK, J/kgK and kJ/kgK
   * @return Cp in specified unit
   */
  public double getCp(String unit);

  /**
   * method to return specific heat capacity (Cv).
   *
   * @return Cv in unit J/K
   */
  public double getCv();

  /**
   * method to return specific heat capacity (Cp) in a specified unit.
   *
   * @param unit Supported units are J/K, J/molK, J/kgK and kJ/kgK
   * @return Cp in specified unit
   */
  public double getCv(String unit);

  /**
   * Get density of a fluid note: without Peneloux volume correction.
   *
   * @return density with unit kg/m3
   */
  public double getDensity();

  /**
   * Get density of a fluid note: with Peneloux volume correction.
   *
   * @param unit Supported units are kg/m3, mol/m3
   * @return density in specified unit
   */
  public double getDensity(String unit);

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
   * <p>
   * getEmptySystemClone.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getEmptySystemClone();

  /**
   * Get the total enthalpy of a fluid.
   *
   * @return molar mass in unit J (Joule)
   */
  public double getEnthalpy();

  /**
   * method to return total enthalpy in a specified unit.
   *
   * @param unit Supported units are 'J', 'J/mol', 'kJ/kmol', 'J/kg' and 'kJ/kg'
   * @return enthalpy in specified unit
   */
  public double getEnthalpy(String unit);

  /**
   * method to return total entropy of the fluid.
   *
   * @return entropy in unit J/K (Joule/Kelvin)
   */
  public double getEntropy();

  /**
   * method to return total entropy of the fluid.
   *
   * @param unit unit supported units are J/K, J/molK, J/kgK and kJ/kgK
   * @return entropy in specified unit
   */
  public double getEntropy(String unit);

  /**
   * method to return exergy defined as (h1-T0*s1) in a unit Joule.
   *
   * @param temperatureOfSurroundings in Kelvin
   * @return a double
   */
  public double getExergy(double temperatureOfSurroundings);

  /**
   * method to return exergy in a specified unit.
   *
   * @param temperatureOfSurroundings in Kelvin
   * @param exergyUnit a {@link java.lang.String} object
   * @return exergy in specified unit
   */
  public double getExergy(double temperatureOfSurroundings, String exergyUnit);

  /**
   * method to return flow rate of fluid.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, kg/day, m3/sec, m3/min, m3/hr,
   *        idSm3/hr, Sm3/sec, Sm3/hr, Sm3/day, MSm3/day, mole/sec, mole/min, mole/hr
   * @return flow rate in specified unit
   */
  public double getFlowRate(String flowunit);

  /**
   * <p>
   * Getter for property info.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getFluidInfo();

  /**
   * <p>
   * getFluidName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getFluidName();

  /**
   * method to return heat capacity ratio/adiabatic index/Poisson constant.
   *
   * @return kappa
   */
  public double getGamma();

  /**
   * method to return heat capacity ratio calculated as Cp/(Cp-R*nMoles).
   *
   * @return kappa
   */
  public default double getGamma2() {
    return getCp() / (getCp() - ThermodynamicConstantsInterface.R * getTotalNumberOfMoles());
  }

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
   * getGibbsEnergy.
   * </p>
   *
   * @return a double
   */
  public double getGibbsEnergy();

  /**
   * <p>
   * getHeatOfVaporization.
   * </p>
   *
   * @return a double
   */
  public double getHeatOfVaporization();

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
   * Getter for property hydrateCheck.
   * </p>
   *
   * @return a boolean
   */
  public boolean getHydrateCheck();

  /**
   * Get ideal liquid density of fluid in given unit.
   *
   * @param unit {@link java.lang.String} Supported units are kg/m3 and gr/cm3
   * @return a double
   */
  public double getIdealLiquidDensity(String unit);

  /**
   * method to return interfacial tension between two phases.
   *
   * @param phase1 phase number of phase1
   * @param phase2 phase number of phase2
   * @return interfacial tension with unit N/m
   */
  public double getInterfacialTension(int phase1, int phase2);

  /**
   * <p>
   * getInterfacialTension.
   * </p>
   *
   * @param phase1 phase number of phase1
   * @param phase2 phase number of phase2
   * @param unit a {@link java.lang.String} object
   * @return interfacial tension with specified unit
   */
  public double getInterfacialTension(int phase1, int phase2, String unit);

  /**
   * method to return interfacial tension between two phases.
   *
   * @param phase1 phase type of phase1 as string (valid phases are gas, oil, aqueous)
   * @param phase2 phase type of phase2 as string (valid phases are gas, oil, aqueous)
   * @return interfacial tension with unit N/m. If one or both phases does not exist - the method
   *         will return NaN
   */
  public double getInterfacialTension(String phase1, String phase2);

  /**
   * method to return internal energy (U) in unit J.
   *
   * @return internal energy in unit Joule (J)
   */
  public double getInternalEnergy();

  /**
   * method to return internal energy (U) in a specified unit.
   *
   * @param unit Supported units are 'J', 'J/mol', 'J/kg' and 'kJ/kg'
   * @return enthalpy in specified unit
   */
  public double getInternalEnergy(String unit);

  /**
   * <p>
   * getInterphaseProperties.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface}
   *         object
   */
  public InterphasePropertiesInterface getInterphaseProperties();

  /**
   * Get the Joule Thomson Coefficient of a system. Based on a phase mole fraction basis average
   *
   * @return Joule Thomson coefficient in K/bar
   */
  public double getJouleThomsonCoefficient();

  /**
   * Get the Joule Thomson Coefficient of a system. Based on a phase mole fraction basis average.
   *
   * @param unit Supported units are K/bar, C/bar
   * @return Joule Thomson coefficient in specified unit
   */
  public double getJouleThomsonCoefficient(String unit);

  /**
   * method to return heat capacity ratio/adiabatic index/Poisson constant.
   *
   * @return kappa
   */
  public double getKappa();

  /**
   * <p>
   * getKinematicViscosity.
   * </p>
   *
   * @return a double
   */
  public double getKinematicViscosity();

  /**
   * method to return kinematic viscosity in a specified unit.
   *
   * @param unit Supported units are m2/sec
   * @return kinematic viscosity in specified unit
   */
  public double getKinematicViscosity(String unit);

  /**
   * <p>
   * getLiquidPhase.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getLiquidPhase();

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
   * getLowestGibbsEnergyPhase.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getLowestGibbsEnergyPhase();

  /**
   * method to return mass of fluid.
   *
   * @param unit Supported units are kg, gr, tons
   * @return mass in specified unit
   */
  public double getMass(String unit);

  /**
   * <p>
   * Getter for property <code>maxNumberOfPhases</code>.
   * </p>
   *
   * @return Gets the maximum allowed number of phases to use.
   */
  public int getMaxNumberOfPhases();

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @return A MixingRuleTypeInterface
   */
  public MixingRuleTypeInterface getMixingRule();

  /**
   * <p>
   * getMixingRuleName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getMixingRuleName();

  /**
   * <p>
   * Getter for property <code>modelName</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getModelName();

  /**
   * <p>
   * Returns the overall mole composition vector in unit mole fraction.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMolarComposition();

  /**
   * Get molar mass of system.
   *
   * @return molar mass in unit kg/mol
   */
  public double getMolarMass();

  /**
   * Get molar mass of a fluid phase.
   *
   * @param unit Supported units are kg/mol, gr/mol
   * @return molar mass in specified unit
   */
  public double getMolarMass(String unit);

  /**
   * Get the total molar flow rate of individual components in a fluid.
   *
   * @return molar flow of individual components in unit mol/sec
   */
  public double[] getMolarRate();

  /**
   * method to return molar volume of the fluid note: without Peneloux volume correction.
   *
   * @return molar volume volume in unit m3/mol*1e5
   */
  public double getMolarVolume();

  /**
   * method to return molar volume of the fluid: eventual volume correction included.
   *
   * @param unit Supported units are m3/mol, litre/mol
   * @return molar volume volume in unit
   */
  public double getMolarVolume(String unit);

  /**
   * <p>
   * getMolecularWeights.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMolecularWeights();

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
   * <p>
   * Get sum of mole fractions for all components. NB! init(0) must be called first.
   * </p>
   *
   * @return a double
   */
  public double getMoleFractionsSum();

  /**
   * <p>
   * getNormalBoilingPointTemperatures.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getNormalBoilingPointTemperatures();

  /**
   * <p>
   * Get number of components added to System.
   * </p>
   *
   * @return the number of components in System.
   */
  public int getNumberOfComponents();

  /**
   * <p>
   * Getter for property <code>numberOfMoles</code>.
   * </p>
   *
   * @return a double
   * @deprecated Replaced by {@link getTotalNumberOfMoles}
   */
  @Deprecated
  public default double getNumberOfMoles() {
    return getTotalNumberOfMoles();
  }

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
   * Getter for property <code>numberOfPhases</code>.
   * </p>
   *
   * @return Number of phases used
   */
  public int getNumberOfPhases();

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
   * getOilFractionLiquidDensityAt25C.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getOilFractionLiquidDensityAt25C();

  /**
   * <p>
   * getOilFractionMolecularMass.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getOilFractionMolecularMass();

  /**
   * <p>
   * getOilFractionNormalBoilingPoints.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getOilFractionNormalBoilingPoints();

  /**
   * <p>
   * Getter for property <code>PC</code>.
   * </p>
   *
   * @return Critical pressure in unit bara.
   */
  public double getPC();

  /**
   * Get phase number i from SystemInterface object.
   *
   * @param i Phase number
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getPhase(int i);

  /**
   * <p>
   * getPhase.
   * </p>
   *
   * @param pt a {@link neqsim.thermo.phase.PhaseType} object
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getPhase(PhaseType pt);

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
   * method to return phase fraction of selected phase.
   *
   * @param phaseTypeName gas/oil/aqueous
   * @param unit mole/volume/weight
   * @return phase: fraction in specified unit
   */
  public double getPhaseFraction(String phaseTypeName, String unit);

  /**
   * <p>
   * Indexed getter for property phaseIndex.
   * </p>
   *
   * @param i Phase number
   * @return PhaseIndex to index into phaseArray.
   */
  public int getPhaseIndex(int i);

  /**
   * Get property phaseIndex corresponding to a phase.
   *
   * @param phase Phase object to search for.
   * @return PhaseIndex to index into phaseArray.
   */
  public int getPhaseIndex(PhaseInterface phase);

  /**
   * <p>
   * Get property phaseIndex corresponding to a phase.
   * </p>
   *
   * @param phaseTypeName a {@link java.lang.String} object
   * @return PhaseIndex to index into phaseArray.
   */
  public int getPhaseIndex(String phaseTypeName);

  /**
   * <p>
   * Get phase number of phase of specific type.
   * </p>
   *
   * @param pt Phase type to look for.
   * @return Phase number
   */
  public int getPhaseNumberOfPhase(PhaseType pt);

  /**
   * <p>
   * Get phase number of phase of specific type.
   * </p>
   *
   * @param phaseTypeName Name of phase type to look for
   * @return Phase number
   */
  public default int getPhaseNumberOfPhase(String phaseTypeName) {
    return getPhaseNumberOfPhase(PhaseType.byDesc(phaseTypeName));
  }

  /**
   * return the phase of to specified type if the phase does not exist, the method will return null.
   *
   * @param phaseTypeName the phase type to be returned (gas, oil, aqueous, wax, hydrate are
   *        supported)
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getPhaseOfType(String phaseTypeName);

  /**
   * <p>
   * getPhases.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.phase.PhaseInterface} objects
   */
  public PhaseInterface[] getPhases();

  /**
   * method to return pressure.
   *
   * @return pressure in unit bara
   */
  public double getPressure();

  /**
   * <p>
   * method to return pressure of phase.
   * </p>
   *
   * @param phaseNumber Number of the phase to get pressure for
   * @return pressure in unit bara
   */
  public double getPressure(int phaseNumber);

  /**
   * method to return pressure in a specified unit.
   *
   * @param unit Supported units are bara, barg, Pa, MPa, psi, psia, psig
   * @return pressure in specified unit
   */
  public double getPressure(String unit);

  /**
   * Get physical properties of System.
   *
   * @return System properties
   */
  public SystemProperties getProperties();

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
   * getProperty.
   * </p>
   *
   * @param prop a {@link java.lang.String} object
   * @param phaseNum a int
   * @return a double
   */
  public double getProperty(String prop, int phaseNum);

  /**
   * <p>
   * getProperty.
   * </p>
   *
   * @param prop a {@link java.lang.String} object
   * @param compName a {@link java.lang.String} object
   * @param phaseNum a int
   * @return a double
   */
  public double getProperty(String prop, String compName, int phaseNum);

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /**
   * Get the speed of sound of a system. The sound speed is implemented based on a molar average
   * over the phases
   *
   * @return speed of sound in m/s
   */
  public double getSoundSpeed();

  /**
   * Get the speed of sound of a system. The sound speed is implemented based on a molar average
   * over the phases
   *
   * @param unit Supported units are m/s, km/h
   * @return speed of sound in m/s
   */
  public double getSoundSpeed(String unit);

  /**
   * <p>
   * Getter for property standard.
   * </p>
   *
   * @return a {@link neqsim.standards.StandardInterface} object
   */
  public neqsim.standards.StandardInterface getStandard();

  /**
   * <p>
   * Getter for property standard.
   * </p>
   *
   * @param standardName a {@link java.lang.String} object
   * @return a {@link neqsim.standards.StandardInterface} object
   */
  public neqsim.standards.StandardInterface getStandard(String standardName);

  /**
   * <p>
   * Get critical temperature.
   * </p>
   *
   * @return Critical temperature in unit Kelvin
   */
  public double getTC();

  /**
   * Get temperature.
   *
   * @return temperature in unit Kelvin
   */
  public double getTemperature();

  /**
   * <p>
   * method to return temperature from a specific phase.
   * </p>
   *
   * @param phaseNumber phase to get temperature of
   * @return temperature in unit Kelvin
   */
  public double getTemperature(int phaseNumber);
  // TODO: is it possible for the phases to have different temperatures?

  /**
   * method to return temperature in a specified unit.
   *
   * @param unit Supported units are K, C, R
   * @return temperature in specified unit
   */
  public double getTemperature(String unit);

  /**
   * method to return conductivity of a fluid.
   *
   * @return conductivity in unit W/mK
   */
  public double getThermalConductivity();

  /**
   * method to return thermal conductivity in a specified unit.
   *
   * @param unit Supported units are W/mK, W/cmK
   * @return conductivity in specified unit
   */
  public double getThermalConductivity(String unit);

  /**
   * Getter for property <code>totalNumberOfMoles</code>.
   *
   * @return Total molar flow rate of fluid in unit mol/sec
   */
  public double getTotalNumberOfMoles();

  /**
   * method to return viscosity of a fluid.
   *
   * @return viscosity in unit kg/msec
   */
  public double getViscosity();

  /**
   * method to return viscosity in a specified unit.
   *
   * @param unit Supported units are kg/msec, cP (centipoise), Pas (Pascal*second)
   * @return viscosity in specified unit
   */
  public double getViscosity(String unit);

  /**
   * method to return fluid volume.
   *
   * @return volume in unit m3*1e5
   */
  public double getVolume();

  /**
   * method to return fluid volume.
   *
   * @param unit Supported units are m3, litre, m3/kg, m3/mol
   * @return volume in specified unit
   */
  public double getVolume(String unit);

  /**
   * method to return the volume fraction of a phase note: without Peneloux volume correction.
   *
   * @param phaseNumber number of the phase to get volume fraction for
   * @return volume fraction
   */
  public double getVolumeFraction(int phaseNumber);

  /**
   * <p>
   * getOilAssayCharacterisation.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.OilAssayCharacterisation} object
   */
  public OilAssayCharacterisation getOilAssayCharacterisation();

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
   * getWaxModel.
   * </p>
   *
   * @return a {@link neqsim.thermo.characterization.WaxModelInterface} object
   */
  public WaxModelInterface getWaxModel();

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
   * method to return compressibility factor of a fluid compressibility factor is defined in EoS
   * from PV=ZnRT where V is total volume of fluid.
   *
   * @return compressibility factor Z
   */
  public double getZ();

  /**
   * method to return Z volume corrected gas compressibility.
   *
   * @return double Z volume corrected
   */
  public double getZvolcorr();

  /**
   * Verify if system has a component.
   *
   * @param name Name of component to look for. NB! Converts name to normalized name.
   * @return True if component is found.
   */
  public default boolean hasComponent(String name) {
    return hasComponent(name, true);
  }

  /**
   * Verify if system has a component.
   *
   * @param name Name of component to look for.
   * @param normalized Set true to convert input name to normalized component name.
   * @return True if component is found.
   */
  public default boolean hasComponent(String name, boolean normalized) {
    return getPhase(0).hasComponent(name, normalized);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode();

  /**
   * Verify if system has a phase of a specific type.
   *
   * @param pt PhaseType to look for
   * @return True if system contains a phase of requested type
   */
  public boolean hasPhaseType(PhaseType pt);

  /**
   * Verify if system has a phase of a specific type.
   *
   * @param phaseTypeName PhaseType to look for
   * @return True if system contains a phase of requested type
   */
  public default boolean hasPhaseType(String phaseTypeName) {
    return hasPhaseType(PhaseType.byDesc(phaseTypeName));
  }

  /**
   * <p>
   * Getter for property hasPlusFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasPlusFraction();

  /**
   * <p>
   * hasSolidPhase.
   * </p>
   *
   * @return True if system contains a solid phase
   */
  public default boolean hasSolidPhase() {
    return hasPhaseType(PhaseType.SOLID); // || hasPhaseType(PhaseType.SOLIDCOMPLEX);
  }

  /**
   * method to calculate thermodynamic properties of the fluid. The temperature, pressure, number of
   * phases and composition of the phases will be used as basis for calculation.
   *
   * @param initType - The number can be 0, 1, 2 or 3. 0: Set feed composition for all phases. 1:
   *        Calculation of density, fugacities and Z-factor 2: 1 + calculation of enthalpy, entropy,
   *        Cp, Cv, and most other thermodynamic properties 3: 1+2 + Calculation of composition
   *        derivatives of fugacity coefficients.
   */
  public void init(int initType);

  /**
   * method to calculate thermodynamic properties of the selected phase. The temperature, pressure,
   * number of phases and composition of the phase will be used as basis for calculation.
   *
   * @param initType - The number can be 0, 1, 2 or 3. 0: Set feed composition. 1: Calculation of
   *        density, fugacities and Z-factor 2: 1 + calculation of enthalpy, entropy, Cp, Cv, and
   *        most other thermodynamic properties 3: 1+2 + Calculation of composition derivatives of
   *        fugacity coefficients.
   * @param phaseNum a int
   */
  public void init(int initType, int phaseNum);

  /**
   * <p>
   * init_x_y.
   * </p>
   */
  public void init_x_y();

  /**
   * <p>
   * Calculate system beta values using Phase.getNumberOfMolesInPhase and getTotalNumberOfMoles.
   * </p>
   */
  public void initBeta();

  /**
   * initNumeric.
   */
  public void initNumeric();

  /**
   * Initialize / calculate all physical properties for all phases and interfaces.
   */
  public void initPhysicalProperties();

  /**
   * Initialize / calculate a specified physical properties for all phases and interfaces.
   *
   * @param ppt PhysicalPropertyType enum object.
   */
  public void initPhysicalProperties(PhysicalPropertyType ppt);

  /**
   * Initialize / calculate a specified physical properties for all phases and interfaces.
   *
   * @param name Name of physical property.
   */
  public default void initPhysicalProperties(String name) {
    initPhysicalProperties(PhysicalPropertyType.byName(name));
  }

  /**
   * Calculates thermodynamic and physical properties of a fluid using initThermoProperties() and
   * initPhysicalProperties().
   */
  public default void initProperties() {
    initThermoProperties();
    initPhysicalProperties();
  }

  /**
   * <p>
   * initRefPhases.
   * </p>
   */
  public void initRefPhases();

  /**
   * Calculates thermodynamic properties of a fluid using the init(2) method.
   */
  public default void initThermoProperties() {
    init(2);
  }

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
   * invertPhaseTypes.
   * </p>
   */
  public void invertPhaseTypes();

  /**
   * <p>
   * isChemicalSystem.
   * </p>
   *
   * @return a boolean
   */
  public boolean isChemicalSystem();

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
   * isForcePhaseTypes.
   * </p>
   *
   * @return a boolean
   */
  public boolean isForcePhaseTypes();

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
   * isImplementedCompositionDeriativesofFugacity.
   * </p>
   *
   * @param isImpl a boolean
   */
  public void isImplementedCompositionDeriativesofFugacity(boolean isImpl);

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
   * isImplementedTemperatureDeriativesofFugacity.
   * </p>
   *
   * @return a boolean
   */
  public boolean isImplementedTemperatureDeriativesofFugacity();

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
   * Getter for property numericDerivatives.
   * </p>
   *
   * @return a boolean
   */
  public boolean isNumericDerivatives();

  /**
   * Returns true if phase exists and is not null.
   *
   * @param i Phase number
   * @return True if phase exists, false if not.
   */
  public boolean isPhase(int i);

  /**
   * <p>
   * normalizeBeta.
   * </p>
   */
  public void normalizeBeta();

  /**
   * Order phases 0-3, as many as there are, by density such that getPhase(0) is lightest.
   *
   * <p>
   * Typically GAS, LIQUID for two-phase or GAS, OIL, AQUEOUS for multiphase .
   * </p>
   */
  public void orderByDensity();

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
   * phaseToSystem.
   * </p>
   *
   * @param newPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface phaseToSystem(PhaseInterface newPhase);

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
   * Prints the fluid in a visually appealing way.
   */
  public default void prettyPrint() {
    neqsim.thermo.util.readwrite.TablePrinter.printTable(createTable(getFluidName()));
  }

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
   * readObject.
   * </p>
   *
   * @param ID a int
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface readObject(int ID);

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
   * reInitPhaseType.
   * </p>
   */
  public void reInitPhaseType();

  /**
   * <p>
   * removeComponent.
   * </p>
   *
   * @param name Name of the component to remove. See NeqSim database for component in the database.
   */
  public void removeComponent(String name);

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
   * removePhaseKeepTotalComposition.
   * </p>
   *
   * @param specPhase a int
   */
  public void removePhaseKeepTotalComposition(int specPhase);

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
   * replacePhase.
   * </p>
   *
   * @param repPhase a int
   * @param newPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void replacePhase(int repPhase, PhaseInterface newPhase);

  /**
   * Set mole fractions of all components to 0.
   */
  public void reset();

  /**
   * <p>
   * reset_x_y.
   * </p>
   */
  public void reset_x_y();

  /**
   * <p>
   * resetCharacterisation.
   * </p>
   */
  public void resetCharacterisation();

  /**
   * <p>
   * resetDatabase.
   * </p>
   */
  public void resetDatabase();

  /**
   * <p>
   * resetPhysicalProperties.
   * </p>
   */
  public void resetPhysicalProperties();

  /**
   * <p>
   * Save System object to file.
   * </p>
   *
   * @param name File path to save to.
   */
  public void save(String name);

  /**
   * <p>
   * saveFluid.
   * </p>
   *
   * @param id a int
   */
  public void saveFluid(int id);

  /**
   * <p>
   * saveFluid.
   * </p>
   *
   * @param id a int
   * @param text a {@link java.lang.String} object
   */
  public void saveFluid(int id, String text);

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
   * saveObjectToFile.
   * </p>
   *
   * @param filePath a {@link java.lang.String} object
   * @param fluidName a {@link java.lang.String} object
   */
  public void saveObjectToFile(String filePath, String fluidName);

  /**
   * <p>
   * saveToDataBase.
   * </p>
   */
  public void saveToDataBase();

  /**
   * <p>
   * setAllComponentsInPhase.
   * </p>
   *
   * @param phaseNum a int
   */
  public void setAllComponentsInPhase(int phaseNum);

  /**
   * Set phase type of all phases.
   *
   * @param pt PhaseType to set phases as.
   */
  public void setAllPhaseType(PhaseType pt);

  /**
   * <p>
   * setAttractiveTerm.
   * </p>
   *
   * @param i a int
   */
  public void setAttractiveTerm(int i);

  /**
   * Setter for property <code>beta</code>.
   * <p>
   * NB! Sets beta = b for first (heaviest) phase and 1-b for second (lightest) phase, not for
   * multiphase systems.
   * </p>
   *
   * @param b Beta value to set.
   */
  public void setBeta(double b);

  /**
   * <p>
   * Setter for property <code>beta</code> for a given phase.
   * </p>
   *
   * @param phaseNum Phase number to set beta for.
   * @param b Beta value to set.
   */
  public void setBeta(int phaseNum, double b);

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
   * setComponentNames.
   * </p>
   *
   * @param componentNames an array of {@link java.lang.String} objects
   */
  public void setComponentNames(String[] componentNames);

  /**
   * <p>
   * setComponentNameTag.
   * </p>
   *
   * @param nameTag a {@link java.lang.String} object
   */
  public void setComponentNameTag(String nameTag);

  /**
   * <p>
   * setComponentNameTagOnNormalComponents.
   * </p>
   *
   * @param nameTag a {@link java.lang.String} object
   */
  public void setComponentNameTagOnNormalComponents(String nameTag);

  /**
   * Set the flow rate (moles) of all components to zero.
   */
  public void setEmptyFluid();

  /**
   * <p>
   * Setter for property info. .
   * </p>
   *
   * @param info a {@link java.lang.String} object
   */
  public void setFluidInfo(String info);

  /**
   * <p>
   * setFluidName.
   * </p>
   *
   * @param fluidName a {@link java.lang.String} object
   */
  public void setFluidName(String fluidName);

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
   * setHeavyTBPfractionAsPlusFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean setHeavyTBPfractionAsPlusFraction();

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
   * setImplementedCompositionDeriativesofFugacity.
   * </p>
   *
   * @param implementedCompositionDeriativesofFugacity a boolean
   */
  public void setImplementedCompositionDeriativesofFugacity(
      boolean implementedCompositionDeriativesofFugacity);

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
   * setImplementedTemperatureDeriativesofFugacity.
   * </p>
   *
   * @param implementedTemperatureDeriativesofFugacity a boolean
   */
  public void setImplementedTemperatureDeriativesofFugacity(
      boolean implementedTemperatureDeriativesofFugacity);

  /**
   * <p>
   * Setter for property <code>maxNumberOfPhases</code>.
   * </p>
   *
   * @param maxNumberOfPhases The maximum allowed number of phases to use.
   */
  public void setMaxNumberOfPhases(int maxNumberOfPhases);

  /**
   * method to set mixing rule used for the fluid.
   *
   * @param emrt MixingRuleTypeInterface enum
   */
  public void setMixingRule(MixingRuleTypeInterface emrt);

  /**
   * method to set mixing rule used for the fluid.
   *
   * @param type The type of mixing rule to be used for the fluid. 1 - classic mixing rule with all
   *        kij set to zero 2 -classic mixing rule with kij from NeqSim database 3- classic mixing
   *        rule with temperature dependent kij 4- Huron Vidal mixing rule with parameters from
   *        NeqSim database 7 -classic mixing rule with kij of CPA from NeqSim Database 9
   *        -classicmixing rule with temperature dependent kij of CPA from NeqSim database
   *        10-classic mixing rule with temperature and composition dependent kij of CPA from NeqSim
   *        database
   */
  public default void setMixingRule(int type) {
    setMixingRule(EosMixingRuleType.byValue(type));
  }

  /**
   * method to set the mixing rule for the fluid.
   *
   * @param typename a {@link java.lang.String} object
   */
  public default void setMixingRule(String typename) {
    setMixingRule(EosMixingRuleType.byName(typename.replace("-", "_")));
  }

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
   * setModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface setModel(String model);

  /**
   * <p>
   * This method is used to set the total molar composition of a fluid. The total flow rate will be
   * kept constant. The input mole fractions will be normalized.
   * </p>
   *
   * @param moles an array of type double
   */
  public void setMolarComposition(double[] moles);

  /**
   * This method is used to set the total molar composition of a characterized fluid. The total flow
   * rate will be kept constant. The input mole fractions will be normalized.
   *
   * @param molefractions is a double array taking the molar fraction of the components in the
   *        fluid. THe last fraction in the array is the total molefraction of the characterized
   *        components.
   */
  public void setMolarCompositionOfPlusFluid(double[] molefractions);

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
   * setMolarFlowRates.
   * </p>
   *
   * @param moles an array of type double
   */
  public void setMolarFlowRates(double[] moles);

  /**
   * <p>
   * setComponentFlowRates.
   * </p>
   *
   * @param componentFlowRates an array of type double
   * @param unit a {@link java.lang.String} object. Allowed units are: "mol/sec", "kmol/sec",
   *        "kmol/hr", "mol/hr", "kg/hr", "kg/sec", "kmol/day"
   */
  public void setComponentFlowRates(double[] componentFlowRates, String unit);

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
   * setMultiphaseWaxCheck.
   * </p>
   *
   * @param multiphaseWaxCheck a boolean
   */
  public void setMultiphaseWaxCheck(boolean multiphaseWaxCheck);

  /**
   * <p>
   * Setter for property <code>numberOfPhases</code>.
   * </p>
   *
   * @param number Number of phases to use.
   */
  public void setNumberOfPhases(int number);

  /**
   * <p>
   * Setter for property numericDerivatives.
   * </p>
   *
   * @param numericDerivatives a boolean
   */
  public void setNumericDerivatives(boolean numericDerivatives);

  /**
   * <p>
   * Getter for property <code>PC</code>.
   * </p>
   *
   * @param PC Critical pressure to set in unit bara.
   */
  public void setPC(double PC);

  /**
   * <p>
   * Set <code>phaseArray[phaseIndex] = phase</code>. NB! Transfers the pressure and temperature
   * from the currently existing phase object at index numb
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param index Phase index to insert object at
   */
  public void setPhase(PhaseInterface phase, int index);

  /**
   * <p>
   * Indexed setter for property <code>phaseIndex</code>.
   * <code>this.phaseIndex[index] = phaseIndex;</code>
   * </p>
   *
   * @param index a int
   * @param phaseIndex a int
   */
  public void setPhaseIndex(int index, int phaseIndex);

  /**
   * Change the phase type of a given phase.
   *
   * @param phaseToChange the phase number of the phase to set phase type
   * @param pt PhaseType to set
   */
  public void setPhaseType(int phaseToChange, PhaseType pt);

  /**
   * Change the phase type of a given phase.
   *
   * @param phaseToChange the phase number of the phase to set phase type
   * @param phaseName String to set
   */
  public void setPhaseType(int phaseToChange, String phaseName);

  /**
   * Set the physical property model type for each phase of the System.
   *
   * @param ppm PhysicalPropertyModel enum object
   */
  public default void setPhysicalPropertyModel(PhysicalPropertyModel ppm) {
    for (int i = 0; i < getNumberOfPhases(); i++) {
      getPhase(i).setPhysicalPropertyModel(ppm);
    }
  }

  /**
   * Set the physical property model type for each phase of the System.
   *
   * @param type 0 Default, 1 Water, 2 Glycol, 3 Amine, 4 CO2Water, 6 Basic
   */
  public default void setPhysicalPropertyModel(int type) {
    setPhysicalPropertyModel(PhysicalPropertyModel.byValue(type));
  }

  /**
   * method to set the pressure of a fluid (same pressure for all phases).
   *
   * @param pres pressure in unit bara (absolute pressure in bar)
   */
  public void setPressure(double pres);

  /**
   * method to set the pressure of a fluid (same pressure for all phases).
   *
   * @param newPressure in specified unit
   * @param unit unit can be bar, bara, barg or atm
   */
  public void setPressure(double newPressure, String unit);

  /**
   * <p>
   * Setter for property solidPhaseCheck.
   * </p>
   *
   * @param test a boolean
   */
  public void setSolidPhaseCheck(boolean test);

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
   * Setter for property standard.
   * </p>
   *
   * @param standardName a {@link java.lang.String} object
   */
  public void setStandard(String standardName);

  /**
   * <p>
   * Getter for property <code>TC</code>.
   * </p>
   *
   * @param TC Critical temperature to set
   */
  public void setTC(double TC);

  /**
   * <p>
   * Set the temperature of a fluid (same temperature for all phases).
   * </p>
   *
   * @param temp Temperature in unit Kelvin
   */
  public void setTemperature(double temp);

  /**
   * <p>
   * Set the temperature of a single phase in the fluid.
   * </p>
   *
   * @param temp Temperature in unit Kelvin
   * @param phaseNumber a int
   */
  public void setTemperature(double temp, int phaseNumber);

  /**
   * method to set the temperature of a fluid (same temperature for all phases).
   *
   * @param temp Temperature in specified unit
   * @param unit unit can be C or K (Celsius or Kelvin)
   */
  public void setTemperature(double temp, String unit);

  /**
   * <p>
   * setTotalFlowRate.
   * </p>
   *
   * @param flowRate a double
   * @param flowunit a {@link java.lang.String} object. flow units are: kg/sec, kg/min, kg/hr
   *        m3/sec, m3/min, m3/hr, mole/sec, mole/min, mole/hr, Sm3/hr, Sm3/day, idSm3/hr, idSm3/day
   */
  public void setTotalFlowRate(double flowRate, String flowunit);

  /**
   * <p>
   * Setter for property <code>totalNumberOfMoles</code>.
   * </p>
   *
   * @param totalNumberOfMoles Total molar flow rate of fluid in unit mol/sec
   */
  public void setTotalNumberOfMoles(double totalNumberOfMoles);

  /**
   * <p>
   * setUseTVasIndependentVariables.
   * </p>
   *
   * @param useTVasIndependentVariables a boolean
   */
  public void setUseTVasIndependentVariables(boolean useTVasIndependentVariables);

  /**
   * <p>
   * tuneModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   * @param val a double
   * @param phaseNum a int
   */
  public void tuneModel(String model, double val, int phaseNum);

  /**
   * <p>
   * useVolumeCorrection.
   * </p>
   *
   * @param volcor a boolean
   */
  public void useVolumeCorrection(boolean volcor);

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
   * getKvector - return vector of equilibrium constants.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getKvector();

  /**
   * <p>
   * getzvector - return vector of total molar composition.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getzvector();

  /**
   * <p>
   * toJson - return String with json inormation of fluid.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String toJson();

  /**
   * <p>
   * toCompJson - return String with json inormation of pure component properties of fluid.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String toCompJson();

  /**
   * <p>
   * setForceSinglePhase - force the fluid to be single phase of type as spesified by phasetype
   * input.
   * </p>
   *
   * @param phasetype a {@link neqsim.thermo.phase.PhaseType} object
   */
  public void setForceSinglePhase(PhaseType phasetype);

  /**
   * <p>
   * setForceSinglePhase - force the fluid to be single phase of type as spesified by phasetype
   * input. phasetypename can be GAS, OIL and AQUEOUS
   * </p>
   *
   * @param phasetypename a {@link java.lang.String} object
   */
  public void setForceSinglePhase(String phasetypename);

  /**
   * <p>
   * isInitialized.
   * </p>
   *
   * @return a boolean
   */
  public boolean isInitialized();

  /**
   * Sets the molar composition of components whose names contain the specified definition.
   *
   * @param nameDef the definition to match component names against
   * @param molarComposition an array of molar compositions to set for the matching components
   */
  public void setMolarCompositionOfNamedComponents(String nameDef, double[] molarComposition);

  /**
   * Adds a TBP fraction to the system.
   *
   * @param componentName the name of the component
   * @param numberOfMoles number of moles
   * @param density density of the component
   * @param boilingPoint boiling point
   */
  public void addTBPfraction3(String componentName, double numberOfMoles, double density,
      double boilingPoint);

  /**
   * Adds a TBP fraction to the system.
   *
   * @param componentName the name of the component
   * @param numberOfMoles number of moles
   * @param molarMass molar mass
   * @param density density of the component
   * @param boilingPoint boiling point
   */
  public void addTBPfraction4(String componentName, double numberOfMoles, double molarMass,
      double density, double boilingPoint);

  /**
   * <p>
   * calculateMolarMassFromDensityAndBoilingPoint.
   * </p>
   *
   * @param density a double
   * @param boilingPoint a double
   * @return a double
   */
  public double calculateMolarMassFromDensityAndBoilingPoint(double density, double boilingPoint);
}
