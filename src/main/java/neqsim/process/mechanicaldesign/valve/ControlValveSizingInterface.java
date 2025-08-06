package neqsim.process.mechanicaldesign.valve;

import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

public interface ControlValveSizingInterface {

  public Map<String, Object> calcValveSize(double percentOpening);

  public double calculateFlowRateFromValveOpening(double ActualKv, StreamInterface inletStream,
      StreamInterface outletStream);

  public double calculateValveOpeningFromFlowRate(double Q, double ActualActualKv,
      StreamInterface inletStream, StreamInterface outletStream);

  public double findOutletPressureForFixedKv(double ActualKv, StreamInterface inletStream);

  public boolean isAllowChoked();

  public void setAllowChoked(boolean allowChoked);

  public double getxT();

  public void setxT(double xT);


}

