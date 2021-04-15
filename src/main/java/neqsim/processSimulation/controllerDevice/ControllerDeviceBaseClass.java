/*
 * ControllerDeviceBaseClass.java
 *
 * Created on 10. oktober 2006, 19:59
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
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
    public ControllerDeviceBaseClass() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTransmitter(MeasurementDeviceInterface device) {
        this.transmitter = device;
    }

    public double getMeasuredValue() {
        return this.transmitter.getMeasuredValue();
    }

    public void run(double percentSignal, double dt) {
        if (reverseActing) {
            propConstant = -1;
        }
        oldoldError = error;
        oldError = error;
        error = transmitter.getMeasuredPercentValue() - (controllerSetPoint - transmitter.getMinimumValue())
                / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100;

        TintValue += Ksp / Tint * error * dt;
        double TderivValue = Ksp * Tderiv * (error - oldError) / dt;
        response = percentSignal + propConstant * (Ksp * error + TintValue + TderivValue);
        System.out.println("error " + error + " %");
//        error = device.getMeasuredPercentValue()-controlValue;
//        double regulatorSignal = error*1.0;
    }

    public void setControllerSetPoint(double signal) {
        this.controllerSetPoint = signal;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getResponse() {
        return response;
    }

    public boolean isReverseActing() {
        return reverseActing;
    }

    public void setReverseActing(boolean reverseActing) {
        this.reverseActing = reverseActing;
    }

    public double getKsp() {
        return Ksp;
    }

    public void setKsp(double Ksp) {
        this.Ksp = Ksp;
    }

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