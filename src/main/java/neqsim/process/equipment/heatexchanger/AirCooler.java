package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.humidair.HumidAir;

/**
 * Air-cooled heat exchanger (fin-fan cooler) model.
 *
 * <p>
 * Models an air cooler with humid-air side energy balance, fin-tube thermal design (LMTD, UA),
 * bundle geometry, and fan power estimation. Supports ambient temperature correction, fan curve
 * modelling, and number-of-bays sizing.
 * </p>
 *
 * <p>
 * Thermal design follows the API 661 framework: the overall heat transfer coefficient considers
 * air-side (fin) and process-side components. The air-side coefficient uses the Briggs-Young
 * fin-tube correlation, and LMTD correction for cross-flow applies per TEMA.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * AirCooler cooler = new AirCooler("AC-100", hotStream);
 * cooler.setOutletTemperature(40.0, "C");
 * cooler.setAirInletTemperature(25.0, "C");
 * cooler.setNumberOfTubeRows(4);
 * cooler.setFinPitch(2.5e-3);
 * cooler.run();
 * double fanPower = cooler.getFanPower("kW");
 * double faceArea = cooler.getFaceArea();
 * </pre>
 *
 * @author esol
 * @version 2.0
 */
public class AirCooler extends Cooler {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger. */
  private static final Logger logger = LogManager.getLogger(AirCooler.class);

  // ======================== Air-side conditions ========================

  /** Air inlet temperature [K]. */
  private double airInletTemperature = 298.15;
  /** Air outlet temperature [K]. */
  private double airOutletTemperature = 308.15;
  /** Relative humidity fraction [0-1]. */
  private double relativeHumidity = 0.6;
  /** Atmospheric pressure [Pa]. */
  private double atmosphericPressure = 101325.0;

  // ======================== Bundle geometry ========================

  /** Tube outside diameter [m]. Default 25.4 mm (1 inch) per API 661. */
  private double tubeOuterDiameter = 0.0254;
  /** Tube wall thickness [m]. Default 2.11 mm (BWG 14). */
  private double tubeWallThickness = 0.00211;
  /** Fin height [m]. Default 15.875 mm (5/8 inch). */
  private double finHeight = 0.015875;
  /** Fin thickness [m]. Default 0.4 mm (aluminium). */
  private double finThickness = 0.0004;
  /** Fin pitch [m]. Default 2.5 mm (394 fpm). */
  private double finPitch = 0.0025;
  /** Fin thermal conductivity [W/m-K]. Default 200 for aluminium. */
  private double finConductivity = 200.0;
  /** Number of tube rows. Default 4 per API 661 typical. */
  private int numberOfTubeRows = 4;
  /** Number of tube passes. Default 2. */
  private int numberOfTubePasses = 2;
  /** Transverse tube pitch [m]. Default 60.3 mm (2-3/8 inch). */
  private double transversePitch = 0.0603;
  /** Tube length (one bay) [m]. Default 12 m (40 ft standard). */
  private double tubeLength = 12.0;
  /** Bay width [m]. Default 3.05 m (10 ft). */
  private double bayWidth = 3.05;
  /** Number of bays. Default 1, can be auto-sized. */
  private int numberOfBays = 1;
  /** Number of fans per bay. Default 2. */
  private int numberOfFansPerBay = 2;
  /** Fan diameter [m]. Default 4.27 m (14 ft). */
  private double fanDiameter = 4.27;

  // ======================== Design parameters ========================

  /** Process-side fouling resistance [m2-K/W]. Default 1.76e-4 (API 661 typical). */
  private double processFoulingResistance = 1.76e-4;
  /** Air-side fouling resistance [m2-K/W]. Default 0 (clean fins). */
  private double airFoulingResistance = 0.0;
  /** LMTD correction factor for cross-flow (F). Default 0.9. */
  private double lmtdCorrectionFactor = 0.9;
  /** Fan total efficiency (fan + belt + motor). Default 0.55. */
  private double fanEfficiency = 0.55;

  // ======================== Fan curve (cubic polynomial) ========================

