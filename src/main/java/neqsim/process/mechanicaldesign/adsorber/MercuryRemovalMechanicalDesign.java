package neqsim.process.mechanicaldesign.adsorber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.costestimation.adsorber.MercuryRemovalCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.adsorber.MercuryRemovalBed;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design class for mercury removal guard beds.
 *
 * <p>
 * Sizes the pressure vessel (shell, heads, nozzles, internals) for a fixed-bed mercury
 * chemisorption unit based on ASME Section VIII Division 1 or equivalent codes. Includes weight
 * breakdown, footprint estimation, and cost estimation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MercuryRemovalMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Wall thickness in mm. */
  private double wallThickness = 0.0;

  /** Outer diameter in m. */
  private double outerDiameter = 0.0;

  /** Material grade for the pressure vessel. */
  private String materialGrade = "SA-516-70";

  /** Design standard code. */
  private String designStandardCode = "ASME-VIII-Div1";

  /** Weight of the sorbent charge (kg). */
  private double sorbentChargeWeight = 0.0;

  /** Weight of internal support grids, screens, and distribution plates (kg). */
  private double internalsWeight = 0.0;

  /**
   * Constructor for MercuryRemovalMechanicalDesign.
   *
   * @param equipment the process equipment (MercuryRemovalBed)
   */
  public MercuryRemovalMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Note: we do NOT call super.readDesignSpecifications() or
    // PressureVesselDesignStandard.calcWallThickness() because that
    // implementation hard-casts to Separator. Wall thickness is calculated
    // in calcDesign() using a hoop-stress formula suitable for any vessel.
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    MercuryRemovalBed bed = (MercuryRemovalBed) getProcessEquipment();

    double bedDiameter = bed.getBedDiameter();
    double bedLength = bed.getBedLength();

    // Vessel geometry: tan-tan length includes bed + inlet/outlet domes
    double domeHeight = bedDiameter * 0.5;
    tantanLength = bedLength + 2.0 * domeHeight;
    innerDiameter = bedDiameter;

    // ---- Wall thickness fallback if no design standard set ----
    if (wallThickness <= 0) {
      // Simple Barlow/hoop-stress estimate: t = P*D / (2*S*E - P)
      double designPressure = getMaxOperationPressure() * 1.1; // bara
      double designPressureMPa = designPressure * 0.1;
      double allowableStress = 137.9; // MPa for SA-516-70 at moderate temperature
      double jointEfficiency = 0.85;
      wallThickness = (designPressureMPa * bedDiameter * 1000.0)
          / (2.0 * allowableStress * jointEfficiency - designPressureMPa);
      wallThickness = Math.max(wallThickness, 6.0); // minimum 6 mm
    }

    // ---- Weights ----
    // Empty vessel shell (cylindrical + heads), empirical formula
    double emptyVesselWeight = 0.032 * wallThickness * innerDiameter * 1e3 * tantanLength;

    // Sorbent charge weight
    sorbentChargeWeight = bed.getSorbentMass();

    // Internal support grids, distribution plates, hold-down screens
    // Rough estimate: ~5% of sorbent weight + fixed per-tray contribution
    internalsWeight = sorbentChargeWeight * 0.05 + 150.0;

    double externalNozzlesWeight = emptyVesselWeight * 0.05;
    double pipingWeight = emptyVesselWeight * 0.40;
    double structuralWeight = emptyVesselWeight * 0.10;
    double electricalWeight = emptyVesselWeight * 0.08;

    double dryWeight = emptyVesselWeight + internalsWeight + externalNozzlesWeight;
    double totalSkidWeight = dryWeight + pipingWeight + structuralWeight + electricalWeight;


    // Set results on base class
    setOuterDiameter(innerDiameter + 2.0 * wallThickness / 1000.0);
    setInnerDiameter(innerDiameter);
    setWallThickness(wallThickness);

    setWeigthVesselShell(emptyVesselWeight);
    setWeigthInternals(internalsWeight);
    setWeightNozzle(externalNozzlesWeight);
    setWeightPiping(pipingWeight);
    setWeightStructualSteel(structuralWeight);
    setWeightElectroInstrument(electricalWeight);
    setWeightTotal(totalSkidWeight);

    // Footprint
    double moduleWidth = innerDiameter * 2.0;
    double moduleLength = innerDiameter * 2.5;
    double moduleHeight = tantanLength + 1.0;

    setModuleWidth(moduleWidth);
    setModuleLength(moduleLength);
    setModuleHeight(moduleHeight);
  }

  /**
   * Generate a bill of materials for the mercury removal unit.
   *
   * @return list of BOM line items
   */
  @Override
  public List<Map<String, Object>> generateBillOfMaterials() {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    // Vessel shell
    Map<String, Object> shell = new LinkedHashMap<String, Object>();
    shell.put("item", "Pressure Vessel Shell");
    shell.put("material", materialGrade);
    shell.put("weight_kg", getWeigthVesselShell());
    bom.add(shell);

    // Sorbent charge
    Map<String, Object> sorbent = new LinkedHashMap<String, Object>();
    sorbent.put("item",
        "Sorbent Charge (" + ((MercuryRemovalBed) getProcessEquipment()).getSorbentType() + ")");
    sorbent.put("material", ((MercuryRemovalBed) getProcessEquipment()).getSorbentType());
    sorbent.put("weight_kg", sorbentChargeWeight);
    bom.add(sorbent);

    // Internals
    Map<String, Object> internals = new LinkedHashMap<String, Object>();
    internals.put("item", "Support Grids and Distribution Plates");
    internals.put("material", "SS316L");
    internals.put("weight_kg", internalsWeight);
    bom.add(internals);

    // Nozzles
    Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
    nozzles.put("item", "Inlet/Outlet Nozzles");
    nozzles.put("material", materialGrade);
    nozzles.put("weight_kg", getWeightNozzle());
    bom.add(nozzles);

    return bom;
  }

  /** {@inheritDoc} */
  @Override
  public MercuryRemovalCostEstimate getCostEstimate() {
    return new MercuryRemovalCostEstimate(this);
  }

  /**
   * Export a comprehensive JSON report of the mechanical design.
   *
   * @return JSON string with all design data
   */
  @Override
  public String toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("equipmentName", getProcessEquipment().getName());
    json.addProperty("equipmentType", "MercuryRemovalBed");
    json.addProperty("designStandardCode", designStandardCode);
    json.addProperty("materialGrade", materialGrade);

    // Geometry
    JsonObject geom = new JsonObject();
    geom.addProperty("innerDiameter_m", innerDiameter);
    geom.addProperty("outerDiameter_m", getOuterDiameter());
    geom.addProperty("wallThickness_mm", wallThickness);
    geom.addProperty("tanTanLength_m", tantanLength);
    json.add("geometry", geom);

    // Weights
    JsonObject weights = new JsonObject();
    weights.addProperty("emptyVesselShell_kg", getWeigthVesselShell());
    weights.addProperty("internals_kg", internalsWeight);
    weights.addProperty("sorbentCharge_kg", sorbentChargeWeight);
    weights.addProperty("nozzles_kg", getWeightNozzle());
    weights.addProperty("piping_kg", getWeightPiping());
    weights.addProperty("structural_kg", getWeightStructualSteel());
    weights.addProperty("electrical_kg", getWeightElectroInstrument());
    weights.addProperty("totalSkid_kg", getWeightTotal());
    json.add("weights", weights);

    // Footprint
    JsonObject footprint = new JsonObject();
    footprint.addProperty("width_m", getModuleWidth());
    footprint.addProperty("length_m", getModuleLength());
    footprint.addProperty("height_m", getModuleHeight());
    json.add("footprint", footprint);

    // BOM
    JsonArray bomArr = new JsonArray();
    for (Map<String, Object> item : generateBillOfMaterials()) {
      JsonObject bomItem = new JsonObject();
      for (Map.Entry<String, Object> entry : item.entrySet()) {
        if (entry.getValue() instanceof Number) {
          bomItem.addProperty(entry.getKey(), (Number) entry.getValue());
        } else {
          bomItem.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
      }
      bomArr.add(bomItem);
    }
    json.add("billOfMaterials", bomArr);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  // ======================================================================
  // Getters / Setters
  // ======================================================================

  /** {@inheritDoc} */
  @Override
  public double getWallThickness() {
    return wallThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  /** {@inheritDoc} */
  @Override
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  /**
   * Get the material grade.
   *
   * @return material grade string
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set the material grade for the pressure vessel.
   *
   * @param materialGrade ASME material grade (e.g. "SA-516-70")
   */
  public void setMaterialGrade(String materialGrade) {
    this.materialGrade = materialGrade;
  }

  /**
   * Get the design standard code.
   *
   * @return design standard code
   */
  public String getDesignStandardCode() {
    return designStandardCode;
  }

  /**
   * Set the design standard code.
   *
   * @param code design standard code (e.g. "ASME-VIII-Div1")
   */
  public void setDesignStandardCode(String code) {
    this.designStandardCode = code;
  }

  /**
   * Get the sorbent charge weight.
   *
   * @return sorbent charge weight in kg
   */
  public double getSorbentChargeWeight() {
    return sorbentChargeWeight;
  }

  /**
   * Get the internals weight.
   *
   * @return internals weight in kg
   */
  public double getInternalsWeight() {
    return internalsWeight;
  }
}
