/*
 * PhaseInterface.java
 *
 * Created on 3. juni 2000, 14:45
 */

package neqsim.thermo.phase;

import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.mixingrule.MixingRuleTypeInterface;
import neqsim.thermo.mixingrule.MixingRulesInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PhaseInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseInterface extends ThermodynamicConstantsInterface, Cloneable {
  /**
   * <p>
   * Add component to component array and update moles variables.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public void addComponent(String name, double moles, double molesInPhase, int compIndex);

  /**
   * <p>
   * Set <code>x</code> and normalize for all Components in phase.
   * </p>
   *
   * @param x Mole fractions of component in a phase.
   */
  public void setMoleFractions(double[] x);

  /**
   * <p>
   * getPhaseFraction.
   * </p>
   *
   * @return a double
   */
  public double getPhaseFraction();

  /**
   * <p>
   * Returns the composition vector in unit molefraction/wtfraction/molespersec/volumefraction.
   * </p>
   *
   * @param unit Supported units are molefraction, wtfraction, molespersec, volumefraction
   * @return composition array with unit
   */
  public double[] getComposition(String unit);

  /**
   * <p>
   * getCp0.
   * </p>
   *
   * @return a double
   */
  public double getCp0();

  /**
   * method to get density of a phase using the AGA8-Detail EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_AGA8();

  /**
   * method to get the Joule Thomson Coefficient of a phase.
   *
   * @return Joule Thomson coefficient in K/bar
   */
  public double getJouleThomsonCoefficient();

  /**
   * method to get the Joule Thomson Coefficient of a phase.
   *
   * @param unit Supported units are K/bar, C/bar
   * @return Joule Thomson coefficient in specified unit
   */
  public double getJouleThomsonCoefficient(String unit);

  /**
   * Returns the mole composition vector in unit mole fraction.
   *
   * @return an array of type double
   */
  public double[] getMolarComposition();

  /**
   * method to return phase volume note: without Peneloux volume correction.
   *
   * @return volume in unit m3*1e5
   */
  public double getVolume();

  /**
   * method to return fluid volume.
   *
   * @param unit Supported units are m3, litre
   * @return volume in specified unit
   */
  public double getVolume(String unit);

  /**
   * method to return heat capacity ratio/adiabatic index/Poisson constant. The method calculates it
   * as Cp (real) /Cv (real).
   *
   * @return gamma
   */
  public double getGamma();

  /**
   * method to return heat capacity ratio calculated as Cp/(Cp-R*getNumberOfMolesInPhase).
   *
   * @return kappa
   */
  public default double getGamma2() {
    return getCp() / (getCp() - ThermodynamicConstantsInterface.R * getNumberOfMolesInPhase());
  }

  /**
   * <p>
   * getComponentWithIndex.
   * </p>
   *
   * @param index a int
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponentWithIndex(int index);

  /**
   * <p>
   * getWtFractionOfWaxFormingComponents.
   * </p>
   *
   * @return a double
   */
  public double getWtFractionOfWaxFormingComponents();

  /**
   * <p>
   * getCompressibilityX.
   * </p>
   *
   * @return a double
   */
  public double getCompressibilityX();

  /**
   * <p>
   * getCompressibilityY.
   * </p>
   *
   * @return a double
   */
  public double getCompressibilityY();

  /**
   * <p>
   * getIsothermalCompressibility.
   * </p>
   *
   * @return a double
   */
  public double getIsothermalCompressibility();

  /**
   * <p>
   * getIsobaricThermalExpansivity.
   * </p>
   *
   * @return a double
   */
  public double getIsobaricThermalExpansivity();

  /**
   * <p>
   * getdrhodN.
   * </p>
   *
   * @return a double
   */
  public double getdrhodN();

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
   * Init using current phase properties.
   * </p>
   */
  public default void init() {
    init(getNumberOfMolesInPhase() / getBeta(), getNumberOfComponents(), getInitType(), getType(),
        getBeta());
  }

  /**
   * <p>
   * init. Uses existing phase type.
   * </p>
   *
   * @param totalNumberOfMoles Total number of moles in all phases of Stream.
   * @param numberOfComponents Number of components in system.
   * @param initType a int. Use 0 to init, and 1 to reset.
   * @param beta Mole fraction of this phase in system.
   */
  public default void init(double totalNumberOfMoles, int numberOfComponents, int initType,
      double beta) {
    init(totalNumberOfMoles, numberOfComponents, initType, getType(), beta);
  }

  /**
   * <p>
   * init.
   * </p>
   *
   * @param totalNumberOfMoles Total number of moles in all phases of Stream.
   * @param numberOfComponents Number of components in system.
   * @param initType a int. Use 0 to init, and 1 to reset.
   * @param pt Type of phase.
   * @param beta Mole fraction of this phase in system.
   */
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta);

  /**
   * <p>
   * Init / calculate all physical properties of phase.
   * </p>
   */
  public void initPhysicalProperties();

  /**
   * <p>
   * Init / calculate specified physical property of phase.
   * </p>
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
   * <p>
   * getdrhodP.
   * </p>
   *
   * @return a double
   */
  public double getdrhodP();

  /**
   * <p>
   * getdrhodT.
   * </p>
   *
   * @return a double
   */
  public double getdrhodT();

  /**
   * <p>
   * getEnthalpydP.
   * </p>
   *
   * @return a double
   */
  public double getEnthalpydP();

  /**
   * <p>
   * getEnthalpydT.
   * </p>
   *
   * @return a double
   */
  public double getEnthalpydT();

  /**
   * <p>
   * getEntropydP.
   * </p>
   *
   * @return a double
   */
  public double getEntropydP();

  /**
   * <p>
   * getEntropydT.
   * </p>
   *
   * @return a double
   */
  public double getEntropydT();

  /**
   * <p>
   * getMoleFraction.
   * </p>
   *
   * @return a double
   */
  public double getMoleFraction();

  /**
   * <p>
   * Get component array of Phase.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  public ComponentInterface[] getcomponentArray();

  /**
   * Get normalized names of components in phase.
   *
   * @return Array of names of components in phase.
   */
  public String[] getComponentNames();

  /**
   * <p>
   * getMass.
   * </p>
   *
   * @return a double
   */
  public double getMass();

  /**
   * <p>
   * getWtFraction.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @return a double
   */
  public double getWtFraction(SystemInterface system);

  /**
   * method to return molar volume of the phase note: without Peneloux volume correction.
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
   * method to return flow rate of a phase.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, m3/sec, m3/min, m3/hr, ft3/sec,
   *        mole/sec, mole/min, mole/hr
   * @return flow rate in specified unit
   */
  public double getFlowRate(String flowunit);

  /**
   * <p>
   * setComponentArray.
   * </p>
   *
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  public void setComponentArray(ComponentInterface[] components);

  /**
   * method to get density of a phase using the GERG-2008 EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_GERG2008();

  /**
   * <p>
   * method to get GERG properties of a phase using the GERG-2008 EoS.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getProperties_GERG2008();

 
  /**
   * method to get Leachman density of a phase using the Leachman EoS.
   *
   * @param hydrogenType Supported types are 'normal', 'para', 'ortho'
   * @return density with unit kg/m3
   */
  public double getDensity_Leachman(String hydrogenType);

  /**
   * Overloaded method to get the Leachman density with default hydrogen type ('normal').
   *
   * @return density with unit kg/m3
   */
  public double getDensity_Leachman();

  /**
   * <p>
   * method to get Leachman properties of a phase using the Leachman EoS.
   * </p>
   *
   * @return an array of type double
   * @param hydrogenType a {@link java.lang.String} object
   */
  public double[] getProperties_Leachman(String hydrogenType);

  /**
   * Overloaded method to get the Leachman properties with default hydrogen type ('normal').
   *
   * @return density with unit kg/m3
   */
  public double[] getProperties_Leachman();


  

    /**
   * method to get helium density of a phase using the Vega EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_Vega();

  /**
   * <p>
   * method to get helium properties of a phase using the Vega EoS.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getProperties_Vega();


  /**
   * method to get density of a phase note: does not use Peneloux volume correction.
   *
   * @return density with unit kg/m3
   */
  public double getDensity();

  /**
   * method to get density of a fluid note: with Peneloux volume correction.
   *
   * @param unit Supported units are kg/m3, mol/m3
   * @return density in specified unit
   */
  public double getDensity(String unit);

  /**
   * <p>
   * Remove component from Phase.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   */
  public void removeComponent(String name, double moles, double molesInPhase);

  /**
   * <p>
   * getFugacity.
   * </p>
   *
   * @param compNumb a int
   * @return a double
   */
  public double getFugacity(int compNumb);

  /**
   * <p>
   * getFugacity.
   * </p>
   *
   * @param compName a {@link java.lang.String} object
   * @return a double
   */
  public double getFugacity(String compName);

  /**
   * method to return phase volume note: without Peneloux volume correction.
   *
   * @return volume in unit m3*1e5
   */
  public double getTotalVolume();

  /**
   * method to return phase volume with Peneloux volume correction need to call
   * initPhysicalProperties() before this method is called.
   *
   * @return volume in unit m3
   */
  public double getCorrectedVolume();

  /**
   * <p>
   * hasTBPFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean hasTBPFraction();

  /**
   * <p>
   * getMolalMeanIonicActivity.
   * </p>
   *
   * @param comp1 a int
   * @param comp2 a int
   * @return a double
   */
  public double getMolalMeanIonicActivity(int comp1, int comp2);

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
   * getMixGibbsEnergy.
   * </p>
   *
   * @return a double
   */
  public double getMixGibbsEnergy();

  /**
   * <p>
   * getExcessGibbsEnergy.
   * </p>
   *
   * @return a double
   */
  public double getExcessGibbsEnergy();

  /**
   * <p>
   * getExcessGibbsEnergySymetric.
   * </p>
   *
   * @return a double
   */
  public double getExcessGibbsEnergySymetric();

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
   * getSresTP.
   * </p>
   *
   * @return a double
   */
  public double getSresTP();

  /**
   * <p>
   * setProperties. Transfer properties from another phase object.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setProperties(PhaseInterface phase);

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
   * useVolumeCorrection.
   * </p>
   *
   * @return a boolean
   */
  public boolean useVolumeCorrection();

  /**
   * <p>
   * Getter for property <code>beta</code>. Beta is the mole fraction of a phase of all the moles of
   * a system.
   * </p>
   *
   * @return Beta value
   */
  public double getBeta();

  /**
   * <p>
   * Setter for property <code>beta</code>. Beta is the mole fraction of a phase of all the moles of
   * a system.
   * </p>
   *
   * @param b Beta value to set.
   */
  public void setBeta(double b);

  /**
   * <p>
   * getWtFrac.
   * </p>
   *
   * @param component a int
   * @return a double
   */
  public double getWtFrac(int component);

  /**
   * <p>
   * getWtFrac.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   * @return a double
   */
  public double getWtFrac(String componentName);

  /**
   * <p>
   * setMixingRuleGEModel.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setMixingRuleGEModel(String name);

  /**
   * <p>
   * Get Component by name.
   * </p>
   *
   * @param name Name of component
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponent(String name);

  /**
   * <p>
   * Get Component by index.
   * </p>
   *
   * @param i Component index
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponent(int i);

  /**
   * <p>
   * getActivityCoefficient.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficient(int k);

  /**
   * <p>
   * getActivityCoefficient.
   * </p>
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getActivityCoefficient(int k, int p);

  /**
   * <p>
   * Set the pressure in bara (absolute pressure in bar).
   * </p>
   *
   * @param pres a double
   */
  public void setPressure(double pres);

  /**
   * <p>
   * getpH.
   * </p>
   *
   * @return a double
   */
  public double getpH();

  /**
   * Normalize property <code>x</code>.
   *
   * <p>
   * Property <code>x</code> is the mole fraction of a component in a specific phase. Normalizing,
   * means that the sum of <code>x</code> for all Components in a phase equal 1.0.
   * </p>
   */
  public void normalize();

  /**
   * <p>
   * getLogPureComponentFugacity.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getLogPureComponentFugacity(int k);

  /**
   * <p>
   * getPureComponentFugacity.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getPureComponentFugacity(int k);

  /**
   * <p>
   * getPureComponentFugacity.
   * </p>
   *
   * @param k a int
   * @param pure a boolean
   * @return a double
   */
  public double getPureComponentFugacity(int k, boolean pure);

  /**
   * <p>
   * Change the number of moles of component of phase,i.e., <code>numberOfMolesInPhase</code> but do
   * not change the total number of moles of component in system.
   *
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to
   * keep consistency between phase and component calculation of total number of moles in system.
   * </p>
   *
   * @param component Component number to change
   * @param dn Number of moles of component added to phase
   */
  public default void addMoles(int component, double dn) {
    addMolesChemReac(component, dn, 0);
  }

  /**
   * <p>
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and
   * total number of moles of component in system, i.e., <code>numberOfMoles</code> with the same
   * amount.
   *
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to
   * keep consistency between phase and component calculation of total number of moles in system.
   * </p>
   *
   * @param component Component number to change
   * @param dn Number of moles of component added to phase and system
   */
  public default void addMolesChemReac(int component, double dn) {
    addMolesChemReac(component, dn, dn);
  }

  /**
   * <p>
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code> and
   * <code>Component</code> properties for the number of moles of component of phase, i.e.,
   * <code>numberOfMolesInPhase</code>, and total number of moles of component in system, i.e.,
   * <code>numberOfMoles</code> with separate amounts.
   *
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to
   * keep consistency between phase and component calculation of total number of moles in system.
   * </p>
   *
   * @param component Component number to change
   * @param dn Number of moles of component to add to phase
   * @param totdn Number of moles of component to add to system
   */
  public void addMolesChemReac(int component, double dn, double totdn);

  /**
   * <p>
   * Set the flow rate (moles) of all components to zero.
   * </p>
   */
  public void setEmptyFluid();

  /**
   * <p>
   * getPhysicalProperties.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PhysicalProperties getPhysicalProperties();

  /**
   * <p>
   * resetPhysicalProperties.
   * </p>
   */
  public void resetPhysicalProperties();

  /**
   * <p>
   * Specify the type of the physical properties.
   * </p>
   */
  public default void setPhysicalProperties() {
    setPhysicalProperties(getPhysicalPropertyModel());
  }

  /**
   * <p>
   * Specify the type of the physical properties.
   * </p>
   *
   * @param ppm PhysicalPropertyModel enum object
   */
  public void setPhysicalProperties(PhysicalPropertyModel ppm);

  /**
   * <p>
   * Getter for property ppm.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.system.PhysicalPropertyModel} object
   */
  public PhysicalPropertyModel getPhysicalPropertyModel();

  /**
   * <p>
   * Setter for property ppm.
   * </p>
   *
   * @param ppm PhysicalPropertyModel enum object
   */
  public void setPpm(PhysicalPropertyModel ppm);

  /**
   * <p>
   * setPhysicalPropertyModel.
   * </p>
   *
   * @param ppm a {@link neqsim.physicalproperties.system.PhysicalPropertyModel} object
   */
  public void setPhysicalPropertyModel(PhysicalPropertyModel ppm);

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
   * setAttractiveTerm.
   * </p>
   *
   * @param i a int
   */
  public void setAttractiveTerm(int i);

  /**
   * <p>
   * setMixingRule.
   * </p>
   *
   * @param mr a MixingRuleTypeInterface
   */
  public void setMixingRule(MixingRuleTypeInterface mr);

  /**
   * <p>
   * setMixingRule.
   * </p>
   *
   * @param mr a int
   */
  public default void setMixingRule(int mr) {
    setMixingRule(EosMixingRuleType.byValue(mr));
  }

  /**
   * <p>
   * resetMixingRule.
   * </p>
   *
   * @param mr a int
   */
  public void resetMixingRule(MixingRuleTypeInterface mr);

  /**
   * Set the temperature of a phase.
   *
   * @param temperature in unit Kelvin
   */
  public void setTemperature(double temperature);

  /**
   * <p>
   * molarVolume.
   * </p>
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param pt the PhaseType of the phase
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException;

  /**
   * <p>
   * geta.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * getb.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * getAntoineVaporPressure.
   * </p>
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressure(double temp);

  /**
   * <p>
   * calcA.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcB.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * <p>
   * calcAi.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcAiT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcAij.
   * </p>
   *
   * @param compNumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcBij.
   * </p>
   *
   * @param compNumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcAT.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAT(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcBi.
   * </p>
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure,
      int numbcomp);

  /**
   * <p>
   * calcR.
   * </p>
   *
   * @return a double
   */
  double calcR();

  // double getf();

  /**
   * <p>
   * getg.
   * </p>
   *
   * @return a double
   */
  double getg();

  /**
   * method to return enthalpy of a phase in unit Joule.
   *
   * @return a double
   */
  public double getEnthalpy();

  /**
   * method to return phase enthalpy in a specified unit.
   *
   * @param unit Supported units are J, J/mol, kJ/kmol, J/kg and kJ/kg
   * @return enthalpy in specified unit
   */
  public double getEnthalpy(String unit);

  /**
   * method to return entropy of the phase.
   *
   * @return a double
   */
  public double getEntropy();

  /**
   * method to return entropy of the phase.
   *
   * @param unit Supported units are J/K, J/moleK, J/kgK and kJ/kgK
   * @return entropy in specified unit
   */
  public double getEntropy(String unit);

  /**
   * method to return viscosity of the phase.
   *
   * @return viscosity in unit kg/msec
   */
  public double getViscosity();

  /**
   * method to return viscosity of the phase in a specified unit.
   *
   * @param unit Supported units are kg/msec, Pas, cP (centipoise)
   * @return viscosity in specified unit
   */
  public double getViscosity(String unit);

  /**
   * method to return conductivity of a phase.
   *
   * @return conductivity in unit W/m*K
   */
  public double getThermalConductivity();

  /**
   * method to return conductivity in a specified unit.
   *
   * @param unit Supported units are W/mK, W/cmK
   * @return conductivity in specified unit
   */
  public double getThermalConductivity(String unit);

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
   * <p>
   * getHresTP.
   * </p>
   *
   * @return a double
   */
  public double getHresTP();

  /**
   * <p>
   * getGresTP.
   * </p>
   *
   * @return a double
   */
  public double getGresTP();

  /**
   * method to return specific heat capacity (Cv).
   *
   * @return Cv in unit J/K
   */
  public double getCv();

  /**
   * method to return specific heat capacity (Cv) in a specified unit.
   *
   * @param unit Supported units are J/K, J/molK, J/kgK and kJ/kgK
   * @return Cv in specified unit
   */
  public double getCv(String unit);

  /**
   * method to return real gas isentropic exponent (kappa = - Cp/Cv*(v/p)*dp/dv method to return
   * heat capacity ratio/adiabatic index/Poisson constant.
   *
   * @return kappa
   */
  public double getKappa();

  /**
   * <p>
   * getCpres.
   * </p>
   *
   * @return a double
   */
  public double getCpres();

  /**
   * <p>
   * getZ.
   * </p>
   *
   * @return a double
   */
  public double getZ();

  /**
   * <p>
   * getPseudoCriticalPressure.
   * </p>
   *
   * @return a double
   */
  public double getPseudoCriticalPressure();

  /**
   * <p>
   * getPseudoCriticalTemperature.
   * </p>
   *
   * @return a double
   */
  public double getPseudoCriticalTemperature();

  /**
   * <p>
   * getPhase.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  PhaseInterface getPhase();

  /**
   * <p>
   * Get number of components added to Phase.
   * </p>
   *
   * @return the number of components in Phase.
   */
  public int getNumberOfComponents();

  /**
   * <p>
   * setNumberOfComponents.
   * </p>
   *
   * @param k a int
   */
  public void setNumberOfComponents(int k);

  /**
   * <p>
   * getComponents.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  ComponentInterface[] getComponents();

  /**
   * <p>
   * Get the number of moles the phase contains.
   * </p>
   *
   * @return The number of moles in the phase.
   */
  public double getNumberOfMolesInPhase();

  /**
   * <p>
   * calcMolarVolume.
   * </p>
   *
   * @param test a boolean
   */
  public void calcMolarVolume(boolean test);

  /**
   * <p>
   * setTotalVolume.
   * </p>
   *
   * @param volume a double
   */
  public void setTotalVolume(double volume);

  /**
   * <p>
   * setMolarVolume.
   * </p>
   *
   * @param molarVolume a double
   */
  public void setMolarVolume(double molarVolume);

  // public double getInfiniteDiluteFugacity(int k);

  /**
   * <p>
   * getInfiniteDiluteFugacity.
   * </p>
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getInfiniteDiluteFugacity(int k, int p);

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
   * getNumberOfMolecularComponents.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfMolecularComponents();

  /**
   * <p>
   * getNumberOfIonicComponents.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfIonicComponents();

  // double calcA2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);
  // double calcB2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);

  /**
   * <p>
   * getA.
   * </p>
   *
   * @return a double
   */
  public double getA();

  /**
   * <p>
   * getB.
   * </p>
   *
   * @return a double
   */
  public double getB();
  // public double getBi();

  /**
   * <p>
   * getAT.
   * </p>
   *
   * @return a double
   */
  public double getAT();

  /**
   * <p>
   * getATT.
   * </p>
   *
   * @return a double
   */
  public double getATT();
  // public double getAiT();

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface clone();

  /**
   * Get temperature of phase.
   *
   * @return temperature in unit K
   */
  public double getTemperature();

  /**
   * method to return temperature in a specified unit.
   *
   * @param unit Supported units are K, C, R
   * @return temperature in specified unit
   */
  public double getTemperature(String unit);

  /**
   * Get pressure of phase.
   *
   * @return pressure in unit bara
   */
  public double getPressure();

  /**
   * Get pressure of phase in a specified unit.
   *
   * @param unit Supported units are bara, barg, Pa, MPa, psi, psia, psig
   * @return pressure in specified unit
   */
  public double getPressure(String unit);

  /**
   * Get molar mass of phase.
   *
   * @return molar mass in unit kg/mol
   */
  public double getMolarMass();

  /**
   * method to get molar mass of a fluid phase.
   *
   * @param unit Supported units are kg/mol, gr/mol
   * @return molar mass in specified unit
   */
  public double getMolarMass(String unit);

  /**
   * <p>
   * getInternalEnergy.
   * </p>
   *
   * @return a double
   */
  public double getInternalEnergy();

  /**
   * <p>
   * getdPdrho.
   * </p>
   *
   * @return a double
   */
  public double getdPdrho();

  /**
   * <p>
   * getdPdTVn.
   * </p>
   *
   * @return a double
   */
  public double getdPdTVn();

  /**
   * <p>
   * getdPdVTn.
   * </p>
   *
   * @return a double
   */
  public double getdPdVTn();

  /**
   * <p>
   * Fn.
   * </p>
   *
   * @return a double
   */
  public double Fn();

  /**
   * <p>
   * FT.
   * </p>
   *
   * @return a double
   */
  public double FT();

  /**
   * <p>
   * FV.
   * </p>
   *
   * @return a double
   */
  public double FV();

  /**
   * <p>
   * FD.
   * </p>
   *
   * @return a double
   */
  public double FD();

  /**
   * <p>
   * FB.
   * </p>
   *
   * @return a double
   */
  public double FB();

  /**
   * <p>
   * gb.
   * </p>
   *
   * @return a double
   */
  public double gb();

  /**
   * <p>
   * fb.
   * </p>
   *
   * @return a double
   */
  public double fb();

  /**
   * <p>
   * gV.
   * </p>
   *
   * @return a double
   */
  public double gV();

  /**
   * <p>
   * fv.
   * </p>
   *
   * @return a double
   */
  public double fv();

  /**
   * <p>
   * FnV.
   * </p>
   *
   * @return a double
   */
  public double FnV();

  /**
   * <p>
   * FnB.
   * </p>
   *
   * @return a double
   */
  public double FnB();

  /**
   * <p>
   * FTT.
   * </p>
   *
   * @return a double
   */
  public double FTT();

  /**
   * <p>
   * FBT.
   * </p>
   *
   * @return a double
   */
  public double FBT();

  /**
   * <p>
   * FDT.
   * </p>
   *
   * @return a double
   */
  public double FDT();

  /**
   * <p>
   * FBV.
   * </p>
   *
   * @return a double
   */
  public double FBV();

  /**
   * <p>
   * FBB.
   * </p>
   *
   * @return a double
   */
  public double FBB();

  /**
   * <p>
   * FDV.
   * </p>
   *
   * @return a double
   */
  public double FDV();

  /**
   * <p>
   * FBD.
   * </p>
   *
   * @return a double
   */
  public double FBD();

  /**
   * <p>
   * FTV.
   * </p>
   *
   * @return a double
   */
  public double FTV();

  /**
   * <p>
   * FVV.
   * </p>
   *
   * @return a double
   */
  public double FVV();

  /**
   * <p>
   * gVV.
   * </p>
   *
   * @return a double
   */
  public double gVV();

  /**
   * <p>
   * gBV.
   * </p>
   *
   * @return a double
   */
  public double gBV();

  /**
   * <p>
   * gBB.
   * </p>
   *
   * @return a double
   */
  public double gBB();

  /**
   * <p>
   * fVV.
   * </p>
   *
   * @return a double
   */
  public double fVV();

  /**
   * <p>
   * fBV.
   * </p>
   *
   * @return a double
   */
  public double fBV();

  /**
   * <p>
   * fBB.
   * </p>
   *
   * @return a double
   */
  public double fBB();

  /**
   * <p>
   * Calculate derivative of F per Temperature, i.e., dF/dT.
   * </p>
   *
   * @return a double
   */
  public double dFdT();

  /**
   * <p>
   * Calculate derivative of F per Volume, i.e., dF/dV.
   * </p>
   *
   * @return a double
   */
  public double dFdV();

  /**
   * <p>
   * Calculate derivative of F per Temperature and Volume, i.e., dF/dT * 1/dV.
   * </p>
   *
   * @return a double
   */
  public double dFdTdV();

  /**
   * <p>
   * dFdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFdVdV();

  /**
   * <p>
   * dFdTdT.
   * </p>
   *
   * @return a double
   */
  public double dFdTdT();

  /**
   * <p>
   * getOsmoticCoefficientOfWater.
   * </p>
   *
   * @return a double
   */
  public double getOsmoticCoefficientOfWater();

  /**
   * <p>
   * getOsmoticCoefficient.
   * </p>
   *
   * @param watNumb a int
   * @return a double
   */
  public double getOsmoticCoefficient(int watNumb);

  /**
   * <p>
   * getMeanIonicActivity.
   * </p>
   *
   * @param comp1 a int
   * @param comp2 a int
   * @return a double
   */
  public double getMeanIonicActivity(int comp1, int comp2);

  /**
   * <p>
   * getLogInfiniteDiluteFugacity.
   * </p>
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getLogInfiniteDiluteFugacity(int k, int p);

  /**
   * <p>
   * getLogInfiniteDiluteFugacity.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getLogInfiniteDiluteFugacity(int k);

  /**
   * Get mixing rule.
   *
   * @return a MixingRulesInterface
   */
  public MixingRulesInterface getMixingRule();

  /**
   * Get mixing rule type.
   *
   * @return a MixingRuleTypeInterface
   */
  public MixingRuleTypeInterface getMixingRuleType();

  /**
   * <p>
   * initRefPhases.
   * </p>
   *
   * @param onlyPure a boolean
   */
  public void initRefPhases(boolean onlyPure);

  /**
   * <p>
   * Indexed getter for property refPhase.
   * </p>
   *
   * @param index a int
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public neqsim.thermo.phase.PhaseInterface getRefPhase(int index);

  /**
   * <p>
   * Getter for property refPhase.
   * </p>
   *
   * @return an array of {@link neqsim.thermo.phase.PhaseInterface} objects
   */
  public neqsim.thermo.phase.PhaseInterface[] getRefPhase();

  /**
   * <p>
   * Indexed setter for property refPhase.
   * </p>
   *
   * @param index a int
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase);

  /**
   * <p>
   * Setter for property refPhase.
   * </p>
   *
   * @param refPhase an array of {@link neqsim.thermo.phase.PhaseInterface} objects
   */
  public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase);

  /**
   * <p>
   * setParams.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param DijT an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT,
      String[][] mixRule, double[][] intparam);

  /**
   * Getter for property pt.
   *
   * @return PhaseType enum object.
   */
  public PhaseType getType();

  /**
   * Setter for property pt.
   *
   * @param pt PhaseType to set.
   */
  public void setType(PhaseType pt);

  /**
   * <p>
   * Getter for property phaseTypeName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public default String getPhaseTypeName() {
    return getType().getDesc();
  }

  /**
   * <p>
   * Setter for property phaseTypeName.
   * </p>
   *
   * @param phaseTypeName a {@link java.lang.String} object
   */
  public default void setPhaseTypeName(String phaseTypeName) {
    setType(PhaseType.byDesc(phaseTypeName));
  }

  /**
   * <p>
   * Check if mixing rule is defined.
   * </p>
   *
   * @return Returns true if MixingRule is defined and false if not.
   */
  public boolean isMixingRuleDefined();

  /**
   * <p>
   * getActivityCoefficientSymetric.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficientSymetric(int k);

  /**
   * <p>
   * getActivityCoefficientUnSymetric.
   * </p>
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficientUnSymetric(int k);

  /**
   * Verify if phase has a component.
   *
   * @param name Name of component to look for. NB! Converts name to normalized name.
   * @return True if component is found.
   */
  public default boolean hasComponent(String name) {
    return hasComponent(name, true);
  }

  /**
   * Verify if phase has a component.
   *
   * @param name Name of component to look for.
   * @param normalized Set true to convert input name to normalized component name.
   * @return True if component is found.
   */
  public boolean hasComponent(String name, boolean normalized);

  /**
   * <p>
   * getLogActivityCoefficient.
   * </p>
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getLogActivityCoefficient(int k, int p);

  /**
   * <p>
   * isConstantPhaseVolume.
   * </p>
   *
   * @return a boolean
   */
  public boolean isConstantPhaseVolume();

  /**
   * <p>
   * setConstantPhaseVolume.
   * </p>
   *
   * @param constantPhaseVolume a boolean
   */
  public void setConstantPhaseVolume(boolean constantPhaseVolume);

  /**
   * method to get the speed of sound of a phase note: implemented in phaseEos.
   *
   * @return speed of sound in m/s
   */
  public double getSoundSpeed();

  /**
   * method to get the speed of sound of a system. The sound speed is implemented based on a molar
   * average over the phases
   *
   * @param unit Supported units are m/s, km/h
   * @return speed of sound in m/s
   */
  public double getSoundSpeed(String unit);

  /**
   * method to return name of thermodynamic model
   *
   * @return String model name
   */
  public String getModelName();

  /**
   * method to return Z volume corrected gas compressibility
   *
   * @return double Z volume corrected
   */
  public double getZvolcorr();
}