  /** Whether a user-defined fan curve is active. */
  private boolean fanCurveActive = false;
  /** Fan curve coefficients: dP = a0 + a1*Q + a2*Q^2 + a3*Q^3, Q in m3/s, dP in Pa. */
  private double fanCurveA0 = 300.0;
  /** Fan curve coefficient a1. */
  private double fanCurveA1 = 0.0;
  /** Fan curve coefficient a2. */
  private double fanCurveA2 = -0.005;
  /** Fan curve coefficient a3. */
  private double fanCurveA3 = 0.0;

  // ======================== Ambient temperature correction ========================

  /** Design ambient temperature [K] (for ITD-based correction). */
  private double designAmbientTemperature = 298.15;

  // ======================== Calculated results ========================

  /** Air mass flow (dry basis) [kg/s]. */
  private double airMassFlow = 0.0;
  /** Air volume flow at inlet [m3/s]. */
  private double airVolumeFlow = 0.0;
  /** Overall heat transfer coefficient (bare tube basis) [W/m2-K]. */
  private double overallU = 0.0;
  /** Required bare-tube area [m2]. */
  private double requiredArea = 0.0;
  /** Log-mean temperature difference [K]. */
  private double lmtd = 0.0;
  /** Air-side heat transfer coefficient (bare tube basis) [W/m2-K]. */
  private double airSideHTC = 0.0;
  /** Process-side heat transfer coefficient [W/m2-K]. */
  private double processSideHTC = 200.0;
  /** Fin efficiency [-]. */
  private double finEfficiency = 0.0;
  /** Fan power (total, all fans) [W]. */
  private double fanPower = 0.0;
  /** Air-side pressure drop [Pa]. */
  private double airSidePressureDrop = 0.0;
  /** Unit face velocity [m/s]. */
  private double faceVelocity = 0.0;
  /** Bundle face area [m2]. */
  private double faceArea = 0.0;
  /** Calculated number of tubes per row. */
  private int tubesPerRow = 0;
  /** Total number of tubes. */
  private int totalTubes = 0;
  /** Total fin area [m2]. */
  private double totalFinArea = 0.0;
  /** Bare tube area [m2]. */
  private double bareTubeArea = 0.0;
  /** Ambient correction factor (duty ratio at different ambient T vs design T). */
  private double ambientCorrectionFactor = 1.0;
  /** Initial temperature difference (ITD = T_process_in - T_air_in) [K]. */
  private double itd = 0.0;

  /**
   * Constructor for AirCooler.
   *
   * @param name equipment name
   */
  public AirCooler(String name) {
    super(name);
  }

  /**
   * Constructor for AirCooler with inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet process stream
   */
  public AirCooler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  // ======================== Air-side setters ========================

  /**
   * Set air inlet temperature.
   *
   * @param temperature temperature value
   * @param unit "C" for Celsius, "K" for Kelvin
   */
  public void setAirInletTemperature(double temperature, String unit) {
    airInletTemperature = unit.equalsIgnoreCase("C") ? temperature + 273.15 : temperature;
  }

  /**
   * Set air outlet temperature.
   *
   * @param temperature temperature value
   * @param unit "C" for Celsius, "K" for Kelvin
   */
  public void setAirOutletTemperature(double temperature, String unit) {
    airOutletTemperature = unit.equalsIgnoreCase("C") ? temperature + 273.15 : temperature;
  }

  /**
   * Set relative humidity fraction (0-1).
   *
   * @param rh relative humidity
   */
  public void setRelativeHumidity(double rh) {
    relativeHumidity = rh;
  }

  /**
   * Set atmospheric pressure [Pa].
   *
   * @param pressure atmospheric pressure in Pa
   */
  public void setAtmosphericPressure(double pressure) {
    this.atmosphericPressure = pressure;
  }

  // ======================== Geometry setters ========================

  /**
   * Set tube outside diameter [m].
   *
   * @param diameter tube OD in meters
   */
  public void setTubeOuterDiameter(double diameter) {
    this.tubeOuterDiameter = diameter;
  }

