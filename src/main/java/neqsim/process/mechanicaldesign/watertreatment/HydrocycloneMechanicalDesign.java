package neqsim.process.mechanicaldesign.watertreatment;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.watertreatment.Hydrocyclone;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesignResponse;

/**
 * Mechanical design for de-oiling hydrocyclone packages.
 *
 * <p>
 * Provides pressure vessel sizing, wall thickness calculation, nozzle sizing, weight estimation,
 * and cost estimation for multi-liner hydrocyclone packages used in produced water treatment.
 * </p>
 *
 * <h2>Design Standards</h2>
 * <table>
 * <caption>Applicable standards for hydrocyclone mechanical design</caption>
 * <tr><th>Standard</th><th>Scope</th></tr>
 * <tr><td>ASME VIII Div 1</td><td>Pressure vessel shell and head design</td></tr>
 * <tr><td>NORSOK P-001</td><td>Process design requirements</td></tr>
 * <tr><td>NORSOK M-001</td><td>Material selection</td></tr>
 * <tr><td>API RP 14E</td><td>Nozzle velocity limits</td></tr>
 * </table>
 *
 * <h2>Vessel Sizing Approach</h2>
 *
 * <p>
 * A hydrocyclone package consists of one or more pressure vessels, each housing multiple liner
 * inserts. The vessel inner diameter is sized to accommodate the liners in a circular arrangement.
 * Key design equations:
 * </p>
 *
 * <pre>
 * Wall thickness (ASME VIII Div 1, UG-27):
 *   t = (P * R) / (S * E - 0.6 * P) + CA
 *
 * Vessel ID from liner packing:
 *   D_vessel = f(liner_diameter, liners_per_vessel)
 *
 * Nozzle velocity (API RP 14E):
 *   V_nozzle = Q / A_nozzle  &lt; V_erosional
 * </pre>
 *
 * @author esol
 * @version 1.0
 */
