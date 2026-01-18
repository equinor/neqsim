package neqsim.process.mechanicaldesign.ejector;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design container for ejector sizing results.
 */
public class EjectorMechanicalDesign extends MechanicalDesign {
  private static final long serialVersionUID = 1L;

  private double mixingPressure;
  private double motiveNozzleThroatArea;
  private double motiveNozzleExitVelocity;
  private double suctionInletArea;
  private double suctionInletVelocity;
  private double mixingChamberArea;
  private double mixingChamberVelocity;
  private double diffuserOutletArea;
  private double diffuserOutletVelocity;
  private double entrainmentRatio;
  private double motiveNozzleEffectiveLength;
  private double suctionInletLength;
  private double mixingChamberLength;
  private double diffuserOutletLength;
  private double bodyVolume;
  private double connectedPipingVolume;
  private double suctionConnectionLength;
  private double dischargeConnectionLength;

  public EjectorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** Reset stored results. */
  public void resetDesign() {
    mixingPressure = 0.0;
    motiveNozzleThroatArea = 0.0;
    motiveNozzleExitVelocity = 0.0;
    suctionInletArea = 0.0;
    suctionInletVelocity = 0.0;
    mixingChamberArea = 0.0;
    mixingChamberVelocity = 0.0;
    diffuserOutletArea = 0.0;
    diffuserOutletVelocity = 0.0;
    entrainmentRatio = 0.0;
    motiveNozzleEffectiveLength = 0.0;
    suctionInletLength = 0.0;
    mixingChamberLength = 0.0;
    diffuserOutletLength = 0.0;
    bodyVolume = 0.0;
    connectedPipingVolume = 0.0;
    suctionConnectionLength = 0.0;
    dischargeConnectionLength = 0.0;
  }

  /**
   * Store the latest mechanical design results from an ejector calculation.
   *
   * @param mixingPressure mixing chamber pressure in Pa
   * @param motiveNozzleThroatArea motive nozzle throat area in m2
   * @param motiveNozzleExitVelocity motive nozzle exit velocity in m/s
   * @param suctionInletArea suction inlet area in m2
   * @param suctionInletVelocity suction inlet velocity in m/s
   * @param mixingChamberArea mixing chamber area in m2
   * @param mixingChamberVelocity mixing chamber velocity in m/s
   * @param diffuserOutletArea diffuser outlet area in m2
   * @param diffuserOutletVelocity diffuser outlet velocity in m/s
   * @param entrainmentRatio ratio of suction to motive mass flow
   * @param motiveNozzleEffectiveLength motive nozzle effective length in m
   * @param suctionInletLength suction inlet length in m
   * @param mixingChamberLength mixing chamber length in m
   * @param diffuserOutletLength diffuser outlet length in m
   * @param bodyVolume ejector body volume in m3
   * @param connectedPipingVolume connected piping volume in m3
   * @param suctionConnectionLength suction connection length in m
   * @param dischargeConnectionLength discharge connection length in m
   */
  public void updateDesign(double mixingPressure, double motiveNozzleThroatArea,
      double motiveNozzleExitVelocity, double suctionInletArea, double suctionInletVelocity,
      double mixingChamberArea, double mixingChamberVelocity, double diffuserOutletArea,
      double diffuserOutletVelocity, double entrainmentRatio, double motiveNozzleEffectiveLength,
      double suctionInletLength, double mixingChamberLength, double diffuserOutletLength,
      double bodyVolume, double connectedPipingVolume, double suctionConnectionLength,
      double dischargeConnectionLength) {
    this.mixingPressure = mixingPressure;
    this.motiveNozzleThroatArea = motiveNozzleThroatArea;
    this.motiveNozzleExitVelocity = motiveNozzleExitVelocity;
    this.suctionInletArea = suctionInletArea;
    this.suctionInletVelocity = suctionInletVelocity;
    this.mixingChamberArea = mixingChamberArea;
    this.mixingChamberVelocity = mixingChamberVelocity;
    this.diffuserOutletArea = diffuserOutletArea;
    this.diffuserOutletVelocity = diffuserOutletVelocity;
    this.entrainmentRatio = entrainmentRatio;
    this.motiveNozzleEffectiveLength = motiveNozzleEffectiveLength;
    this.suctionInletLength = suctionInletLength;
    this.mixingChamberLength = mixingChamberLength;
    this.diffuserOutletLength = diffuserOutletLength;
    this.bodyVolume = bodyVolume;
    this.connectedPipingVolume = connectedPipingVolume;
    this.suctionConnectionLength = suctionConnectionLength;
    this.dischargeConnectionLength = dischargeConnectionLength;
  }

  public double getMixingPressure() {
    return mixingPressure;
  }

  public double getMotiveNozzleThroatArea() {
    return motiveNozzleThroatArea;
  }

  public double getMotiveNozzleExitVelocity() {
    return motiveNozzleExitVelocity;
  }

  public double getMotiveNozzleDiameter() {
    return areaToDiameter(motiveNozzleThroatArea);
  }

  public double getSuctionInletArea() {
    return suctionInletArea;
  }

  public double getSuctionInletVelocity() {
    return suctionInletVelocity;
  }

  public double getSuctionInletDiameter() {
    return areaToDiameter(suctionInletArea);
  }

  public double getMixingChamberArea() {
    return mixingChamberArea;
  }

  public double getMixingChamberVelocity() {
    return mixingChamberVelocity;
  }

  public double getMixingChamberDiameter() {
    return areaToDiameter(mixingChamberArea);
  }

  public double getDiffuserOutletArea() {
    return diffuserOutletArea;
  }

  public double getDiffuserOutletVelocity() {
    return diffuserOutletVelocity;
  }

  public double getDiffuserOutletDiameter() {
    return areaToDiameter(diffuserOutletArea);
  }

  public double getEntrainmentRatio() {
    return entrainmentRatio;
  }

  public double getMotiveNozzleEffectiveLength() {
    return motiveNozzleEffectiveLength;
  }

  public double getSuctionInletLength() {
    return suctionInletLength;
  }

  public double getMixingChamberLength() {
    return mixingChamberLength;
  }

  public double getDiffuserOutletLength() {
    return diffuserOutletLength;
  }

  public double getBodyVolume() {
    return bodyVolume;
  }

  public double getConnectedPipingVolume() {
    return connectedPipingVolume;
  }

  public double getTotalVolume() {
    return bodyVolume + connectedPipingVolume;
  }

  public double getSuctionConnectionLength() {
    return suctionConnectionLength;
  }

  public double getDischargeConnectionLength() {
    return dischargeConnectionLength;
  }

  private static double areaToDiameter(double area) {
    if (area <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * area / Math.PI);
  }
}