  /**
   * Set tube wall thickness [m].
   *
   * @param thickness tube wall thickness in meters
   */
  public void setTubeWallThickness(double thickness) {
    this.tubeWallThickness = thickness;
  }

  /**
   * Set fin height [m].
   *
   * @param height fin height in meters
   */
  public void setFinHeight(double height) {
    this.finHeight = height;
  }

  /**
   * Set fin thickness [m].
   *
   * @param thickness fin thickness in meters
   */
  public void setFinThickness(double thickness) {
    this.finThickness = thickness;
  }

  /**
   * Set fin pitch (fin-to-fin spacing) [m].
   *
   * @param pitch fin pitch in meters
   */
  public void setFinPitch(double pitch) {
    this.finPitch = pitch;
  }

  /**
   * Set fin thermal conductivity [W/m-K].
   *
   * @param conductivity fin thermal conductivity
   */
  public void setFinConductivity(double conductivity) {
    this.finConductivity = conductivity;
  }

  /**
   * Set number of tube rows (typically 3-6, per API 661).
   *
   * @param rows number of tube rows
   */
  public void setNumberOfTubeRows(int rows) {
    this.numberOfTubeRows = rows;
  }

  /**
   * Set number of tube passes.
   *
   * @param passes number of tube passes
   */
  public void setNumberOfTubePasses(int passes) {
    this.numberOfTubePasses = passes;
  }

  /**
   * Set transverse tube pitch [m].
   *
   * @param pitch tube pitch in meters
   */
  public void setTransversePitch(double pitch) {
    this.transversePitch = pitch;
  }

  /**
   * Set tube length per bay [m].
   *
   * @param length tube length in meters
   */
  public void setTubeLength(double length) {
    this.tubeLength = length;
  }

  /**
   * Set bay width [m].
   *
   * @param width bay width in meters
   */
  public void setBayWidth(double width) {
    this.bayWidth = width;
  }

  /**
   * Set number of bays.
   *
   * @param bays number of bays
   */
  public void setNumberOfBays(int bays) {
    this.numberOfBays = bays;
  }

  /**
   * Set number of fans per bay.
   *
   * @param fans fans per bay
   */
  public void setNumberOfFansPerBay(int fans) {
    this.numberOfFansPerBay = fans;
  }

  /**
   * Set fan diameter [m].
   *
   * @param diameter fan diameter in meters
   */
  public void setFanDiameter(double diameter) {
    this.fanDiameter = diameter;
  }

  // ======================== Design parameter setters ========================

  /**
   * Set process-side fouling resistance [m2-K/W].
   *
   * @param resistance fouling resistance
   */
  public void setProcessFoulingResistance(double resistance) {
    this.processFoulingResistance = resistance;
  }

  /**
   * Set air-side fouling resistance [m2-K/W].
   *
   * @param resistance fouling resistance
   */
  public void setAirFoulingResistance(double resistance) {
    this.airFoulingResistance = resistance;
  }

  /**
   * Set LMTD correction factor for cross-flow arrangement.
   *
   * @param factor F factor (0.7-1.0)
   */
  public void setLmtdCorrectionFactor(double factor) {
    this.lmtdCorrectionFactor = factor;
  }

  /**
   * Set overall fan efficiency (fan * belt * motor).
   *
   * @param efficiency overall efficiency (0-1)
   */
  public void setFanEfficiency(double efficiency) {
    this.fanEfficiency = efficiency;
  }

  /**
   * Set process-side heat transfer coefficient [W/m2-K]. If not set, a default of 200 is used.
   *
   * @param htc process-side HTC
   */
  public void setProcessSideHTC(double htc) {
    this.processSideHTC = htc;
  }

  /**
   * Set design ambient temperature (for ambient correction).
   *
   * @param temperature temperature value
   * @param unit "C" or "K"
   */
  public void setDesignAmbientTemperature(double temperature, String unit) {
    designAmbientTemperature = unit.equalsIgnoreCase("C") ? temperature + 273.15 : temperature;
  }

