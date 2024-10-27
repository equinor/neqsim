/*
 * PhysicalPropertiesInterface.java
 *
 * Created on 29. oktober 2000, 16:14
 */

package neqsim.physicalproperties.physicalpropertysystem;

import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * PhysicalPropertiesInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhysicalPropertiesInterface extends Cloneable {
  /**
   * <p>
   * getPureComponentViscosity.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getPureComponentViscosity(int i);

  /**
   * <p>
   * setMixingRule.
   * </p>
   *
   * @param mixingRule a
   *        {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface} object
   */
  public void setMixingRule(
      neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface mixingRule);

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface}
   *         object
   */
  public neqsim.physicalproperties.mixingrule.PhysicalPropertyMixingRuleInterface getMixingRule();

  /**
   * <p>
   * getViscosity.
   * </p>
   *
   * @return a double
   */
  public double getViscosity();

  /**
   * <p>
   * setMixingRuleNull.
   * </p>
   */
  public void setMixingRuleNull();

  /**
   * <p>
   * getViscosityOfWaxyOil.
   * </p>
   *
   * @param waxVolumeFraction a double
   * @param shareRate a double
   * @return a double
   */
  public double getViscosityOfWaxyOil(double waxVolumeFraction, double shareRate);

  /**
   * <p>
   * getDiffusionCoefficient.
   * </p>
   *
   * @param comp1 a {@link java.lang.String} object
   * @param comp2 a {@link java.lang.String} object
   * @return a double
   */
  public double getDiffusionCoefficient(String comp1, String comp2);

  /**
   * <p>
   * getDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getDiffusionCoefficient(int i, int j);

  /**
   * <p>
   * getConductivity.
   * </p>
   *
   * @return a double
   */
  public double getConductivity();

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
   * setViscosityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setViscosityModel(String model);

  /**
   * <p>
   * setConductivityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setConductivityModel(String model);

  /**
   * <p>
   * calcDensity.
   * </p>
   *
   * @return a double
   */
  public double calcDensity();

  /**
   * <p>
   * getDensity.
   * </p>
   *
   * @return a double
   */
  public double getDensity();

  /**
   * <p>
   * setDensityModel.
   * </p>
   *
   * @param model a {@link java.lang.String} object
   */
  public void setDensityModel(String model);

  /**
   * <p>
   * getPhase.
   * </p>
   *
   * @return a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public PhaseInterface getPhase();

  /**
   * <p>
   * setPhase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setPhase(PhaseInterface phase);

  /**
   * <p>
   * getEffectiveSchmidtNumber.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getEffectiveSchmidtNumber(int i);

  /**
   * <p>
   * getEffectiveDiffusionCoefficient.
   * </p>
   *
   * @param compName a {@link java.lang.String} object
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(String compName);

  /**
   * <p>
   * getEffectiveDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getEffectiveDiffusionCoefficient(int i);

  /**
   * <p>
   * calcEffectiveDiffusionCoefficients.
   * </p>
   */
  public void calcEffectiveDiffusionCoefficients();

  /**
   * <p>
   * getFickDiffusionCoefficient.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getFickDiffusionCoefficient(int i, int j);

  /**
   * <p>
   * Initialize / calculate all physical properties of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void init(PhaseInterface phase);

  /**
   * <p>
   * Initialize / calculate a specific physical property of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param ppt PhysicalPropertyType enum object.
   */
  public void init(PhaseInterface phase, PhysicalPropertyType ppt);

  /**
   * <p>
   * Initialize / calculate a specific physical property of phase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param name Name of physical property.
   */
  public default void init(PhaseInterface phase, String name) {
    init(phase, PhysicalPropertyType.byName(name));
  }

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.physicalpropertysystem.PhysicalPropertiesInterface}
   *         object
   */
  public PhysicalPropertiesInterface clone();

  /**
   * <p>
   * setBinaryDiffusionCoefficientMethod.
   * </p>
   *
   * @param i a int
   */
  public void setBinaryDiffusionCoefficientMethod(int i);

  /**
   * <p>
   * setMulticomponentDiffusionMethod.
   * </p>
   *
   * @param i a int
   */
  public void setMulticomponentDiffusionMethod(int i);

  /**
   * <p>
   * getViscosityModel.
   * </p>
   *
   * @return a
   *         {@link neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface}
   *         object
   */
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.ViscosityInterface getViscosityModel();

  /**
   * <p>
   * getConductivityModel.
   * </p>
   *
   * @return a
   *         {@link neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface}
   *         object
   */
  public neqsim.physicalproperties.physicalpropertymethods.methodinterface.ConductivityInterface getConductivityModel();
}
