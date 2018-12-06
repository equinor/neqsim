/*
 * WettedWallColumnData.java
 *
 * Created on 9. februar 2001, 10:01
 */

package neqsim.statistics.experimentalEquipmentData.wettedWallColumnData;

import neqsim.statistics.experimentalEquipmentData.ExperimentalEquipmentData;

/**
 *
 * @author  even solbraa
 * @version 
 */
public class WettedWallColumnData extends ExperimentalEquipmentData{

    private static final long serialVersionUID = 1000;

    /** Creates new WettedWallColumnData */
   
    public WettedWallColumnData() {
    }
    
    public WettedWallColumnData(double diameter, double length, double volume) {
        this.diameter = diameter;
        this.length = length;
        this.volume = volume;
    }
    
    public void setDiameter(double diameter){
        this.diameter = diameter;
    }
    
    public double getDiameter(){
        return this.diameter;
    }
    
     public void setLength(double diameter){
        this.length = length;
    }
    
    public double getLength(){
        return this.length;
    }
    
    public void setVolume(double volume){
        this.volume = volume;
    }
    
    public double getVolume(){
        return this.volume;
    }
}
