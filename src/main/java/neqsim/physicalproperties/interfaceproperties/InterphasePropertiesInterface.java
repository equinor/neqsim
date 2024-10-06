/*
 * InterphasePropertiesInterface.java
 *
 * Created on 13. august 2001, 13:13
 */

package neqsim.physicalproperties.interfaceproperties;

import neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface;
import neqsim.physicalproperties.interfaceproperties.surfacetension.SurfaceTensionInterface;

/**
 * <p>
 * InterphasePropertiesInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface InterphasePropertiesInterface extends Cloneable {
  /**
   * <p>
   * init.
   * </p>
   */
  public void init();

  /**
   * <p>
   * init.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void init(neqsim.thermo.system.SystemInterface system);

  /**
   * <p>
   * initAdsorption.
   * </p>
   */
  public void initAdsorption();

  /**
   * <p>
   * calcAdsorption.
   * </p>
   */
  public void calcAdsorption();

  /**
   * <p>
   * setSolidAdsorbentMaterial.
   * </p>
   *
   * @param material a {@link java.lang.String} object
   */
  public void setSolidAdsorbentMaterial(String material);

  /**
   * <p>
   * clone.
   * </p>
   *
   * @return a {@link neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface}
   *         object
   */
  public InterphasePropertiesInterface clone();

  /**
   * <p>
   * Get Surface tension between two phases.
   * </p>
   *
   * @param i First phase number.
   * @param j Second phase number.
   * @return Surface tension in default unit.
   */
  public double getSurfaceTension(int i, int j);

  /**
   * <p>
   * Get Surface tension between two phases in a specified unit.
   * </p>
   *
   * @param numb1 First phase number.
   * @param numb2 Second phase number.
   * @param unit a {@link java.lang.String} object
   * @return Surface tension in specified unit.
   */
  public double getSurfaceTension(int numb1, int numb2, String unit);

  /**
   * <p>
   * getInterfacialTensionModel.
   * </p>
   *
   * @return a int
   */
  public int getInterfacialTensionModel();

  /**
   * <p>
   * getSurfaceTensionModel.
   * </p>
   *
   * @param i a int
   * @return a
   *         {@link neqsim.physicalproperties.interfaceproperties.surfacetension.SurfaceTensionInterface}
   *         object
   */
  public SurfaceTensionInterface getSurfaceTensionModel(int i);

  /**
   * <p>
   * setInterfacialTensionModel.
   * </p>
   *
   * @param phase1 a {@link java.lang.String} object
   * @param phase2 a {@link java.lang.String} object
   * @param model a {@link java.lang.String} object
   */
  public void setInterfacialTensionModel(String phase1, String phase2, String model);

  /**
   * <p>
   * setInterfacialTensionModel.
   * </p>
   *
   * @param interfacialTensionModel a int
   */
  public void setInterfacialTensionModel(int interfacialTensionModel);

  /**
   * <p>
   * getAdsorptionCalc.
   * </p>
   *
   * @return an array of
   *         {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface}
   *         objects
   */
  public AdsorptionInterface[] getAdsorptionCalc();

  /**
   * <p>
   * getAdsorptionCalc.
   * </p>
   *
   * @param phaseName a {@link java.lang.String} object
   * @return a
   *         {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface}
   *         object
   */
  public AdsorptionInterface getAdsorptionCalc(String phaseName);

  /**
   * <p>
   * setAdsorptionCalc.
   * </p>
   *
   * @param adsorptionCalc an array of
   *        {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface}
   *        objects
   */
  public void setAdsorptionCalc(AdsorptionInterface[] adsorptionCalc);
}
