/*
 * InterphasePropertiesInterface.java
 *
 * Created on 13. august 2001, 13:13
 */
package neqsim.physicalProperties.interfaceProperties;

import neqsim.physicalProperties.interfaceProperties.surfaceTension.SurfaceTensionInterface;

/**
 *
 * @author  esol
 * @version
 */
public interface InterphasePropertiesInterface {

    public void init();

    public void calcAdsorption();
   public void setInterfacialTensionModel(String phase1, String phase2, String model);
    public void setSolidAdsorbentMaterial(String material);

    //public double getSurfaceTension(int i);

    public double getSurfaceTension(int i, int j);

    public void initAdsorption();

    public void init(neqsim.thermo.system.SystemInterface system);

    public int getInterfacialTensionModel();

    public void setInterfacialTensionModel(int interfacialTensionModel);

    public SurfaceTensionInterface getSurfaceTensionModel(int i);
}

