package neqsim.process.mechanicaldesign.valve;

import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;

public interface ControlValveSizingInterface {

  public Map<String, Object> calcValveSize();

  public double calculateFlowRateFromValveOpening(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double calculateValveOpeningFromFlowRate(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double findOutletPressureForFixedCv(double Cv, double valveOpening,
      StreamInterface inletStream);


  public double calculateFlowRateFromValveOpeningLiquid(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);


  public double calculateValveOpeningFromFlowRateLiquid(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);


  public double findOutletPressureForFixedCvLiquid(double Cv, double valveOpening,
      StreamInterface inletStream);

  public double calculateFlowRateFromValveOpeningGas(double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double calculateValveOpeningFromFlowRateGas(double Q, double Cv, double valveOpening,
      StreamInterface inletStream, StreamInterface outletStream);

  public double findOutletPressureForFixedCvGas(double Cv, double valveOpening,
      StreamInterface inletStream);

  public boolean isAllowChoked();

  public void setAllowChoked(boolean allowChoked);

  public double getxT();

  public void setxT(double xT);


}

