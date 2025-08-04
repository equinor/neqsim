package neqsim.process.mechanicaldesign.valve;

import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

public interface ControlValveSizingInterface {

  public Map<String, Object> calcValveSize();

  public double calculateFlowRateFromValveOpening(double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double calculateValveOpeningFromFlowRate(double Q, double Kv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double findOutletPressureForFixedKv(double Kv, double valveOpening,
      StreamInterface inletStream);

  public boolean isAllowChoked();

  public void setAllowChoked(boolean allowChoked);

  public double getxT();

  public void setxT(double xT);


}