public class HydrocycloneMechanicalDesign extends SeparatorMechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ---------------------------------------------------------------------------
  // DESIGN PARAMETERS
  // ---------------------------------------------------------------------------

  /** Design pressure margin factor (e.g. 1.10 for 10% above max operating). */
  private double designPressureMarginFactor = 1.10;

  /** Design temperature margin above max operating (Celsius). */
  private double designTemperatureMarginC = 25.0;

  /** Minimum design temperature (Celsius), material-driven. */
  private double minDesignTemperatureC = -46.0;

  /** Corrosion allowance for produced water service (mm). */
  private double corrosionAllowanceMm = 3.0;

  /** Joint efficiency for welded vessel (1.0 = full radiography). */
  private double vesselJointEfficiency = 1.0;

  /** Material allowable stress at design temperature (MPa). SA-316L typical. */
  private double allowableStressMPa = 115.0;

  /** Material grade designation. */
  private String materialGrade = "SA-316L";

  /** Design code reference. */
  private String designCode = "ASME VIII Div 1";

  // ---------------------------------------------------------------------------
  // NOZZLE SIZING
  // ---------------------------------------------------------------------------

  /** Maximum inlet nozzle velocity (m/s). API RP 14E erosional limit. */
  private double maxInletVelocity = 3.0;

  /** Maximum overflow (clean water) nozzle velocity (m/s). */
  private double maxOverflowVelocity = 3.0;

  /** Maximum reject (oil-rich) nozzle velocity (m/s). */
  private double maxRejectVelocity = 5.0;

  // ---------------------------------------------------------------------------
  // CALCULATED RESULTS
  // ---------------------------------------------------------------------------

  /** Vessel inner diameter (m) per vessel. */
  private double vesselInnerDiameterM = 0.0;

  /** Vessel wall thickness (mm), calculated. */
  private double vesselWallThicknessMm = 0.0;

  /** Vessel tangent-to-tangent length (m). */
  private double vesselLengthM = 0.0;

  /** Number of vessels. */
  private int numberOfVessels = 1;

  /** Number of active liners per vessel. */
  private int linersPerVessel = 6;

  /** Liner diameter (mm). */
  private double linerDiameterMm = 35.0;

  /** Total number of active liners. */
  private int totalActiveLiners = 1;

  /** Number of spare liners. */
  private int spareLiners = 0;

  /** Head type for vessel ends. */
  private String headType = "2:1 ellipsoidal";

  /** Head thickness (mm). */
  private double headThicknessMm = 0.0;

  /** Inlet nozzle ID (mm). */
  private double inletNozzleIdMm = 0.0;

  /** Overflow (clean water) nozzle ID (mm). */
  private double overflowNozzleIdMm = 0.0;

  /** Reject (oil-rich) nozzle ID (mm). */
  private double rejectNozzleIdMm = 0.0;

  /** Empty vessel weight per vessel (kg). */
  private double emptyVesselWeightKg = 0.0;

  /** Total liner insert weight per vessel (kg). */
  private double linerWeightPerVesselKg = 0.0;

  /** Design pressure (barg). */
  private double designPressureBarg = 0.0;

  /** Design temperature high (Celsius). */
  private double designTemperatureHighC = 0.0;

  /** Design temperature low (Celsius). */
  private double designTemperatureLowC = 0.0;

  /** Operating pressure (barg). */
  private double operatingPressureBarg = 0.0;

  /** Operating temperature (Celsius). */
  private double operatingTemperatureC = 0.0;

  // ---------------------------------------------------------------------------
  // CONSTRUCTOR
  // ---------------------------------------------------------------------------

  /**
   * Creates a hydrocyclone mechanical design for the given equipment.
   *
   * @param equipment the hydrocyclone process equipment
   */
  public HydrocycloneMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  // ---------------------------------------------------------------------------
  // DESIGN CALCULATION
  // ---------------------------------------------------------------------------

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Hydrocyclone hc = (Hydrocyclone) getProcessEquipment();

    // --- Transfer from process equipment ---
    linerDiameterMm = hc.getLinerDiameterMm();
    totalActiveLiners = hc.getNumberOfLiners();
    spareLiners = hc.getNumberOfSpareLiners();
    linersPerVessel = hc.getLinersPerVessel();
    numberOfVessels = hc.getNumberOfVessels();

    // --- Operating conditions ---
    double operatingPressureBara = hc.getLiquidOutStream().getPressure();
    operatingPressureBarg = operatingPressureBara - 1.01325;
    operatingTemperatureC = hc.getLiquidOutStream().getTemperature() - 273.15;

    // --- Design conditions ---
    designPressureBarg = operatingPressureBarg * designPressureMarginFactor;
    designTemperatureHighC = operatingTemperatureC + designTemperatureMarginC;
    designTemperatureLowC = minDesignTemperatureC;

    setMaxOperationPressure(operatingPressureBara);
    setMaxOperationTemperature(hc.getLiquidOutStream().getTemperature());

    // --- Vessel sizing ---
    calcVesselDimensions();

    // --- Wall thickness (ASME VIII Div 1, UG-27 circumferential stress) ---
    calcWallThickness();

    // --- Nozzle sizing ---
    calcNozzleSizes(hc);

    // --- Weight estimation ---
    calcWeights(hc);

    // --- Populate base class fields ---
    populateBaseFields();
  }

  /**
   * Calculates vessel inner diameter and length based on liner packing.
   *
   * <p>
   * The vessel must accommodate the liners plus header/manifold space. The diameter is estimated
   * from the number of liners per vessel packed in a circular cross-section. The vessel length
   * depends on the liner effective length plus inlet/outlet header space.
   * </p>
   */
  private void calcVesselDimensions() {
    int totalLinersInVessel = linersPerVessel + (spareLiners / Math.max(1, numberOfVessels));

    // Each liner has an outer tube diameter approximately 2x the cone diameter
    double linerOuterDiameterM = (linerDiameterMm * 2.0) / 1000.0;

    // Packing factor: for circular arrangement, vessel ID ~ linerOD * sqrt(n) * 1.3
    // This accounts for the circular packing geometry and annular clearance
    vesselInnerDiameterM =
        linerOuterDiameterM * Math.sqrt(totalLinersInVessel) * 1.3;
    // Minimum vessel ID for access (man-way requirement)
    if (vesselInnerDiameterM < 0.4) {
      vesselInnerDiameterM = 0.4;
    }

    // Vessel length: liner length + inlet header + outlet header
    double linerEffectiveLengthM = 0.50 * (linerDiameterMm / 35.0);
    double headerSpaceM = 0.4; // inlet + outlet manifold headers
    vesselLengthM = linerEffectiveLengthM + headerSpaceM;
    if (vesselLengthM < 1.0) {
      vesselLengthM = 1.0;
    }
  }

  /**
   * Calculates required wall thickness per ASME VIII Div 1 UG-27.
   *
   * <p>
   * For cylindrical shells under internal pressure:
   * </p>
   *
   * <pre>
   *   t = (P * R) / (S * E - 0.6 * P) + CA
   * </pre>
   *
   * <p>
   * where P = design pressure (MPa), R = inside radius (mm), S = allowable stress (MPa), E =
   * joint efficiency, CA = corrosion allowance (mm).
   * </p>
   */
  private void calcWallThickness() {
    double designPressureMPa = designPressureBarg * 0.1; // barg -> MPa
    double insideRadiusMm = vesselInnerDiameterM * 1000.0 / 2.0;

    double tRequired =
        (designPressureMPa * insideRadiusMm)
            / (allowableStressMPa * vesselJointEfficiency - 0.6 * designPressureMPa)
            + corrosionAllowanceMm;

    // Round up to nearest 0.5 mm
    vesselWallThicknessMm = Math.ceil(tRequired * 2.0) / 2.0;

    // Minimum wall thickness 3 mm
    if (vesselWallThicknessMm < 3.0) {
      vesselWallThicknessMm = 3.0;
    }

    // Head thickness (2:1 ellipsoidal per ASME UG-32)
    headThicknessMm =
        (designPressureMPa * insideRadiusMm * 2.0)
            / (2.0 * allowableStressMPa * vesselJointEfficiency
                - 0.2 * designPressureMPa)
            + corrosionAllowanceMm;
    headThicknessMm = Math.ceil(headThicknessMm * 2.0) / 2.0;
    if (headThicknessMm < vesselWallThicknessMm) {
      headThicknessMm = vesselWallThicknessMm;
    }
  }

  /**
   * Calculates nozzle internal diameters from flow rates and velocity limits.
   *
   * @param hc the hydrocyclone equipment
   */
  private void calcNozzleSizes(Hydrocyclone hc) {
    // Feed flow per vessel (m3/s)
    double feedFlowM3h = hc.getFeedFlowM3h();
    double feedPerVesselM3s = feedFlowM3h / Math.max(1, numberOfVessels) / 3600.0;

    // Inlet nozzle: full feed flow
    double areaInlet = feedPerVesselM3s / maxInletVelocity;
    inletNozzleIdMm = Math.sqrt(4.0 * areaInlet / Math.PI) * 1000.0;
    inletNozzleIdMm = roundUpToStandardNozzle(inletNozzleIdMm);

    // Overflow nozzle: (1 - rejectRatio) * feed
    double rejectRatio = hc.getRejectRatio();
    double overflowM3s = feedPerVesselM3s * (1.0 - rejectRatio);
    double areaOverflow = overflowM3s / maxOverflowVelocity;
    overflowNozzleIdMm = Math.sqrt(4.0 * areaOverflow / Math.PI) * 1000.0;
    overflowNozzleIdMm = roundUpToStandardNozzle(overflowNozzleIdMm);

    // Reject nozzle: rejectRatio * feed
    double rejectM3s = feedPerVesselM3s * rejectRatio;
    double areaReject = rejectM3s / maxRejectVelocity;
    rejectNozzleIdMm = Math.sqrt(4.0 * areaReject / Math.PI) * 1000.0;
    rejectNozzleIdMm = roundUpToStandardNozzle(rejectNozzleIdMm);

    // Minimum nozzle size 25 mm (1 inch)
    if (rejectNozzleIdMm < 25.0) {
      rejectNozzleIdMm = 25.0;
    }
  }

  /**
   * Rounds up a nozzle ID to the nearest standard pipe size.
   *
   * @param idMm calculated nozzle ID in mm
   * @return next standard pipe size in mm
   */
  private double roundUpToStandardNozzle(double idMm) {
    // Standard pipe NPS sizes in mm (approximate ID)
    double[] standardSizes =
        {25.0, 40.0, 50.0, 80.0, 100.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0};
    for (double size : standardSizes) {
      if (size >= idMm) {
        return size;
      }
    }
    return standardSizes[standardSizes.length - 1];
  }

  /**
   * Calculates vessel and package weights.
   *
   * @param hc the hydrocyclone equipment
   */
  private void calcWeights(Hydrocyclone hc) {
    // Shell weight: pi * D * L * t * density_steel
    double steelDensity = 7850.0; // kg/m3 for stainless steel
    double shellThicknessM = vesselWallThicknessMm / 1000.0;
    double shellWeight =
        Math.PI * vesselInnerDiameterM * vesselLengthM * shellThicknessM * steelDensity;

    // Head weight (two 2:1 ellipsoidal heads)
    // Approximate: head weight ~ 0.5 * shell diameter * head thickness * density
    double headThicknessM = headThicknessMm / 1000.0;
    double headWeight = 2.0 * 0.9 * Math.PI * Math.pow(vesselInnerDiameterM / 2.0, 2.0)
        * headThicknessM * steelDensity;

    emptyVesselWeightKg = shellWeight + headWeight;

    // Liner inserts: each liner is approximately 3 kg per 35mm, scales with diameter
    double linerWeightKg = 3.0 * Math.pow(linerDiameterMm / 35.0, 2.0);
    int totalLinersInVessel = linersPerVessel + (spareLiners / Math.max(1, numberOfVessels));
    linerWeightPerVesselKg = linerWeightKg * totalLinersInVessel;

    // Nozzle weight (approximate: 15% of vessel weight)
    double nozzleWeight = emptyVesselWeightKg * 0.15;

    // Piping weight (approximate: 40% of vessel weight)
    double pipingWeight = emptyVesselWeightKg * 0.40;

    // Structural support + skid
    double structuralWeight = (emptyVesselWeightKg + linerWeightPerVesselKg) * 0.10;

    // E&I weight
    double eiWeight = (emptyVesselWeightKg + linerWeightPerVesselKg) * 0.08;

    // Per-vessel totals
    double perVesselTotal = emptyVesselWeightKg + linerWeightPerVesselKg
        + nozzleWeight + pipingWeight + structuralWeight + eiWeight;

    // Total package weight (all vessels)
    double totalWeight = perVesselTotal * numberOfVessels;

    // Store in base class fields
    setWeigthVesselShell(emptyVesselWeightKg * numberOfVessels);
    setWeigthInternals(linerWeightPerVesselKg * numberOfVessels);
    setWeightNozzle(nozzleWeight * numberOfVessels);
    setWeightPiping(pipingWeight * numberOfVessels);
    setWeightStructualSteel(structuralWeight * numberOfVessels);
    setWeightElectroInstrument(eiWeight * numberOfVessels);
    setWeightTotal(totalWeight);

    // Module dimensions (footprint for all vessels side by side)
    double vesselOuterDiameterM = vesselInnerDiameterM + 2.0 * shellThicknessM;
    setModuleWidth(vesselOuterDiameterM * numberOfVessels + 0.5 * (numberOfVessels - 1));
    setModuleLength(vesselLengthM + 1.0); // vessel + piping space
    setModuleHeight(vesselOuterDiameterM + 1.0); // vessel + supports + access
  }

  /**
   * Populates base class fields from calculated results.
   */
  private void populateBaseFields() {
    innerDiameter = vesselInnerDiameterM;
    outerDiameter = vesselInnerDiameterM + 2.0 * vesselWallThicknessMm / 1000.0;
    wallThickness = vesselWallThicknessMm / 1000.0; // base class uses meters
    tantanLength = vesselLengthM;
    setCorrosionAllowance(corrosionAllowanceMm);
    setJointEfficiency(vesselJointEfficiency);
    setConstrutionMaterial(materialGrade);
    setTensileStrength(allowableStressMPa * 3.5); // reverse from allowable
  }

  // ---------------------------------------------------------------------------
  // JSON REPORTING
  // ---------------------------------------------------------------------------

  /** {@inheritDoc} */
  @Override
  public SeparatorMechanicalDesignResponse getResponse() {
    SeparatorMechanicalDesignResponse response = super.getResponse();
    response.setEquipmentType("Hydrocyclone");
    response.setDesignStandard(designCode);

    // Add hydrocyclone-specific parameters
    response.addSpecificParameter("designPressureBarg", designPressureBarg);
    response.addSpecificParameter("designTemperatureHighC", designTemperatureHighC);
    response.addSpecificParameter("designTemperatureLowC", designTemperatureLowC);
    response.addSpecificParameter("operatingPressureBarg", operatingPressureBarg);
    response.addSpecificParameter("operatingTemperatureC", operatingTemperatureC);
    response.addSpecificParameter("materialGrade", materialGrade);
    response.addSpecificParameter("designCode", designCode);
    response.addSpecificParameter("headType", headType);
    response.addSpecificParameter("numberOfVessels", numberOfVessels);
    response.addSpecificParameter("linersPerVessel", linersPerVessel);
    response.addSpecificParameter("totalActiveLiners", totalActiveLiners);
    response.addSpecificParameter("spareLiners", spareLiners);
    response.addSpecificParameter("linerDiameterMm", linerDiameterMm);
    response.addSpecificParameter("vesselInnerDiameterMm",
        vesselInnerDiameterM * 1000.0);
    response.addSpecificParameter("vesselWallThicknessMm", vesselWallThicknessMm);
    response.addSpecificParameter("headThicknessMm", headThicknessMm);
    response.addSpecificParameter("vesselLengthM", vesselLengthM);
    response.addSpecificParameter("inletNozzleIdMm", inletNozzleIdMm);
    response.addSpecificParameter("overflowNozzleIdMm", overflowNozzleIdMm);
    response.addSpecificParameter("rejectNozzleIdMm", rejectNozzleIdMm);
    response.addSpecificParameter("emptyVesselWeightKg", emptyVesselWeightKg);
    response.addSpecificParameter("linerWeightPerVesselKg", linerWeightPerVesselKg);
    response.addSpecificParameter("corrosionAllowanceMm", corrosionAllowanceMm);
    response.addSpecificParameter("jointEfficiency", vesselJointEfficiency);
    response.addSpecificParameter("allowableStressMPa", allowableStressMPa);

    return response;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return getResponse().toJson();
  }

  // ---------------------------------------------------------------------------
  // GETTERS AND SETTERS
  // ---------------------------------------------------------------------------

  /**
   * Gets the vessel inner diameter.
   *
   * @return diameter in meters
   */
  public double getVesselInnerDiameterM() {
    return vesselInnerDiameterM;
  }

  /**
   * Gets the vessel wall thickness.
   *
   * @return thickness in mm
   */
  public double getVesselWallThicknessMm() {
    return vesselWallThicknessMm;
  }

  /**
   * Gets the vessel tangent-to-tangent length.
   *
   * @return length in meters
   */
  public double getVesselLengthM() {
    return vesselLengthM;
  }

  /**
   * Gets the number of vessels.
   *
   * @return number of pressure housings
   */
  public int getNumberOfVessels() {
    return numberOfVessels;
  }

  /**
   * Gets the inlet nozzle internal diameter.
   *
   * @return nozzle ID in mm
   */
  public double getInletNozzleIdMm() {
    return inletNozzleIdMm;
  }

  /**
   * Gets the overflow nozzle internal diameter.
   *
   * @return nozzle ID in mm
   */
  public double getOverflowNozzleIdMm() {
    return overflowNozzleIdMm;
  }

  /**
   * Gets the reject nozzle internal diameter.
   *
   * @return nozzle ID in mm
   */
  public double getRejectNozzleIdMm() {
    return rejectNozzleIdMm;
  }

  /**
   * Gets the empty vessel weight (per vessel).
   *
   * @return weight in kg
   */
  public double getEmptyVesselWeightKg() {
    return emptyVesselWeightKg;
  }

  /**
   * Gets the total liner weight per vessel.
   *
   * @return weight in kg
   */
  public double getLinerWeightPerVesselKg() {
    return linerWeightPerVesselKg;
  }

  /**
   * Gets the design pressure.
   *
   * @return pressure in barg
   */
  public double getDesignPressureBarg() {
    return designPressureBarg;
  }

  /**
   * Gets the high design temperature.
   *
   * @return temperature in Celsius
   */
  public double getDesignTemperatureHighC() {
    return designTemperatureHighC;
  }

  /**
   * Gets the low design temperature.
   *
   * @return temperature in Celsius
   */
  public double getDesignTemperatureLowC() {
    return designTemperatureLowC;
  }

  /**
   * Gets the head thickness.
   *
   * @return thickness in mm
   */
  public double getHeadThicknessMm() {
    return headThicknessMm;
  }

  /**
   * Sets the material grade and updates allowable stress.
   *
   * @param grade material designation (e.g. "SA-316L", "SA-316", "22Cr Duplex")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    // Update allowable stress for common grades
    if ("SA-316L".equals(grade) || "SA-316".equals(grade)) {
      allowableStressMPa = 115.0;
    } else if ("22Cr Duplex".equals(grade) || "SA-790".equals(grade)) {
      allowableStressMPa = 207.0;
    } else if ("SA-516-70".equals(grade)) {
      allowableStressMPa = 138.0;
    }
  }

  /**
   * Gets the material grade.
   *
   * @return material designation string
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Sets the corrosion allowance.
   *
   * @param caMm corrosion allowance in mm
   */
  public void setCorrosionAllowanceMm(double caMm) {
    this.corrosionAllowanceMm = caMm;
  }

  /**
   * Gets the corrosion allowance.
   *
   * @return corrosion allowance in mm
   */
  public double getCorrosionAllowanceMm() {
    return corrosionAllowanceMm;
  }

  /**
   * Sets the design pressure margin factor.
   *
   * @param factor margin factor (e.g. 1.10 for 10%)
   */
  public void setDesignPressureMarginFactor(double factor) {
    this.designPressureMarginFactor = factor;
  }

  /**
   * Sets the allowable stress at design temperature.
   *
   * @param stressMPa allowable stress in MPa
   */
  public void setAllowableStressMPa(double stressMPa) {
    this.allowableStressMPa = stressMPa;
  }

  /**
   * Gets the allowable stress.
   *
   * @return stress in MPa
   */
  public double getAllowableStressMPa() {
    return allowableStressMPa;
  }

  /**
   * Gets a summary map of the hydrocyclone mechanical design results.
   *
   * @return map of design parameter names to values
   */
  public Map<String, Object> getHydrocycloneDesignSummary() {
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("designCode", designCode);
    summary.put("materialGrade", materialGrade);
    summary.put("designPressureBarg", designPressureBarg);
    summary.put("designTemperatureHighC", designTemperatureHighC);
    summary.put("designTemperatureLowC", designTemperatureLowC);
    summary.put("numberOfVessels", numberOfVessels);
    summary.put("linersPerVessel", linersPerVessel);
    summary.put("totalActiveLiners", totalActiveLiners);
    summary.put("spareLiners", spareLiners);
    summary.put("vesselInnerDiameterMm", vesselInnerDiameterM * 1000.0);
    summary.put("vesselWallThicknessMm", vesselWallThicknessMm);
    summary.put("headThicknessMm", headThicknessMm);
    summary.put("vesselLengthM", vesselLengthM);
    summary.put("inletNozzleIdMm", inletNozzleIdMm);
    summary.put("overflowNozzleIdMm", overflowNozzleIdMm);
    summary.put("rejectNozzleIdMm", rejectNozzleIdMm);
    summary.put("emptyVesselWeightKg", emptyVesselWeightKg);
    summary.put("totalPackageWeightKg", getWeightTotal());
    return summary;
  }
}
