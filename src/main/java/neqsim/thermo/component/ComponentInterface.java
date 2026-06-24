/*
 * ComponentInterface.java
 *
 * Created on 8. april 2000, 23:15
 */

package neqsim.thermo.component;

import java.util.LinkedHashMap;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.atomelement.Element;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * ComponentInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentInterface extends ThermodynamicConstantsInterface, Cloneable {
  /**
   * Helper function to create component. Typically called from constructors.
   *
   * @param name Name of component.
   * @param moles Total number of moles of component in system.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public void createComponent(String name, double moles, double molesInPhase, int compIndex);

  /**
   * isInert.
   *
   * @return a boolean
   */
  public boolean isInert();

  /**
   * setIdealGasEnthalpyOfFormation.
   *
   * @param idealGasEnthalpyOfFormation a double
   */
  public void setIdealGasEnthalpyOfFormation(double idealGasEnthalpyOfFormation);

  /**
   * getFormulae.
   *
   * @return a {@link java.lang.String} object
   */
  public String getFormulae();

  /**
   * getVolumeCorrectionT.
   *
   * @return a double
   */
  public double getVolumeCorrectionT();

  /**
   * setVolumeCorrection.
   *
   * @param volumeCorrection a double
   */
  public void setVolumeCorrection(double volumeCorrection);

  /**
   * setVolumeCorrectionT.
   *
   * @param volumeCorrectionT a double
   */
  public void setVolumeCorrectionT(double volumeCorrectionT);

  /**
   * getVolumeCorrectionConst.
   *
   * @return a double
   */
  public double getVolumeCorrectionConst();

  /**
   * getCASnumber.
   *
   * @return a {@link java.lang.String} object
   */
  public String getCASnumber();

  /**
   * setVolumeCorrectionConst.
   *
   * @param volumeCorrection a double
   */
  public void setVolumeCorrectionConst(double volumeCorrection);

  /**
   * getPureComponentCpLiquid.
   *
   * @param temperature a double
   * @return a double
   */
  public double getPureComponentCpLiquid(double temperature);

  /**
   * getPureComponentCpSolid.
   *
   * @param temperature a double
   * @return a double
   */
  public double getPureComponentCpSolid(double temperature);

  /**
   * getdrhodN.
   *
   * @return a double
   */
  public double getdrhodN();

  /**
   * getVolumeCorrectionT_CPA.
   *
   * @return a double
   */
  public double getVolumeCorrectionT_CPA();

  /**
   * method to return flow rate of a component.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, tonnes/year, m3/sec, m3/min, m3/hr, mole/sec, mol/sec,
   * mole/min, mol/min, mole/hr, mol/hr, kmole/sec, kmol/sec, kmole/min, kmol/min, kmole/hr, kmol/hr, kmole/day,
   * kmol/day, lbmole/hr, lbmol/hr, lb/hr, barrel/day, bbl/day
   * @return flow rate in specified unit
   */
  public double getFlowRate(String flowunit);

  /**
   * method to return total flow rate of a component.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, mole/sec, mol/sec, mole/min, mol/min, mole/hr, mol/hr,
   * kmole/sec, kmol/sec, kmole/min, kmol/min, kmole/hr, kmol/hr, kmole/day, kmol/day, lbmole/hr, lbmol/hr, lb/hr,
   * barrel/day, bbl/day
   * @return total flow rate in specified unit
   */
  public double getTotalFlowRate(String flowunit);

  /**
   * setVolumeCorrectionT_CPA.
   *
   * @param volumeCorrectionT_CPA a double
   */
  public void setVolumeCorrectionT_CPA(double volumeCorrectionT_CPA);

  /**
   * setNumberOfAssociationSites.
   *
   * @param numb a int
   */
  public void setNumberOfAssociationSites(int numb);

  /**
   * setCASnumber.
   *
   * @param CASnumber a {@link java.lang.String} object
   */
  public void setCASnumber(String CASnumber);

  /**
   * setFormulae.
   *
   * @param formulae a {@link java.lang.String} object
   */
  public void setFormulae(String formulae);

  /**
   * Insert this component into NeqSim component database.
   *
   * @param databaseName Name of database. Not in use, overwritten as comptemp.
   */
  public void insertComponentIntoDatabase(String databaseName);

  /**
   * getOrginalNumberOfAssociationSites.
   *
   * @return a int
   */
  public int getOrginalNumberOfAssociationSites();

  /**
   * getRacketZCPA.
   *
   * @return a double
   */
  public double getRacketZCPA();

  /**
   * setRacketZCPA.
   *
   * @param racketZCPA a double
   */
  public void setRacketZCPA(double racketZCPA);

  /**
   * isHydrocarbon.
   *
   * @return a boolean
   */
  public boolean isHydrocarbon();

  /**
   * getChemicalPotentialdP.
   *
   * @return a double
   */
  public double getChemicalPotentialdP();

  /**
   * setHeatOfFusion.
   *
   * @param heatOfFusion a double
   */
  public void setHeatOfFusion(double heatOfFusion);

  /**
   * getChemicalPotentialIdealReference.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialIdealReference(PhaseInterface phase);

  /**
   * getHeatOfFusion.
   *
   * @return a double
   */
  public double getHeatOfFusion();

  /**
   * setSurfTensInfluenceParam.
   *
   * @param factNum a int
   * @param val a double
   */
  public void setSurfTensInfluenceParam(int factNum, double val);

  /**
   * isWaxFormer.
   *
   * @return a boolean
   */
  public boolean isWaxFormer();

  /**
   * setWaxFormer.
   *
   * @param waxFormer a boolean
   */
  public void setWaxFormer(boolean waxFormer);

  /**
   * getSurfTensInfluenceParam.
   *
   * @param factNum a int
   * @return a double
   */
  public double getSurfTensInfluenceParam(int factNum);

  /**
   * getChemicalPotentialdN.
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdN(int i, PhaseInterface phase);

  /**
   * getChemicalPotential.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotential(PhaseInterface phase);

  /**
   * getChemicalPotential.
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getChemicalPotential(double temperature, double pressure);
  // public double fugcoef(PhaseInterface phase, int numberOfComponents, double
  // temperature, double pressure);
  // public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents,
  // double temperature, double pressure);
  // public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents,
  // double temperature, double pressure);
  // public double[] fugcoefDiffN(PhaseInterface phase, int numberOfComponents,
  // double temperature, double pressure);

  /**
   * getChemicalPotentialdT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdT(PhaseInterface phase);

  /**
   * getChemicalPotentialdNTV.
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdNTV(int i, PhaseInterface phase);

  /**
   * setTriplePointTemperature.
   *
   * @param triplePointTemperature a double
   */
  public void setTriplePointTemperature(double triplePointTemperature);

  /**
   * setComponentType.
   *
   * @param componentType a {@link java.lang.String} object
   */
  public void setComponentType(String componentType);

  /**
   * seta.
   *
   * @param a a double
   */
  public void seta(double a);

  /**
   * getSphericalCoreRadius.
   *
   * @return a double
   */
  public double getSphericalCoreRadius();

  /**
   * setb.
   *
   * @param b a double
   */
  public void setb(double b);

  /**
   * getNumberOfAssociationSites.
   *
   * @return a int
   */
  public int getNumberOfAssociationSites();

  /**
   * getComponentType.
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getComponentType();

  /**
   * getRate.
   *
   * @param unitName a {@link java.lang.String} object
   * @return a double
   */
  public double getRate(String unitName);

  /**
   * Calculate, set and return fugacity coefficient.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient of.
   * @return Fugacity coefficient
   */
  public double fugcoef(PhaseInterface phase);

  /**
   * setFugacityCoefficient.
   *
   * @param val a double
   */
  public void setFugacityCoefficient(double val);

  /**
   * fugcoefDiffPresNumeric.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double fugcoefDiffPresNumeric(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * fugcoefDiffTempNumeric.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double fugcoefDiffTempNumeric(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure);

  /**
   * logfugcoefdT.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double logfugcoefdT(PhaseInterface phase);

  /**
   * logfugcoefdNi.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param k a int
   * @return a double
   */
  public double logfugcoefdNi(PhaseInterface phase, int k);

  /**
   * logfugcoefdP.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double logfugcoefdP(PhaseInterface phase);

  /**
   * logfugcoefdN.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of type double
   */
  public double[] logfugcoefdN(PhaseInterface phase);

  /**
   * setdfugdt.
   *
   * @param val a double
   */
  public void setdfugdt(double val);

  /**
   * setdfugdp.
   *
   * @param val a double
   */
  public void setdfugdp(double val);

  /**
   * setdfugdn.
   *
   * @param i a int
   * @param val a double
   */
  public void setdfugdn(int i, double val);

  /**
   * setdfugdx.
   *
   * @param i a int
   * @param val a double
   */
  public void setdfugdx(int i, double val);

  /**
   * setStokesCationicDiameter.
   *
   * @param stokesCationicDiameter a double
   */
  public void setStokesCationicDiameter(double stokesCationicDiameter);

  /**
   * setProperties.
   *
   * @param component a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public void setProperties(ComponentInterface component);

  /**
   * getTriplePointDensity.
   *
   * @return a double
   */
  public double getTriplePointDensity();

  /**
   * getCriticalCompressibilityFactor.
   *
   * @return a double
   */
  public double getCriticalCompressibilityFactor();

  /**
   * setCriticalCompressibilityFactor.
   *
   * @param criticalCompressibilityFactor a double
   */
  public void setCriticalCompressibilityFactor(double criticalCompressibilityFactor);

  /**
   * setMolarMass.
   *
   * @param molarMass a double
   */
  public void setMolarMass(double molarMass);

  /**
   * setMolarMass.
   *
   * @param molarMass a double
   * @param unit a String
   */
  public void setMolarMass(double molarMass, String unit);

  /**
   * calcActivity.
   *
   * @return a boolean
   */
  public boolean calcActivity();

  /**
   * getMolality.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolality(PhaseInterface phase);

  /**
   * setLennardJonesMolecularDiameter.
   *
   * @param lennardJonesMolecularDiameter a double
   */
  public void setLennardJonesMolecularDiameter(double lennardJonesMolecularDiameter);

  /**
   * setLennardJonesEnergyParameter.
   *
   * @param lennardJonesEnergyParameter a double
   */
  public void setLennardJonesEnergyParameter(double lennardJonesEnergyParameter);

  /**
   * setSphericalCoreRadius.
   *
   * @param sphericalCoreRadius a double
   */
  public void setSphericalCoreRadius(double sphericalCoreRadius);

  /**
   * getTriplePointPressure.
   *
   * @return a double
   */
  public double getTriplePointPressure();

  /**
   * getTriplePointTemperature.
   *
   * @return a double
   */
  public double getTriplePointTemperature();

  /**
   * getMeltingPointTemperature.
   *
   * @return a double
   */
  public double getMeltingPointTemperature();

  /**
   * getIdealGasEnthalpyOfFormation.
   *
   * @return a double
   */
  public double getIdealGasEnthalpyOfFormation();

  /**
   * Change the number of moles of component of phase,i.e., <code>numberOfMolesInPhase</code> but do not change the
   * total number of moles of component in system.
   *
   * @param dn Number of moles of component added to phase
   */
  public default void addMoles(double dn) {
    addMolesChemReac(dn, 0);
  }

  /**
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and total number of
   * moles of component in system, i.e., <code>numberOfMoles</code> with the same amount.
   *
   * @param dn Number of moles of component added to phase and system
   */
  public default void addMolesChemReac(double dn) {
    addMolesChemReac(dn, dn);
  }

  /**
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and total number of
   * moles of component in system, i.e., <code>numberOfMoles</code> with separate amounts.
   *
   * @param dn Number of moles of component to add to phase
   * @param totdn Number of moles of component to add to system
   */
  public void addMolesChemReac(double dn, double totdn);

  /**
   * getIdealGasGibbsEnergyOfFormation.
   *
   * @return a double
   */
  public double getIdealGasGibbsEnergyOfFormation();

  /**
   * setTC.
   *
   * @param val a double
   */
  public void setTC(double val);

  /**
   * setTC.
   *
   * @param val a double
   * @param unit a String
   */
  public void setTC(double val, String unit);

  /**
   * Setter for critical pressure.
   *
   * @param val Critical pressure in unit bara.
   */
  public void setPC(double val);

  /**
   * Setter for critical pressure in specified unit.
   *
   * @param val Critical pressure in unit specified by <code>unit</code>.
   * @param unit Engineering unit.
   */
  public void setPC(double val, String unit);

  /**
   * getDielectricConstantdTdT.
   *
   * @param temperature a double
   * @return a double
   */
  public double getDielectricConstantdTdT(double temperature);

  /**
   * getIdealGasAbsoluteEntropy.
   *
   * @return a double
   */
  public double getIdealGasAbsoluteEntropy();

  /**
   * getDielectricConstantdT.
   *
   * @param temperature a double
   * @return a double
   */
  public double getDielectricConstantdT(double temperature);

  /**
   * Initialize component.
   *
   * @param temperature Temperature in unit ?. Used to calculate <code>K</code>.
   * @param pressure Pressure in unit ?. Used to calculate <code>K</code>.
   * @param totalNumberOfMoles Total number of moles of component.
   * @param beta Beta value, i.e.,
   * @param initType Init type. Calculate <code>K</code>, <code>z</code>, <code>x</code> if type == 0.
   */
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta, int initType);

  /**
   * Finit.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param totalNumberOfMoles a double
   * @param beta a double
   * @param numberOfComponents a int
   * @param initType a int
   */
  public void Finit(PhaseInterface phase, double temperature, double pressure, double totalNumberOfMoles, double beta,
      int numberOfComponents, int initType);

  /**
   * Getter for property x, i.e., the mole fraction of a component in a specific phase. For the mole fraction for a
   * specific phase see {@link getz} NB! init(0) must be called first from system.
   *
   * @return a double
   */
  public double getx();

  /**
   * Getter for property z, i.e., the mole fraction of a component in the fluid. For the mole fraction for a specific
   * phase see {@link getx} NB! init(0) must be called first from system.
   *
   * @return a double
   */
  public double getz();

  /**
   * The distribution coefficient y/x between gas and liquid for a component. NB! init must be called first.
   *
   * @return a double
   */
  public double getK();

  /**
   * Returns the critical temperature of the component.
   *
   * @return The critical temperature of the component in Kelvin.
   */
  public double getTC();

  /**
   * Returns the critical temperature of the component.
   *
   * @param unit Unit of return temperature
   * @return The critical temperature of the component in specified unit.
   */
  public double getTC(String unit);

  /**
   * Getter for property NormalBoilingPoint.
   *
   * @return The normal boiling point of the component with unit Kelvin
   */
  public double getNormalBoilingPoint();

  /**
   * Getter for property NormalBoilingPoint.
   *
   * @param unit Unit of return pressure
   * @return The normal boiling point of the component in specified unit.
   */
  public double getNormalBoilingPoint(String unit);

  /**
   * setNormalBoilingPoint.
   *
   * @param normalBoilingPoint a double with unit Kelvin
   */
  public void setNormalBoilingPoint(double normalBoilingPoint);

  /**
   * Returns the critical pressure of the component.
   *
   * @return The critical pressure of the component in unit bara.
   */
  public double getPC();

  /**
   * Returns the critical pressure of the component.
   *
   * @param unit Unit of return pressure
   * @return The critical pressure of the component in specified unit.
   */
  public double getPC(String unit);

  /**
   * setViscosityAssociationFactor.
   *
   * @param val a double
   */
  public void setViscosityAssociationFactor(double val);

  /**
   * getIndex.
   *
   * @return a int
   */
  public int getIndex();

  /**
   * getReferenceStateType.
   *
   * @return a {@link java.lang.String} object
   */
  public String getReferenceStateType();

  /**
   * setLiquidConductivityParameter.
   *
   * @param number a double
   * @param i a int
   */
  public void setLiquidConductivityParameter(double number, int i);

  /**
   * getLiquidConductivityParameter.
   *
   * @param i a int
   * @return a double
   */
  public double getLiquidConductivityParameter(int i);

  /**
   * getNormalLiquidDensity.
   *
   * @return a double
   */
  public double getNormalLiquidDensity();

  /**
   * getNormalLiquidDensity.
   *
   * @param unit i String with unit of return return a double
   * @return a double
   */
  public double getNormalLiquidDensity(String unit);

  /**
   * Getter for property <code>componentName</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getComponentName();

  /**
   * Setter for property <code>componentName</code>.
   *
   * @param componentName a {@link java.lang.String} object
   */
  public void setComponentName(String componentName);

  /**
   * Getter for property <code>componentNumber</code>.
   *
   * @return Index number of component in phase object component array.
   */
  public int getComponentNumber();

  /**
   * Setter for property <code>componentNumber</code>.
   *
   * @param numb Index number of component in phase object component array.
   */
  public void setComponentNumber(int numb);

  /**
   * getHeatOfVapourization.
   *
   * @param temp a double
   * @return a double
   */
  public double getHeatOfVapourization(double temp);

  /**
   * getNumberOfmoles.
   *
   * @return a double
   */
  public double getNumberOfmoles();

  /**
   * getGibbsEnergyOfFormation.
   *
   * @return a double
   */
  public double getGibbsEnergyOfFormation();

  /**
   * getReferencePotential.
   *
   * @return a double
   */
  public double getReferencePotential();

  /**
   * getLogFugacityCoefficient.
   *
   * @return a double
   */
  public default double getLogFugacityCoefficient() {
    return Math.log(getFugacityCoefficient());
  }

  /**
   * setReferencePotential.
   *
   * @param ref a double
   */
  public void setReferencePotential(double ref);

  /**
   * getNumberOfMolesInPhase.
   *
   * @return a double
   */
  public double getNumberOfMolesInPhase();

  /**
   * setNumberOfMolesInPhase.
   *
   * @param moles a double
   */
  public void setNumberOfMolesInPhase(double moles);

  /**
   * getIdEntropy.
   *
   * @param temperature a double
   * @return a double
   */
  public double getIdEntropy(double temperature);

  /**
   * setx.
   *
   * @param newx a double
   */
  public void setx(double newx);

  /**
   * setz.
   *
   * @param newz a double
   */
  public void setz(double newz);

  /**
   * setK.
   *
   * @param newK a double
   */
  public void setK(double newK);

  /**
   * getDielectricConstant.
   *
   * @param temperature a double
   * @return a double
   */
  public double getDielectricConstant(double temperature);

  /**
   * getIonicCharge.
   *
   * @return a double
   */
  public double getIonicCharge();

  /**
   * getdfugdt.
   *
   * @return a double
   */
  public double getdfugdt();

  /**
   * getdfugdp.
   *
   * @return a double
   */
  public double getdfugdp();

  /**
   * getSolidVaporPressure.
   *
   * @param temperature a double
   * @return a double
   */
  public double getSolidVaporPressure(double temperature);

  /**
   * Return the ideal-gas molar heat capacity of a chemical using polynomial regressed coefficients as described by
   * Poling, Bruce E. The Properties of Gases and Liquids. 5th edition. New York: McGraw-Hill Professional, 2000.
   *
   * @param temperature a double
   * @return ideal gas Cp for the component in the specific phase [J/molK]
   */
  public double getCp0(double temperature);

  /**
   * getCv0.
   *
   * @param temperature a double
   * @return ideal gas Cv for the component in the specific phase [J/molK]
   */
  public double getCv0(double temperature);

  /**
   * getHID.
   *
   * @param T a double
   * @return a double
   */
  public double getHID(double T);

  /**
   * getEnthalpy.
   *
   * @param temperature a double
   * @return a double
   */
  public double getEnthalpy(double temperature);

  /**
   * Get molar mass of component.
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
   * Get molar mass of component in the specified unit.
   *
   * @param unit Supported units are kg/mol, g/mol, gr/mol, kg/kmol, lbm/lbmol
   * @return molar mass in specified unit
   */
  public double getMolarMass(String unit);

  /**
   * getLennardJonesMolecularDiameter.
   *
   * @return Units in m*e10
   */
  public double getLennardJonesMolecularDiameter();

  /**
   * getLennardJonesEnergyParameter.
   *
   * @return a double
   */
  public double getLennardJonesEnergyParameter();

  /**
   * getEntropy.
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getEntropy(double temperature, double pressure);

  /**
   * getdfugdx.
   *
   * @param i a int
   * @return a double
   */
  public double getdfugdx(int i);

  /**
   * getdfugdn.
   *
   * @param i a int
   * @return a double
   */
  public double getdfugdn(int i);

  /**
   * getHresTP.
   *
   * @param temperature a double
   * @return a double
   */
  public double getHresTP(double temperature);

  /**
   * getGresTP.
   *
   * @param temperature a double
   * @return a double
   */
  public double getGresTP(double temperature);

  /**
   * getSresTP.
   *
   * @param temperature a double
   * @return a double
   */
  public double getSresTP(double temperature);

  /**
   * getFugacityCoefficient.
   *
   * @return a double
   */
  public double getFugacityCoefficient();

  /**
   * getAcentricFactor.
   *
   * @return a double
   */
  public double getAcentricFactor();

  /**
   * setAttractiveTerm.
   *
   * @param i a int
   */
  public void setAttractiveTerm(int i);

  /**
   * getAttractiveTerm.
   *
   * @return a {@link neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface} object
   */
  public AttractiveTermInterface getAttractiveTerm();

  /**
   * setNumberOfmoles.
   *
   * @param newmoles a double
   */
  public void setNumberOfmoles(double newmoles);

  /**
   * getAntoineVaporPressure.
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressure(double temp);

  /**
   * getAntoineVaporTemperature.
   *
   * @param pres a double
   * @return a double
   */
  public double getAntoineVaporTemperature(double pres);

  /**
   * getSolidVaporPressuredT.
   *
   * @param temperature a double
   * @return a double
   */
  public double getSolidVaporPressuredT(double temperature);

  /**
   * getGibbsEnergy.
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getGibbsEnergy(double temperature, double pressure);

  /**
   * clone.
   *
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface clone();

  /**
   * This function handles the retrieval of a chemical’s dipole moment. Dipole moment, [debye] as a double
   *
   * @return a double
   */
  public double getDebyeDipoleMoment();

  /**
   * Set the dipole moment of the component.
   *
   * @param debyeDipoleMoment dipole moment in Debye
   */
  public void setDebyeDipoleMoment(double debyeDipoleMoment);

  /**
   * getViscosityCorrectionFactor.
   *
   * @return a double
   */
  public double getViscosityCorrectionFactor();

  /**
   * Get component specific friction factor used in friction-theory viscosity model.
   *
   * @return friction factor
   */
  public double getViscosityFrictionK();

  /**
   * Set component specific friction factor used in friction-theory viscosity model.
   *
   * @param viscosityFrictionK friction factor
   */
  public void setViscosityFrictionK(double viscosityFrictionK);

  /**
   * getCriticalVolume.
   *
   * @return a double
   */
  public double getCriticalVolume();

  /**
   * Get the COSTALD characteristic volume V* in cm3/mol. If zero, the critical volume should be used as fallback.
   *
   * @return COSTALD characteristic volume V* in cm3/mol
   */
  public double getCostaldCharacteristicVolume();

  /**
   * Set the COSTALD characteristic volume V* in cm3/mol. Set to 0 to use critical volume as fallback.
   *
   * @param costaldCharacteristicVolume COSTALD characteristic volume V* in cm3/mol
   */
  public void setCostaldCharacteristicVolume(double costaldCharacteristicVolume);

  /**
   * getRacketZ.
   *
   * @return a double
   */
  public double getRacketZ();

  /**
   * Getter for property <code>componentName</code>, i.e., normalized component name.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * getLiquidViscosityParameter.
   *
   * @param i a int
   * @return a double
   */
  public double getLiquidViscosityParameter(int i);

  /**
   * getLiquidViscosityModel.
   *
   * @return a int
   */
  public int getLiquidViscosityModel();

  /**
   * setAcentricFactor.
   *
   * @param val a double
   */
  public void setAcentricFactor(double val);

  /**
   * getVolumeCorrection.
   *
   * @return a double
   */
  public double getVolumeCorrection();

  /**
   * setRacketZ.
   *
   * @param val a double
   */
  public void setRacketZ(double val);

  /**
   * setLiquidViscosityModel.
   *
   * @param modelNumber a int
   */
  public void setLiquidViscosityModel(int modelNumber);

  /**
   * setLiquidViscosityParameter.
   *
   * @param number a double
   * @param i a int
   */
  public void setLiquidViscosityParameter(double number, int i);

  /**
   * getElements.
   *
   * @return a {@link neqsim.thermo.atomelement.Element} object
   */
  public Element getElements();

  /**
   * getSchwartzentruberParams.
   *
   * @return an array of type double
   */
  public double[] getSchwartzentruberParams();

  /**
   * setSchwartzentruberParams.
   *
   * @param i a int
   * @param param a double
   */
  public void setSchwartzentruberParams(int i, double param);

  /**
   * getTwuCoonParams.
   *
   * @return an array of type double
   */
  public double[] getTwuCoonParams();

  /**
   * setTwuCoonParams.
   *
   * @param i a int
   * @param param a double
   */
  public void setTwuCoonParams(int i, double param);

  /**
   * getParachorParameter.
   *
   * @return a double
   */
  public double getParachorParameter();

  /**
   * setParachorParameter.
   *
   * @param parachorParameter a double
   */
  public void setParachorParameter(double parachorParameter);

  /**
   * getPureComponentSolidDensity. Calculates the pure component solid density in kg/liter Should only be used in the
   * valid temperature range (specified in component database).
   *
   * @param temperature a double
   * @return pure component solid density in kg/liter
   */
  public double getPureComponentSolidDensity(double temperature);

  /**
   * getPureComponentLiquidDensity. Calculates the pure component liquid density in kg/liter Should only be used in the
   * valid temperature range (specified in component database). This method seems to give bad results at the moment
   *
   * @param temperature a double
   * @return pure component liquid density in kg/liter
   */
  public double getPureComponentLiquidDensity(double temperature);

  /**
   * getChemicalPotentialdV.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdV(PhaseInterface phase);

  /**
   * Calculates the pure component heat of vaporization in J/mol.
   *
   * @param temperature a double
   * @return a double
   */
  public double getPureComponentHeatOfVaporization(double temperature);

  /**
   * getPaulingAnionicDiameter.
   *
   * @return a double
   */
  public double getPaulingAnionicDiameter();

  /**
   * getStokesCationicDiameter.
   *
   * @return a double
   */
  public double getStokesCationicDiameter();

  /**
   * getAttractiveTermNumber.
   *
   * @return a int
   */
  public int getAttractiveTermNumber();

  /**
   * getVoli.
   *
   * @return a double
   */
  public double getVoli();

  /**
   * getAntoineVaporPressuredT.
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressuredT(double temp);

  /**
   * getMatiascopemanParams.
   *
   * @return an array of type double
   */
  public double[] getMatiascopemanParams();

  /**
   * setMatiascopemanParams.
   *
   * @param index a int
   * @param matiascopemanParams a double
   */
  public void setMatiascopemanParams(int index, double matiascopemanParams);

  /**
   * setMatiascopemanParams.
   *
   * @param matiascopemanParams an array of type double
   */
  public void setMatiascopemanParams(double[] matiascopemanParams);

  /**
   * getAssociationVolume.
   *
   * @return a double
   */
  public double getAssociationVolume();

  /**
   * setAssociationVolume.
   *
   * @param associationVolume a double
   */
  public void setAssociationVolume(double associationVolume);

  /**
   * getAssociationEnergy.
   *
   * @return a double
   */
  public double getAssociationEnergy();

  /**
   * setAssociationEnergy.
   *
   * @param associationEnergy a double
   */
  public void setAssociationEnergy(double associationEnergy);

  /**
   * getAntoineASolid.
   *
   * @return a double
   */
  public double getAntoineASolid();

  /**
   * setAntoineASolid.
   *
   * @param AntoineASolid a double
   */
  public void setAntoineASolid(double AntoineASolid);

  /**
   * getAntoineBSolid.
   *
   * @return a double
   */
  public double getAntoineBSolid();

  /**
   * setAntoineBSolid.
   *
   * @param AntoineBSolid a double
   */
  public void setAntoineBSolid(double AntoineBSolid);

  /**
   * isIsTBPfraction.
   *
   * @return a boolean
   */
  public boolean isIsTBPfraction();

  /**
   * setIsTBPfraction.
   *
   * @param isTBPfraction a boolean
   */
  public void setIsTBPfraction(boolean isTBPfraction);

  /**
   * isIsPlusFraction.
   *
   * @return a boolean
   */
  public boolean isIsPlusFraction();

  /**
   * setIsPlusFraction.
   *
   * @param isPlusFraction a boolean
   */
  public void setIsPlusFraction(boolean isPlusFraction);

  /**
   * isIsNormalComponent.
   *
   * @return a boolean
   */
  public boolean isIsNormalComponent();

  /**
   * setIsNormalComponent.
   *
   * @param isNormalComponent a boolean
   */
  public void setIsNormalComponent(boolean isNormalComponent);

  /**
   * isIsIon.
   *
   * @return a boolean
   */
  public boolean isIsIon();

  /**
   * setIsIon.
   *
   * @param isIon a boolean
   */
  public void setIsIon(boolean isIon);

  /**
   * setNormalLiquidDensity.
   *
   * @param normalLiquidDensity a double
   */
  public void setNormalLiquidDensity(double normalLiquidDensity);

  /**
   * Getter for field <code>solidCheck</code>.
   *
   * @return a boolean
   */
  public boolean doSolidCheck();

  /**
   * Setter for field <code>solidCheck</code>.
   *
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public void setSolidCheck(boolean checkForSolids);

  /**
   * getAssociationScheme.
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getAssociationScheme();

  /**
   * setAssociationScheme.
   *
   * @param associationScheme a {@link java.lang.String} object
   */
  public void setAssociationScheme(java.lang.String associationScheme);

  /**
   * getAntoineCSolid.
   *
   * @return a double
   */
  public double getAntoineCSolid();

  /**
   * setAntoineCSolid.
   *
   * @param AntoineCSolid a double
   */
  public void setAntoineCSolid(double AntoineCSolid);

  /**
   * getCCsolidVaporPressure. Calculates the pure comonent solid vapor pressure (bar) with the C-C equation, based on
   * Hsub Should only be used in the valid temperature range below the triple point (specified in component database).
   *
   * @param temperature a double
   * @return Calculated solid vapor pressure in bar.
   */
  public double getCCsolidVaporPressure(double temperature);

  /**
   * getCCsolidVaporPressuredT. Calculates the DT of pure comonent solid vapor pressure (bar) with the C-C equation,
   * based on Hsub Should only be used in the valid temperature range below the triple point (specified in component
   * database).
   *
   * @param temperature a double
   * @return Calculated solid vapor pressure in bar.
   */
  public double getCCsolidVaporPressuredT(double temperature);

  /**
   * getHsub.
   *
   * @return a double
   */
  public double getHsub();

  /**
   * getHenryCoefParameter.
   *
   * @return an array of type double
   */
  public double[] getHenryCoefParameter();

  /**
   * setHenryCoefParameter.
   *
   * @param henryCoefParameter an array of type double
   */
  public void setHenryCoefParameter(double[] henryCoefParameter);

  /**
   * getHenryCoef. Getter for property Henrys Coefficient. Unit is bar. ln H = C1 + C2/T + C3lnT + C4*T
   *
   * @param temperature a double
   * @return Henrys Coefficient in bar
   */
  public double getHenryCoef(double temperature);

  /**
   * getHenryCoefdT.
   *
   * @param temperature a double
   * @return a double
   */
  public double getHenryCoefdT(double temperature);

  /**
   * getMatiascopemanSolidParams.
   *
   * @return an array of type double
   */
  public double[] getMatiascopemanSolidParams();

  /**
   * setCriticalVolume.
   *
   * @param criticalVolume a double
   */
  public void setCriticalVolume(double criticalVolume);

  /**
   * getCriticalViscosity.
   *
   * @return a double
   */
  public double getCriticalViscosity();

  /**
   * setCriticalViscosity.
   *
   * @param criticalViscosity a double
   */
  public void setCriticalViscosity(double criticalViscosity);

  /**
   * getMolarity.
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolarity(PhaseInterface phase);

  /**
   * isHydrateFormer.
   *
   * @return a boolean
   */
  public boolean isHydrateFormer();

  /**
   * setIsHydrateFormer.
   *
   * @param isHydrateFormer a boolean
   */
  public void setIsHydrateFormer(boolean isHydrateFormer);

  /**
   * getmSAFTi.
   *
   * @return a double
   */
  public double getmSAFTi();

  /**
   * setmSAFTi.
   *
   * @param mSAFTi a double
   */
  public void setmSAFTi(double mSAFTi);

  /**
   * getSigmaSAFTi.
   *
   * @return a double
   */
  public double getSigmaSAFTi();

  /**
   * setSigmaSAFTi.
   *
   * @param sigmaSAFTi a double
   */
  public void setSigmaSAFTi(double sigmaSAFTi);

  /**
   * getEpsikSAFT.
   *
   * @return a double
   */
  public double getEpsikSAFT();

  /**
   * setEpsikSAFT.
   *
   * @param epsikSAFT a double
   */
  public void setEpsikSAFT(double epsikSAFT);

  /**
   * getLambdaRSAFTVRMie.
   *
   * @return repulsive Mie exponent
   */
  public double getLambdaRSAFTVRMie();

  /**
   * setLambdaRSAFTVRMie.
   *
   * @param lambdaRSAFTVRMie repulsive Mie exponent
   */
  public void setLambdaRSAFTVRMie(double lambdaRSAFTVRMie);

  /**
   * getLambdaASAFTVRMie.
   *
   * @return attractive Mie exponent
   */
  public double getLambdaASAFTVRMie();

  /**
   * setLambdaASAFTVRMie.
   *
   * @param lambdaASAFTVRMie attractive Mie exponent
   */
  public void setLambdaASAFTVRMie(double lambdaASAFTVRMie);

  /**
   * Gets the SAFT-VR Mie segment number.
   *
   * @return segment number for SAFT-VR Mie
   */
  public double getmSAFTVRMie();

  /**
   * Sets the SAFT-VR Mie segment number.
   *
   * @param mSAFTVRMie segment number
   */
  public void setmSAFTVRMie(double mSAFTVRMie);

  /**
   * Gets the SAFT-VR Mie segment diameter.
   *
   * @return sigma for SAFT-VR Mie in meters
   */
  public double getSigmaSAFTVRMie();

  /**
   * Sets the SAFT-VR Mie segment diameter.
   *
   * @param sigmaSAFTVRMie sigma in meters
   */
  public void setSigmaSAFTVRMie(double sigmaSAFTVRMie);

  /**
   * Gets the SAFT-VR Mie well depth divided by k.
   *
   * @return eps/k for SAFT-VR Mie in Kelvin
   */
  public double getEpsikSAFTVRMie();

  /**
   * Sets the SAFT-VR Mie well depth divided by k.
   *
   * @param epsikSAFTVRMie eps/k in Kelvin
   */
  public void setEpsikSAFTVRMie(double epsikSAFTVRMie);

  /**
   * getAssociationVolumeSAFT.
   *
   * @return a double
   */
  public double getAssociationVolumeSAFT();

  /**
   * setAssociationVolumeSAFT.
   *
   * @param associationVolumeSAFT a double
   */
  public void setAssociationVolumeSAFT(double associationVolumeSAFT);

  /**
   * getAssociationEnergySAFT.
   *
   * @return a double
   */
  public double getAssociationEnergySAFT();

  /**
   * setAssociationEnergySAFT.
   *
   * @param associationEnergySAFT a double
   */
  public void setAssociationEnergySAFT(double associationEnergySAFT);

  /**
   * Get the association energy for SAFT-VR Mie EOS. Returns the VR-Mie-specific value if set, otherwise falls back to
   * the PC-SAFT/CPA association energy.
   *
   * @return association energy in J/mol for SAFT-VR Mie
   */
  public double getAssociationEnergySAFTVRMie();

  /**
   * Set the association energy for SAFT-VR Mie EOS.
   *
   * @param associationEnergySAFTVRMie association energy in J/mol
   */
  public void setAssociationEnergySAFTVRMie(double associationEnergySAFTVRMie);

  /**
   * Get the SAFT-VR Mie bond volume K_HB in m^3 (Lafitte 2013 Eq. 39). For water: 101.69 Ang^3 = 1.0169e-28 m^3.
   * Returns 0 if not set (caller should fall back to kappa * sigma^3).
   *
   * @return bond volume K_HB in m^3
   */
  public double getAssociationVolumeSAFTVRMie();

  /**
   * Set the SAFT-VR Mie bond volume K_HB in m^3.
   *
   * @param associationVolumeSAFTVRMie bond volume in m^3
   */
  public void setAssociationVolumeSAFTVRMie(double associationVolumeSAFTVRMie);

  /**
   * getSurfaceTenisionInfluenceParameter.
   *
   * @param temperature a double
   * @return a double
   */
  public double getSurfaceTenisionInfluenceParameter(double temperature);

  /**
   * getCpA.
   *
   * @return a double
   */
  public double getCpA();

  /**
   * setCpA.
   *
   * @param CpA a double
   */
  public void setCpA(double CpA);

  /**
   * getCpB.
   *
   * @return a double
   */
  public double getCpB();

  /**
   * setCpB.
   *
   * @param CpB a double
   */
  public void setCpB(double CpB);

  /**
   * getCpC.
   *
   * @return a double
   */
  public double getCpC();

  /**
   * setCpC.
   *
   * @param CpC a double
   */
  public void setCpC(double CpC);

  /**
   * getCpD.
   *
   * @return a double
   */
  public double getCpD();

  /**
   * setCpD.
   *
   * @param CpD a double
   */
  public void setCpD(double CpD);

  /**
   * getCpE.
   *
   * @return a double
   */
  public double getCpE();

  /**
   * setCpE.
   *
   * @param CpE a double
   */
  public void setCpE(double CpE);

  /**
   * getComponentNameFromAlias. Used to look up normal component name aliases.
   *
   * @param name Component name or alias of component name.
   * @return Component name as used in database.
   */
  public static String getComponentNameFromAlias(String name) {
    LinkedHashMap<String, String> c = getComponentNameMap();
    if (c.containsKey(name)) {
      return c.get(name);
    } else {
      return name;
    }
  }

  /**
   * Get lookup map for component name alias.
   *
   * @return a {@link java.util.LinkedHashMap} Map with component alias name as key and component name as value.
   */
  public static LinkedHashMap<String, String> getComponentNameMap() {
    LinkedHashMap<String, String> c = new LinkedHashMap<>();
    c.put("H2O", "water");
    c.put("N2", "nitrogen");
    c.put("C1", "methane");
    c.put("C2", "ethane");
    c.put("C3", "propane");
    c.put("iC4", "i-butane");
    c.put("nC4", "n-butane");
    c.put("iC5", "i-pentane");
    c.put("nC5", "n-pentane");
    c.put("C6", "n-hexane");
    c.put("O2", "oxygen");
    c.put("He", "helium");
    c.put("H2", "hydrogen");
    c.put("Ar", "argon");
    c.put("H2S", "H2S");
    c.put("nC7", "n-heptane");
    c.put("nC8", "n-octane");
    c.put("nC9", "n-nonane");
    c.put("O2", "oxygen");
    return c;
  }

  /**
   * Returns the reduced temperature (T/Tc) for a given temperature.
   *
   * @param temperature Temperature in Kelvin
   * @return reduced temperature []
   */
  public double reducedTemperature(double temperature);

  /**
   * Returns the reduced pressure (P/Pc) for a given pressure.
   *
   * @param pressure Pressure in bara
   * @return reduced pressure []
   */
  public double reducedPressure(double pressure);
}
