package neqsim.process.mechanicaldesign.separator;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.separator.SeparatorCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.MaterialPlateDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PressureVesselDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;

/**
 * <p>
 * SeparatorMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /**
   * Gas load factor (K-factor) for Souders-Brown equation [m/s]. Default 0.107 for mesh demister.
   */
  double gasLoadFactor = 0.107;
  /**
   * Volume safety factor for design flow calculations. Default 1.0 (no margin).
   */
  double volumeSafetyFactor = 1.0;
  /**
   * Gas area fraction (1 - liquid level fraction). Default 0.5 for 50% liquid level.
   */
  double Fg = 0.5;
  /** Liquid retention time in seconds. Default 120s (2 minutes). */
  double retentionTime = 120.0;

  // ============================================================================
  // Process Design Parameters (loaded from TechnicalRequirements_Process)
  // ============================================================================

  /** Design pressure margin factor (e.g., 1.10 for 10% margin). */
  private double designPressureMargin = 1.10;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Minimum design temperature in Celsius (material limit). */
  private double minDesignTemperatureC = -46.0;

  /** Foam allowance factor for liquid level (1.0 = no foam, 1.5 = 50% foam). */
  private double foamAllowanceFactor = 1.0;

  /** Design droplet diameter for gas-liquid separation in micrometers. */
  private double dropletDiameterGasLiquid = 100.0;

  /** Design droplet diameter for liquid-liquid separation in micrometers. */
  private double dropletDiameterLiquidLiquid = 500.0;

  /** Maximum gas velocity limit in m/s. */
  private double maxGasVelocity = 3.0;

  /** Maximum liquid outlet velocity in m/s. */
  private double maxLiquidVelocity = 1.0;

  /** Minimum oil retention time in minutes. */
  private double minOilRetentionTime = 2.0;

  /** Minimum water retention time in minutes. */
  private double minWaterRetentionTime = 3.0;

  // ============================================================================
  // Demister/Mist Eliminator Parameters
  // ============================================================================

  /** Demister pad pressure drop in mbar. */
  private double demisterPressureDrop = 1.5;

  /** Wire mesh demister void fraction (typically 0.97-0.99). */
  private double demisterVoidFraction = 0.98;

  /** Demister pad thickness in mm. */
  private double demisterThickness = 150.0;

  /** Demister pad wire diameter in mm. */
  private double demisterWireDiameter = 0.28;

  /** Inlet device K-factor reduction (1.0 = no reduction). */
  private double inletDeviceKFactor = 0.85;

  /** Demister type: "wire_mesh", "vane_pack", "cyclone". */
  private String demisterType = "wire_mesh";

  // ============================================================================
  // Liquid level design parameters (as fraction of internal diameter for
  // horizontal separators)
  /** High-High Liquid Level (HHLL) as fraction of ID. Typically ~0.80-0.85. */
  private double hhllFraction = 0.80;
  /** High Liquid Level (HLL) as fraction of ID. Typically ~0.70-0.75. */
  private double hllFraction = 0.70;
  /** Normal Liquid Level (NLL) as fraction of ID. Typically ~0.50-0.60. */
  private double nllFraction = 0.50;
  /** Low Liquid Level (LLL) as fraction of ID. Typically ~0.30-0.40. */
  private double lllFraction = 0.30;
  /** Low-Low Liquid Level (LLLL) as fraction of ID. Typically ~0.15-0.20. */
  private double llllFraction = 0.15;

  // Three-phase separator interface levels (as fraction of ID)
  /** High Interface Level (HIL) - oil/water interface. */
  private double hilFraction = 0.25;
  /** Normal Interface Level (NIL) - oil/water interface. */
  private double nilFraction = 0.20;
  /** Low Interface Level (LIL) - oil/water interface. */
  private double lilFraction = 0.15;
  /** Weir height as fraction of ID. */
  private double weirFraction = 0.25;

  // Effective lengths (calculated based on internals)
  /** Effective length for liquid separation/retention [m]. */
  private double effectiveLengthLiquid = 0.0;
  /** Effective length for gas separation/demisting [m]. */
  private double effectiveLengthGas = 0.0;
  /** Distance from inlet to perforated plate [m]. */
  private double inletToPerforatedPlate = 0.0;
  /** Distance from perforated plate to weir plate [m]. */
  private double perforatedPlateToWeir = 0.0;
  /** Distance from inlet to gas demister [m]. */
  private double inletToGasDemister = 0.0;

  // Nozzle sizes
  /** Inlet nozzle internal diameter [m]. */
  private double inletNozzleID = 0.0;
  /** Gas outlet nozzle internal diameter [m]. */
  private double gasOutletNozzleID = 0.0;
  /** Oil outlet nozzle internal diameter [m]. */
  private double oilOutletNozzleID = 0.0;
  /** Water outlet nozzle internal diameter [m]. */
  private double waterOutletNozzleID = 0.0;

  /**
   * <p>
   * Constructor for SeparatorMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public SeparatorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new SeparatorCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();
    if (getDesignStandard().containsKey("material plate design codes")) {
      ((MaterialPlateDesignStandard) getDesignStandard().get("material plate design codes"))
          .readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "55", 1);
    } else {
      System.out.println("material plate design codes specified......");
    }
    if (getDesignStandard().containsKey("pressure vessel design code")) {
      System.out.println("pressure vessel code standard: "
          + getDesignStandard().get("pressure vessel design code").getStandardName());
      wallThickness =
          ((PressureVesselDesignStandard) getDesignStandard().get("pressure vessel design code"))
              .calcWallThickness();
    } else {
      System.out.println("no pressure vessel code standard specified......");
    }

    if (getDesignStandard().containsKey("separator process design")) {
      System.out.println("separator process design: "
          + getDesignStandard().get("separator process design").getStandardName());
      gasLoadFactor =
          ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
              .getGasLoadFactor();
      Fg = ((SeparatorDesignStandard) getDesignStandard().get("separator process design")).getFg();
      volumeSafetyFactor =
          ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
              .getVolumetricDesignFactor();
      retentionTime = 120.0; // ((SeparatorDesignStandard)
                             // getDesignStandard().get("separator process
                             // design")).getLiquidRetentionTime("API12J", this);
    } else {
      System.out.println("no separator process design specified......");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"Name", "Value", "Unit"};
    String[][] table = new String[16][3]; // createTable(getProcessEquipment().getName());

    table[1][0] = "Separator Inner Diameter";
    table[1][1] = Double.toString(getInnerDiameter());
    table[1][2] = "m";

    table[2][0] = "Separator TanTan Length";
    table[2][1] = Double.toString(getTantanLength());
    table[2][2] = "m";

    table[3][0] = "Wall thickness";
    table[3][1] = Double.toString(getWallThickness());
    table[3][2] = "m";

    table[4][0] = "Empty Vessel Weight Weight";
    table[4][1] = Double.toString(getWeigthVesselShell());
    table[4][2] = "kg";

    table[5][0] = "Internals+Nozzle Weight";
    table[5][1] = Double.toString(getWeigthInternals());
    table[5][2] = "kg";

    table[8][0] = "Module Length";
    table[8][1] = Double.toString(getModuleLength());
    table[8][2] = "m";

    table[9][0] = "Module Height";
    table[9][1] = Double.toString(getModuleHeight());
    table[9][2] = "m";

    table[10][0] = "Module Width";
    table[10][1] = Double.toString(getModuleWidth());
    table[10][2] = "m";

    table[11][0] = "Module Total Weight";
    table[11][1] = Double.toString(getWeightTotal());
    table[11][2] = "kg";

    // table[5][0] = "Module Total Cost";
    // // table[5][1] = Double.toString(getMod());
    // table[5][2] = "kg";
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600); // pack();
    // dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Separator separator = (Separator) getProcessEquipment();
    separator.getThermoSystem().initPhysicalProperties();
    separator.setDesignLiquidLevelFraction(Fg);

    double emptyVesselWeight = 0.0;
    double internalsWeight = 0.0;
    double externalNozzelsWeight = 0.0;
    double pipingWeight = 0.0;
    double structualWeight = 0.0;
    double electricalWeight = 0.0;
    double totalSkidWeight = 0.0;

    // double moduleWidth = 0.0, moduleHeight = 0.0, moduleLength = 0.0;
    double materialsCost = 0.0;
    double gasDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0)
        .getPhysicalProperties().getDensity();

    double liqDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(1)
        .getPhysicalProperties().getDensity();
    double liqViscosity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(1)
        .getPhysicalProperties().getViscosity();
    if (((SeparatorInterface) getProcessEquipment()).getThermoSystem().getNumberOfPhases() == 1) {
      liqDensity = getDefaultLiquidDensity();
      liqViscosity = getDefaultLiquidViscosity();
    }
    maxDesignVolumeFlow = volumeSafetyFactor
        * ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0).getVolume()
        / 1e5;

    double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);

    innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
    outerDiameter = innerDiameter + 2.0 * wallThickness;

    // Calculate max allowable gas volume flow based on sized diameter
    // This is the design capacity used for capacity utilization calculations
    double crossSectionalArea = Math.PI * Math.pow(innerDiameter / 2.0, 2);
    maxDesignGassVolumeFlow = maxGasVelocity * crossSectionalArea * Fg * 3600.0; // m³/hr

    // tantanLength = innerDiameter * 5.0;
    retentionTime = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
        .getLiquidRetentionTime("API12J", this);

    tantanLength = Math.sqrt(4.0 * retentionTime
        * ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getLiquidVolume() / 1e5
        / (Math.PI * innerDiameter * innerDiameter * (1 - Fg)));
    double sepratorLength = tantanLength + innerDiameter;

    if (sepratorLength / innerDiameter > 6 || sepratorLength / innerDiameter < 3) {
      // System.out
      // .println("Fg need to be modified ... L/D separator= " + sepratorLength /
      // innerDiameter);
      tantanLength = innerDiameter * 5.0;
      sepratorLength = tantanLength + innerDiameter;
    }
    // System.out.println("inner Diameter " + innerDiameter);

    // alternative design
    double bubbleDiameter = 250.0e-6;
    double bubVelocity =
        9.82 * Math.pow(bubbleDiameter, 2.0) * (liqDensity - gasDensity) / 18.0 / liqViscosity;
    double Ar = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getLiquidVolume()
        / 1e5 / bubVelocity;
    double Daim = Math.sqrt(Ar / 4.0);
    double Length2 = 4.0 * Daim;

    if (Daim > innerDiameter) {
      innerDiameter = Daim;
      tantanLength = Length2;
    }
    // calculating from standard codes
    // sepLength = innerDiameter * 2.0;
    emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;

    setOuterDiameter(innerDiameter + 2.0 * getWallThickness());
    for (SeparatorSection sep : separator.getSeparatorSections()) {
      sep.setOuterDiameter(getOuterDiameter());
      sep.getMechanicalDesign().calcDesign();
      internalsWeight += sep.getMechanicalDesign().getTotalWeight();
    }

    // System.out.println("internal weight " + internalsWeight);

    externalNozzelsWeight = 0.0; // need to be implemented
    double Wv = emptyVesselWeight + internalsWeight + externalNozzelsWeight;
    pipingWeight = Wv * 0.4;
    structualWeight = Wv * 0.1;
    electricalWeight = Wv * 0.08;
    totalSkidWeight = Wv + pipingWeight + structualWeight + electricalWeight;
    materialsCost = totalSkidWeight / 1000.0 * (1000 * 6.0) / 1000.0; // kNOK
    moduleWidth = innerDiameter * 2;
    moduleLength = tantanLength * 1.5;
    moduleHeight = innerDiameter * 2 + 1.0;
    // }

    /*
     * System.out.println("wall thickness: " + separator.getName() + " " + getWallThickness() +
     * " m"); System.out.println("separator dry weigth: " + emptyVesselWeight + " kg");
     * System.out.println("total skid weigth: " + totalSkidWeight + " kg");
     * System.out.println("foot print: width:" + moduleWidth + " length " + moduleLength +
     * " height " + moduleHeight + " meter."); System.out.println("mechanical price: " +
     * materialsCost + " kNOK");
     */
    setWeigthVesselShell(emptyVesselWeight);

    // tantanLength = innerDiameter * 5;
    setInnerDiameter(innerDiameter);

    setOuterDiameter(outerDiameter);

    setWeightElectroInstrument(electricalWeight);

    setWeightNozzle(externalNozzelsWeight);

    setWeightPiping(pipingWeight);

    setWeightStructualSteel(structualWeight);

    setWeightTotal(totalSkidWeight);

    setWeigthInternals(internalsWeight);

    setWallThickness(wallThickness);

    setModuleHeight(moduleHeight);

    setModuleWidth(moduleWidth);

    setModuleLength(moduleLength);
  }

  /**
   * Performs the sizing calculations without reading design specifications. This method is called
   * by autoSize() after design specs have been read and any user overrides have been applied.
   */
  public void performSizingCalculations() {
    Separator separator = (Separator) getProcessEquipment();
    separator.getThermoSystem().initPhysicalProperties();

    double emptyVesselWeight = 0.0;
    double internalsWeight = 0.0;
    double externalNozzelsWeight = 0.0;
    double pipingWeight = 0.0;
    double structualWeight = 0.0;
    double electricalWeight = 0.0;
    double totalSkidWeight = 0.0;
    double materialsCost = 0.0;

    double gasDensity =
        separator.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();

    double liqDensity = getDefaultLiquidDensity();
    double liqViscosity = getDefaultLiquidViscosity();
    if (separator.getThermoSystem().getNumberOfPhases() > 1) {
      liqDensity = separator.getThermoSystem().getPhase(1).getPhysicalProperties().getDensity();
      liqViscosity = separator.getThermoSystem().getPhase(1).getPhysicalProperties().getViscosity();
    }

    // Apply safety factor to flow
    maxDesignVolumeFlow =
        volumeSafetyFactor * separator.getThermoSystem().getPhase(0).getVolume() / 1e5;

    // Souders-Brown equation for max gas velocity
    double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);

    // Calculate diameter based on gas area (Fg is gas fraction = 1 - liquid level)
    innerDiameter = Math.sqrt(4.0 * maxDesignVolumeFlow
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
    outerDiameter = innerDiameter + 2.0 * wallThickness;

    // Calculate max allowable gas volume flow based on sized diameter
    // This is the design capacity used for capacity utilization calculations
    double crossSectionalArea = Math.PI * Math.pow(innerDiameter / 2.0, 2);
    maxDesignGassVolumeFlow = maxGasVelocity * crossSectionalArea * Fg * 3600.0; // m³/hr

    // Calculate length based on liquid retention time
    double liquidVolume = separator.getThermoSystem().getLiquidVolume() / 1e5;
    if (liquidVolume > 1e-10) {
      tantanLength = Math.sqrt(4.0 * retentionTime * liquidVolume * volumeSafetyFactor
          / (Math.PI * innerDiameter * innerDiameter * (1 - Fg)));
    } else {
      // No liquid - use L/D ratio of 4
      tantanLength = innerDiameter * 4.0;
    }

    double separatorTotalLength = tantanLength + innerDiameter;

    // Check L/D ratio and adjust if needed
    if (separatorTotalLength / innerDiameter > 6 || separatorTotalLength / innerDiameter < 3) {
      tantanLength = innerDiameter * 4.0; // Default to L/D = 5
    }

    // Weight calculations
    emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;
    setOuterDiameter(innerDiameter + 2.0 * getWallThickness());

    // Calculate internals weight
    for (SeparatorSection sep : separator.getSeparatorSections()) {
      sep.setOuterDiameter(getOuterDiameter());
      sep.getMechanicalDesign().calcDesign();
      internalsWeight += sep.getMechanicalDesign().getTotalWeight();
    }

    externalNozzelsWeight = 0.0;
    double Wv = emptyVesselWeight + internalsWeight + externalNozzelsWeight;
    pipingWeight = Wv * 0.4;
    structualWeight = Wv * 0.1;
    electricalWeight = Wv * 0.08;
    totalSkidWeight = Wv + pipingWeight + structualWeight + electricalWeight;
    materialsCost = totalSkidWeight / 1000.0 * (1000 * 6.0) / 1000.0;
    moduleWidth = innerDiameter * 2;
    moduleLength = tantanLength * 1.5;
    moduleHeight = innerDiameter * 2 + 1.0;

    setWeigthVesselShell(emptyVesselWeight);
    setInnerDiameter(innerDiameter);
    setOuterDiameter(outerDiameter);
    setWeightElectroInstrument(electricalWeight);
    setWeightNozzle(externalNozzelsWeight);
    setWeightPiping(pipingWeight);
    setWeightStructualSteel(structualWeight);
    setWeightTotal(totalSkidWeight);
    setModuleHeight(moduleHeight);
    setModuleWidth(moduleWidth);
    setModuleLength(moduleLength);

    // Calculate effective lengths based on typical internals layout
    // Inlet device typically occupies ~0.8m, weir plate near end
    effectiveLengthLiquid = tantanLength - 0.8; // Perforated plate to weir
    effectiveLengthGas = tantanLength * 0.64; // Perforated plate to demister

    // Calculate nozzle sizes
    calcInletNozzleID();
    calcGasOutletNozzleID();
    calcOilOutletNozzleID();

    // Set default liquid level fractions
    calculateDefaultLevelFractions();
  }

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    Separator separator = (Separator) getProcessEquipment();
    separator.setInternalDiameter(innerDiameter);
    separator.setSeparatorLength(tantanLength);
    // Synchronize design parameters back to separator
    separator.setDesignGasLoadFactor(gasLoadFactor);
    separator.setDesignLiquidLevelFraction(1.0 - Fg);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a separator-specific response with additional fields for vessel sizing, internals, and
   * process design data.
   * </p>
   */
  @Override
  public SeparatorMechanicalDesignResponse getResponse() {
    return new SeparatorMechanicalDesignResponse(this);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns JSON with separator-specific fields.
   * </p>
   */
  @Override
  public String toJson() {
    return getResponse().toJson();
  }

  /**
   * Get gas load factor (K-factor).
   *
   * @return gas load factor
   */
  public double getGasLoadFactor() {
    return gasLoadFactor;
  }

  /**
   * Set gas load factor (K-factor).
   *
   * @param gasLoadFactor gas load factor [m/s], typically 0.07-0.15 for horizontal separators
   */
  public void setGasLoadFactor(double gasLoadFactor) {
    this.gasLoadFactor = gasLoadFactor;
  }

  /**
   * Get volume safety factor.
   *
   * @return volume safety factor
   */
  public double getVolumeSafetyFactor() {
    return volumeSafetyFactor;
  }

  /**
   * Set volume safety factor for design flow calculations.
   *
   * @param volumeSafetyFactor safety factor (typically 1.1-1.3)
   */
  public void setVolumeSafetyFactor(double volumeSafetyFactor) {
    this.volumeSafetyFactor = volumeSafetyFactor;
  }

  /**
   * Get liquid level fraction (Fg).
   *
   * @return liquid level fraction
   */
  public double getFg() {
    return Fg;
  }

  /**
   * Set liquid level fraction (Fg = 1 - liquid level).
   *
   * @param fg gas area fraction
   */
  public void setFg(double fg) {
    this.Fg = fg;
  }

  /**
   * Get retention time in seconds.
   *
   * @return retention time
   */
  public double getRetentionTime() {
    return retentionTime;
  }

  /**
   * Set retention time in seconds.
   *
   * @param retentionTime liquid retention time in seconds (typically 60-300s)
   */
  public void setRetentionTime(double retentionTime) {
    this.retentionTime = retentionTime;
  }

  // ==================== Liquid Level Calculations ====================

  /**
   * Calculate the High-High Liquid Level (HHLL) in meters. HHLL is the maximum safe operating level
   * before emergency shutdown.
   *
   * @return HHLL height in meters
   */
  public double getHHLL() {
    return hhllFraction * innerDiameter;
  }

  /**
   * Calculate the High Liquid Level (HLL) in meters. HLL triggers high level alarm.
   *
   * @return HLL height in meters
   */
  public double getHLL() {
    return hllFraction * innerDiameter;
  }

  /**
   * Calculate the Normal Liquid Level (NLL) in meters. NLL is the target operating level during
   * normal operation.
   *
   * @return NLL height in meters
   */
  public double getNLL() {
    return nllFraction * innerDiameter;
  }

  /**
   * Calculate the Low Liquid Level (LLL) in meters. LLL triggers low level alarm.
   *
   * @return LLL height in meters
   */
  public double getLLL() {
    return lllFraction * innerDiameter;
  }

  /**
   * Calculate the Low-Low Liquid Level (LLLL) in meters. LLLL triggers emergency shutdown to
   * prevent pump dry-running.
   *
   * @return LLLL height in meters
   */
  public double getLLLL() {
    return llllFraction * innerDiameter;
  }

  /**
   * Calculate the weir height in meters. The weir separates oil and water compartments in
   * three-phase separators.
   *
   * @return weir height in meters
   */
  public double getWeirHeight() {
    return weirFraction * innerDiameter;
  }

  /**
   * Calculate the High Interface Level (HIL) in meters. HIL is the high oil/water interface level
   * alarm point.
   *
   * @return HIL height in meters
   */
  public double getHIL() {
    return hilFraction * innerDiameter;
  }

  /**
   * Calculate the Normal Interface Level (NIL) in meters. NIL is the target oil/water interface
   * level.
   *
   * @return NIL height in meters
   */
  public double getNIL() {
    return nilFraction * innerDiameter;
  }

  /**
   * Calculate the Low Interface Level (LIL) in meters. LIL is the low oil/water interface level
   * alarm point.
   *
   * @return LIL height in meters
   */
  public double getLIL() {
    return lilFraction * innerDiameter;
  }

  // ==================== Effective Length Calculations ====================

  /**
   * Calculate effective length for liquid separation. This is typically: TanTan length - inlet
   * device length - weir plate distance.
   *
   * @return effective liquid separation length in meters
   */
  public double getEffectiveLengthLiquid() {
    if (effectiveLengthLiquid > 0) {
      return effectiveLengthLiquid;
    }
    // Default calculation: ~82% of tan-tan length
    // Accounts for inlet device (~0.8m) and weir plate position
    return tantanLength * 0.82;
  }

  /**
   * Calculate effective length for gas separation. This is typically: TanTan length - inlet device
   * length - demister position.
   *
   * @return effective gas separation length in meters
   */
  public double getEffectiveLengthGas() {
    if (effectiveLengthGas > 0) {
      return effectiveLengthGas;
    }
    // Default calculation: ~64% of tan-tan length
    // Accounts for inlet device and distance to demister
    return tantanLength * 0.64;
  }

  /**
   * Calculate liquid retention time based on effective length and NLL.
   * 
   * @return liquid retention time in seconds
   */
  public double calcLiquidRetentionTime() {
    Separator separator = (Separator) getProcessEquipment();
    double liquidFlowRate = 0.0;
    if (separator.getThermoSystem().getNumberOfPhases() > 1) {
      liquidFlowRate = separator.getThermoSystem().getLiquidVolume() / 1e5; // m³/hr
    }
    if (liquidFlowRate < 1e-10) {
      return 0.0;
    }
    // Calculate liquid volume at NLL
    double liquidVolume = calcLiquidVolumeAtLevel(getNLL());
    return liquidVolume / (liquidFlowRate / 3600.0); // seconds
  }

  /**
   * Calculate liquid volume at a given level height for horizontal cylindrical vessel.
   *
   * @param levelHeight liquid level height in meters
   * @return liquid volume in m³
   */
  public double calcLiquidVolumeAtLevel(double levelHeight) {
    double r = innerDiameter / 2.0;
    double h = Math.min(levelHeight, innerDiameter);

    // Segment area of circular cross-section
    double theta = 2.0 * Math.acos((r - h) / r);
    double segmentArea = (r * r / 2.0) * (theta - Math.sin(theta));

    // Multiply by effective length
    return segmentArea * getEffectiveLengthLiquid();
  }

  /**
   * Calculate surge volume between NLL and HLL. This volume provides time buffer for control system
   * response.
   *
   * @return surge volume in m³
   */
  public double calcSurgeVolume() {
    return calcLiquidVolumeAtLevel(getHLL()) - calcLiquidVolumeAtLevel(getNLL());
  }

  /**
   * Calculate surge time based on surge volume and liquid flow rate.
   *
   * @return surge time in seconds
   */
  public double calcSurgeTime() {
    Separator separator = (Separator) getProcessEquipment();
    double liquidFlowRate = 0.0;
    if (separator.getThermoSystem().getNumberOfPhases() > 1) {
      liquidFlowRate = separator.getThermoSystem().getLiquidVolume() / 1e5; // m³/hr
    }
    if (liquidFlowRate < 1e-10) {
      return 0.0;
    }
    return calcSurgeVolume() / (liquidFlowRate / 3600.0); // seconds
  }

  // ==================== Nozzle Size Calculations ====================

  /**
   * Calculate inlet nozzle diameter based on inlet momentum. Uses API 12J recommendations for
   * maximum momentum.
   *
   * @return inlet nozzle ID in meters
   */
  public double calcInletNozzleID() {
    Separator separator = (Separator) getProcessEquipment();
    neqsim.thermo.system.SystemInterface thermoSystem = separator.getThermoSystem();

    if (thermoSystem == null) {
      inletNozzleID = 0.10; // Default 4" if no thermo system
      return inletNozzleID;
    }

    // Calculate total volumetric flow from all phases
    double totalVolumetricFlow = 0.0; // m³/hr
    double mixDensity = 0.0;
    double totalMassFlow = 0.0;

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      double phaseFlowM3hr = thermoSystem.getPhase(i).getFlowRate("m3/hr");
      double phaseDensity = thermoSystem.getPhase(i).getDensity("kg/m3");
      double phaseMassFlow = phaseFlowM3hr * phaseDensity;

      totalVolumetricFlow += phaseFlowM3hr;
      totalMassFlow += phaseMassFlow;
    }

    if (totalVolumetricFlow > 0 && totalMassFlow > 0) {
      mixDensity = totalMassFlow / totalVolumetricFlow;
    } else {
      // Fallback to gas-only calculation
      if (thermoSystem.hasPhaseType("gas")) {
        int gasIdx = thermoSystem.getPhaseNumberOfPhase("gas");
        totalVolumetricFlow = thermoSystem.getPhase(gasIdx).getFlowRate("m3/hr");
        mixDensity = thermoSystem.getPhase(gasIdx).getDensity("kg/m3");
      }
    }

    if (totalVolumetricFlow <= 0 || mixDensity <= 0) {
      inletNozzleID = 0.10; // Default 4" if no flow
      return inletNozzleID;
    }

    // API 12J recommends ρv² < 8000 Pa for inlet devices
    // For revamp situations, use 16000 Pa
    double maxMomentum = 8000.0; // Pa (conservative)
    double maxVelocity = Math.sqrt(maxMomentum / mixDensity);

    // Calculate minimum nozzle area
    double minArea = (totalVolumetricFlow / 3600.0) / maxVelocity;
    inletNozzleID = Math.sqrt(4.0 * minArea / Math.PI);

    // Round up to nearest standard size (50mm increments)
    inletNozzleID = Math.max(0.05, Math.ceil(inletNozzleID / 0.05) * 0.05);

    return inletNozzleID;
  }

  /**
   * Calculate gas outlet nozzle diameter. Based on gas velocity limit (typically 15-20 m/s).
   *
   * @return gas outlet nozzle ID in meters
   */
  public double calcGasOutletNozzleID() {
    Separator separator = (Separator) getProcessEquipment();
    double gasFlowRate = separator.getThermoSystem().getPhase(0).getVolume() / 1e5; // m³/hr

    // Max gas velocity in outlet nozzle: 20 m/s
    double maxVelocity = 20.0;
    double minArea = (gasFlowRate / 3600.0) / maxVelocity;
    gasOutletNozzleID = Math.sqrt(4.0 * minArea / Math.PI);

    // Round up to nearest standard size
    gasOutletNozzleID = Math.ceil(gasOutletNozzleID / 0.05) * 0.05;

    return gasOutletNozzleID;
  }

  /**
   * Calculate oil outlet nozzle diameter. Based on liquid velocity limit (typically 1-2 m/s).
   *
   * @return oil outlet nozzle ID in meters
   */
  public double calcOilOutletNozzleID() {
    Separator separator = (Separator) getProcessEquipment();
    double oilFlowRate = 0.0;
    if (separator.getThermoSystem().getNumberOfPhases() > 1) {
      oilFlowRate = separator.getThermoSystem().getPhase(1).getVolume() / 1e5; // m³/hr
    }
    if (oilFlowRate < 1e-10) {
      return 0.05; // Minimum 50mm
    }

    // Max liquid velocity in outlet nozzle: 1.5 m/s
    double maxVelocity = 1.5;
    double minArea = (oilFlowRate / 3600.0) / maxVelocity;
    oilOutletNozzleID = Math.sqrt(4.0 * minArea / Math.PI);

    // Round up to nearest standard size, minimum 50mm
    oilOutletNozzleID = Math.max(0.05, Math.ceil(oilOutletNozzleID / 0.025) * 0.025);

    return oilOutletNozzleID;
  }

  // ==================== Setters for Level Fractions ====================

  /**
   * Set HHLL fraction of internal diameter.
   *
   * @param fraction HHLL as fraction of ID (0-1)
   */
  public void setHHLLFraction(double fraction) {
    this.hhllFraction = fraction;
  }

  /**
   * Set HLL fraction of internal diameter.
   *
   * @param fraction HLL as fraction of ID (0-1)
   */
  public void setHLLFraction(double fraction) {
    this.hllFraction = fraction;
  }

  /**
   * Set NLL fraction of internal diameter.
   *
   * @param fraction NLL as fraction of ID (0-1)
   */
  public void setNLLFraction(double fraction) {
    this.nllFraction = fraction;
  }

  /**
   * Set LLL fraction of internal diameter.
   *
   * @param fraction LLL as fraction of ID (0-1)
   */
  public void setLLLFraction(double fraction) {
    this.lllFraction = fraction;
  }

  /**
   * Set LLLL fraction of internal diameter.
   *
   * @param fraction LLLL as fraction of ID (0-1)
   */
  public void setLLLLFraction(double fraction) {
    this.llllFraction = fraction;
  }

  /**
   * Set weir height fraction of internal diameter.
   *
   * @param fraction weir height as fraction of ID (0-1)
   */
  public void setWeirFraction(double fraction) {
    this.weirFraction = fraction;
  }

  /**
   * Set HIL fraction of internal diameter.
   *
   * @param fraction HIL as fraction of ID (0-1)
   */
  public void setHILFraction(double fraction) {
    this.hilFraction = fraction;
  }

  /**
   * Set NIL fraction of internal diameter.
   *
   * @param fraction NIL as fraction of ID (0-1)
   */
  public void setNILFraction(double fraction) {
    this.nilFraction = fraction;
  }

  /**
   * Set LIL fraction of internal diameter.
   *
   * @param fraction LIL as fraction of ID (0-1)
   */
  public void setLILFraction(double fraction) {
    this.lilFraction = fraction;
  }

  /**
   * Set effective length for liquid separation.
   *
   * @param length effective length in meters
   */
  public void setEffectiveLengthLiquid(double length) {
    this.effectiveLengthLiquid = length;
  }

  /**
   * Set effective length for gas separation.
   *
   * @param length effective length in meters
   */
  public void setEffectiveLengthGas(double length) {
    this.effectiveLengthGas = length;
  }

  /**
   * Set inlet nozzle ID.
   *
   * @param id inlet nozzle internal diameter in meters
   */
  public void setInletNozzleID(double id) {
    this.inletNozzleID = id;
  }

  /**
   * Set gas outlet nozzle ID.
   *
   * @param id gas outlet nozzle internal diameter in meters
   */
  public void setGasOutletNozzleID(double id) {
    this.gasOutletNozzleID = id;
  }

  /**
   * Set oil outlet nozzle ID.
   *
   * @param id oil outlet nozzle internal diameter in meters
   */
  public void setOilOutletNozzleID(double id) {
    this.oilOutletNozzleID = id;
  }

  /**
   * Set water outlet nozzle ID.
   *
   * @param id water outlet nozzle internal diameter in meters
   */
  public void setWaterOutletNozzleID(double id) {
    this.waterOutletNozzleID = id;
  }

  // ==================== Getters for Fractions ====================

  /**
   * Get HHLL (High-High Liquid Level) fraction.
   *
   * @return HHLL fraction
   */
  public double getHHLLFraction() {
    return hhllFraction;
  }

  /**
   * Get HLL (High Liquid Level) fraction.
   *
   * @return HLL fraction
   */
  public double getHLLFraction() {
    return hllFraction;
  }

  /**
   * Get NLL (Normal Liquid Level) fraction.
   *
   * @return NLL fraction
   */
  public double getNLLFraction() {
    return nllFraction;
  }

  /**
   * Get LLL (Low Liquid Level) fraction.
   *
   * @return LLL fraction
   */
  public double getLLLFraction() {
    return lllFraction;
  }

  /**
   * Get LLLL (Low-Low Liquid Level) fraction.
   *
   * @return LLLL fraction
   */
  public double getLLLLFraction() {
    return llllFraction;
  }

  /**
   * Get weir fraction.
   *
   * @return weir fraction
   */
  public double getWeirFraction() {
    return weirFraction;
  }

  /**
   * Get HIL (High Interface Level) fraction.
   *
   * @return HIL fraction
   */
  public double getHILFraction() {
    return hilFraction;
  }

  /**
   * Get NIL (Normal Interface Level) fraction.
   *
   * @return NIL fraction
   */
  public double getNILFraction() {
    return nilFraction;
  }

  /**
   * Get LIL (Low Interface Level) fraction.
   *
   * @return LIL fraction
   */
  public double getLILFraction() {
    return lilFraction;
  }

  /**
   * Get inlet nozzle internal diameter.
   *
   * @return inlet nozzle ID in meters
   */
  public double getInletNozzleID() {
    return inletNozzleID;
  }

  /**
   * Get gas outlet nozzle internal diameter.
   *
   * @return gas outlet nozzle ID in meters
   */
  public double getGasOutletNozzleID() {
    return gasOutletNozzleID;
  }

  /**
   * Get oil outlet nozzle internal diameter.
   *
   * @return oil outlet nozzle ID in meters
   */
  public double getOilOutletNozzleID() {
    return oilOutletNozzleID;
  }

  /**
   * Get water outlet nozzle internal diameter.
   *
   * @return water outlet nozzle ID in meters
   */
  public double getWaterOutletNozzleID() {
    return waterOutletNozzleID;
  }

  /**
   * Configure liquid levels based on provided values (heights in meters). Automatically calculates
   * fractions based on internal diameter.
   *
   * @param hhll High-High Liquid Level in meters
   * @param hll High Liquid Level in meters
   * @param nll Normal Liquid Level in meters
   * @param lll Low Liquid Level in meters
   */
  public void setLiquidLevelsFromHeights(double hhll, double hll, double nll, double lll) {
    if (innerDiameter > 0) {
      this.hhllFraction = hhll / innerDiameter;
      this.hllFraction = hll / innerDiameter;
      this.nllFraction = nll / innerDiameter;
      this.lllFraction = lll / innerDiameter;
    }
  }

  /**
   * Configure interface levels for three-phase separator based on heights.
   *
   * @param hil High Interface Level in meters
   * @param nil Normal Interface Level in meters
   * @param lil Low Interface Level in meters
   * @param weir Weir height in meters
   */
  public void setInterfaceLevelsFromHeights(double hil, double nil, double lil, double weir) {
    if (innerDiameter > 0) {
      this.hilFraction = hil / innerDiameter;
      this.nilFraction = nil / innerDiameter;
      this.lilFraction = lil / innerDiameter;
      this.weirFraction = weir / innerDiameter;
    }
  }

  /**
   * Calculate all level fractions based on the actual internal diameter. Uses typical design ratios
   * from API 12J and NORSOK.
   */
  public void calculateDefaultLevelFractions() {
    // Typical level fractions for horizontal separator
    // Based on API 12J and NORSOK P-001 guidelines
    this.hhllFraction = 0.80; // Emergency shutdown level
    this.hllFraction = 0.70; // High alarm
    this.nllFraction = 0.50; // Normal operating level
    this.lllFraction = 0.30; // Low alarm
    this.llllFraction = 0.15; // Emergency shutdown (dry pump protection)

    // Interface levels for three-phase (below weir)
    this.weirFraction = 0.25;
    this.hilFraction = 0.24;
    this.nilFraction = 0.21;
    this.lilFraction = 0.14;
  }

  // ==================== Pre-Designed Separator Configuration
  // ====================

  /**
   * Configure all separator dimensions from an existing pre-designed separator. This allows
   * importing a complete design from external sources (e.g., vendor data).
   *
   * @param id Inner diameter in meters
   * @param tanTanLength Tan-tan length in meters
   * @param wallThick Wall thickness in meters
   */
  public void setFromExistingDesign(double id, double tanTanLength, double wallThick) {
    this.innerDiameter = id;
    this.tantanLength = tanTanLength;
    this.wallThickness = wallThick;
    this.outerDiameter = id + 2.0 * wallThick;
  }

  /**
   * Configure all separator dimensions and levels from an existing design. This is the
   * comprehensive method for importing a complete pre-designed separator.
   *
   * @param id Inner diameter in meters
   * @param tanTanLength Tan-tan length in meters
   * @param wallThick Wall thickness in meters
   * @param lEffLiquid Effective length for liquid separation in meters
   * @param lEffGas Effective length for gas separation in meters
   * @param inletNozzle Inlet nozzle ID in meters
   * @param gasNozzle Gas outlet nozzle ID in meters
   * @param oilNozzle Oil outlet nozzle ID in meters
   * @param waterNozzle Water outlet nozzle ID in meters (0 for two-phase)
   */
  public void setFromExistingDesign(double id, double tanTanLength, double wallThick,
      double lEffLiquid, double lEffGas, double inletNozzle, double gasNozzle, double oilNozzle,
      double waterNozzle) {
    setFromExistingDesign(id, tanTanLength, wallThick);
    this.effectiveLengthLiquid = lEffLiquid;
    this.effectiveLengthGas = lEffGas;
    this.inletNozzleID = inletNozzle;
    this.gasOutletNozzleID = gasNozzle;
    this.oilOutletNozzleID = oilNozzle;
    this.waterOutletNozzleID = waterNozzle;
  }

  /**
   * Configure all liquid levels from heights in meters. Automatically calculates fractions based on
   * internal diameter.
   *
   * @param hhll High-High Liquid Level in meters
   * @param hll High Liquid Level in meters
   * @param nll Normal Liquid Level in meters
   * @param lll Low Liquid Level in meters
   * @param llll Low-Low Liquid Level in meters
   */
  public void setAllLiquidLevelsFromHeights(double hhll, double hll, double nll, double lll,
      double llll) {
    if (innerDiameter > 0) {
      this.hhllFraction = hhll / innerDiameter;
      this.hllFraction = hll / innerDiameter;
      this.nllFraction = nll / innerDiameter;
      this.lllFraction = lll / innerDiameter;
      this.llllFraction = llll / innerDiameter;
    }
  }

  /**
   * Set all design parameters from a Python-style dictionary-like specification. This method
   * mirrors the Python dict format: {'ID': 3.154, 'L': 13.1, ...}.
   *
   * @param id Inner diameter in meters
   * @param length Tan-tan length in meters
   * @param lEffLiquid Effective length for liquid separation in meters
   * @param lEffGas Effective length for gas separation in meters
   * @param inletNozzleId Inlet nozzle ID in meters
   * @param hhll HHLL height in meters
   * @param hll HLL height in meters
   * @param nll NLL height in meters
   * @param lll LLL height in meters
   * @param weir Weir height in meters
   * @param hil HIL height in meters
   * @param nil NIL height in meters
   * @param lil LIL height in meters
   */
  public void setFromDesignSpec(double id, double length, double lEffLiquid, double lEffGas,
      double inletNozzleId, double hhll, double hll, double nll, double lll, double weir,
      double hil, double nil, double lil) {
    this.innerDiameter = id;
    this.tantanLength = length;
    this.effectiveLengthLiquid = lEffLiquid;
    this.effectiveLengthGas = lEffGas;
    this.inletNozzleID = inletNozzleId;

    // Set liquid levels as fractions
    if (id > 0) {
      this.hhllFraction = hhll / id;
      this.hllFraction = hll / id;
      this.nllFraction = nll / id;
      this.lllFraction = lll / id;
      this.weirFraction = weir / id;
      this.hilFraction = hil / id;
      this.nilFraction = nil / id;
      this.lilFraction = lil / id;
    }
  }

  /**
   * Set distance from inlet to perforated plate.
   *
   * @param distance distance in meters
   */
  public void setInletToPerforatedPlate(double distance) {
    this.inletToPerforatedPlate = distance;
  }

  /**
   * Get distance from inlet to perforated plate.
   *
   * @return distance in meters
   */
  public double getInletToPerforatedPlate() {
    return inletToPerforatedPlate;
  }

  /**
   * Set distance from perforated plate to weir.
   *
   * @param distance distance in meters
   */
  public void setPerforatedPlateToWeir(double distance) {
    this.perforatedPlateToWeir = distance;
  }

  /**
   * Get distance from perforated plate to weir.
   *
   * @return distance in meters
   */
  public double getPerforatedPlateToWeir() {
    return perforatedPlateToWeir;
  }

  /**
   * Set distance from inlet to gas demister.
   *
   * @param distance distance in meters
   */
  public void setInletToGasDemister(double distance) {
    this.inletToGasDemister = distance;
  }

  /**
   * Get distance from inlet to gas demister.
   *
   * @return distance in meters
   */
  public double getInletToGasDemister() {
    return inletToGasDemister;
  }

  /**
   * Calculate effective lengths from internal positions. Call this after setting
   * inlet-to-perforated-plate and perforated-plate-to-weir distances.
   */
  public void calculateEffectiveLengthsFromInternals() {
    // L_eff_liquid = perforated plate to weir
    if (perforatedPlateToWeir > 0) {
      this.effectiveLengthLiquid = perforatedPlateToWeir;
    }
    // L_eff_gas = perforated plate to demister
    if (inletToGasDemister > 0 && inletToPerforatedPlate > 0) {
      this.effectiveLengthGas = inletToGasDemister - inletToPerforatedPlate;
    }
  }

  /**
   * Validate that the design parameters are within acceptable ranges.
   *
   * @return true if design is valid
   */
  public boolean validateDesign() {
    boolean valid = true;

    // Check L/D ratio (typically 3-6 for horizontal separators)
    if (innerDiameter > 0) {
      double ldRatio = tantanLength / innerDiameter;
      if (ldRatio < 2.5 || ldRatio > 7.0) {
        System.out.println("Warning: L/D ratio " + ldRatio + " outside typical range 3-6");
        valid = false;
      }
    }

    // Check level sequence
    if (hhllFraction <= hllFraction || hllFraction <= nllFraction || nllFraction <= lllFraction
        || lllFraction <= llllFraction) {
      // Levels should be in descending order
    } else {
      System.out.println("Warning: Liquid levels not in correct order");
      valid = false;
    }

    // Check interface levels below weir
    if (hilFraction > weirFraction) {
      System.out.println("Warning: HIL above weir height");
      valid = false;
    }

    return valid;
  }

  /**
   * Get a complete design summary as a formatted string.
   *
   * @return design summary
   */
  public String getDesignSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Separator Mechanical Design Summary ===\n");
    sb.append(String.format("ID: %.3f m%n", innerDiameter));
    sb.append(String.format("L (TanTan): %.3f m%n", tantanLength));
    sb.append(String.format("L_eff_liquid: %.3f m%n", getEffectiveLengthLiquid()));
    sb.append(String.format("L_eff_gas: %.3f m%n", getEffectiveLengthGas()));
    sb.append(String.format("Inlet Nozzle ID: %.3f m%n", inletNozzleID));
    sb.append("\n--- Liquid Levels ---\n");
    sb.append(String.format("HHLL: %.3f m (%.0f%% of ID)%n", getHHLL(), hhllFraction * 100));
    sb.append(String.format("HLL: %.3f m (%.0f%% of ID)%n", getHLL(), hllFraction * 100));
    sb.append(String.format("NLL: %.3f m (%.0f%% of ID)%n", getNLL(), nllFraction * 100));
    sb.append(String.format("LLL: %.3f m (%.0f%% of ID)%n", getLLL(), lllFraction * 100));
    sb.append(String.format("Weir: %.3f m (%.0f%% of ID)%n", getWeirHeight(), weirFraction * 100));
    sb.append("\n--- Interface Levels ---\n");
    sb.append(String.format("HIL: %.3f m (%.0f%% of ID)%n", getHIL(), hilFraction * 100));
    sb.append(String.format("NIL: %.3f m (%.0f%% of ID)%n", getNIL(), nilFraction * 100));
    sb.append(String.format("LIL: %.3f m (%.0f%% of ID)%n", getLIL(), lilFraction * 100));
    return sb.toString();
  }

  // ============================================================================
  // Process Design Parameter Getters/Setters
  // ============================================================================

  /**
   * Gets the design pressure margin factor.
   *
   * @return design pressure margin (e.g., 1.10 for 10% margin)
   */
  public double getDesignPressureMargin() {
    return designPressureMargin;
  }

  /**
   * Sets the design pressure margin factor.
   *
   * @param margin margin factor (e.g., 1.10 for 10%)
   */
  public void setDesignPressureMargin(double margin) {
    this.designPressureMargin = margin;
  }

  /**
   * Gets the design temperature margin in Celsius.
   *
   * @return temperature margin in Celsius
   */
  public double getDesignTemperatureMarginC() {
    return designTemperatureMarginC;
  }

  /**
   * Sets the design temperature margin in Celsius.
   *
   * @param marginC temperature margin in Celsius
   */
  public void setDesignTemperatureMarginC(double marginC) {
    this.designTemperatureMarginC = marginC;
  }

  /**
   * Gets the minimum design temperature in Celsius.
   *
   * @return minimum design temperature in Celsius
   */
  public double getMinDesignTemperatureC() {
    return minDesignTemperatureC;
  }

  /**
   * Sets the minimum design temperature in Celsius.
   *
   * @param tempC minimum design temperature in Celsius
   */
  public void setMinDesignTemperatureC(double tempC) {
    this.minDesignTemperatureC = tempC;
  }

  /**
   * Gets the foam allowance factor.
   *
   * @return foam allowance factor (1.0 = no foam)
   */
  public double getFoamAllowanceFactor() {
    return foamAllowanceFactor;
  }

  /**
   * Sets the foam allowance factor.
   *
   * @param factor foam allowance factor (typically 1.0-1.5)
   */
  public void setFoamAllowanceFactor(double factor) {
    this.foamAllowanceFactor = factor;
  }

  /**
   * Gets the design droplet diameter for gas-liquid separation.
   *
   * @return droplet diameter in micrometers
   */
  public double getDropletDiameterGasLiquid() {
    return dropletDiameterGasLiquid;
  }

  /**
   * Sets the design droplet diameter for gas-liquid separation.
   *
   * @param diameterUm droplet diameter in micrometers
   */
  public void setDropletDiameterGasLiquid(double diameterUm) {
    this.dropletDiameterGasLiquid = diameterUm;
  }

  /**
   * Gets the design droplet diameter for liquid-liquid separation.
   *
   * @return droplet diameter in micrometers
   */
  public double getDropletDiameterLiquidLiquid() {
    return dropletDiameterLiquidLiquid;
  }

  /**
   * Sets the design droplet diameter for liquid-liquid separation.
   *
   * @param diameterUm droplet diameter in micrometers
   */
  public void setDropletDiameterLiquidLiquid(double diameterUm) {
    this.dropletDiameterLiquidLiquid = diameterUm;
  }

  /**
   * Gets the maximum gas velocity limit.
   *
   * @return maximum gas velocity in m/s
   */
  public double getMaxGasVelocityLimit() {
    return maxGasVelocity;
  }

  /**
   * Sets the maximum gas velocity limit.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxGasVelocityLimit(double velocity) {
    this.maxGasVelocity = velocity;
  }

  /**
   * Gets the maximum liquid outlet velocity.
   *
   * @return maximum liquid velocity in m/s
   */
  public double getMaxLiquidVelocity() {
    return maxLiquidVelocity;
  }

  /**
   * Sets the maximum liquid outlet velocity.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxLiquidVelocity(double velocity) {
    this.maxLiquidVelocity = velocity;
  }

  /**
   * Gets the minimum oil retention time.
   *
   * @return retention time in minutes
   */
  public double getMinOilRetentionTime() {
    return minOilRetentionTime;
  }

  /**
   * Sets the minimum oil retention time.
   *
   * @param minutes retention time in minutes
   */
  public void setMinOilRetentionTime(double minutes) {
    this.minOilRetentionTime = minutes;
  }

  /**
   * Gets the minimum water retention time.
   *
   * @return retention time in minutes
   */
  public double getMinWaterRetentionTime() {
    return minWaterRetentionTime;
  }

  /**
   * Sets the minimum water retention time.
   *
   * @param minutes retention time in minutes
   */
  public void setMinWaterRetentionTime(double minutes) {
    this.minWaterRetentionTime = minutes;
  }

  // ============================================================================
  // Demister/Mist Eliminator Getters/Setters
  // ============================================================================

  /**
   * Gets the demister pressure drop.
   *
   * @return pressure drop in mbar
   */
  public double getDemisterPressureDrop() {
    return demisterPressureDrop;
  }

  /**
   * Sets the demister pressure drop.
   *
   * @param pressureDrop pressure drop in mbar
   */
  public void setDemisterPressureDrop(double pressureDrop) {
    this.demisterPressureDrop = pressureDrop;
  }

  /**
   * Gets the demister void fraction.
   *
   * @return void fraction (0.0-1.0)
   */
  public double getDemisterVoidFraction() {
    return demisterVoidFraction;
  }

  /**
   * Sets the demister void fraction.
   *
   * @param voidFraction void fraction (typically 0.97-0.99)
   */
  public void setDemisterVoidFraction(double voidFraction) {
    this.demisterVoidFraction = voidFraction;
  }

  /**
   * Gets the demister pad thickness.
   *
   * @return thickness in mm
   */
  public double getDemisterThickness() {
    return demisterThickness;
  }

  /**
   * Sets the demister pad thickness.
   *
   * @param thickness thickness in mm
   */
  public void setDemisterThickness(double thickness) {
    this.demisterThickness = thickness;
  }

  /**
   * Gets the demister wire diameter.
   *
   * @return wire diameter in mm
   */
  public double getDemisterWireDiameter() {
    return demisterWireDiameter;
  }

  /**
   * Sets the demister wire diameter.
   *
   * @param diameter wire diameter in mm
   */
  public void setDemisterWireDiameter(double diameter) {
    this.demisterWireDiameter = diameter;
  }

  /**
   * Gets the inlet device K-factor reduction.
   *
   * @return K-factor reduction (1.0 = no reduction)
   */
  public double getInletDeviceKFactor() {
    return inletDeviceKFactor;
  }

  /**
   * Sets the inlet device K-factor reduction.
   *
   * @param factor K-factor reduction (typically 0.8-1.0)
   */
  public void setInletDeviceKFactor(double factor) {
    this.inletDeviceKFactor = factor;
  }

  /**
   * Gets the demister type.
   *
   * @return demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public String getDemisterType() {
    return demisterType;
  }

  /**
   * Sets the demister type.
   *
   * @param type demister type ("wire_mesh", "vane_pack", or "cyclone")
   */
  public void setDemisterType(String type) {
    this.demisterType = type;
  }

  // ============================================================================
  // Design Calculation Methods
  // ============================================================================

  /**
   * Calculates the design pressure from maximum operating pressure.
   *
   * @return design pressure in barg
   */
  public double calculateDesignPressure() {
    return getMaxOperationPressure() * designPressureMargin;
  }

  /**
   * Calculates the design temperature from maximum operating temperature.
   *
   * @return design temperature in Celsius
   */
  public double calculateDesignTemperature() {
    double maxOpTemp = getMaxOperationTemperature() - 273.15; // Convert K to C
    return maxOpTemp + designTemperatureMarginC;
  }

  /**
   * Calculates the minimum design temperature considering material limits.
   *
   * @return minimum design temperature in Celsius
   */
  public double calculateMinDesignTemperature() {
    double minOpTemp = getMinOperationTemperature() - 273.15; // Convert K to C
    return Math.max(minOpTemp - 10.0, minDesignTemperatureC);
  }

  /**
   * Calculates the terminal settling velocity for a droplet using Stokes' law.
   *
   * @param dropletDiameterUm droplet diameter in micrometers
   * @param continuousDensity density of continuous phase in kg/m³
   * @param dispersedDensity density of dispersed phase in kg/m³
   * @param continuousViscosity viscosity of continuous phase in Pa·s
   * @return terminal velocity in m/s
   */
  public double calculateStokesVelocity(double dropletDiameterUm, double continuousDensity,
      double dispersedDensity, double continuousViscosity) {
    double diameterM = dropletDiameterUm * 1e-6;
    double g = 9.81;
    double deltaDensity = Math.abs(continuousDensity - dispersedDensity);
    return (g * diameterM * diameterM * deltaDensity) / (18.0 * continuousViscosity);
  }

  /**
   * Calculates the maximum allowable gas velocity using Souders-Brown equation.
   *
   * @param gasDensity gas density in kg/m³
   * @param liquidDensity liquid density in kg/m³
   * @return maximum gas velocity in m/s
   */
  public double calculateSoudersBrownVelocity(double gasDensity, double liquidDensity) {
    double kEffective = gasLoadFactor * inletDeviceKFactor;
    return kEffective * Math.sqrt((liquidDensity - gasDensity) / gasDensity);
  }

  /**
   * Calculates the adjusted liquid volume accounting for foam.
   *
   * @param baseLiquidVolume base liquid volume in m³
   * @return adjusted volume with foam allowance in m³
   */
  public double calculateFoamAdjustedVolume(double baseLiquidVolume) {
    return baseLiquidVolume * foamAllowanceFactor;
  }

  /**
   * Loads process design parameters from the TechnicalRequirements_Process database.
   */
  public void loadProcessDesignParameters() {
    try {
      neqsim.util.database.NeqSimProcessDesignDataBase database =
          new neqsim.util.database.NeqSimProcessDesignDataBase();
      java.sql.ResultSet dataSet =
          database.getResultSet("SELECT * FROM technicalrequirements_process WHERE "
              + "EQUIPMENTTYPE='Separator' AND Company='" + getCompanySpecificDesignStandards()
              + "'");

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double minVal = dataSet.getDouble("MINVALUE");
        double maxVal = dataSet.getDouble("MAXVALUE");
        double value = (minVal + maxVal) / 2.0;

        switch (spec) {
          case "DesignPressureMargin":
            this.designPressureMargin = value;
            break;
          case "DesignTemperatureMargin":
            this.designTemperatureMarginC = value;
            break;
          case "MinDesignTemperature":
            this.minDesignTemperatureC = value;
            break;
          case "FoamAllowanceFactor":
            this.foamAllowanceFactor = value;
            break;
          case "DropletDiameterGas":
            this.dropletDiameterGasLiquid = value;
            break;
          case "DropletDiameterLiquid":
            this.dropletDiameterLiquidLiquid = value;
            break;
          case "MaxGasVelocity":
            this.maxGasVelocity = value;
            break;
          case "MaxLiquidVelocity":
            this.maxLiquidVelocity = value;
            break;
          case "MinLiquidRetentionOil":
            this.minOilRetentionTime = value;
            break;
          case "MinLiquidRetentionWater":
            this.minWaterRetentionTime = value;
            break;
          case "DemisterDeltaP":
            this.demisterPressureDrop = value;
            break;
          case "DemisterVoidFraction":
            this.demisterVoidFraction = value;
            break;
          case "InletDeviceKFactor":
            this.inletDeviceKFactor = value;
            break;
          default:
            // Ignore unknown parameters
            break;
        }
      }
      dataSet.close();
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  // ============================================================================
  // Validation Methods
  // ============================================================================

  /**
   * Validates that the actual gas velocity is within acceptable limits.
   *
   * @param actualVelocity actual gas velocity in m/s
   * @return true if velocity is acceptable, false if too high
   */
  public boolean validateGasVelocity(double actualVelocity) {
    return actualVelocity <= maxGasVelocity;
  }

  /**
   * Validates that the actual liquid outlet velocity is within acceptable limits.
   *
   * @param actualVelocity actual liquid velocity in m/s
   * @return true if velocity is acceptable, false if too high
   */
  public boolean validateLiquidVelocity(double actualVelocity) {
    return actualVelocity <= maxLiquidVelocity;
  }

  /**
   * Validates that the actual retention time meets minimum requirements.
   *
   * @param actualRetentionMinutes actual retention time in minutes
   * @param isOil true for oil retention, false for water retention
   * @return true if retention time is sufficient
   */
  public boolean validateRetentionTime(double actualRetentionMinutes, boolean isOil) {
    double minRequired = isOil ? minOilRetentionTime : minWaterRetentionTime;
    return actualRetentionMinutes >= minRequired;
  }

  /**
   * Validates that the droplet diameter is appropriate for separation.
   *
   * @param actualDropletDiameterUm actual droplet diameter in micrometers
   * @param isGasLiquid true for gas-liquid separation, false for liquid-liquid
   * @return true if droplet diameter is at or above design diameter
   */
  public boolean validateDropletDiameter(double actualDropletDiameterUm, boolean isGasLiquid) {
    double designDiameter = isGasLiquid ? dropletDiameterGasLiquid : dropletDiameterLiquidLiquid;
    return actualDropletDiameterUm >= designDiameter;
  }

  /**
   * Performs comprehensive validation of separator design against process requirements.
   *
   * @return ValidationResult with status and any issues found
   */
  public SeparatorValidationResult validateDesignComprehensive() {
    SeparatorValidationResult result = new SeparatorValidationResult();

    // Calculate actual gas velocity from design
    double crossSectionalArea = Math.PI * Math.pow(innerDiameter / 2.0, 2);
    double gasArea = crossSectionalArea * Fg;
    double actualGasVelocity = maxDesignVolumeFlow / gasArea;

    if (!validateGasVelocity(actualGasVelocity)) {
      result.addIssue("Gas velocity " + String.format("%.2f", actualGasVelocity)
          + " m/s exceeds maximum " + String.format("%.2f", maxGasVelocity) + " m/s");
    }

    // Validate retention time
    if (retentionTime < minOilRetentionTime * 60.0) {
      result.addIssue("Oil retention time " + String.format("%.1f", retentionTime / 60.0)
          + " min below minimum " + String.format("%.1f", minOilRetentionTime) + " min");
    }

    // Validate L/D ratio
    double ldRatio = tantanLength / innerDiameter;
    if (ldRatio < 2.0 || ldRatio > 6.0) {
      result.addIssue(
          "L/D ratio " + String.format("%.1f", ldRatio) + " outside recommended range 2.0-6.0");
    }

    // Validate design pressure margin
    if (designPressureMargin < 1.05) {
      result.addIssue("Design pressure margin " + String.format("%.2f", designPressureMargin)
          + " below recommended 1.05");
    }

    result.setValid(result.getIssues().isEmpty());
    return result;
  }

  /**
   * Inner class to hold validation results.
   */
  public static class SeparatorValidationResult {
    private boolean valid = true;
    private java.util.List<String> issues = new java.util.ArrayList<>();

    public boolean isValid() {
      return valid;
    }

    public void setValid(boolean valid) {
      this.valid = valid;
    }

    public java.util.List<String> getIssues() {
      return issues;
    }

    public void addIssue(String issue) {
      issues.add(issue);
    }
  }
}
