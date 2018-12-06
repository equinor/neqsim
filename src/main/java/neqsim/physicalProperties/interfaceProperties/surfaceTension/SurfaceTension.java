/*
 * SurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalProperties.interfaceProperties.surfaceTension;

import neqsim.physicalProperties.interfaceProperties.InterfaceProperties;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author  esol
 * @version
 */
public class SurfaceTension  extends InterfaceProperties implements SurfaceTensionInterface{

    private static final long serialVersionUID = 1000;
    
    transient protected SystemInterface system;
    /** Creates new SurfaceTension */
    public SurfaceTension() {
    }
    
    public SurfaceTension(SystemInterface system) {
        this.system = system;
    }
    
    public double calcPureComponentSurfaceTension(int componentNumber){
        return 0.0;
    }
    
    public double calcSurfaceTension(int int1, int int2){
        return 0.0;
    }

     public int getComponentWithHighestBoilingpoint(){
        int compNumb = 0;
        double boilPoint=-273.15;
        for(int i=0;i<system.getPhase(0).getNumberOfComponents();i++){
            if(system.getPhase(0).getComponent(i).getNormalBoilingPoint()>boilPoint){
                compNumb = i;
                boilPoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
            }
        }
        return compNumb;
    }

}