  // ======================== Fan curve ========================

  /**
   * Set fan curve as cubic polynomial: dP = a0 + a1*Q + a2*Q^2 + a3*Q^3.
   *
   * @param a0 constant term [Pa]
   * @param a1 linear coefficient [Pa/(m3/s)]
   * @param a2 quadratic coefficient [Pa/(m3/s)^2]
   * @param a3 cubic coefficient [Pa/(m3/s)^3]
   */
  public void setFanCurve(double a0, double a1, double a2, double a3) {
    this.fanCurveA0 = a0;
    this.fanCurveA1 = a1;
    this.fanCurveA2 = a2;
    this.fanCurveA3 = a3;
    this.fanCurveActive = true;
  }

  /**
   * Get fan static pressure from the fan curve for given volume flow [Pa].
   *
   * @param volumeFlow air volume flow per fan [m3/s]
   * @return fan static pressure [Pa]
   */
  public double getFanStaticPressure(double volumeFlow) {
    return fanCurveA0 + fanCurveA1 * volumeFlow + fanCurveA2 * volumeFlow * volumeFlow
        + fanCurveA3 * volumeFlow * volumeFlow * volumeFlow;
  }

  // ======================== Result getters ========================

  /**
   * Get air mass flow (dry basis) [kg/s].
   *
   * @return air mass flow
   */
  public double getAirMassFlow() {
    return airMassFlow;
  }

  /**
   * Get air volume flow at inlet conditions [m3/s].
   *
   * @return air volume flow
   */
  public double getAirVolumeFlow() {
    return airVolumeFlow;
  }

  /**
   * Get overall heat transfer coefficient (bare tube basis) [W/m2-K].
   *
   * @return U value
   */
  public double getOverallU() {
    return overallU;
  }

  /**
   * Get required heat transfer area (bare tube basis) [m2].
   *
   * @return required area in m2
   */
  public double getRequiredArea() {
    return requiredArea;
  }

  /**
   * Get log-mean temperature difference [K].
   *
   * @return LMTD
   */
  public double getLMTD() {
    return lmtd;
  }

  /**
   * Get air-side heat transfer coefficient (bare tube basis) [W/m2-K].
   *
   * @return air-side HTC
   */
  public double getAirSideHTC() {
    return airSideHTC;
  }

  /**
   * Get fin efficiency [-].
   *
   * @return fin efficiency (0-1)
   */
  public double getFinEfficiency() {
    return finEfficiency;
  }

  /**
   * Get total fan power [W].
   *
   * @return fan power in W
   */
  public double getFanPower() {
    return fanPower;
  }

  /**
   * Get total fan power with unit.
   *
   * @param unit "W", "kW", "hp"
   * @return fan power in specified unit
   */
  public double getFanPower(String unit) {
    if ("kW".equalsIgnoreCase(unit)) {
      return fanPower / 1000.0;
    } else if ("hp".equalsIgnoreCase(unit)) {
      return fanPower / 745.7;
    }
    return fanPower;
  }

  /**
   * Get air-side pressure drop [Pa].
   *
   * @return air-side DP
   */
  public double getAirSidePressureDrop() {
    return airSidePressureDrop;
  }

  /**
   * Get face velocity [m/s].
   *
   * @return face velocity
   */
  public double getFaceVelocity() {
    return faceVelocity;
  }

  /**
   * Get bundle face area [m2].
   *
   * @return face area
   */
  public double getFaceArea() {
    return faceArea;
  }

  /**
   * Get number of tubes per row.
   *
   * @return tubes per row
   */
  public int getTubesPerRow() {
    return tubesPerRow;
  }

  /**
   * Get total number of tubes.
   *
   * @return total tubes
   */
  public int getTotalTubes() {
    return totalTubes;
  }

  /**
   * Get ambient correction factor (actual duty / design duty ratio).
   *
   * @return correction factor
   */
  public double getAmbientCorrectionFactor() {
    return ambientCorrectionFactor;
  }

  /**
   * Get initial temperature difference (ITD) [K].
   *
   * @return ITD
   */
  public double getITD() {
    return itd;
  }

