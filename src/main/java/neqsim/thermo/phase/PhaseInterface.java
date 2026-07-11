/*
 * PhaseInterface.java
 *
 * Created on 3. juni 2000, 14:45
 */

package neqsim.thermo.phase;

import org.netlib.util.doubleW;
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
 * PhaseInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseInterface extends ThermodynamicConstantsInterface, Cloneable {
  /**
   * Add component to component array and update moles variables.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public void addComponent(String name, double moles, double molesInPhase, int compIndex);

  /**
   * Set <code>x</code> and normalize for all Components in phase.
   *
   * @param x Mole fractions of component in a phase.
   */
  public void setMoleFractions(double[] x);

  /**
   * getPhaseFraction. Alias for getBeta()
   *
   * @return Beta value
   */
  public default double getPhaseFraction() {
    return getBeta();
  }

  /**
   * Returns the composition vector in unit molefraction/wtfraction/molespersec/volumefraction.
   *
   * @param unit Supported units are molefraction, wtfraction, molespersec, volumefraction
   * @return composition array with unit
   */
  public double[] getComposition(String unit);

  /**
   * getCp0.
   *
   * @return a double
   */
  public double getCp0();

  /**
   * Get density of a phase using the AGA8-Detail EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_AGA8();

  /**
   * Get the Joule Thomson Coefficient of a phase.
   *
   * @return Joule Thomson coefficient in K/bar
   */
  public double getJouleThomsonCoefficient();

  /**
   * Get the Joule Thomson Coefficient of a phase.
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
   * method to return heat capacity ratio/adiabatic index/Poisson constant. The method calculates it as Cp (real) /Cv
   * (real).
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
   * getComponentWithIndex.
   *
   * @param index a int
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponentWithIndex(int index);

  /**
   * getWtFractionOfWaxFormingComponents.
   *
   * @return a double
   */
  public double getWtFractionOfWaxFormingComponents();

  /**
   * getCompressibilityX.
   *
   * @return a double
   */
  public double getCompressibilityX();

  /**
   * getCompressibilityY.
   *
   * @return a double
   */
  public double getCompressibilityY();

  /**
   * getIsothermalCompressibility.
   *
   * @return a double
   */
  public double getIsothermalCompressibility();

  /**
   * getIsobaricThermalExpansivity.
   *
   * @return a double
   */
  public double getIsobaricThermalExpansivity();

  /**
   * getdrhodN.
   *
   * @return a double
   */
  public double getdrhodN();

  /**
   * setInitType.
   *
   * @param initType a int
   */
  public void setInitType(int initType);

  /**
   * Init using current phase properties.
   */
  public default void init() {
    init(getNumberOfMolesInPhase() / getBeta(), getNumberOfComponents(), getInitType(), getType(), getBeta());
  }

  /**
   * init. Uses existing phase type.
   *
   * @param totalNumberOfMoles Total number of moles in all phases of Stream.
   * @param numberOfComponents Number of components in system.
   * @param initType a int. Use 0 to init, and 1 to reset.
   * @param beta Mole fraction of this phase in system.
   */
  public default void init(double totalNumberOfMoles, int numberOfComponents, int initType, double beta) {
    init(totalNumberOfMoles, numberOfComponents, initType, getType(), beta);
  }

  /**
   * init.
   *
   * @param totalNumberOfMoles Total number of moles in all phases of Stream.
   * @param numberOfComponents Number of components in system.
   * @param initType a int. Use 0 to init, and 1 to reset.
   * @param pt Type of phase.
   * @param beta Mole fraction of this phase in system.
   */
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt, double beta);

  /**
   * Init / calculate all physical properties of phase.
   */
  public void initPhysicalProperties();

  /**
   * Init / calculate specified physical property of phase.
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
   * getdrhodP.
   *
   * @return a double
   */
  public double getdrhodP();

  /**
   * getdrhodT.
   *
   * @return a double
   */
  public double getdrhodT();

  /**
   * getEnthalpydP.
   *
   * @return a double
   */
  public double getEnthalpydP();

  /**
   * getEnthalpydT.
   *
   * @return a double
   */
  public double getEnthalpydT();

  /**
   * getEntropydP.
   *
   * @return a double
   */
  public double getEntropydP();

  /**
   * getEntropydT.
   *
   * @return a double
   */
  public double getEntropydT();

  /**
   * getMoleFraction.
   *
   * @return a double
   */
  public double getMoleFraction();

  /**
   * Get component array of Phase.
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
   * getMass.
   *
   * @return a double
   */
  public double getMass();

  /**
   * getWtFraction.
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
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, m3/sec, m3/min, m3/hr, ft3/sec, Sm3/sec, Sm3/hr,
   * Sm3/day, MSm3/day, mole/sec, mol/sec, mole/min, mol/min, mole/hr, mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min,
   * kmole/hr, kmol/hr, kmole/day, kmol/day, lbmole/hr, lb/hr, barrel/day
   * @return flow rate in specified unit
   */
  public double getFlowRate(String flowunit);

  /**
   * setComponentArray.
   *
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  public void setComponentArray(ComponentInterface[] components);

  /**
   * Get density of a phase using the GERG-2008 EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_GERG2008();

  /**
   * Get GERG properties of a phase using the GERG-2008 EoS.
   *
   * @return an array of type double
   */
  public double[] getProperties_GERG2008();

  /**
   * Overloaded method to get the Leachman a0matrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced ideal helmholtz free energy and its derivatives
   */
  doubleW[] getAlpha0_GERG2008();

  /**
   * Overloaded method to get the Leachman armatrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced residual helmholtz free energy and its derivatives
   */
  doubleW[][] getAlphares_GERG2008();

  /**
   * Get density of a phase using the EOS-CG EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_EOSCG();

  /**
   * Get EOS-CG properties of a phase using the EOS-CG model.
   *
   * @return an array of type double
   */
  public double[] getProperties_EOSCG();

  /**
   * Get EOS-CG ideal Helmholtz contribution and derivatives.
   *
   * @return matrix of the reduced ideal helmholtz free energy and its derivatives
   */
  doubleW[] getAlpha0_EOSCG();

  /**
   * Get EOS-CG residual Helmholtz contribution and derivatives.
   *
   * @return matrix of the reduced residual helmholtz free energy and its derivatives
   */
  doubleW[][] getAlphares_EOSCG();

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
   * method to get Leachman properties of a phase using the Leachman EoS.
   *
   * @param hydrogenType a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] getProperties_Leachman(String hydrogenType);

  /**
   * Overloaded method to get the Leachman properties with default hydrogen type ('normal').
   *
   * @return density with unit kg/m3
   */
  public double[] getProperties_Leachman();

  /**
   * Overloaded method to get the Leachman a0matrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced ideal helmholtz free energy and its derivatives
   */
  doubleW[] getAlpha0_Leachman();

  /**
   * method to get Leachman a0matrix of a phase using the Leachman EoS.
   *
   * @param hydrogenType Supported types are 'normal', 'para', 'ortho'
   * @return matrix of the reduced ideal helmholtz free energy and its derivatives
   */
  doubleW[] getAlpha0_Leachman(String hydrogenType);

  /**
   * method to get Leachman armatrix of a phase using the Leachman EoS.
   *
   * @param hydrogenType Supported types are 'normal', 'para', 'ortho'
   * @return matrix of the reduced residual helmholtz free energy and its derivatives
   */
  doubleW[][] getAlphares_Leachman(String hydrogenType);

  /**
   * Overloaded method to get the Leachman armatrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced residual helmholtz free energy and its derivatives
   */
  doubleW[][] getAlphares_Leachman();

  /**
   * method to get helium density of a phase using the Vega EoS.
   *
   * @return density with unit kg/m3
   */
  public double getDensity_Vega();

  /**
   * method to get helium properties of a phase using the Vega EoS.
   *
   * @return an array of type double
   */
  public double[] getProperties_Vega();

  /**
   * Overloaded method to get the Leachman a0matrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced ideal helmholtz free energy and its derivatives
   */
  public doubleW[] getAlpha0_Vega();

  /**
   * Overloaded method to get the Leachman armatrix with default hydrogen type ('normal').
   *
   * @return matrix of the reduced residual helmholtz free energy and its derivatives
   */
  public doubleW[][] getAlphares_Vega();

  /**
   * Get density of a phase note: does not use Peneloux volume correction.
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
   * method to get density of a water phase using specific water method.
   *
   * @param unit Supported units are kg/m3, mol/m3
   * @return density in specified unit
   */
  public double getWaterDensity(String unit);

  /**
   * Remove component from Phase.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   */
  public void removeComponent(String name, double moles, double molesInPhase);

  /**
   * getFugacity.
   *
   * @param compNumb a int
   * @return a double
   */
  public double getFugacity(int compNumb);

  /**
   * getFugacity.
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
   * method to return phase volume with Peneloux volume correction need to call initPhysicalProperties() before this
   * method is called.
   *
   * @return volume in unit m3
   */
  public double getCorrectedVolume();

  /**
   * hasTBPFraction.
   *
   * @return a boolean
   */
  public boolean hasTBPFraction();

  /**
   * Calculates the mean ionic activity coefficient on the molality scale for an electrolyte defined by two ionic
   * species. The conversion from mole fraction scale (γ±,x) to molality scale (γ±,m) follows: γ±,m = γ±,x * x_water
   *
   * <p>
   * Reference: Robinson, R.A. and Stokes, R.H. "Electrolyte Solutions", 2nd ed., Butterworths, London, 1965.
   * </p>
   *
   * @param comp1 component index of the first ion (e.g., cation)
   * @param comp2 component index of the second ion (e.g., anion)
   * @return mean ionic activity coefficient on the molality scale
   */
  public double getMolalMeanIonicActivity(int comp1, int comp2);

  /**
   * getGibbsEnergy.
   *
   * @return a double
   */
  public double getGibbsEnergy();

  /**
   * getMixGibbsEnergy.
   *
   * @return a double
   */
  public double getMixGibbsEnergy();

  /**
   * getExcessGibbsEnergy.
   *
   * @return a double
   */
  public double getExcessGibbsEnergy();

  /**
   * getExcessGibbsEnergySymetric.
   *
   * @return a double
   */
  public double getExcessGibbsEnergySymetric();

  /**
   * hasPlusFraction.
   *
   * @return a boolean
   */
  public boolean hasPlusFraction();

  /**
   * getSresTP.
   *
   * @return a double
   */
  public double getSresTP();

  /**
   * setProperties. Transfer properties from another phase object.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setProperties(PhaseInterface phase);

  /**
   * useVolumeCorrection.
   *
   * @param volcor a boolean
   */
  public void useVolumeCorrection(boolean volcor);

  /**
   * useVolumeCorrection.
   *
   * @return a boolean
   */
  public boolean useVolumeCorrection();

  /**
   * Getter for property <code>beta</code>. Beta is the mole fraction of a phase of all the moles of a system.
   *
   * @return Beta value
   */
  public double getBeta();

  /**
   * Setter for property <code>beta</code>. Beta is the mole fraction of a phase of all the moles of a system.
   *
   * @param b Beta value to set.
   */
  public void setBeta(double b);

  /**
   * getWtFrac.
   *
   * @param component a int
   * @return a double
   */
  public double getWtFrac(int component);

  /**
   * getWtFrac.
   *
   * @param componentName a {@link java.lang.String} object
   * @return a double
   */
  public double getWtFrac(String componentName);

  /**
   * setMixingRuleGEModel.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setMixingRuleGEModel(String name);

  /**
   * Get Component by name.
   *
   * @param name Name of component
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponent(String name);

  /**
   * Get Component by index.
   *
   * @param i Component index
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface getComponent(int i);

  /**
   * getActivityCoefficient.
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficient(int k);

  /**
   * getActivityCoefficient.
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getActivityCoefficient(int k, int p);

  /**
   * Get activity coefficient on a specified concentration scale.
   *
   * @param k component index
   * @param scale concentration scale: "molefraction" (default), "molality" (mol/kg solvent), or "molarity" (mol/L
   * solution)
   * @return activity coefficient on the specified scale
   */
  public double getActivityCoefficient(int k, String scale);

  /**
   * Set the pressure in bara (absolute pressure in bar).
   *
   * @param pres a double
   */
  public void setPressure(double pres);

  /**
   * Calculate pH of the aqueous phase using the IUPAC standard definition.
   *
   * <p>
   * Uses activity-based pH calculation since NeqSim's chemical reaction equilibrium constants are defined on the mole
   * fraction scale: pH = -log10(gamma_x * x_H3O+) where gamma_x is the activity coefficient and x_H3O+ is the mole
   * fraction of H3O+.
   * </p>
   *
   * @return pH value
   */
  public double getpH();

  /**
   * Calculate pH of the phase using specified method.
   *
   * <p>
   * Available methods:
   * </p>
   * <ul>
   * <li><b>activity</b> (default): pH = -log10(gamma_x * x_H3O+) - consistent with mole fraction-based equilibrium
   * constants</li>
   * <li><b>molality</b> (IUPAC standard): pH = -log10(gamma_m * m_H3O+) - correct for all concentrations</li>
   * <li><b>molarity</b>: pH = -log10([H3O+]) where [H3O+] is in mol/L - ignores activity coefficient</li>
   * <li><b>acidgas</b>: screening in-situ pH of an acid-gas-loaded aqueous phase from dissolved CO2/H2S via the
   * carbonic/hydrosulfuric acid first-dissociation equilibria (used automatically as a fallback when no explicit H3O+
   * species is present)</li>
   * </ul>
   *
   * @param method The calculation method: "activity" (default), "molality", "molarity", or "acidgas"
   * @return pH value
   */
  public double getpH(String method);

  /**
   * Screening in-situ pH of a carbonate/sulfide aqueous phase that carries a net alkalinity (buffered brine).
   *
   * <p>
   * Extends the unbuffered acid-gas estimate to water that also carries a net alkalinity - produced water with
   * bicarbonate hardness, or scrubber water dosed with an alkaline H2S scavenger (a triazine/amine base) that raises
   * the pH into the neutral-to-alkaline band where carbonate (CaCO3) scaling occurs. Uses the carbonic/hydrosulfuric
   * acid charge balance Alk = S/[H+] - [H+] with S = K1(CO2)&middot;C_CO2 + K1(H2S)&middot;C_H2S + Kw, so [H+] = (-Alk
   * + sqrt(Alk^2 + 4&middot;S))/2. At {@code alkalinity = 0} this reduces exactly to the "acidgas" estimate; a positive
   * alkalinity (net base) raises the pH and a negative alkalinity (net strong acid, e.g. an acidifying pH regulator
   * such as formic acid) lowers it. Screening level - not rigorous buffered-brine speciation.
   * </p>
   *
   * @param alkalinityEqPerLitre net alkalinity of the aqueous phase [equivalents per litre]; positive for a net base
   * (raises pH), negative for a net strong acid (lowers pH), zero for the unbuffered acid-gas case
   * @return estimated buffered pH, or {@link Double#NaN} if it cannot be evaluated
   */
  public double getpHwithAlkalinity(double alkalinityEqPerLitre);

  /**
   * Normalize property <code>x</code>.
   *
   * <p>
   * Property <code>x</code> is the mole fraction of a component in a specific phase. Normalizing, means that the sum of
   * <code>x</code> for all Components in a phase equal 1.0.
   * </p>
   */
  public void normalize();

  /**
   * getLogPureComponentFugacity.
   *
   * @param k a int
   * @return a double
   */
  public double getLogPureComponentFugacity(int k);

  /**
   * getPureComponentFugacity.
   *
   * @param k a int
   * @return a double
   */
  public double getPureComponentFugacity(int k);

  /**
   * getPureComponentFugacity.
   *
   * @param k a int
   * @param pure a boolean
   * @return a double
   */
  public double getPureComponentFugacity(int k, boolean pure);

  /**
   * Change the number of moles of component of phase,i.e., <code>numberOfMolesInPhase</code> but do not change the
   * total number of moles of component in system.
   *
   * <p>
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to keep consistency
   * between phase and component calculation of total number of moles in system.
   *
   * @param component Component number to change
   * @param dn Number of moles of component added to phase
   */
  public default void addMoles(int component, double dn) {
    addMolesChemReac(component, dn, 0);
  }

  /**
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and total number of
   * moles of component in system, i.e., <code>numberOfMoles</code> with the same amount.
   *
   * <p>
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to keep consistency
   * between phase and component calculation of total number of moles in system.
   *
   * @param component Component number to change
   * @param dn Number of moles of component added to phase and system
   */
  public default void addMolesChemReac(int component, double dn) {
    addMolesChemReac(component, dn, dn);
  }

  /**
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code> and
   * <code>Component</code> properties for the number of moles of component of phase, i.e.,
   * <code>numberOfMolesInPhase</code>, and total number of moles of component in system, i.e.,
   * <code>numberOfMoles</code> with separate amounts.
   *
   * <p>
   * NB! Phase fraction <code>beta</code> is not updated by this method. Must be done separately to keep consistency
   * between phase and component calculation of total number of moles in system.
   *
   * @param component Component number to change
   * @param dn Number of moles of component to add to phase
   * @param totdn Number of moles of component to add to system
   */
  public void addMolesChemReac(int component, double dn, double totdn);

  /**
   * Set the flow rate (moles) of all components to zero.
   */
  public void setEmptyFluid();

  /**
   * getPhysicalProperties.
   *
   * @return a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public PhysicalProperties getPhysicalProperties();

  /**
   * resetPhysicalProperties.
   */
  public void resetPhysicalProperties();

  /**
   * Specify the type of the physical properties.
   */
  public default void setPhysicalProperties() {
    setPhysicalProperties(getPhysicalPropertyModel());
  }

  /**
   * Specify the type of the physical properties.
   *
   * @param ppm PhysicalPropertyModel enum object
   */
  public void setPhysicalProperties(PhysicalPropertyModel ppm);

  /**
   * Getter for property ppm.
   *
   * @return a {@link neqsim.physicalproperties.system.PhysicalPropertyModel} object
   */
  public PhysicalPropertyModel getPhysicalPropertyModel();

  /**
   * Setter for property ppm.
   *
   * @param ppm PhysicalPropertyModel enum object
   */
  public void setPpm(PhysicalPropertyModel ppm);

  /**
   * setPhysicalPropertyModel.
   *
   * @param ppm a {@link neqsim.physicalproperties.system.PhysicalPropertyModel} object
   */
  public void setPhysicalPropertyModel(PhysicalPropertyModel ppm);

  /**
   * getInitType.
   *
   * @return a int
   */
  public int getInitType();

  /**
   * setAttractiveTerm.
   *
   * @param i a int
   */
  public void setAttractiveTerm(int i);

  /**
   * setMixingRule.
   *
   * @param mr a MixingRuleTypeInterface
   */
  public void setMixingRule(MixingRuleTypeInterface mr);

  /**
   * setMixingRule.
   *
   * @param mr a int
   */
  public default void setMixingRule(int mr) {
    setMixingRule(EosMixingRuleType.byValue(mr));
  }

  /**
   * resetMixingRule.
   *
   * @param mr a int
   */
  public void resetMixingRule(MixingRuleTypeInterface mr);

  /**
   * Set the temperature of the phase.
   *
   * @param temperature in unit Kelvin
   */
  public void setTemperature(double temperature);

  /**
   * molarVolume.
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
   * geta.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double geta(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * getb.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  public double getb(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * getAntoineVaporPressure.
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressure(double temp);

  /**
   * calcA.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcB.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAi.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAiT.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAij.
   *
   * @param compNumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcBij.
   *
   * @param compNumb a int
   * @param j a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBij(int compNumb, int j, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcAT.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcAT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcBi.
   *
   * @param compNumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param numbcomp a int
   * @return a double
   */
  double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

  /**
   * calcR.
   *
   * @return a double
   */
  double calcR();

  // double getf();

  /**
   * getg.
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
   * getHresTP.
   *
   * @return a double
   */
  public double getHresTP();

  /**
   * getGresTP.
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
   * method to return real gas isentropic exponent (kappa = - Cp/Cv*(v/p)*dp/dv method to return heat capacity
   * ratio/adiabatic index/Poisson constant.
   *
   * @return kappa
   */
  public double getKappa();

  /**
   * getCpres.
   *
   * @return a double
   */
  public double getCpres();

  /**
   * getZ.
   *
   * @return a double
   */
  public double getZ();

  /**
   * getPseudoCriticalPressure.
   *
   * @return a double
   */
  public double getPseudoCriticalPressure();

  /**
   * getPseudoCriticalTemperature.
   *
   * @return a double
   */
  public double getPseudoCriticalTemperature();

  /**
   * getPhase.
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  PhaseInterface getPhase();

  /**
   * Get number of components added to Phase.
   *
   * @return the number of components in Phase.
   */
  public int getNumberOfComponents();

  /**
   * Check if the phase contains ionic components (e.g., Na+, Cl-, Ca+2).
   *
   * <p>
   * This method scans all components in the phase and returns true if any component has a non-zero ionic charge or is
   * marked as an ion.
   * </p>
   *
   * @return true if the phase contains at least one ionic component, false otherwise
   */
  public default boolean hasIons() {
    for (int i = 0; i < getNumberOfComponents(); i++) {
      if (getComponent(i).getIonicCharge() != 0 || getComponent(i).isIsIon()) {
        return true;
      }
    }
    return false;
  }

  /**
   * setNumberOfComponents.
   *
   * @param k a int
   */
  public void setNumberOfComponents(int k);

  /**
   * getComponents.
   *
   * @return an array of {@link neqsim.thermo.component.ComponentInterface} objects
   */
  ComponentInterface[] getComponents();

  /**
   * Get the number of moles the phase contains.
   *
   * @return The number of moles in the phase.
   */
  public double getNumberOfMolesInPhase();

  /**
   * calcMolarVolume.
   *
   * @param test a boolean
   */
  public void calcMolarVolume(boolean test);

  /**
   * setTotalVolume.
   *
   * @param volume a double
   */
  public void setTotalVolume(double volume);

  /**
   * setMolarVolume.
   *
   * @param molarVolume a double
   */
  public void setMolarVolume(double molarVolume);

  // public double getInfiniteDiluteFugacity(int k);

  /**
   * getInfiniteDiluteFugacity.
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getInfiniteDiluteFugacity(int k, int p);

  /**
   * getHelmholtzEnergy.
   *
   * @return a double
   */
  public double getHelmholtzEnergy();

  /**
   * getNumberOfMolecularComponents.
   *
   * @return a int
   */
  public int getNumberOfMolecularComponents();

  /**
   * getNumberOfIonicComponents.
   *
   * @return a int
   */
  public int getNumberOfIonicComponents();

  // double calcA2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);
  // double calcB2(PhaseInterface phase, double temperature, double pressure, int
  // numbcomp);

  /**
   * getA.
   *
   * @return a double
   */
  public double getA();

  /**
   * getB.
   *
   * @return a double
   */
  public double getB();
  // public double getBi();

  /**
   * getAT.
   *
   * @return a double
   */
  public double getAT();

  /**
   * getATT.
   *
   * @return a double
   */
  public double getATT();
  // public double getAiT();

  /**
   * clone.
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
   * Get temperature in a specified unit.
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
   * <p>
   * Note: the return value is in kg/mol (SI), not g/mol. Multiply by 1000 to convert to g/mol, or use
   * {@link #getMolarMass(String)} with unit "g/mol".
   * </p>
   *
   * @return molar mass in unit kg/mol
   */
  public double getMolarMass();

  /**
   * Get molar mass of a fluid phase in the specified unit.
   *
   * @param unit Supported units are kg/mol, g/mol, gr/mol, lbm/lbmol
   * @return molar mass in specified unit
   */
  public double getMolarMass(String unit);

  /**
   * Calcualte the total internal energy for the phase in Joules.
   *
   * @return internal energy for the phase in Joules
   */
  public double getInternalEnergy();

  /**
   * Calculates the internal energy in the specified units.
   *
   * @param unit a {@link java.lang.String} object
   * @return internal energy for the phase in specified unit
   */
  public double getInternalEnergy(String unit);

  /**
   * getdPdrho.
   *
   * @return a double
   */
  public double getdPdrho();

  /**
   * getdPdTVn.
   *
   * @return a double
   */
  public double getdPdTVn();

  /**
   * getdPdVTn.
   *
   * @return a double
   */
  public double getdPdVTn();

  /**
   * Fn.
   *
   * @return a double
   */
  public double Fn();

  /**
   * FT.
   *
   * @return a double
   */
  public double FT();

  /**
   * FV.
   *
   * @return a double
   */
  public double FV();

  /**
   * FD.
   *
   * @return a double
   */
  public double FD();

  /**
   * FB.
   *
   * @return a double
   */
  public double FB();

  /**
   * gb.
   *
   * @return a double
   */
  public double gb();

  /**
   * fb.
   *
   * @return a double
   */
  public double fb();

  /**
   * gV.
   *
   * @return a double
   */
  public double gV();

  /**
   * fv.
   *
   * @return a double
   */
  public double fv();

  /**
   * FnV.
   *
   * @return a double
   */
  public double FnV();

  /**
   * FnB.
   *
   * @return a double
   */
  public double FnB();

  /**
   * FTT.
   *
   * @return a double
   */
  public double FTT();

  /**
   * FBT.
   *
   * @return a double
   */
  public double FBT();

  /**
   * FDT.
   *
   * @return a double
   */
  public double FDT();

  /**
   * FBV.
   *
   * @return a double
   */
  public double FBV();

  /**
   * FBB.
   *
   * @return a double
   */
  public double FBB();

  /**
   * FDV.
   *
   * @return a double
   */
  public double FDV();

  /**
   * FBD.
   *
   * @return a double
   */
  public double FBD();

  /**
   * FTV.
   *
   * @return a double
   */
  public double FTV();

  /**
   * FVV.
   *
   * @return a double
   */
  public double FVV();

  /**
   * gVV.
   *
   * @return a double
   */
  public double gVV();

  /**
   * gBV.
   *
   * @return a double
   */
  public double gBV();

  /**
   * gBB.
   *
   * @return a double
   */
  public double gBB();

  /**
   * fVV.
   *
   * @return a double
   */
  public double fVV();

  /**
   * fBV.
   *
   * @return a double
   */
  public double fBV();

  /**
   * fBB.
   *
   * @return a double
   */
  public double fBB();

  /**
   * Calculate derivative of F per Temperature, i.e., dF/dT.
   *
   * @return a double
   */
  public double dFdT();

  /**
   * Calculate derivative of F per Volume, i.e., dF/dV.
   *
   * @return a double
   */
  public double dFdV();

  /**
   * Calculate derivative of F per Temperature and Volume, i.e., dF/dT * 1/dV.
   *
   * @return a double
   */
  public double dFdTdV();

  /**
   * dFdVdV.
   *
   * @return a double
   */
  public double dFdVdV();

  /**
   * dFdTdT.
   *
   * @return a double
   */
  public double dFdTdT();

  /**
   * getOsmoticCoefficientOfWater.
   *
   * @return a double
   */
  public double getOsmoticCoefficientOfWater();

  /**
   * getOsmoticCoefficient.
   *
   * @param watNumb a int
   * @return a double
   */
  public double getOsmoticCoefficient(int watNumb);

  /**
   * Get the osmotic coefficient of water on the molality scale. This is the definition used by Robinson and Stokes
   * (1965):
   *
   * <pre>
   * φ = -ln(a_w) / (M_w * Σm_i)
   * </pre>
   *
   * <p>
   * where:
   * <ul>
   * <li>a_w = water activity</li>
   * <li>M_w = molar mass of water (kg/mol)</li>
   * <li>Σm_i = sum of ion molalities (mol/kg solvent)</li>
   * </ul>
   *
   * @return osmotic coefficient on molality scale
   */
  public double getOsmoticCoefficientOfWaterMolality();

  /**
   * getMeanIonicActivity.
   *
   * @param comp1 a int
   * @param comp2 a int
   * @return a double
   */
  public double getMeanIonicActivity(int comp1, int comp2);

  /**
   * getLogInfiniteDiluteFugacity.
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getLogInfiniteDiluteFugacity(int k, int p);

  /**
   * getLogInfiniteDiluteFugacity.
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
   * initRefPhases.
   *
   * @param onlyPure a boolean
   */
  public void initRefPhases(boolean onlyPure);

  /**
   * Indexed getter for property refPhase.
   *
   * @param index a int
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public neqsim.thermo.phase.PhaseInterface getRefPhase(int index);

  /**
   * Getter for property refPhase.
   *
   * @return an array of {@link neqsim.thermo.phase.PhaseInterface} objects
   */
  public neqsim.thermo.phase.PhaseInterface[] getRefPhase();

  /**
   * Indexed setter for property refPhase.
   *
   * @param index a int
   * @param refPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setRefPhase(int index, neqsim.thermo.phase.PhaseInterface refPhase);

  /**
   * Setter for property refPhase.
   *
   * @param refPhase an array of {@link neqsim.thermo.phase.PhaseInterface} objects
   */
  public void setRefPhase(neqsim.thermo.phase.PhaseInterface[] refPhase);

  /**
   * setParams.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param alpha an array of type double
   * @param Dij an array of type double
   * @param DijT an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @param intparam an array of type double
   */
  public void setParams(PhaseInterface phase, double[][] alpha, double[][] Dij, double[][] DijT, String[][] mixRule,
      double[][] intparam);

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
   * Getter for property phaseTypeName.
   *
   * @return a {@link java.lang.String} object
   */
  public default String getPhaseTypeName() {
    return getType().getDesc();
  }

  /**
   * Setter for property phaseTypeName.
   *
   * @param phaseTypeName a {@link java.lang.String} object
   */
  public default void setPhaseTypeName(String phaseTypeName) {
    setType(PhaseType.byDesc(phaseTypeName));
  }

  /**
   * Check if mixing rule is defined.
   *
   * @return Returns true if MixingRule is defined and false if not.
   */
  public boolean isMixingRuleDefined();

  /**
   * getActivityCoefficientSymetric.
   *
   * @param k a int
   * @return a double
   */
  public double getActivityCoefficientSymetric(int k);

  /**
   * getActivityCoefficientUnSymetric.
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
   * Check if this phase is rich in asphaltene components.
   *
   * <p>
   * This method detects asphaltene-rich phases regardless of whether the phase is modeled as solid
   * (PhaseType.ASPHALTENE) or liquid (PhaseType.LIQUID_ASPHALTENE, Pedersen's liquid-liquid approach). A phase is
   * considered asphaltene-rich if:
   * </p>
   * <ul>
   * <li>The phase type is ASPHALTENE or LIQUID_ASPHALTENE, or</li>
   * <li>The total mole fraction of asphaltene components exceeds 0.5</li>
   * </ul>
   *
   * <p>
   * Asphaltene components are identified by name containing "asphaltene" (case-insensitive).
   * </p>
   *
   * @return true if the phase is asphaltene-rich
   */
  public default boolean isAsphalteneRich() {
    // Check if phase type is asphaltene (solid or liquid)
    if (StateOfMatter.isAsphaltene(getType())) {
      return true;
    }

    // Check for asphaltene components by name
    double asphalteneFraction = 0.0;
    for (int i = 0; i < getNumberOfComponents(); i++) {
      ComponentInterface comp = getComponent(i);
      String compName = comp.getComponentName();
      if (compName != null && compName.toLowerCase().contains("asphaltene")) {
        asphalteneFraction += comp.getx();
      }
    }

    return asphalteneFraction > 0.5;
  }

  /**
   * getLogActivityCoefficient.
   *
   * @param k a int
   * @param p a int
   * @return a double
   */
  public double getLogActivityCoefficient(int k, int p);

  /**
   * isConstantPhaseVolume.
   *
   * @return a boolean
   */
  public boolean isConstantPhaseVolume();

  /**
   * setConstantPhaseVolume.
   *
   * @param constantPhaseVolume a boolean
   */
  public void setConstantPhaseVolume(boolean constantPhaseVolume);

  /**
   * Get the speed of sound of a phase note: implemented in phaseEos.
   *
   * @return speed of sound in m/s
   */
  public double getSoundSpeed();

  /**
   * Get the speed of sound of a system. The sound speed is implemented based on a molar average over the phases
   *
   * @param unit Supported units are m/s, km/h
   * @return speed of sound in m/s
   */
  public double getSoundSpeed(String unit);

  /**
   * method to return name of thermodynamic model.
   *
   * @return String model name
   */
  public String getModelName();

  /**
   * method to return Z volume corrected gas compressibility.
   *
   * @return double Z volume corrected
   */
  public double getZvolcorr();
}
