package neqsim.process.equipment.diffpressure;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;

public class Orifice extends TwoPortEquipment {
  private static final long serialVersionUID = 1L;
  private String name;
  private StreamInterface inputstream;
  private StreamInterface outputstream;
  private Double dp;
  private Double diameter;
  private Double diameter_outer;
  private Double C;
  private double orificeDiameter;
  private double pressureUpstream;
  private double pressureDownstream;
  private double dischargeCoefficient;

  public Orifice(String name) {
    super(name);
  }

  public Orifice(String name, double diameter, double orificeDiameter, double pressureUpstream,
      double pressureDownstream, double dischargeCoefficient) {
    super(name);
    this.diameter = diameter;
    this.orificeDiameter = orificeDiameter;
    this.pressureUpstream = pressureUpstream;
    this.pressureDownstream = pressureDownstream;
    this.dischargeCoefficient = dischargeCoefficient;
  }

  public void setInputStream(StreamInterface stream) {
    this.inputstream = stream;
    this.outputstream = (StreamInterface) stream.clone();
  }

  public StreamInterface getOutputStream() {
    return outputstream;
  }

  public void setOrificeParameters(Double diameter, Double diameter_outer, Double C) {
    this.diameter = diameter;
    this.diameter_outer = diameter_outer;
    this.C = C;
  }

  public Double calc_dp() {
    double beta = orificeDiameter / diameter;
    double beta2 = beta * beta;
    double beta4 = beta2 * beta2;
    double dP = pressureUpstream - pressureDownstream;

    double deltaW = (Math.sqrt(1.0 - beta4 * (1.0 - dischargeCoefficient * dischargeCoefficient))
        - dischargeCoefficient * beta2)
        / (Math.sqrt(1.0 - beta4 * (1.0 - dischargeCoefficient * dischargeCoefficient))
            + dischargeCoefficient * beta2)
        * dP;

    return deltaW;
  }

  @Override
  public void run(UUID uuid) {
    if (inputstream != null && outputstream != null) {
      double newPressure = inputstream.getPressure("bara") - calc_dp();
      outputstream.getFluid().setPressure(newPressure, "bara");
      outputstream.run();
    }
  }

}