  /**
   * Get number of bays.
   *
   * @return number of bays
   */
  public int getNumberOfBays() {
    return numberOfBays;
  }

  /**
   * Get number of tube rows.
   *
   * @return number of tube rows
   */
  public int getNumberOfTubeRows() {
    return numberOfTubeRows;
  }

  // ======================== Calculation methods ========================

  /**
   * Calculate air mass and volume flow from duty and humid-air energy balance.
   *
   * @param duty heat duty [W] (positive = heat rejection)
   */
  private void calcAirFlow(double duty) {
    double W = HumidAir.humidityRatioFromRH(airInletTemperature, atmosphericPressure,
        relativeHumidity);
    double hin = HumidAir.enthalpy(airInletTemperature, W);
    double hout = HumidAir.enthalpy(airOutletTemperature, W);
    double dh = hout - hin;
    if (Math.abs(dh) < 1e-6) {
      airMassFlow = 0.0;
      airVolumeFlow = 0.0;
      return;
    }
    airMassFlow = duty / (dh * 1000.0);
    double Mda = 28.965e-3;
    double Mw = 18.01528e-3;
    double volumePerKgDryAir =
        ((1.0 / Mda) + (W / Mw)) * 8.314 * airInletTemperature / atmosphericPressure;
    airVolumeFlow = airMassFlow * volumePerKgDryAir;
  }

  /**
   * Calculate LMTD for cross-flow with F correction. Uses process inlet/outlet and air
   * inlet/outlet temperatures.
   *
   * @param tProcessIn process inlet temperature [K]
   * @param tProcessOut process outlet temperature [K]
   */
  private void calcLMTD(double tProcessIn, double tProcessOut) {
    double dt1 = tProcessIn - airOutletTemperature;
    double dt2 = tProcessOut - airInletTemperature;
    if (dt1 <= 0 || dt2 <= 0) {
      lmtd = Math.max(dt1, dt2);
      if (lmtd <= 0) {
        lmtd = 1.0;
      }
      return;
    }
    if (Math.abs(dt1 - dt2) < 0.01) {
      lmtd = dt1;
    } else {
      lmtd = (dt1 - dt2) / Math.log(dt1 / dt2);
    }
    lmtd *= lmtdCorrectionFactor;
    itd = tProcessIn - airInletTemperature;
  }

