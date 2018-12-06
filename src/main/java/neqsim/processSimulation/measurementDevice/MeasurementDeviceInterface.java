/*
 * OperationInterafce.java
 *
 * Created on 2. oktober 2000, 22:14
 */
package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.measurementDevice.online.OnlineSignal;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface MeasurementDeviceInterface extends java.io.Serializable {

    public void displayResult();

    public String getName();

    public void setName(String name);

    public double getMeasuredValue();
    
      public OnlineSignal getOnlineSignal();

    public double getMeasuredPercentValue();

    public String getUnit();

    public void setUnit(String unit);

    public double getMaximumValue();

    public void setMaximumValue(double maximumValue);

    public double getMinimumValue();

    public void setMinimumValue(double minimumValue);

    public boolean isLogging();

    public void setLogging(boolean logging);

    public double getOnlineValue();

    public boolean isOnlineSignal();
}
