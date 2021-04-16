/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author ESOL
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected Separator separator = null;

    /** Creates a new instance of TemperatureTransmitter */
    public LevelTransmitter() {
    }

    public LevelTransmitter(Separator separator) {
        this.separator = separator;
    }

    public void displayResult() {
        System.out.println("measured temperature " + separator.getLiquidLevel());
    }

    public double getMeasuredValue() {
        return separator.getLiquidLevel();
    }

}