  /**
   * Calculate air-side heat transfer coefficient using Briggs-Young correlation for finned tubes.
   *
   * <p>
   * Briggs, D.E. and Young, E.H. (1963), "Convection Heat Transfer and Pressure Drop of Air
   * Flowing Across Triangular Pitch Banks of Finned Tubes", Chemical Engineering Progress Symposium
   * Series 59(41):1-10.
   * </p>
   */
  private void calcAirSideHTC() {
    double finOD = tubeOuterDiameter + 2.0 * finHeight;
    double finSpacing = finPitch - finThickness;
    if (finSpacing <= 0 || finHeight <= 0) {
      airSideHTC = 30.0;
      return;
    }

    // Air properties at mean temperature
    double tMean = (airInletTemperature + airOutletTemperature) / 2.0;
    double rhoAir = atmosphericPressure / (287.05 * tMean);
    double muAir = 1.458e-6 * Math.pow(tMean, 1.5) / (tMean + 110.4);
    double cpAir = 1006.0;
    double kAir = 0.0241 * Math.pow(tMean / 273.15, 0.81);
    double prAir = muAir * cpAir / kAir;

    // Maximum air velocity through minimum flow area
    double aFin = 2.0 * Math.PI / 4.0 * (finOD * finOD - tubeOuterDiameter * tubeOuterDiameter);
    double nFinsPerMeter = 1.0 / finPitch;
    double aFinPerMeter = aFin * nFinsPerMeter;
    double aBareTubePerMeter = Math.PI * tubeOuterDiameter * (1.0 - nFinsPerMeter * finThickness);

    // Minimum free area ratio
    double aMin = transversePitch - tubeOuterDiameter - 2.0 * finHeight * finThickness / finPitch;
    if (aMin <= 0) {
      aMin = transversePitch * 0.3;
    }
    double sigmaRatio = aMin / transversePitch;

    double vMax = 0.0;
    if (faceArea > 0 && airVolumeFlow > 0) {
      double vFace = airVolumeFlow / faceArea;
      vMax = vFace / sigmaRatio;
    } else {
      vMax = 4.0 / sigmaRatio;
    }

    double Re = rhoAir * vMax * tubeOuterDiameter / muAir;
    if (Re < 1.0) {
      Re = 1000.0;
    }

    // Briggs-Young: Nu = 0.134 * Re^0.681 * Pr^(1/3) * (finSpacing/finHeight)^0.2 *
    // (finSpacing/finThickness)^0.1134
    double Nu = 0.134 * Math.pow(Re, 0.681) * Math.pow(prAir, 1.0 / 3.0)
        * Math.pow(finSpacing / finHeight, 0.2) * Math.pow(finSpacing / finThickness, 0.1134);

    double hAir = Nu * kAir / tubeOuterDiameter;

    // Fin efficiency using annular fin approximation (Schmidt method)
    double r1 = tubeOuterDiameter / 2.0;
    double r2 = finOD / 2.0;
    double m = Math.sqrt(2.0 * hAir / (finConductivity * finThickness));
    double phi = (r2 / r1 - 1.0) * (1.0 + 0.35 * Math.log(r2 / r1));
    double mPhi = m * r1 * phi;
    finEfficiency = (mPhi > 0) ? Math.tanh(mPhi) / mPhi : 1.0;
    finEfficiency = Math.min(finEfficiency, 1.0);
    finEfficiency = Math.max(finEfficiency, 0.3);

    // Weighted surface efficiency
    double aTotal = aFinPerMeter + aBareTubePerMeter;
    double etaO = 1.0 - (aFinPerMeter / aTotal) * (1.0 - finEfficiency);

    // Air-side HTC referenced to bare tube area
    double aBareTubePerMeterTotal = Math.PI * tubeOuterDiameter;
    airSideHTC = hAir * etaO * aTotal / aBareTubePerMeterTotal;
  }

  /**
   * Calculate overall U, required area, and bundle sizing.
   *
   * @param duty heat duty [W]
   */
  private void calcThermalDesign(double duty) {
    if (lmtd <= 0 || duty <= 0) {
      return;
    }

    // Air-side HTC
    calcAirSideHTC();

    // Overall U (bare tube basis): 1/U = 1/h_air + R_air + R_process + 1/h_process
    double resistance = 1.0 / Math.max(airSideHTC, 1.0) + airFoulingResistance
        + processFoulingResistance + 1.0 / Math.max(processSideHTC, 1.0);
    overallU = 1.0 / resistance;

    // Required area
    requiredArea = duty / (overallU * lmtd);

    // Bundle geometry
    tubesPerRow = (int) Math.ceil(bayWidth / transversePitch);
    if (tubesPerRow < 1) {
      tubesPerRow = 1;
    }
    totalTubes = tubesPerRow * numberOfTubeRows * numberOfBays;

    faceArea = bayWidth * tubeLength * numberOfBays;
    bareTubeArea = Math.PI * tubeOuterDiameter * tubeLength * totalTubes;

    double finOD = tubeOuterDiameter + 2.0 * finHeight;
    double nFinsPerMeter = 1.0 / finPitch;
    double aFinPerTube =
        2.0 * Math.PI / 4.0 * (finOD * finOD - tubeOuterDiameter * tubeOuterDiameter)
            * nFinsPerMeter * tubeLength;
    totalFinArea = aFinPerTube * totalTubes;

    if (faceArea > 0 && airVolumeFlow > 0) {
      faceVelocity = airVolumeFlow / faceArea;
    }
  }

