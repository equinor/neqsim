/*
 * ControllerDeviceBaseClass.java
 *
 * Created on 10. oktober 2006, 19:59
 */

package neqsim.processSimulation.controllerDevice;

import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;

/**
 *
 * @author ESOL
 */
public class ControllerDeviceBaseClass implements ControllerDeviceInterface {

    private static final long serialVersionUID = 1000;

    private String name = "controller", unit = "[?]";
    private MeasurementDeviceInterface transmitter = null;
    private double controllerSetPoint = 0.0;
    private double oldError = 0.0, oldoldError = 0.0;
    private double error = 0.0;
    private double response = 30.0;
    int propConstant = 1;
    private boolean reverseActing = false;
    private double Ksp = 1.0;
    private double Tint = 300.0, TintValue = 0.0;
    private double Tderiv = 300.0;

    /** Creates a new instance of ControllerDeviceBaseClass */
    public ControllerDeviceBaseClass() {}

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setTransmitter(MeasurementDeviceInterface device) {
        this.transmitter = device;
    }

    @Override
    public double getMeasuredValue() {
        return this.transmitter.getMeasuredValue();
    }

    @Override
    public void run(double percentSignal, double dt) {
        if (reverseActing) {
            propConstant = -1;
        }
        oldoldError = error;
        oldError = error;
        error = transmitter.getMeasuredPercentValue()
                - (controllerSetPoint - transmitter.getMinimumValue())
                        / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100;

        TintValue += Ksp / Tint * error * dt;
        double TderivValue = Ksp * Tderiv * (error - oldError) / dt;
        response = percentSignal + propConstant * (Ksp * error + TintValue + TderivValue);
        System.out.println("error " + error + " %");
        // error = device.getMeasuredPercentValue()-controlValue;
        // double regulatorSignal = error*1.0;
    }

    @Override
    public void setControllerSetPoint(double signal) {
        this.controllerSetPoint = signal;
    }

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public double getResponse() {
        return response;
    }

    @Override
    public boolean isReverseActing() {
        return reverseActing;
    }

    @Override
    public void setReverseActing(boolean reverseActing) {
        this.reverseActing = reverseActing;
    }

    public double getKsp() {
        return Ksp;
    }

    public void setKsp(double Ksp) {
        this.Ksp = Ksp;
    }

    @Override
    public void setControllerParameters(double Ksp, double Ti, double Td) {
        this.setKsp(Ksp);
        this.setTint(Ti);
        this.setTderiv(Td);
    }

    public double getTint() {
        return Tint;
    }

    public void setTint(double Tint) {
        this.Tint = Tint;
    }

    public double getTderiv() {
        return Tderiv;
    }

    public void setTderiv(double Tderiv) {
        this.Tderiv = Tderiv;
    }

}
