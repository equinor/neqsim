/*
 * InterphasePropertiesInterface.java
 *
 * Created on 13. august 2001, 13:13
 */

package neqsim.physicalproperties.interfaceproperties;

import neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.physicalproperties.interfaceproperties.surfacetension.SurfaceTensionInterface;

/**
 * InterphasePropertiesInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface InterphasePropertiesInterface extends Cloneable {
  /**
   * init.
   */
  public void init();

  /**
   * init.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void init(neqsim.thermo.system.SystemInterface system);

  /**
   * initAdsorption.
   */
  public void initAdsorption();

  /**
   * Initialize adsorption with a specified isotherm model type.
   *
   * @param type the isotherm type to use (DRA, LANGMUIR, BET, FREUNDLICH, SIPS)
   */
  public void initAdsorption(IsothermType type);

  /**
   * calcAdsorption.
   */
  public void calcAdsorption();

  /**
   * setSolidAdsorbentMaterial.
   *
   * @param material a {@link java.lang.String} object
   */
  public void setSolidAdsorbentMaterial(String material);

  /**
   * clone.
   *
   * @return a {@link neqsim.physicalproperties.interfaceproperties.InterphasePropertiesInterface} object
   */
  public InterphasePropertiesInterface clone();

  /**
   * Get Surface tension between two phases.
   *
   * @param i First phase number.
   * @param j Second phase number.
   * @return Surface tension in default unit.
   */
  public double getSurfaceTension(int i, int j);

  /**
   * Get Surface tension between two phases in a specified unit.
   *
   * @param numb1 First phase number.
   * @param numb2 Second phase number.
   * @param unit a {@link java.lang.String} object
   * @return Surface tension in specified unit.
   */
  public double getSurfaceTension(int numb1, int numb2, String unit);

  /**
   * getInterfacialTensionModel.
   *
   * @return a int
   */
  public int getInterfacialTensionModel();

  /**
   * getSurfaceTensionModel.
   *
   * @param i a int
   * @return a {@link neqsim.physicalproperties.interfaceproperties.surfacetension.SurfaceTensionInterface} object
   */
  public SurfaceTensionInterface getSurfaceTensionModel(int i);

  /**
   * setInterfacialTensionModel.
   *
   * @param phase1 a {@link java.lang.String} object
   * @param phase2 a {@link java.lang.String} object
   * @param model a {@link java.lang.String} object
   */
  public void setInterfacialTensionModel(String phase1, String phase2, String model);

  /**
   * setInterfacialTensionModel.
   *
   * @param interfacialTensionModel a int
   */
  public void setInterfacialTensionModel(int interfacialTensionModel);

  /**
   * getAdsorptionCalc.
   *
   * @return an array of {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface}
   * objects
   */
  public AdsorptionInterface[] getAdsorptionCalc();

  /**
   * getAdsorptionCalc.
   *
   * @param phaseName a {@link java.lang.String} object
   * @return a {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface} object
   */
  public AdsorptionInterface getAdsorptionCalc(String phaseName);

  /**
   * setAdsorptionCalc.
   *
   * @param adsorptionCalc an array of
   * {@link neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface} objects
   */
  public void setAdsorptionCalc(AdsorptionInterface[] adsorptionCalc);
}
