package neqsim.process.equipment.ejector;

/**
 * Immutable container for mechanical design results of an ejector.
 */
public final class EjectorDesignResult {
  private final double mixingPressure;
  private final double motiveNozzleThroatArea;
  private final double motiveNozzleExitVelocity;
  private final double suctionInletArea;
  private final double suctionInletVelocity;
  private final double mixingChamberArea;
  private final double mixingChamberVelocity;
  private final double diffuserOutletArea;
  private final double diffuserOutletVelocity;
  private final double entrainmentRatio;
  private final double motiveNozzleEffectiveLength;
  private final double suctionInletLength;
  private final double mixingChamberLength;
  private final double diffuserOutletLength;
  private final double bodyVolume;
  private final double connectedPipingVolume;
  private final double suctionConnectionLength;
  private final double dischargeConnectionLength;

  /**
   * Creates a new design result.
   *
   * @param mixingPressure mixing pressure
   * @param motiveNozzleThroatArea motive nozzle throat area
   * @param motiveNozzleExitVelocity motive nozzle exit velocity
   * @param suctionInletArea suction inlet area
   * @param suctionInletVelocity suction inlet velocity
   * @param mixingChamberArea mixing chamber area
   * @param mixingChamberVelocity mixing chamber velocity
   * @param diffuserOutletArea diffuser outlet area
   * @param diffuserOutletVelocity diffuser outlet velocity
   * @param entrainmentRatio entrainment ratio
   * @param motiveNozzleEffectiveLength motive nozzle effective length
   * @param suctionInletLength suction inlet length
   * @param mixingChamberLength mixing chamber length
   * @param diffuserOutletLength diffuser outlet length
   * @param bodyVolume body volume
   * @param connectedPipingVolume connected piping volume
   * @param suctionConnectionLength suction connection length
   * @param dischargeConnectionLength discharge connection length
   */
  public EjectorDesignResult(double mixingPressure, double motiveNozzleThroatArea,
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

  /**
   * Returns an empty design result.
   */
  public static EjectorDesignResult empty() {
    return new EjectorDesignResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0);
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
