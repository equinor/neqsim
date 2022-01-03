/*
 * InterphasePropertiesInterface.java
 *
 * Created on 13. august 2001, 13:13
 */
package neqsim.physicalProperties.interfaceProperties;

import neqsim.physicalProperties.interfaceProperties.solidAdsorption.AdsorptionInterface;
import neqsim.physicalProperties.interfaceProperties.surfaceTension.SurfaceTensionInterface;

/**
 * <p>InterphasePropertiesInterface interface.</p>
 *
 * @author  esol
 */
public interface InterphasePropertiesInterface extends Cloneable {

    /**
     * <p>init.</p>
     */
    public void init();

    /**
     * <p>calcAdsorption.</p>
     */
    public void calcAdsorption();

    /**
     * <p>setInterfacialTensionModel.</p>
     *
     * @param phase1 a {@link java.lang.String} object
     * @param phase2 a {@link java.lang.String} object
     * @param model a {@link java.lang.String} object
     */
    public void setInterfacialTensionModel(String phase1, String phase2, String model);

    /**
     * <p>setSolidAdsorbentMaterial.</p>
     *
     * @param material a {@link java.lang.String} object
     */
    public void setSolidAdsorbentMaterial(String material);

    /**
     * <p>clone.</p>
     *
     * @return a {@link neqsim.physicalProperties.interfaceProperties.InterphasePropertiesInterface} object
     */
    public InterphasePropertiesInterface clone();

    /**
     * <p>getSurfaceTension.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double getSurfaceTension(int i, int j);

    /**
     * <p>initAdsorption.</p>
     */
    public void initAdsorption();

    /**
     * <p>init.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void init(neqsim.thermo.system.SystemInterface system);

    /**
     * <p>getInterfacialTensionModel.</p>
     *
     * @return a int
     */
    public int getInterfacialTensionModel();

    /**
     * <p>setInterfacialTensionModel.</p>
     *
     * @param interfacialTensionModel a int
     */
    public void setInterfacialTensionModel(int interfacialTensionModel);

    /**
     * <p>getSurfaceTensionModel.</p>
     *
     * @param i a int
     * @return a {@link neqsim.physicalProperties.interfaceProperties.surfaceTension.SurfaceTensionInterface} object
     */
    public SurfaceTensionInterface getSurfaceTensionModel(int i);

    /**
     * <p>getSurfaceTension.</p>
     *
     * @param numb1 a int
     * @param numb2 a int
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getSurfaceTension(int numb1, int numb2, String unit);

    /**
     * <p>getAdsorptionCalc.</p>
     *
     * @return an array of {@link neqsim.physicalProperties.interfaceProperties.solidAdsorption.AdsorptionInterface} objects
     */
    public AdsorptionInterface[] getAdsorptionCalc();

    /**
     * <p>setAdsorptionCalc.</p>
     *
     * @param adsorptionCalc an array of {@link neqsim.physicalProperties.interfaceProperties.solidAdsorption.AdsorptionInterface} objects
     */
    public void setAdsorptionCalc(AdsorptionInterface[] adsorptionCalc);

    /**
     * <p>getAdsorptionCalc.</p>
     *
     * @param phaseName a {@link java.lang.String} object
     * @return a {@link neqsim.physicalProperties.interfaceProperties.solidAdsorption.AdsorptionInterface} object
     */
    public AdsorptionInterface getAdsorptionCalc(String phaseName);
}
