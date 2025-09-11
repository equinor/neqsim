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
 * <p>
 * ComponentInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ComponentInterface extends ThermodynamicConstantsInterface, Cloneable {
  /**
   * <p>
   * Helper function to create component. Typically called from constructors.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component in system.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public void createComponent(String name, double moles, double molesInPhase, int compIndex);

  /**
   * <p>
   * isInert.
   * </p>
   *
   * @return a boolean
   */
  public boolean isInert();

  /**
   * <p>
   * setIdealGasEnthalpyOfFormation.
   * </p>
   *
   * @param idealGasEnthalpyOfFormation a double
   */
  public void setIdealGasEnthalpyOfFormation(double idealGasEnthalpyOfFormation);

  /**
   * <p>
   * getFormulae.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getFormulae();

  /**
   * <p>
   * getVolumeCorrectionT.
   * </p>
   *
   * @return a double
   */
  public double getVolumeCorrectionT();

  /**
   * <p>
   * setVolumeCorrectionT.
   * </p>
   *
   * @param volumeCorrectionT a double
   */
  public void setVolumeCorrectionT(double volumeCorrectionT);

  /**
   * <p>
   * getVolumeCorrectionConst.
   * </p>
   *
   * @return a double
   */
  public double getVolumeCorrectionConst();

  /**
   * <p>
   * getCASnumber.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getCASnumber();

  /**
   * <p>
   * setVolumeCorrectionConst.
   * </p>
   *
   * @param volumeCorrection a double
   */
  public void setVolumeCorrectionConst(double volumeCorrection);

  /**
   * <p>
   * getPureComponentCpLiquid.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getPureComponentCpLiquid(double temperature);

  /**
   * <p>
   * getPureComponentCpSolid.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getPureComponentCpSolid(double temperature);

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
   * getVolumeCorrectionT_CPA.
   * </p>
   *
   * @return a double
   */
  public double getVolumeCorrectionT_CPA();

  /**
   * method to return flow rate of a component.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, tonnes/year, m3/sec, m3/min, m3/hr,
   *        mole/sec, mole/min, mole/hr
   * @return flow rate in specified unit
   */
  public double getFlowRate(String flowunit);

  /**
   * method to return total flow rate of a component.
   *
   * @param flowunit Supported units are kg/sec, kg/min, kg/hr, mole/sec, mole/min, mole/hr
   * @return total flow rate in specified unit
   */
  public double getTotalFlowRate(String flowunit);

  /**
   * <p>
   * setVolumeCorrectionT_CPA.
   * </p>
   *
   * @param volumeCorrectionT_CPA a double
   */
  public void setVolumeCorrectionT_CPA(double volumeCorrectionT_CPA);

  /**
   * <p>
   * setNumberOfAssociationSites.
   * </p>
   *
   * @param numb a int
   */
  public void setNumberOfAssociationSites(int numb);

  /**
   * <p>
   * setCASnumber.
   * </p>
   *
   * @param CASnumber a {@link java.lang.String} object
   */
  public void setCASnumber(String CASnumber);

  /**
   * <p>
   * setFormulae.
   * </p>
   *
   * @param formulae a {@link java.lang.String} object
   */
  public void setFormulae(String formulae);

  /**
   * <p>
   * Insert this component into NeqSim component database.
   * </p>
   *
   * @param databaseName Name of database. Not in use, overwritten as comptemp.
   */
  public void insertComponentIntoDatabase(String databaseName);

  /**
   * <p>
   * getOrginalNumberOfAssociationSites.
   * </p>
   *
   * @return a int
   */
  public int getOrginalNumberOfAssociationSites();

  /**
   * <p>
   * getRacketZCPA.
   * </p>
   *
   * @return a double
   */
  public double getRacketZCPA();

  /**
   * <p>
   * setRacketZCPA.
   * </p>
   *
   * @param racketZCPA a double
   */
  public void setRacketZCPA(double racketZCPA);

  /**
   * <p>
   * isHydrocarbon.
   * </p>
   *
   * @return a boolean
   */
  public boolean isHydrocarbon();

  /**
   * <p>
   * getChemicalPotentialdP.
   * </p>
   *
   * @return a double
   */
  public double getChemicalPotentialdP();

  /**
   * <p>
   * setHeatOfFusion.
   * </p>
   *
   * @param heatOfFusion a double
   */
  public void setHeatOfFusion(double heatOfFusion);

  /**
   * <p>
   * getChemicalPotentialIdealReference.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialIdealReference(PhaseInterface phase);

  /**
   * <p>
   * getHeatOfFusion.
   * </p>
   *
   * @return a double
   */
  public double getHeatOfFusion();

  /**
   * <p>
   * setSurfTensInfluenceParam.
   * </p>
   *
   * @param factNum a int
   * @param val a double
   */
  public void setSurfTensInfluenceParam(int factNum, double val);

  /**
   * <p>
   * isWaxFormer.
   * </p>
   *
   * @return a boolean
   */
  public boolean isWaxFormer();

  /**
   * <p>
   * setWaxFormer.
   * </p>
   *
   * @param waxFormer a boolean
   */
  public void setWaxFormer(boolean waxFormer);

  /**
   * <p>
   * getSurfTensInfluenceParam.
   * </p>
   *
   * @param factNum a int
   * @return a double
   */
  public double getSurfTensInfluenceParam(int factNum);

  /**
   * <p>
   * getChemicalPotentialdN.
   * </p>
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdN(int i, PhaseInterface phase);

  /**
   * <p>
   * getChemicalPotential.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotential(PhaseInterface phase);

  /**
   * <p>
   * getChemicalPotential.
   * </p>
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
   * <p>
   * getChemicalPotentialdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdT(PhaseInterface phase);

  /**
   * <p>
   * getChemicalPotentialdNTV.
   * </p>
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getChemicalPotentialdNTV(int i, PhaseInterface phase);

  /**
   * <p>
   * setTriplePointTemperature.
   * </p>
   *
   * @param triplePointTemperature a double
   */
  public void setTriplePointTemperature(double triplePointTemperature);

  /**
   * <p>
   * setComponentType.
   * </p>
   *
   * @param componentType a {@link java.lang.String} object
   */
  public void setComponentType(String componentType);

  /**
   * <p>
   * seta.
   * </p>
   *
   * @param a a double
   */
  public void seta(double a);

  /**
   * <p>
   * getSphericalCoreRadius.
   * </p>
   *
   * @return a double
   */
  public double getSphericalCoreRadius();

  /**
   * <p>
   * setb.
   * </p>
   *
   * @param b a double
   */
  public void setb(double b);

  /**
   * <p>
   * getNumberOfAssociationSites.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfAssociationSites();

  /**
   * <p>
   * getComponentType.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getComponentType();

  /**
   * <p>
   * getRate.
   * </p>
   *
   * @param unitName a {@link java.lang.String} object
   * @return a double
   */
  public double getRate(String unitName);

  /**
   * <p>
   * Calculate, set and return fugacity coefficient.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient
   *        of.
   * @return Fugacity coefficient
   */
  public double fugcoef(PhaseInterface phase);

  /**
   * <p>
   * setFugacityCoefficient.
   * </p>
   *
   * @param val a double
   */
  public void setFugacityCoefficient(double val);

  /**
   * <p>
   * fugcoefDiffPresNumeric.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double fugcoefDiffPresNumeric(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure);

  /**
   * <p>
   * fugcoefDiffTempNumeric.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double fugcoefDiffTempNumeric(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure);

  /**
   * <p>
   * logfugcoefdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double logfugcoefdT(PhaseInterface phase);

  /**
   * <p>
   * logfugcoefdNi.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param k a int
   * @return a double
   */
  public double logfugcoefdNi(PhaseInterface phase, int k);

  /**
   * <p>
   * logfugcoefdP.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double logfugcoefdP(PhaseInterface phase);

  /**
   * <p>
   * logfugcoefdN.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of type double
   */
  public double[] logfugcoefdN(PhaseInterface phase);

  /**
   * <p>
   * setdfugdt.
   * </p>
   *
   * @param val a double
   */
  public void setdfugdt(double val);

  /**
   * <p>
   * setdfugdp.
   * </p>
   *
   * @param val a double
   */
  public void setdfugdp(double val);

  /**
   * <p>
   * setdfugdn.
   * </p>
   *
   * @param i a int
   * @param val a double
   */
  public void setdfugdn(int i, double val);

  /**
   * <p>
   * setdfugdx.
   * </p>
   *
   * @param i a int
   * @param val a double
   */
  public void setdfugdx(int i, double val);

  /**
   * <p>
   * setStokesCationicDiameter.
   * </p>
   *
   * @param stokesCationicDiameter a double
   */
  public void setStokesCationicDiameter(double stokesCationicDiameter);

  /**
   * <p>
   * setProperties.
   * </p>
   *
   * @param component a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public void setProperties(ComponentInterface component);

  /**
   * <p>
   * getTriplePointDensity.
   * </p>
   *
   * @return a double
   */
  public double getTriplePointDensity();

  /**
   * <p>
   * getCriticalCompressibilityFactor.
   * </p>
   *
   * @return a double
   */
  public double getCriticalCompressibilityFactor();

  /**
   * <p>
   * setCriticalCompressibilityFactor.
   * </p>
   *
   * @param criticalCompressibilityFactor a double
   */
  public void setCriticalCompressibilityFactor(double criticalCompressibilityFactor);

  /**
   * <p>
   * setMolarMass.
   * </p>
   *
   * @param molarMass a double
   */
  public void setMolarMass(double molarMass);

  /**
   * <p>
   * setMolarMass.
   * </p>
   *
   * @param molarMass a double
   * @param unit a String
   */
  public void setMolarMass(double molarMass, String unit);

  /**
   * <p>
   * calcActivity.
   * </p>
   *
   * @return a boolean
   */
  public boolean calcActivity();

  /**
   * <p>
   * getMolality.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolality(PhaseInterface phase);

  /**
   * <p>
   * setLennardJonesMolecularDiameter.
   * </p>
   *
   * @param lennardJonesMolecularDiameter a double
   */
  public void setLennardJonesMolecularDiameter(double lennardJonesMolecularDiameter);

  /**
   * <p>
   * setLennardJonesEnergyParameter.
   * </p>
   *
   * @param lennardJonesEnergyParameter a double
   */
  public void setLennardJonesEnergyParameter(double lennardJonesEnergyParameter);

  /**
   * <p>
   * setSphericalCoreRadius.
   * </p>
   *
   * @param sphericalCoreRadius a double
   */
  public void setSphericalCoreRadius(double sphericalCoreRadius);

  /**
   * <p>
   * getTriplePointPressure.
   * </p>
   *
   * @return a double
   */
  public double getTriplePointPressure();

  /**
   * <p>
   * getTriplePointTemperature.
   * </p>
   *
   * @return a double
   */
  public double getTriplePointTemperature();

  /**
   * <p>
   * getMeltingPointTemperature.
   * </p>
   *
   * @return a double
   */
  public double getMeltingPointTemperature();

  /**
   * <p>
   * getIdealGasEnthalpyOfFormation.
   * </p>
   *
   * @return a double
   */
  public double getIdealGasEnthalpyOfFormation();

  /**
   * <p>
   * Change the number of moles of component of phase,i.e., <code>numberOfMolesInPhase</code> but do
   * not change the total number of moles of component in system.
   * </p>
   *
   * @param dn Number of moles of component added to phase
   */
  public default void addMoles(double dn) {
    addMolesChemReac(dn, 0);
  }

  /**
   * <p>
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and
   * total number of moles of component in system, i.e., <code>numberOfMoles</code> with the same
   * amount.
   * </p>
   *
   * @param dn Number of moles of component added to phase and system
   */
  public default void addMolesChemReac(double dn) {
    addMolesChemReac(dn, dn);
  }

  /**
   * <p>
   * Change the number of moles of component of phase, i.e., <code>numberOfMolesInPhase</code>, and
   * total number of moles of component in system, i.e., <code>numberOfMoles</code> with separate
   * amounts.
   * </p>
   *
   * @param dn Number of moles of component to add to phase
   * @param totdn Number of moles of component to add to system
   */
  public void addMolesChemReac(double dn, double totdn);

  /**
   * <p>
   * getIdealGasGibbsEnergyOfFormation.
   * </p>
   *
   * @return a double
   */
  public double getIdealGasGibbsEnergyOfFormation();

  /**
   * <p>
   * setTC.
   * </p>
   *
   * @param val a double
   */
  public void setTC(double val);

  /**
   * <p>
   * setTC.
   * </p>
   *
   * @param val a double
   * @param unit a String
   */
  public void setTC(double val, String unit);

  /**
   * <p>
   * Setter for critical pressure.
   * </p>
   *
   * @param val Critical pressure in unit bara.
   */
  public void setPC(double val);

  /**
   * <p>
   * Setter for critical pressure in specified unit.
   * </p>
   *
   * @param val Critical pressure in unit specified by <code>unit</code>.
   * @param unit Engineering unit.
   */
  public void setPC(double val, String unit);

  /**
   * <p>
   * getDiElectricConstantdTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getDiElectricConstantdTdT(double temperature);

  /**
   * <p>
   * getIdealGasAbsoluteEntropy.
   * </p>
   *
   * @return a double
   */
  public double getIdealGasAbsoluteEntropy();

  /**
   * <p>
   * getDiElectricConstantdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getDiElectricConstantdT(double temperature);

  /**
   * <p>
   * Initialize component.
   * </p>
   *
   * @param temperature Temperature in unit ?. Used to calculate <code>K</code>.
   * @param pressure Pressure in unit ?. Used to calculate <code>K</code>.
   * @param totalNumberOfMoles Total number of moles of component.
   * @param beta Beta value, i.e.,
   * @param initType Init type. Calculate <code>K</code>, <code>z</code>, <code>x</code> if type ==
   *        0.
   */
  public void init(double temperature, double pressure, double totalNumberOfMoles, double beta,
      int initType);

  /**
   * <p>
   * Finit.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param temperature a double
   * @param pressure a double
   * @param totalNumberOfMoles a double
   * @param beta a double
   * @param numberOfComponents a int
   * @param initType a int
   */
  public void Finit(PhaseInterface phase, double temperature, double pressure,
      double totalNumberOfMoles, double beta, int numberOfComponents, int initType);

  /**
   * <p>
   * Getter for property x, i.e., the mole fraction of a component in a specific phase. For the mole
   * fraction for a specific phase see {@link getz} NB! init(0) must be called first from system.
   * </p>
   *
   * @return a double
   */
  public double getx();

  /**
   * <p>
   * Getter for property z, i.e., the mole fraction of a component in the fluid. For the mole
   * fraction for a specific phase see {@link getx} NB! init(0) must be called first from system.
   * </p>
   *
   * @return a double
   */
  public double getz();

  /**
   * <p>
   * The distribution coefficient y/x between gas and liquid for a component. NB! init must be
   * called first.
   * </p>
   *
   * @return a double
   */
  public double getK();

  /**
   * <p>
   * Returns the critical temperature of the component.
   * </p>
   *
   * @return The critical temperature of the component in Kelvin.
   */
  public double getTC();

  /**
   * <p>
   * Returns the critical temperature of the component.
   * </p>
   *
   * @param unit Unit of return temperature
   * @return The critical temperature of the component in specified unit.
   */
  public double getTC(String unit);

  /**
   * <p>
   * Getter for property NormalBoilingPoint.
   * </p>
   *
   * @return The normal boiling point of the component with unit Kelvin
   */
  public double getNormalBoilingPoint();

  /**
   * <p>
   * Getter for property NormalBoilingPoint.
   * </p>
   *
   * @param unit Unit of return pressure
   * @return The normal boiling point of the component in specified unit.
   */
  public double getNormalBoilingPoint(String unit);

  /**
   * <p>
   * setNormalBoilingPoint.
   * </p>
   *
   * @param normalBoilingPoint a double with unit Kelvin
   */
  public void setNormalBoilingPoint(double normalBoilingPoint);

  /**
   * <p>
   * Returns the critical pressure of the component.
   * </p>
   *
   * @return The critical pressure of the component in unit bara.
   */
  public double getPC();

  /**
   * <p>
   * Returns the critical pressure of the component.
   * </p>
   *
   * @param unit Unit of return pressure
   * @return The critical pressure of the component in specified unit.
   */
  public double getPC(String unit);

  /**
   * <p>
   * setViscosityAssociationFactor.
   * </p>
   *
   * @param val a double
   */
  public void setViscosityAssociationFactor(double val);

  /**
   * <p>
   * getIndex.
   * </p>
   *
   * @return a int
   */
  public int getIndex();

  /**
   * <p>
   * getReferenceStateType.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getReferenceStateType();

  /**
   * <p>
   * setLiquidConductivityParameter.
   * </p>
   *
   * @param number a double
   * @param i a int
   */
  public void setLiquidConductivityParameter(double number, int i);

  /**
   * <p>
   * getLiquidConductivityParameter.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getLiquidConductivityParameter(int i);

  /**
   * <p>
   * getNormalLiquidDensity.
   * </p>
   *
   * @return a double
   */
  public double getNormalLiquidDensity();

  /**
   * <p>
   * getNormalLiquidDensity.
   * </p>
   *
   * @param unit i String with unit of return return a double
   * @return a double
   */
  public double getNormalLiquidDensity(String unit);

  /**
   * <p>
   * Getter for property <code>componentName</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getComponentName();

  /**
   * <p>
   * Setter for property <code>componentName</code>.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   */
  public void setComponentName(String componentName);

  /**
   * <p>
   * Getter for property <code>componentNumber</code>.
   * </p>
   *
   * @return Index number of component in phase object component array.
   */
  public int getComponentNumber();

  /**
   * <p>
   * Setter for property <code>componentNumber</code>.
   * </p>
   *
   * @param numb Index number of component in phase object component array.
   */
  public void setComponentNumber(int numb);

  /**
   * <p>
   * getHeatOfVapourization.
   * </p>
   *
   * @param temp a double
   * @return a double
   */
  public double getHeatOfVapourization(double temp);

  /**
   * <p>
   * getNumberOfmoles.
   * </p>
   *
   * @return a double
   */
  public double getNumberOfmoles();

  /**
   * <p>
   * getGibbsEnergyOfFormation.
   * </p>
   *
   * @return a double
   */
  public double getGibbsEnergyOfFormation();

  /**
   * <p>
   * getReferencePotential.
   * </p>
   *
   * @return a double
   */
  public double getReferencePotential();

  /**
   * <p>
   * getLogFugacityCoefficient.
   * </p>
   *
   * @return a double
   */
  public default double getLogFugacityCoefficient() {
    return Math.log(getFugacityCoefficient());
  }

  /**
   * <p>
   * setReferencePotential.
   * </p>
   *
   * @param ref a double
   */
  public void setReferencePotential(double ref);

  /**
   * <p>
   * getNumberOfMolesInPhase.
   * </p>
   *
   * @return a double
   */
  public double getNumberOfMolesInPhase();

  /**
   * <p>
   * setNumberOfMolesInPhase.
   * </p>
   *
   * @param moles a double
   */
  public void setNumberOfMolesInPhase(double moles);

  /**
   * <p>
   * getIdEntropy.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getIdEntropy(double temperature);

  /**
   * <p>
   * setx.
   * </p>
   *
   * @param newx a double
   */
  public void setx(double newx);

  /**
   * <p>
   * setz.
   * </p>
   *
   * @param newz a double
   */
  public void setz(double newz);

  /**
   * <p>
   * setK.
   * </p>
   *
   * @param newK a double
   */
  public void setK(double newK);

  /**
   * <p>
   * getDiElectricConstant.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getDiElectricConstant(double temperature);

  /**
   * <p>
   * getIonicCharge.
   * </p>
   *
   * @return a double
   */
  public double getIonicCharge();

  /**
   * <p>
   * getdfugdt.
   * </p>
   *
   * @return a double
   */
  public double getdfugdt();

  /**
   * <p>
   * getdfugdp.
   * </p>
   *
   * @return a double
   */
  public double getdfugdp();

  /**
   * <p>
   * getSolidVaporPressure.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getSolidVaporPressure(double temperature);

  /**
   * <p>
   * Return the ideal-gas molar heat capacity of a chemical using polynomial regressed coefficients
   * as described by Poling, Bruce E. The Properties of Gases and Liquids. 5th edition. New York:
   * McGraw-Hill Professional, 2000.
   * </p>
   *
   * @param temperature a double
   * @return ideal gas Cp for the component in the specific phase [J/molK]
   */
  public double getCp0(double temperature);

  /**
   * <p>
   * getCv0.
   * </p>
   *
   * @param temperature a double
   * @return ideal gas Cv for the component in the specific phase [J/molK]
   */
  public double getCv0(double temperature);

  /**
   * <p>
   * getHID.
   * </p>
   *
   * @param T a double
   * @return a double
   */
  public double getHID(double T);

  /**
   * <p>
   * getEnthalpy.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getEnthalpy(double temperature);

  /**
   * Get molar mass of component.
   *
   * @return molar mass in unit kg/mol
   */
  public double getMolarMass();

  /**
   * Get molar mass of component.
   *
   * @param unit a String
   * @return molar mass in unit kg/mol
   */
  public double getMolarMass(String unit);

  /**
   * <p>
   * getLennardJonesMolecularDiameter.
   * </p>
   *
   * @return Units in m*e10
   */
  public double getLennardJonesMolecularDiameter();

  /**
   * <p>
   * getLennardJonesEnergyParameter.
   * </p>
   *
   * @return a double
   */
  public double getLennardJonesEnergyParameter();

  /**
   * <p>
   * getEntropy.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getEntropy(double temperature, double pressure);

  /**
   * <p>
   * getdfugdx.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getdfugdx(int i);

  /**
   * <p>
   * getdfugdn.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getdfugdn(int i);

  /**
   * <p>
   * getHresTP.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getHresTP(double temperature);

  /**
   * <p>
   * getGresTP.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getGresTP(double temperature);

  /**
   * <p>
   * getSresTP.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getSresTP(double temperature);

  /**
   * <p>
   * getFugacityCoefficient.
   * </p>
   *
   * @return a double
   */
  public double getFugacityCoefficient();

  /**
   * <p>
   * getAcentricFactor.
   * </p>
   *
   * @return a double
   */
  public double getAcentricFactor();

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
   * getAttractiveTerm.
   * </p>
   *
   * @return a {@link neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface} object
   */
  public AttractiveTermInterface getAttractiveTerm();

  /**
   * <p>
   * setNumberOfmoles.
   * </p>
   *
   * @param newmoles a double
   */
  public void setNumberOfmoles(double newmoles);

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
   * getAntoineVaporTemperature.
   * </p>
   *
   * @param pres a double
   * @return a double
   */
  public double getAntoineVaporTemperature(double pres);

  /**
   * <p>
   * getSolidVaporPressuredT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getSolidVaporPressuredT(double temperature);

  /**
   * <p>
   * getGibbsEnergy.
   * </p>
   *
   * @param temperature a double
   * @param pressure a double
   * @return a double
   */
  public double getGibbsEnergy(double temperature, double pressure);

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.thermo.component.ComponentInterface} object
   */
  public ComponentInterface clone();

  /**
   * <p>
   * This function handles the retrieval of a chemical’s dipole moment. Dipole moment, [debye] as a
   * double
   * </p>
   *
   * @return a double
   */
  public double getDebyeDipoleMoment();

  /**
   * <p>
   * getViscosityCorrectionFactor.
   * </p>
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
   * <p>
   * getCriticalVolume.
   * </p>
   *
   * @return a double
   */
  public double getCriticalVolume();

  /**
   * <p>
   * getRacketZ.
   * </p>
   *
   * @return a double
   */
  public double getRacketZ();

  /**
   * <p>
   * Getter for property <code>componentName</code>, i.e., normalized component name.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * <p>
   * getLiquidViscosityParameter.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getLiquidViscosityParameter(int i);

  /**
   * <p>
   * getLiquidViscosityModel.
   * </p>
   *
   * @return a int
   */
  public int getLiquidViscosityModel();

  /**
   * <p>
   * setAcentricFactor.
   * </p>
   *
   * @param val a double
   */
  public void setAcentricFactor(double val);

  /**
   * <p>
   * getVolumeCorrection.
   * </p>
   *
   * @return a double
   */
  public double getVolumeCorrection();

  /**
   * <p>
   * setRacketZ.
   * </p>
   *
   * @param val a double
   */
  public void setRacketZ(double val);

  /**
   * <p>
   * setLiquidViscosityModel.
   * </p>
   *
   * @param modelNumber a int
   */
  public void setLiquidViscosityModel(int modelNumber);

  /**
   * <p>
   * setLiquidViscosityParameter.
   * </p>
   *
   * @param number a double
   * @param i a int
   */
  public void setLiquidViscosityParameter(double number, int i);

  /**
   * <p>
   * getElements.
   * </p>
   *
   * @return a {@link neqsim.thermo.atomelement.Element} object
   */
  public Element getElements();

  /**
   * <p>
   * getSchwartzentruberParams.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getSchwartzentruberParams();

  /**
   * <p>
   * setSchwartzentruberParams.
   * </p>
   *
   * @param i a int
   * @param param a double
   */
  public void setSchwartzentruberParams(int i, double param);

  /**
   * <p>
   * getTwuCoonParams.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getTwuCoonParams();

  /**
   * <p>
   * setTwuCoonParams.
   * </p>
   *
   * @param i a int
   * @param param a double
   */
  public void setTwuCoonParams(int i, double param);

  /**
   * <p>
   * getParachorParameter.
   * </p>
   *
   * @return a double
   */
  public double getParachorParameter();

  /**
   * <p>
   * setParachorParameter.
   * </p>
   *
   * @param parachorParameter a double
   */
  public void setParachorParameter(double parachorParameter);

  /**
   * <p>
   * getPureComponentSolidDensity. Calculates the pure component solid density in kg/liter Should
   * only be used in the valid temperature range (specified in component database).
   * </p>
   *
   * @param temperature a double
   * @return pure component solid density in kg/liter
   */
  public double getPureComponentSolidDensity(double temperature);

  /**
   * <p>
   * getPureComponentLiquidDensity. Calculates the pure component liquid density in kg/liter Should
   * only be used in the valid temperature range (specified in component database). This method
   * seems to give bad results at the moment
   * </p>
   *
   * @param temperature a double
   * @return pure component liquid density in kg/liter
   */
  public double getPureComponentLiquidDensity(double temperature);

  /**
   * <p>
   * getChemicalPotentialdV.
   * </p>
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
   * <p>
   * getPaulingAnionicDiameter.
   * </p>
   *
   * @return a double
   */
  public double getPaulingAnionicDiameter();

  /**
   * <p>
   * getStokesCationicDiameter.
   * </p>
   *
   * @return a double
   */
  public double getStokesCationicDiameter();

  /**
   * <p>
   * getAttractiveTermNumber.
   * </p>
   *
   * @return a int
   */
  public int getAttractiveTermNumber();

  /**
   * <p>
   * getVoli.
   * </p>
   *
   * @return a double
   */
  public double getVoli();

  /**
   * <p>
   * getAntoineVaporPressuredT.
   * </p>
   *
   * @param temp a double
   * @return a double
   */
  public double getAntoineVaporPressuredT(double temp);

  /**
   * <p>
   * getMatiascopemanParams.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMatiascopemanParams();

  /**
   * <p>
   * setMatiascopemanParams.
   * </p>
   *
   * @param index a int
   * @param matiascopemanParams a double
   */
  public void setMatiascopemanParams(int index, double matiascopemanParams);

  /**
   * <p>
   * setMatiascopemanParams.
   * </p>
   *
   * @param matiascopemanParams an array of type double
   */
  public void setMatiascopemanParams(double[] matiascopemanParams);

  /**
   * <p>
   * getAssociationVolume.
   * </p>
   *
   * @return a double
   */
  public double getAssociationVolume();

  /**
   * <p>
   * setAssociationVolume.
   * </p>
   *
   * @param associationVolume a double
   */
  public void setAssociationVolume(double associationVolume);

  /**
   * <p>
   * getAssociationEnergy.
   * </p>
   *
   * @return a double
   */
  public double getAssociationEnergy();

  /**
   * <p>
   * setAssociationEnergy.
   * </p>
   *
   * @param associationEnergy a double
   */
  public void setAssociationEnergy(double associationEnergy);

  /**
   * <p>
   * getAntoineASolid.
   * </p>
   *
   * @return a double
   */
  public double getAntoineASolid();

  /**
   * <p>
   * setAntoineASolid.
   * </p>
   *
   * @param AntoineASolid a double
   */
  public void setAntoineASolid(double AntoineASolid);

  /**
   * <p>
   * getAntoineBSolid.
   * </p>
   *
   * @return a double
   */
  public double getAntoineBSolid();

  /**
   * <p>
   * setAntoineBSolid.
   * </p>
   *
   * @param AntoineBSolid a double
   */
  public void setAntoineBSolid(double AntoineBSolid);

  /**
   * <p>
   * isIsTBPfraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean isIsTBPfraction();

  /**
   * <p>
   * setIsTBPfraction.
   * </p>
   *
   * @param isTBPfraction a boolean
   */
  public void setIsTBPfraction(boolean isTBPfraction);

  /**
   * <p>
   * isIsPlusFraction.
   * </p>
   *
   * @return a boolean
   */
  public boolean isIsPlusFraction();

  /**
   * <p>
   * setIsPlusFraction.
   * </p>
   *
   * @param isPlusFraction a boolean
   */
  public void setIsPlusFraction(boolean isPlusFraction);

  /**
   * <p>
   * isIsNormalComponent.
   * </p>
   *
   * @return a boolean
   */
  public boolean isIsNormalComponent();

  /**
   * <p>
   * setIsNormalComponent.
   * </p>
   *
   * @param isNormalComponent a boolean
   */
  public void setIsNormalComponent(boolean isNormalComponent);

  /**
   * <p>
   * isIsIon.
   * </p>
   *
   * @return a boolean
   */
  public boolean isIsIon();

  /**
   * <p>
   * setIsIon.
   * </p>
   *
   * @param isIon a boolean
   */
  public void setIsIon(boolean isIon);

  /**
   * <p>
   * setNormalLiquidDensity.
   * </p>
   *
   * @param normalLiquidDensity a double
   */
  public void setNormalLiquidDensity(double normalLiquidDensity);

  /**
   * <p>
   * Getter for field <code>solidCheck</code>.
   * </p>
   *
   * @return a boolean
   */
  public boolean doSolidCheck();

  /**
   * <p>
   * Setter for field <code>solidCheck</code>.
   * </p>
   *
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public void setSolidCheck(boolean checkForSolids);

  /**
   * <p>
   * getAssociationScheme.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public java.lang.String getAssociationScheme();

  /**
   * <p>
   * setAssociationScheme.
   * </p>
   *
   * @param associationScheme a {@link java.lang.String} object
   */
  public void setAssociationScheme(java.lang.String associationScheme);

  /**
   * <p>
   * getAntoineCSolid.
   * </p>
   *
   * @return a double
   */
  public double getAntoineCSolid();

  /**
   * <p>
   * setAntoineCSolid.
   * </p>
   *
   * @param AntoineCSolid a double
   */
  public void setAntoineCSolid(double AntoineCSolid);

  /**
   * <p>
   * getCCsolidVaporPressure. Calculates the pure comonent solid vapor pressure (bar) with the C-C
   * equation, based on Hsub Should only be used in the valid temperature range below the triple
   * point (specified in component database).
   * </p>
   *
   * @param temperature a double
   * @return Calculated solid vapor pressure in bar.
   */
  public double getCCsolidVaporPressure(double temperature);

  /**
   * <p>
   * getCCsolidVaporPressuredT. Calculates the DT of pure comonent solid vapor pressure (bar) with
   * the C-C equation, based on Hsub Should only be used in the valid temperature range below the
   * triple point (specified in component database).
   * </p>
   *
   * @param temperature a double
   * @return Calculated solid vapor pressure in bar.
   */
  public double getCCsolidVaporPressuredT(double temperature);

  /**
   * <p>
   * getHsub.
   * </p>
   *
   * @return a double
   */
  public double getHsub();

  /**
   * <p>
   * getHenryCoefParameter.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getHenryCoefParameter();

  /**
   * <p>
   * setHenryCoefParameter.
   * </p>
   *
   * @param henryCoefParameter an array of type double
   */
  public void setHenryCoefParameter(double[] henryCoefParameter);

  /**
   * <p>
   * getHenryCoef. Getter for property Henrys Coefficient. Unit is bar. ln H = C1 + C2/T + C3lnT +
   * C4*T
   * </p>
   *
   * @param temperature a double
   * @return Henrys Coefficient in bar
   */
  public double getHenryCoef(double temperature);

  /**
   * <p>
   * getHenryCoefdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getHenryCoefdT(double temperature);

  /**
   * <p>
   * getMatiascopemanSolidParams.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getMatiascopemanSolidParams();

  /**
   * <p>
   * setCriticalVolume.
   * </p>
   *
   * @param criticalVolume a double
   */
  public void setCriticalVolume(double criticalVolume);

  /**
   * <p>
   * getCriticalViscosity.
   * </p>
   *
   * @return a double
   */
  public double getCriticalViscosity();

  /**
   * <p>
   * setCriticalViscosity.
   * </p>
   *
   * @param criticalViscosity a double
   */
  public void setCriticalViscosity(double criticalViscosity);

  /**
   * <p>
   * getMolarity.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getMolarity(PhaseInterface phase);

  /**
   * <p>
   * isHydrateFormer.
   * </p>
   *
   * @return a boolean
   */
  public boolean isHydrateFormer();

  /**
   * <p>
   * setIsHydrateFormer.
   * </p>
   *
   * @param isHydrateFormer a boolean
   */
  public void setIsHydrateFormer(boolean isHydrateFormer);

  /**
   * <p>
   * getmSAFTi.
   * </p>
   *
   * @return a double
   */
  public double getmSAFTi();

  /**
   * <p>
   * setmSAFTi.
   * </p>
   *
   * @param mSAFTi a double
   */
  public void setmSAFTi(double mSAFTi);

  /**
   * <p>
   * getSigmaSAFTi.
   * </p>
   *
   * @return a double
   */
  public double getSigmaSAFTi();

  /**
   * <p>
   * setSigmaSAFTi.
   * </p>
   *
   * @param sigmaSAFTi a double
   */
  public void setSigmaSAFTi(double sigmaSAFTi);

  /**
   * <p>
   * getEpsikSAFT.
   * </p>
   *
   * @return a double
   */
  public double getEpsikSAFT();

  /**
   * <p>
   * setEpsikSAFT.
   * </p>
   *
   * @param epsikSAFT a double
   */
  public void setEpsikSAFT(double epsikSAFT);

  /**
   * <p>
   * getAssociationVolumeSAFT.
   * </p>
   *
   * @return a double
   */
  public double getAssociationVolumeSAFT();

  /**
   * <p>
   * setAssociationVolumeSAFT.
   * </p>
   *
   * @param associationVolumeSAFT a double
   */
  public void setAssociationVolumeSAFT(double associationVolumeSAFT);

  /**
   * <p>
   * getAssociationEnergySAFT.
   * </p>
   *
   * @return a double
   */
  public double getAssociationEnergySAFT();

  /**
   * <p>
   * setAssociationEnergySAFT.
   * </p>
   *
   * @param associationEnergySAFT a double
   */
  public void setAssociationEnergySAFT(double associationEnergySAFT);

  /**
   * <p>
   * getSurfaceTenisionInfluenceParameter.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getSurfaceTenisionInfluenceParameter(double temperature);

  /**
   * <p>
   * getCpA.
   * </p>
   *
   * @return a double
   */
  public double getCpA();

  /**
   * <p>
   * setCpA.
   * </p>
   *
   * @param CpA a double
   */
  public void setCpA(double CpA);

  /**
   * <p>
   * getCpB.
   * </p>
   *
   * @return a double
   */
  public double getCpB();

  /**
   * <p>
   * setCpB.
   * </p>
   *
   * @param CpB a double
   */
  public void setCpB(double CpB);

  /**
   * <p>
   * getCpC.
   * </p>
   *
   * @return a double
   */
  public double getCpC();

  /**
   * <p>
   * setCpC.
   * </p>
   *
   * @param CpC a double
   */
  public void setCpC(double CpC);

  /**
   * <p>
   * getCpD.
   * </p>
   *
   * @return a double
   */
  public double getCpD();

  /**
   * <p>
   * setCpD.
   * </p>
   *
   * @param CpD a double
   */
  public void setCpD(double CpD);

  /**
   * <p>
   * getCpE.
   * </p>
   *
   * @return a double
   */
  public double getCpE();

  /**
   * <p>
   * setCpE.
   * </p>
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
   * @return a {@link java.util.LinkedHashMap} Map with component alias name as key and component
   *         name as value.
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