  /**
   * Calculate air-side pressure drop using Robinson-Briggs correlation.
   */
  private void calcAirSidePressureDrop() {
    double tMean = (airInletTemperature + airOutletTemperature) / 2.0;
    double rhoAir = atmosphericPressure / (287.05 * tMean);
    double muAir = 1.458e-6 * Math.pow(tMean, 1.5) / (tMean + 110.4);

    double finOD = tubeOuterDiameter + 2.0 * finHeight;
    double finSpacing = finPitch - finThickness;
    if (finSpacing <= 0) {
      airSidePressureDrop = 100.0 * numberOfTubeRows;
      return;
    }

    double aMin = transversePitch - tubeOuterDiameter - 2.0 * finHeight * finThickness / finPitch;
    if (aMin <= 0) {
      aMin = transversePitch * 0.3;
    }
    double sigmaRatio = aMin / transversePitch;

    double vMax = (faceArea > 0 && airVolumeFlow > 0) ? airVolumeFlow / (faceArea * sigmaRatio)
        : 4.0 / sigmaRatio;
    double gMax = rhoAir * vMax;

    double Re = gMax * tubeOuterDiameter / muAir;
    if (Re < 1.0) {
      Re = 1000.0;
    }

    // Robinson-Briggs: f = 18.93 * Re^(-0.316) * (Pt/do)^(-0.927) * (Pt/finOD)^0.515
    double f = 18.93 * Math.pow(Re, -0.316)
        * Math.pow(transversePitch / tubeOuterDiameter, -0.927)
        * Math.pow(transversePitch / finOD, 0.515);

    airSidePressureDrop = f * numberOfTubeRows * gMax * gMax / (2.0 * rhoAir);
  }

  /**
   * Calculate fan power.
   */
  private void calcFanPower() {
    int totalFans = numberOfBays * numberOfFansPerBay;
    if (totalFans <= 0 || airVolumeFlow <= 0) {
      fanPower = 0.0;
      return;
    }

    double volumePerFan = airVolumeFlow / totalFans;

    if (fanCurveActive) {
      double dP = getFanStaticPressure(volumePerFan);
      dP = Math.max(dP, airSidePressureDrop);
      fanPower = dP * volumePerFan / Math.max(fanEfficiency, 0.1) * totalFans;
    } else {
      double dP = Math.max(airSidePressureDrop, 100.0);
      fanPower = dP * volumePerFan / Math.max(fanEfficiency, 0.1) * totalFans;
    }
  }

  /**
   * Calculate ambient temperature correction factor. Ratio of available duty at actual ambient vs
   * design ambient, based on the mean-temperature-difference method.
   */
  private void calcAmbientCorrection() {
    if (getInletStream() == null) {
      ambientCorrectionFactor = 1.0;
      return;
    }
    double tProcessIn = getInletStream().getTemperature();
    double designITD = tProcessIn - designAmbientTemperature;
    double actualITD = tProcessIn - airInletTemperature;
    if (designITD > 0 && actualITD > 0) {
      ambientCorrectionFactor = actualITD / designITD;
    } else {
      ambientCorrectionFactor = 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);
    double duty = -getDuty();
    if (duty <= 0) {
      return;
    }

    // 1. Air flow from energy balance
    calcAirFlow(duty);

    // 2. Ambient correction
    calcAmbientCorrection();

    // 3. LMTD
    double tProcessIn = 0.0;
    double tProcessOut = 0.0;
    if (getInletStream() != null) {
      tProcessIn = getInletStream().getTemperature();
    }
    if (getOutletStream() != null) {
      tProcessOut = getOutletStream().getTemperature();
    }
    calcLMTD(tProcessIn, tProcessOut);

    // 4. Process-side HTC estimate from fluid properties
    estimateProcessSideHTC();

    // 5. Thermal design (U, area, bundle)
    calcThermalDesign(duty);

    // 6. Air-side pressure drop
    calcAirSidePressureDrop();

    // 7. Fan power
    calcFanPower();
  }

