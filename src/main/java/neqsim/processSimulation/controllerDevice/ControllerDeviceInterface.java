/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.processSimulation.controllerDevice;

import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public interface ControllerDeviceInterface extends java.io.Serializable {
    public String getName();
    public void setName(String name);
    public double getMeasuredValue();
    public void setControllerSetPoint(double signal);
    public String getUnit();
    public void setUnit(String unit);
    public void setTransmitter(MeasurementDeviceInterface device);
    public void run(double signal, double dt);
    public double getResponse();
    public boolean isReverseActing();
    public void setReverseActing(boolean reverseActing);
      public void setControllerParameters(double Ksp, double Ti, double Td);
}

