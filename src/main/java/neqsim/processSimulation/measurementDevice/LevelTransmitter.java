package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author ESOL
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass {

    private static final long serialVersionUID = 1000;

    protected Separator separator = null;

    public LevelTransmitter() {}

    public LevelTransmitter(Separator separator) {
        this.separator = separator;
    }

    @Override
    public void displayResult() {
        System.out.println("measured level " + separator.getLiquidLevel());
    }

    @Override
    public double getMeasuredValue() {
        return separator.getLiquidLevel();
    }
}