  /**
   * Estimate process-side HTC from fluid properties if not explicitly set.
   */
  private void estimateProcessSideHTC() {
    if (getOutletStream() == null) {
      return;
    }
    try {
      SystemInterface fluid = getOutletStream().getFluid();
      if (fluid != null) {
        fluid.initProperties();
        if (fluid.hasPhaseType("gas") && !fluid.hasPhaseType("oil")
            && !fluid.hasPhaseType("aqueous")) {
          processSideHTC = 120.0;
        } else if (fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous")) {
          processSideHTC = 500.0;
        }
      }
    } catch (Exception ex) {
      logger.debug("Could not estimate process HTC: " + ex.getMessage());
    }
  }

  /**
   * Generate a comprehensive JSON report of the air cooler design.
   *
   * @return JSON string
   */
  @Override
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("equipmentName", getName());
    root.addProperty("equipmentType", "AirCooler");

    // Operating conditions
    JsonObject operating = new JsonObject();
    if (getInletStream() != null) {
      operating.addProperty("processInletTemperature_C",
          getInletStream().getTemperature() - 273.15);
    }
    if (getOutletStream() != null) {
      operating.addProperty("processOutletTemperature_C",
          getOutletStream().getTemperature() - 273.15);
    }
    operating.addProperty("duty_kW", -getDuty() / 1000.0);
    operating.addProperty("airInletTemperature_C", airInletTemperature - 273.15);
    operating.addProperty("airOutletTemperature_C", airOutletTemperature - 273.15);
    operating.addProperty("relativeHumidity", relativeHumidity);
    operating.addProperty("ITD_K", itd);
    root.add("operatingConditions", operating);

    // Air-side results
    JsonObject airSide = new JsonObject();
    airSide.addProperty("airMassFlow_kg_s", airMassFlow);
    airSide.addProperty("airVolumeFlow_m3_s", airVolumeFlow);
    airSide.addProperty("airSidePressureDrop_Pa", airSidePressureDrop);
    airSide.addProperty("faceVelocity_m_s", faceVelocity);
    root.add("airSide", airSide);

    // Thermal design
    JsonObject thermal = new JsonObject();
    thermal.addProperty("LMTD_K", lmtd);
    thermal.addProperty("overallU_W_m2K", overallU);
    thermal.addProperty("requiredArea_m2", requiredArea);
    thermal.addProperty("airSideHTC_W_m2K", airSideHTC);
    thermal.addProperty("processSideHTC_W_m2K", processSideHTC);
    thermal.addProperty("finEfficiency", finEfficiency);
    thermal.addProperty("ambientCorrectionFactor", ambientCorrectionFactor);
    root.add("thermalDesign", thermal);

    // Bundle geometry
    JsonObject bundle = new JsonObject();
    bundle.addProperty("numberOfBays", numberOfBays);
    bundle.addProperty("numberOfTubeRows", numberOfTubeRows);
    bundle.addProperty("numberOfTubePasses", numberOfTubePasses);
    bundle.addProperty("tubesPerRow", tubesPerRow);
    bundle.addProperty("totalTubes", totalTubes);
    bundle.addProperty("tubeOD_mm", tubeOuterDiameter * 1000.0);
    bundle.addProperty("finHeight_mm", finHeight * 1000.0);
    bundle.addProperty("finPitch_mm", finPitch * 1000.0);
    bundle.addProperty("finThickness_mm", finThickness * 1000.0);
    bundle.addProperty("tubeLength_m", tubeLength);
    bundle.addProperty("bayWidth_m", bayWidth);
    bundle.addProperty("faceArea_m2", faceArea);
    bundle.addProperty("bareTubeArea_m2", bareTubeArea);
    bundle.addProperty("totalFinArea_m2", totalFinArea);
    root.add("bundleGeometry", bundle);

    // Fan data
    JsonObject fan = new JsonObject();
    fan.addProperty("numberOfFans", numberOfBays * numberOfFansPerBay);
    fan.addProperty("fanDiameter_m", fanDiameter);
    fan.addProperty("fanEfficiency", fanEfficiency);
    fan.addProperty("fanCurveActive", fanCurveActive);
    fan.addProperty("totalFanPower_kW", fanPower / 1000.0);
    root.add("fanData", fan);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }
}
