package neqsim.process.equipment.ejector;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.ejector.EjectorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Ejector class represents an ejector in a process simulation. It mixes a motive stream with a
 * suction stream and calculates the resulting mixed stream using a quasi one-dimensional
 * formulation. The implementation combines energy and momentum balances commonly used in
 * steam-jet ejector design as summarised by Keenan et al. (1950) and ESDU 86030.
 */
public class Ejector extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final Logger logger = LogManager.getLogger(Ejector.class);

  private StreamInterface motiveStream;
  private StreamInterface suctionStream;
  private StreamInterface mixedStream;

  private double dischargePressure;
  private double mixingPressure = Double.NaN;
  private double efficiencyIsentropic = 0.75; // default nozzle efficiency
  private double diffuserEfficiency = 0.8; // diffuser pressure recovery efficiency

  private double designSuctionVelocity = 0.0; // estimated from operating conditions unless set
  private double designDiffuserOutletVelocity = 0.0; // estimated from operating conditions
  private boolean designSuctionVelocityOverride = false;
  private boolean designDiffuserVelocityOverride = false;

  private double suctionConnectionLength = 0.0; // m, optional straight pipe upstream
  private double dischargeConnectionLength = 0.0; // m, optional straight pipe downstream
  private boolean suctionConnectionLengthOverride = false;
  private boolean dischargeConnectionLengthOverride = false;

  private transient EjectorMechanicalDesign mechanicalDesign;

  private static final double BAR_TO_PA = 1.0e5;
  private static final double DEFAULT_NOZZLE_LENGTH_FACTOR = 3.0; // ~3x throat diameter
  private static final double DEFAULT_SUCTION_LENGTH_FACTOR = 2.5; // bellmouth and settling
  private static final double DEFAULT_MIXING_LENGTH_FACTOR = 4.0; // per Keenan et al.
  private static final double DEFAULT_DIFFUSER_LENGTH_FACTOR = 6.0; // gradual pressure recovery

  /**
   * Constructs an Ejector with the specified name, motive stream, and suction stream.
   *
   * @param name the name of the ejector
   * @param motiveStream the motive stream
   * @param suctionStream the suction stream
   */
  public Ejector(String name, StreamInterface motiveStream, StreamInterface suctionStream) {
    super(name);
    this.motiveStream = motiveStream;
    this.suctionStream = suctionStream;
    this.mixedStream = motiveStream.clone();
  }

  /**
   * <p>
   * Setter for the field <code>dischargePressure</code>.
   * </p>
   *
   * @param dischargePressure a double
   */
  public void setDischargePressure(double dischargePressure) {
    this.dischargePressure = dischargePressure;
  }

  /**
   * <p>
   * Setter for the field <code>efficiencyIsentropic</code>.
   * </p>
   *
   * @param efficiencyIsentropic a double
   */
  public void setEfficiencyIsentropic(double efficiencyIsentropic) {
    this.efficiencyIsentropic = efficiencyIsentropic;
  }

  /**
   * <p>
   * Setter for the field <code>diffuserEfficiency</code>.
   * </p>
   *
   * @param diffuserEfficiency a double
   */
  public void setDiffuserEfficiency(double diffuserEfficiency) {
    this.diffuserEfficiency = diffuserEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>throatArea</code>.
   * </p>
   *
   * @param throatArea a double
   */
  public void setThroatArea(double throatArea) {
    // Retained for backwards compatibility. The area is now calculated from the
    // hydraulic design and therefore this setter is deprecated.
  }

  /**
   * Sets the target mixing pressure within the ejector. If not set the suction pressure is used.
   *
   * @param mixingPressure the mixing pressure in bara
   */
  public void setMixingPressure(double mixingPressure) {
    this.mixingPressure = mixingPressure;
  }

  /**
   * Sets the design suction velocity used when calculating mechanical dimensions.
   *
   * @param velocity velocity in m/s
   */
  public void setDesignSuctionVelocity(double velocity) {
    this.designSuctionVelocity = velocity;
    this.designSuctionVelocityOverride = true;
  }

  /**
   * Sets the design diffuser outlet velocity used when calculating mechanical dimensions.
   *
   * @param velocity velocity in m/s
   */
  public void setDesignDiffuserOutletVelocity(double velocity) {
    this.designDiffuserOutletVelocity = velocity;
    this.designDiffuserVelocityOverride = true;
  }

  /**
   * Sets the straight length of suction piping to include in total volume calculations.
   *
   * @param length length in metres
   */
  public void setSuctionConnectionLength(double length) {
    this.suctionConnectionLength = Math.max(length, 0.0);
    this.suctionConnectionLengthOverride = true;
  }

  /**
   * Sets the straight length of discharge piping to include in total volume calculations.
   *
   * @param length length in metres
   */
  public void setDischargeConnectionLength(double length) {
    this.dischargeConnectionLength = Math.max(length, 0.0);
    this.dischargeConnectionLengthOverride = true;
  }

  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new EjectorMechanicalDesign(this);
  }

  @Override
  public EjectorMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      initMechanicalDesign();
    }
    return mechanicalDesign;
  }

  /**
   * Backwards compatible accessor for mechanical design results.
   *
   * @return the ejector mechanical design container
   */
  public EjectorMechanicalDesign getDesignResult() {
    return getMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    motiveStream.run();
    suctionStream.run();
    mixedStream = motiveStream.clone();

    double suctionPressure = suctionStream.getPressure("bara");
    double mDotMotive = motiveStream.getFlowRate("kg/sec");
    double mDotSuction = suctionStream.getFlowRate("kg/sec");

    double localMixingPressure = Double.isNaN(mixingPressure)
        ? estimateDefaultMixingPressure(suctionPressure, dischargePressure, mDotMotive, mDotSuction)
        : mixingPressure;

    if (localMixingPressure > suctionPressure) {
      logger.warn("Mixing pressure {} bara exceeds suction pressure {} bara – using suction "
          + "pressure for mixing calculations.", localMixingPressure, suctionPressure);
      localMixingPressure = suctionPressure;
    }

    double mDotTotal = mDotMotive + mDotSuction;

    if (mDotTotal <= 0.0) {
      mixedStream.setThermoSystem(motiveStream.getThermoSystem().clone());
      mixedStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      getMechanicalDesign().resetDesign();
      return;
    }

    // Motive nozzle calculation
    SystemInterface motiveNozzle = motiveStream.getThermoSystem().clone();
    double entropyMotive = motiveNozzle.getEntropy();
    double hMotiveIn = motiveNozzle.getEnthalpy("J/kg");
    motiveNozzle.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations nozzleOps = new ThermodynamicOperations(motiveNozzle);
    nozzleOps.PSflash(entropyMotive);
    double hMotiveIsentropic = motiveNozzle.getEnthalpy("J/kg");
    double hMotiveActual =
        hMotiveIn - efficiencyIsentropic * (hMotiveIn - hMotiveIsentropic);
    nozzleOps.PHflash(hMotiveActual, "J/kg");
    motiveNozzle.init(3);
    double deltaHNozzle = Math.max(hMotiveIn - hMotiveActual, 0.0);
    double velocityNozzle = Math.sqrt(2.0 * deltaHNozzle);
    double rhoNozzle = Math.max(motiveNozzle.getDensity("kg/m3"), 1.0e-9);
    double motiveNozzleArea = mDotMotive / (rhoNozzle * Math.max(velocityNozzle, 1.0e-6));

    // Suction flow accelerated to mixing section
    SystemInterface suctionAtMixing = suctionStream.getThermoSystem().clone();
    double hSuctionIn = suctionAtMixing.getEnthalpy("J/kg");
    suctionAtMixing.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations suctionOps = new ThermodynamicOperations(suctionAtMixing);
    suctionOps.PHflash(hSuctionIn, "J/kg");
    suctionAtMixing.init(3);
    double rhoSuction = Math.max(suctionAtMixing.getDensity("kg/m3"), 1.0e-9);

    double localDesignSuctionVelocity = designSuctionVelocityOverride ? designSuctionVelocity
        : estimateDesignSuctionVelocity(suctionPressure, dischargePressure, rhoSuction, mDotSuction);
    localDesignSuctionVelocity = Math.max(localDesignSuctionVelocity, 1.0e-6);

    double suctionArea = mDotSuction / (rhoSuction * localDesignSuctionVelocity);
    double velocitySuction =
        mDotSuction / (rhoSuction * Math.max(suctionArea, 1.0e-9));

    double totalEnthalpyMotive = hMotiveActual + 0.5 * velocityNozzle * velocityNozzle;
    double totalEnthalpySuction = hSuctionIn + 0.5 * velocitySuction * velocitySuction;

    double mixingVelocity =
        (mDotMotive * velocityNozzle + mDotSuction * velocitySuction) / mDotTotal;
    double mixedTotalEnthalpy =
        (mDotMotive * totalEnthalpyMotive + mDotSuction * totalEnthalpySuction) / mDotTotal;
    double mixedStaticEnthalpy =
        mixedTotalEnthalpy - 0.5 * mixingVelocity * mixingVelocity;

    SystemInterface mixedFluid = motiveStream.getThermoSystem().clone();
    mixedFluid.addFluid(suctionStream.getThermoSystem());
    mixedFluid.setPressure(localMixingPressure, "bara");
    ThermodynamicOperations mixingOps = new ThermodynamicOperations(mixedFluid);
    mixingOps.PHflash(mixedStaticEnthalpy, "J/kg");
    mixedFluid.init(3);
    double rhoMixing = Math.max(mixedFluid.getDensity("kg/m3"), 1.0e-9);
    double mixingArea = mDotTotal / (rhoMixing * Math.max(mixingVelocity, 1.0e-6));

    // Diffuser recovery
    double recoveredSpecificEnergy = diffuserEfficiency * 0.5 * mixingVelocity * mixingVelocity;
    double staticEnthalpyBeforeDiffuser = mixedStaticEnthalpy + recoveredSpecificEnergy;

    mixedFluid.setPressure(dischargePressure, "bara");
    ThermodynamicOperations diffuserOps = new ThermodynamicOperations(mixedFluid);
    diffuserOps.PHflash(staticEnthalpyBeforeDiffuser, "J/kg");
    mixedFluid.init(3);
    double rhoDiffuser = Math.max(mixedFluid.getDensity("kg/m3"), 1.0e-9);

    double localDesignDiffuserOutletVelocity = designDiffuserVelocityOverride
        ? designDiffuserOutletVelocity
        : estimateDesignDiffuserOutletVelocity(localMixingPressure, dischargePressure,
            rhoDiffuser, mDotTotal);
    localDesignDiffuserOutletVelocity = Math.max(localDesignDiffuserOutletVelocity, 1.0e-6);

    double diffuserArea = mDotTotal / (rhoDiffuser * localDesignDiffuserOutletVelocity);
    double diffuserVelocity =
        mDotTotal / (rhoDiffuser * Math.max(diffuserArea, 1.0e-9));
    double finalStaticEnthalpy =
        staticEnthalpyBeforeDiffuser - 0.5 * diffuserVelocity * diffuserVelocity;

    diffuserOps.PHflash(finalStaticEnthalpy, "J/kg");
    mixedFluid.init(3);

    double motiveNozzleDiameter = diameterFromArea(motiveNozzleArea);
    double suctionDiameter = diameterFromArea(suctionArea);
    double mixingDiameter = diameterFromArea(mixingArea);
    double diffuserDiameter = diameterFromArea(diffuserArea);

    double nozzleLength = estimateLength(motiveNozzleDiameter, DEFAULT_NOZZLE_LENGTH_FACTOR);
    double suctionLength = estimateLength(suctionDiameter, DEFAULT_SUCTION_LENGTH_FACTOR);
    double mixingLength = estimateLength(mixingDiameter, DEFAULT_MIXING_LENGTH_FACTOR);
    double diffuserLength = estimateLength(diffuserDiameter, DEFAULT_DIFFUSER_LENGTH_FACTOR);

    double localSuctionConnectionLength = suctionConnectionLengthOverride ? suctionConnectionLength
        : estimateSuctionConnectionLength(suctionDiameter, suctionPressure, dischargePressure);
    double localDischargeConnectionLength = dischargeConnectionLengthOverride
        ? dischargeConnectionLength
        : estimateDischargeConnectionLength(diffuserDiameter, localMixingPressure,
            dischargePressure);

    double bodyVolume = cylinderVolume(motiveNozzleArea, nozzleLength)
        + cylinderVolume(suctionArea, suctionLength) + cylinderVolume(mixingArea, mixingLength)
        + cylinderVolume(diffuserArea, diffuserLength);

    double connectedPipingVolume = cylinderVolume(suctionArea, localSuctionConnectionLength)
        + cylinderVolume(diffuserArea, localDischargeConnectionLength);

    mixedStream.setThermoSystem(mixedFluid);
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    getMechanicalDesign().updateDesign(localMixingPressure, motiveNozzleArea, velocityNozzle,
        suctionArea, velocitySuction, mixingArea, mixingVelocity, diffuserArea, diffuserVelocity,
        getEntrainmentRatio(), nozzleLength, suctionLength, mixingLength, diffuserLength,
        bodyVolume, connectedPipingVolume, localSuctionConnectionLength,
        localDischargeConnectionLength);
  }

  /**
   * <p>
   * getOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * <p>
   * getEntrainmentRatio.
   * </p>
   *
   * @return a double
   */
  public double getEntrainmentRatio() {
    return suctionStream.getFlowRate("kg/sec") / motiveStream.getFlowRate("kg/sec");
  }

  private static double diameterFromArea(double area) {
    if (area <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * area / Math.PI);
  }

  private static double estimateLength(double diameter, double factor) {
    if (diameter <= 0.0 || factor <= 0.0) {
      return 0.0;
    }
    return factor * diameter;
  }

  private static double cylinderVolume(double area, double length) {
    if (area <= 0.0 || length <= 0.0) {
      return 0.0;
    }
    return area * length;
  }

  private double estimateDefaultMixingPressure(double suctionPressure, double dischargePressure,
      double mDotMotive, double mDotSuction) {
    if (suctionPressure <= 0.0) {
      return Math.max(dischargePressure, 0.0);
    }

    double entrainmentRatio = mDotMotive > 1.0e-9 ? Math.max(mDotSuction, 0.0) / mDotMotive : 1.0;
    double clampedRatio = clamp(entrainmentRatio, 0.0, 5.0);
    double pressureLift = Math.max(dischargePressure - suctionPressure, 0.0);
    double pressureDrop = pressureLift * (0.1 + 0.03 * clampedRatio);
    double suctionMargin = suctionPressure * 0.03;
    double estimated = suctionPressure - Math.max(pressureDrop, suctionMargin);
    double minimumPressure = Math.max(0.01, suctionPressure * 0.4);
    return clamp(estimated, minimumPressure, suctionPressure);
  }

  private double estimateDesignSuctionVelocity(double suctionPressure, double dischargePressure,
      double rhoSuction, double mDotSuction) {
    double density = Math.max(rhoSuction, 1.0e-6);
    double availableLiftPa = Math.max((dischargePressure - suctionPressure) * BAR_TO_PA, 0.0);
    double targetDynamic = Math.max(availableLiftPa * 0.02, 500.0);
    double baseline = Math.sqrt(2.0 * targetDynamic / density);
    double volumetricFlow = mDotSuction > 0.0 ? mDotSuction / density : 0.0;
    double flowScaling = volumetricFlow > 0.0
        ? 50.0 + 30.0 * Math.log10(1.0 + volumetricFlow * 5.0)
        : 50.0;
    double blended = 0.6 * baseline + 0.4 * flowScaling;
    return clamp(blended, 25.0, 120.0);
  }

  private double estimateDesignDiffuserOutletVelocity(double mixingPressure,
      double dischargePressure, double rhoDiffuser, double mDotTotal) {
    double density = Math.max(rhoDiffuser, 1.0e-6);
    double pressureRecoveryPa = Math.max((dischargePressure - mixingPressure) * BAR_TO_PA, 0.0);
    double targetDynamic = Math.max(pressureRecoveryPa * 0.01, 250.0);
    double baseline = Math.sqrt(2.0 * targetDynamic / density);
    double volumetricFlow = mDotTotal > 0.0 ? mDotTotal / density : 0.0;
    double flowScaling = volumetricFlow > 0.0
        ? 20.0 + 15.0 * Math.log10(1.0 + volumetricFlow * 4.0)
        : 25.0;
    double blended = 0.5 * baseline + 0.5 * flowScaling;
    return clamp(blended, 10.0, 60.0);
  }

  private double estimateSuctionConnectionLength(double suctionDiameter, double suctionPressure,
      double dischargePressure) {
    if (suctionDiameter <= 0.0) {
      return 0.0;
    }
    double pressureRatio = dischargePressure > 0.0 && suctionPressure > 0.0
        ? dischargePressure / suctionPressure
        : 1.0;
    double factor = 3.0 + 1.5 * clamp(pressureRatio - 1.0, 0.0, 3.0);
    return factor * suctionDiameter;
  }

  private double estimateDischargeConnectionLength(double diffuserDiameter,
      double mixingPressure, double dischargePressure) {
    if (diffuserDiameter <= 0.0) {
      return 0.0;
    }
    double pressureRatio = dischargePressure > 0.0 && mixingPressure > 0.0
        ? dischargePressure / mixingPressure
        : 1.0;
    double factor = 5.0 + 2.0 * clamp(pressureRatio - 1.0, 0.0, 3.0);
    return factor * diffuserDiameter;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(value, max));
  }
}
