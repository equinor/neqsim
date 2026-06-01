package neqsim.process.mechanicaldesign.separator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.entrainment.InletDeviceModel;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.mechanicaldesign.designstandards.GasScrubberDesignStandard;
import neqsim.process.mechanicaldesign.separator.conformity.ConformityReport;
import neqsim.process.mechanicaldesign.separator.conformity.ConformityRuleSet;
import neqsim.process.mechanicaldesign.separator.sectiontype.SepDesignSection;

/**
 * <p>
 * GasScrubberMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class GasScrubberMechanicalDesign extends SeparatorMechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GasScrubberMechanicalDesign.class);

  // ============================================================================
  // Inlet cyclone configuration
  // ============================================================================
  /** Whether inlet cyclones are installed. */
  private boolean hasInletCyclones = false;
  /** Number of inlet cyclones. */
  private int numberOfInletCyclones = 0;
  /** Inlet cyclone inner diameter [m]. */
  private double inletCycloneDiameterM = 0.0;

  // ============================================================================
  // Demisting cyclone configuration
  // ============================================================================
  /** Whether demisting cyclones are installed. */
  private boolean hasDemistingCyclones = false;
  /** Number of demisting cyclones. */
  private int numberOfDemistingCyclones = 0;
  /** Demisting cyclone inner diameter [m]. */
  private double demistingCycloneDiameterM = 0.0;
  /** Cyclone deck elevation from bottom of vessel [m]. */
  private double cycloneDeckElevationM = 0.0;
  /** Cyclone tube length [m]. */
  private double cycloneLengthM = 0.0;
  /** Cyclone Euler number (total dp vs rho*v^2). */
  private double cycloneEulerNumber = 4.5;
  /** Fraction of cyclone dp to drain chamber [%]. */
  private double cycloneDpToDrainPct = 60.0;

  // ============================================================================
  // Mesh pad configuration
  // ============================================================================
  /** Whether mesh pad is installed (above inlet, below cyclones). */
  private boolean hasMeshPad = false;
  /** Mesh pad area [m2]. */
  private double meshPadAreaM2 = 0.0;
  /** Mesh pad thickness [mm]. */
  private double meshPadThicknessMm = 100.0;
  /** Mesh pad centerline elevation from bottom of vessel [m]. */
  private double meshPadElevationM = 0.0;

  // ============================================================================
  // Vane pack configuration
  // ============================================================================
  /** Whether vane pack is installed. */
  private boolean hasVanePack = false;
  /** Vane pack area [m2]. */
  private double vanePackAreaM2 = 0.0;

  // ============================================================================
  // Drain pipe
  // ============================================================================
  /** Drain pipe inner diameter [m]. */
  private double drainPipeDiameterM = 0.0;

  // ============================================================================
  // TR1965 conformity metadata
  // ============================================================================
  /** Inlet device centerline elevation from bottom of vessel [m]. */
  private double inletDeviceElevationM = 0.0;
  /** Documented liquid entrainment to gas outlet [litre/MSm3]. */
  private double liquidEntrainmentLitresPerMSm3 = Double.NaN;
  /** Documented liquid design margin as fraction of operating liquid load. */
  private double liquidDesignMarginFraction = Double.NaN;

  // ============================================================================
  // Liquid level elevations from BTL [m] — optional for general use,
  // but LA(H) is required when cyclones are present (drainage height check)
  // ============================================================================
  /** LA(LL) — Low-Low level alarm elevation from BTL [m]. */
  private double laLLElevationM = 0.0;
  /** LA(L) — Low level alarm elevation from BTL [m]. */
  private double laLElevationM = 0.0;
  /**
   * LA(H) — High level alarm elevation from BTL [m]. Required for cyclone
   * drainage calc.
   */
  private double laHElevationM = 0.0;
  /** LA(HH) — High-High level alarm elevation from BTL [m]. */
  private double laHHElevationM = 0.0;

  /** Active conformity rule set, null if none set. */
  private transient ConformityRuleSet conformityRuleSet = null;

  /**
   * <p>
   * Constructor for GasScrubberMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface}
   *                  object
   */
  public GasScrubberMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("gas scrubber process design")) {
      logger.info("gas scrubber process design: {}",
          getDesignStandard().get("gas scrubber process design").getStandardName());
      gasLoadFactor = ((GasScrubberDesignStandard) getDesignStandard().get("gas scrubber process design"))
          .getGasLoadFactor();
      volumeSafetyFactor = ((GasScrubberDesignStandard) getDesignStandard().get("gas scrubber process design"))
          .getVolumetricDesignFactor();
    } else {
      logger.info("no gas scrubber process design specified");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    Separator separator = (Separator) getProcessEquipment();
    double Fg = 1.0;
    if (separator.getOrientation().equals("horizontal")) {
      Fg = 1.0 - separator.getDesignLiquidLevelFraction();
    }

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
    if (((SeparatorInterface) getProcessEquipment()).getThermoSystem().getNumberOfPhases() == 1) {
      liqDensity = getDefaultLiquidDensity();
    }
    maxDesignVolumeFlow = volumeSafetyFactor
        * ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0).getVolume()
        / 1e5;

    double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
    innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
    tantanLength = innerDiameter * 5.0;
    // System.out.println("inner Diameter " + innerDiameter);

    // calculating from standard codes
    // sepLength = innerDiameter * 2.0;
    emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;

    setOuterDiameter(innerDiameter + 2.0 * getWallThickness());
    for (SeparatorSection sep : separator.getSeparatorSections()) {
      sep.setOuterDiameter(getOuterDiameter());
      SepDesignSection sect = sep.getMechanicalDesign();
      sect.calcDesign();
      internalsWeight += sect.getTotalWeight();
    }

    // System.out.println("internal weight " + internalsWeight);

    externalNozzelsWeight = 0.0;
    double Wv = emptyVesselWeight + internalsWeight + externalNozzelsWeight;
    pipingWeight = Wv * 0.4;
    structualWeight = Wv * 0.1;
    electricalWeight = Wv * 0.08;
    totalSkidWeight = Wv + pipingWeight + structualWeight + electricalWeight;
    materialsCost = totalSkidWeight / 1000.0 * (1000 * 6.0) / 1000.0; // kNOK
    moduleWidth = innerDiameter * 2;
    moduleLength = innerDiameter * 2.5;
    moduleLength = tantanLength * 1.5;
    moduleHeight = innerDiameter * 2;
    /*
     * System.out.println("wall thickness: " + separator.getName() + " " +
     * getWallThickness() +
     * " m"); System.out.println("separator dry weigth: " + emptyVesselWeight +
     * " kg");
     * System.out.println("total skid weigth: " + totalSkidWeight + " kg");
     * System.out.println("foot print: width:" + moduleWidth + " length " +
     * moduleLength +
     * " height " + moduleHeight + " meter.");
     * System.out.println("mechanical price: " +
     * materialsCost + " kNOK");
     */
    setWeigthVesselShell(emptyVesselWeight);

    tantanLength = innerDiameter * 5;
    setInnerDiameter(innerDiameter);

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

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    ((SeparatorInterface) getProcessEquipment()).setInternalDiameter(innerDiameter);
    ((Separator) getProcessEquipment()).setSeparatorLength(tantanLength);
  }

  // ============================================================================
  // Conformity checking
  // ============================================================================

  /**
   * Sets the conformity standard to use for checking.
   *
   * <p>
   * This also enables the corresponding capacity constraints on the scrubber,
   * so that the optimizer and capacity reporting use the same criteria.
   * </p>
   *
   * @param standardName the standard identifier: "TR3500", "API-12J",
   *                     "Shell-DEP", "NORSOK-P002"
   */
  public void setConformityRules(String standardName) {
    this.conformityRuleSet = ConformityRuleSet.create(standardName);
    // Enable matching capacity constraints on the separator
    Separator sep = (Separator) getProcessEquipment();
    List<String> constraintNames = conformityRuleSet.getConstraintNames(this);
    sep.enableConstraints(constraintNames.toArray(new String[0]));
  }

  /**
   * Runs all applicable conformity checks using the current operating state.
   *
   * <p>
   * The scrubber must have been run (process simulation) before calling this
   * method, so that
   * the fluid state reflects current operating conditions.
   * </p>
   *
   * @return a conformity report with all check results
   * @throws IllegalStateException if no conformity rules have been set
   */
  public ConformityReport checkConformity() {
    if (conformityRuleSet == null) {
      throw new IllegalStateException(
          "No conformity rules set. Call setConformityRules(\"TR3500\") first.");
    }
    return conformityRuleSet.evaluate(this);
  }

  /**
   * Gets the active conformity rule set name, or null if none is set.
   *
   * @return the standard name, or null
   */
  public String getConformityStandard() {
    return conformityRuleSet != null ? conformityRuleSet.getName() : null;
  }

  /**
   * Sets the inlet device type by name string.
   *
   * <p>
   * Accepted names (case-insensitive): "schoepentoeter", "inlet_vane",
   * "inlet_cyclone",
   * "deflector_plate", "half_pipe", "impingement_plate", "none".
   * </p>
   *
   * @param deviceTypeName the inlet device type name
   * @throws IllegalArgumentException if the name does not match any known device
   *                                  type
   */
  public void setInletDevice(String deviceTypeName) {
    InletDeviceModel.InletDeviceType matched = null;
    for (InletDeviceModel.InletDeviceType t : InletDeviceModel.InletDeviceType.values()) {
      if (t.name().equalsIgnoreCase(deviceTypeName)
          || t.getDisplayName().equalsIgnoreCase(deviceTypeName)) {
        matched = t;
        break;
      }
    }
    if (matched == null) {
      throw new IllegalArgumentException("Unknown inlet device type: " + deviceTypeName
          + ". Use one of: schoepentoeter, inlet_vane, inlet_cyclone, "
          + "deflector_plate, half_pipe, impingement_plate, none");
    }
    setInletDeviceType(matched);
  }

  // ============================================================================
  // Inlet cyclone getters/setters
  // ============================================================================

  /**
   * Configures the inlet cyclones.
   *
   * @param numberOfCyclones number of inlet cyclones
   * @param cycloneDiameterM inlet cyclone inner diameter [m]
   */
  public void setInletCyclones(int numberOfCyclones, double cycloneDiameterM) {
    this.hasInletCyclones = true;
    this.numberOfInletCyclones = numberOfCyclones;
    this.inletCycloneDiameterM = cycloneDiameterM;
  }

  /**
   * Whether inlet cyclones are installed.
   *
   * @return true if inlet cyclones are configured
   */
  public boolean hasInletCyclones() {
    return hasInletCyclones;
  }

  /**
   * Gets the number of inlet cyclones.
   *
   * @return number of inlet cyclones
   */
  public int getNumberOfInletCyclones() {
    return numberOfInletCyclones;
  }

  /**
   * Gets the inlet cyclone inner diameter.
   *
   * @return cyclone diameter [m]
   */
  public double getInletCycloneDiameterM() {
    return inletCycloneDiameterM;
  }

  // ============================================================================
  // Demisting cyclone getters/setters
  // ============================================================================

  /**
   * Configures the demisting cyclones.
   *
   * @param numberOfCyclones number of demisting cyclones
   * @param cycloneDiameterM demisting cyclone inner diameter [m]
   * @param deckElevationM   cyclone deck elevation from bottom of vessel [m]
   */
  public void setDemistingCyclones(int numberOfCyclones, double cycloneDiameterM,
      double deckElevationM) {
    this.hasDemistingCyclones = true;
    this.numberOfDemistingCyclones = numberOfCyclones;
    this.demistingCycloneDiameterM = cycloneDiameterM;
    this.cycloneDeckElevationM = deckElevationM;
  }

  /**
   * Configures the demisting cyclones with tube length.
   *
   * @param numberOfCyclones number of demisting cyclones
   * @param cycloneDiameterM demisting cyclone inner diameter [m]
   * @param deckElevationM   cyclone deck elevation from bottom of vessel [m]
   * @param cycloneLengthM   cyclone tube length [m]
   */
  public void setDemistingCyclones(int numberOfCyclones, double cycloneDiameterM,
      double deckElevationM, double cycloneLengthM) {
    setDemistingCyclones(numberOfCyclones, cycloneDiameterM, deckElevationM);
    this.cycloneLengthM = cycloneLengthM;
  }

  /**
   * Whether demisting cyclones are installed.
   *
   * @return true if demisting cyclones are configured
   */
  public boolean hasDemistingCyclones() {
    return hasDemistingCyclones;
  }

  /**
   * Gets the number of demisting cyclones.
   *
   * @return number of demisting cyclones
   */
  public int getNumberOfDemistingCyclones() {
    return numberOfDemistingCyclones;
  }

  /**
   * Gets the demisting cyclone inner diameter.
   *
   * @return cyclone diameter [m]
   */
  public double getDemistingCycloneDiameterM() {
    return demistingCycloneDiameterM;
  }

  /**
   * Gets the cyclone deck elevation.
   *
   * @return deck elevation from bottom of vessel [m]
   */
  public double getCycloneDeckElevationM() {
    return cycloneDeckElevationM;
  }

  /**
   * Sets the cyclone deck elevation.
   *
   * @param elevationM deck elevation from bottom of vessel [m]
   */
  public void setCycloneDeckElevationM(double elevationM) {
    this.cycloneDeckElevationM = elevationM;
  }

  /**
   * Gets the cyclone tube length.
   *
   * @return cyclone tube length [m]
   */
  public double getCycloneLengthM() {
    return cycloneLengthM;
  }

  /**
   * Sets the cyclone tube length.
   *
   * @param lengthM cyclone tube length [m]
   */
  public void setCycloneLengthM(double lengthM) {
    this.cycloneLengthM = lengthM;
  }

  /**
   * Gets the cyclone Euler number for total pressure drop.
   *
   * @return Euler number (dp vs rho*v^2, not 0.5*rho*v^2)
   */
  public double getCycloneEulerNumber() {
    return cycloneEulerNumber;
  }

  /**
   * Sets the cyclone Euler number.
   *
   * @param eulerNumber Euler number for total dp
   */
  public void setCycloneEulerNumber(double eulerNumber) {
    this.cycloneEulerNumber = eulerNumber;
  }

  /**
   * Gets the fraction of cyclone dp to drain chamber.
   *
   * @return fraction [%]
   */
  public double getCycloneDpToDrainPct() {
    return cycloneDpToDrainPct;
  }

  /**
   * Sets the fraction of cyclone dp to drain chamber.
   *
   * @param pct fraction [%]
   */
  public void setCycloneDpToDrainPct(double pct) {
    this.cycloneDpToDrainPct = pct;
  }

  // ============================================================================
  // Mesh pad getters/setters
  // ============================================================================

  /**
   * Configures the mesh pad.
   *
   * @param areaM2      mesh pad area [m2]
   * @param thicknessMm mesh pad thickness [mm]
   */
  public void setMeshPad(double areaM2, double thicknessMm) {
    this.hasMeshPad = true;
    this.meshPadAreaM2 = areaM2;
    this.meshPadThicknessMm = thicknessMm;
  }

  /**
   * Whether mesh pad is installed.
   *
   * @return true if mesh pad is configured
   */
  public boolean hasMeshPad() {
    return hasMeshPad;
  }

  /**
   * Gets the mesh pad area.
   *
   * @return mesh pad area [m2]
   */
  public double getMeshPadAreaM2() {
    return meshPadAreaM2;
  }

  /**
   * Gets the mesh pad thickness.
   *
   * @return mesh pad thickness [mm]
   */
  public double getMeshPadThicknessMm() {
    return meshPadThicknessMm;
  }

  /**
   * Sets the mesh pad centerline elevation from the bottom tangent line.
   *
   * @param elevationM mesh pad elevation in m; values less than zero are allowed only for explicit
   *        project data corrections and will be checked by conformity rules
   */
  public void setMeshPadElevationM(double elevationM) {
    this.meshPadElevationM = elevationM;
  }

  /**
   * Gets the mesh pad centerline elevation from the bottom tangent line.
   *
   * @return mesh pad elevation in m, or zero when not configured
   */
  public double getMeshPadElevationM() {
    return meshPadElevationM;
  }

  // ============================================================================
  // Vane pack getters/setters
  // ============================================================================

  /**
   * Configures the vane pack.
   *
   * @param areaM2 vane pack area [m2]
   */
  public void setVanePack(double areaM2) {
    this.hasVanePack = true;
    this.vanePackAreaM2 = areaM2;
  }

  /**
   * Whether vane pack is installed.
   *
   * @return true if vane pack is configured
   */
  public boolean hasVanePack() {
    return hasVanePack;
  }

  /**
   * Gets the vane pack area.
   *
   * @return vane pack area [m2]
   */
  public double getVanePackAreaM2() {
    return vanePackAreaM2;
  }

  // ============================================================================
  // Drain pipe getters/setters
  // ============================================================================

  /**
   * Sets the drain pipe inner diameter.
   *
   * @param diameterM drain pipe ID [m]
   */
  public void setDrainPipeDiameterM(double diameterM) {
    this.drainPipeDiameterM = diameterM;
  }

  /**
   * Gets the drain pipe inner diameter.
   *
   * @return drain pipe ID [m]
   */
  public double getDrainPipeDiameterM() {
    return drainPipeDiameterM;
  }

  /**
   * Sets the inlet device centerline elevation from the bottom tangent line.
   *
   * @param elevationM inlet device elevation in m; values less than zero are allowed only for
   *        explicit project data corrections and will be checked by conformity rules
   */
  public void setInletDeviceElevationM(double elevationM) {
    this.inletDeviceElevationM = elevationM;
  }

  /**
   * Gets the inlet device centerline elevation from the bottom tangent line.
   *
   * @return inlet device elevation in m, or zero when not configured
   */
  public double getInletDeviceElevationM() {
    return inletDeviceElevationM;
  }

  /**
   * Sets the documented liquid entrainment to the gas outlet for conformity checking.
   *
   * @param entrainmentLitresPerMSm3 liquid entrainment in litre/MSm3; use {@link Double#NaN} when
   *        project data are unavailable
   */
  public void setLiquidEntrainmentLitresPerMSm3(double entrainmentLitresPerMSm3) {
    this.liquidEntrainmentLitresPerMSm3 = entrainmentLitresPerMSm3;
  }

  /**
   * Gets the documented liquid entrainment to the gas outlet.
   *
   * @return liquid entrainment in litre/MSm3, or NaN when not configured
   */
  public double getLiquidEntrainmentLitresPerMSm3() {
    return liquidEntrainmentLitresPerMSm3;
  }

  /**
   * Sets the documented liquid design margin as fraction of the operating liquid load.
   *
   * @param marginFraction liquid design margin fraction; 0.20 means 20 percent margin
   */
  public void setLiquidDesignMarginFraction(double marginFraction) {
    this.liquidDesignMarginFraction = marginFraction;
  }

  /**
   * Gets the documented liquid design margin as fraction of the operating liquid load.
   *
   * @return liquid design margin fraction, or NaN when not configured
   */
  public double getLiquidDesignMarginFraction() {
    return liquidDesignMarginFraction;
  }

  // ============================================================================
  // Liquid level alarm elevation getters/setters
  // ============================================================================

  /**
   * Sets the LA(LL) — Low-Low level alarm elevation from BTL.
   *
   * @param elevationM LA(LL) elevation [m]
   */
  public void setLaLLElevationM(double elevationM) {
    this.laLLElevationM = elevationM;
  }

  /**
   * Gets the LA(LL) elevation from BTL.
   *
   * @return LA(LL) elevation [m]
   */
  public double getLaLLElevationM() {
    return laLLElevationM;
  }

  /**
   * Sets the LA(L) — Low level alarm elevation from BTL.
   *
   * @param elevationM LA(L) elevation [m]
   */
  public void setLaLElevationM(double elevationM) {
    this.laLElevationM = elevationM;
  }

  /**
   * Gets the LA(L) elevation from BTL.
   *
   * @return LA(L) elevation [m]
   */
  public double getLaLElevationM() {
    return laLElevationM;
  }

  /**
   * Sets the LA(H) — High level alarm elevation from BTL. Required when demisting
   * cyclones are
   * present for drainage height conformity check.
   *
   * @param elevationM LA(H) elevation [m]
   */
  public void setLaHElevationM(double elevationM) {
    this.laHElevationM = elevationM;
  }

  /**
   * Gets the LA(H) elevation from BTL.
   *
   * @return LA(H) elevation [m]
   */
  public double getLaHElevationM() {
    return laHElevationM;
  }

  /**
   * Sets the LA(HH) — High-High level alarm elevation from BTL.
   *
   * @param elevationM LA(HH) elevation [m]
   */
  public void setLaHHElevationM(double elevationM) {
    this.laHHElevationM = elevationM;
  }

  /**
   * Gets the LA(HH) elevation from BTL.
   *
   * @return LA(HH) elevation [m]
   */
  public double getLaHHElevationM() {
    return laHHElevationM;
  }

  /**
   * Sets the HHLL elevation from bottom of vessel. Kept for backward
   * compatibility; prefer
   * {@link #setLaHElevationM(double)} for drainage calculations.
   *
   * @param elevationM HHLL elevation [m]
   * @deprecated use {@link #setLaHHElevationM(double)} instead
   */
  @Deprecated
  public void setHhllElevationM(double elevationM) {
    this.laHHElevationM = elevationM;
  }

  /**
   * Gets the HHLL elevation from bottom of vessel. Kept for backward
   * compatibility.
   *
   * @return HHLL elevation [m]
   * @deprecated use {@link #getLaHHElevationM()} instead
   */
  @Deprecated
  public double getHhllElevationM() {
    return laHHElevationM;
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * {@inheritDoc} Overrides to populate scrubber-specific parameters (internals,
   * elevations) into
   * the JSON response.
   */
  @Override
  public SeparatorMechanicalDesignResponse getResponse() {
    SeparatorMechanicalDesignResponse resp = super.getResponse();
    resp.addSpecificParameter("equipmentSubType", "GasScrubber");

    // Vessel geometry (stored in MechanicalDesign; Separator delegates to us)
    double vesselID = innerDiameter;
    double vesselLen = tantanLength;
    resp.addSpecificParameter("vesselInnerDiameter_mm", vesselID * 1000.0);
    resp.addSpecificParameter("vesselTanTan_mm", vesselLen * 1000.0);
    resp.addSpecificParameter("inletNozzleID_mm", getInletNozzleID() * 1000.0);

    // Inlet device
    if (hasInletCyclones) {
      Map<String, Object> inletCyc = new LinkedHashMap<String, Object>();
      inletCyc.put("type", "Inlet Cyclones");
      inletCyc.put("count", numberOfInletCyclones);
      inletCyc.put("diameter_mm", inletCycloneDiameterM * 1000.0);
      resp.addSpecificParameter("inletDevice", inletCyc);
    }

    // Mesh pad
    if (hasMeshPad) {
      Map<String, Object> mesh = new LinkedHashMap<String, Object>();
      mesh.put("area_m2", meshPadAreaM2);
      mesh.put("thickness_mm", meshPadThicknessMm);
      if (meshPadElevationM > 0) {
        mesh.put("elevation_mm", meshPadElevationM * 1000.0);
      }
      resp.addSpecificParameter("meshPad", mesh);
    }

    // Vane pack
    if (hasVanePack) {
      Map<String, Object> vane = new LinkedHashMap<String, Object>();
      vane.put("area_m2", vanePackAreaM2);
      resp.addSpecificParameter("vanePack", vane);
    }

    // Demisting cyclone deck
    if (hasDemistingCyclones) {
      Map<String, Object> cyc = new LinkedHashMap<String, Object>();
      cyc.put("count", numberOfDemistingCyclones);
      cyc.put("diameter_mm", demistingCycloneDiameterM * 1000.0);
      cyc.put("deckElevation_mm", cycloneDeckElevationM * 1000.0);
      cyc.put("eulerNumber", cycloneEulerNumber);
      cyc.put("dpToDrain_pct", cycloneDpToDrainPct);
      resp.addSpecificParameter("demistingCyclones", cyc);
    }

    // Drain pipe
    if (drainPipeDiameterM > 0) {
      resp.addSpecificParameter("drainPipeDiameter_mm", drainPipeDiameterM * 1000.0);
    }
    if (inletDeviceElevationM > 0) {
      resp.addSpecificParameter("inletDeviceElevation_mm", inletDeviceElevationM * 1000.0);
    }
    if (Double.isFinite(liquidEntrainmentLitresPerMSm3)) {
      resp.addSpecificParameter("liquidEntrainment_litre_per_MSm3",
          liquidEntrainmentLitresPerMSm3);
    }
    if (Double.isFinite(liquidDesignMarginFraction)) {
      resp.addSpecificParameter("liquidDesignMargin_fraction", liquidDesignMarginFraction);
    }

    // Liquid levels
    Map<String, Object> levels = new LinkedHashMap<String, Object>();
    if (laLLElevationM > 0) {
      levels.put("LA_LL_mm", laLLElevationM * 1000.0);
    }
    if (laLElevationM > 0) {
      levels.put("LA_L_mm", laLElevationM * 1000.0);
    }
    if (laHElevationM > 0) {
      levels.put("LA_H_mm", laHElevationM * 1000.0);
    }
    if (laHHElevationM > 0) {
      levels.put("LA_HH_mm", laHHElevationM * 1000.0);
    }
    if (!levels.isEmpty()) {
      resp.addSpecificParameter("liquidLevels", levels);
    }

    // Drainage height
    if (hasDemistingCyclones && laHHElevationM > 0) {
      double drainageHeight = cycloneDeckElevationM - laHHElevationM;
      resp.addSpecificParameter("drainageHeightAvailable_mm", drainageHeight * 1000.0);
    }

    return resp;
  }

  /**
   * Generates a formatted text report of the scrubber mechanical design
   * configuration. Shows
   * vessel geometry, internals, elevations, and liquid levels in a readable table
   * format.
   *
   * @return formatted text report string
   */
  public String toTextReport() {
    Separator sep = (Separator) getProcessEquipment();
    // Geometry is stored in MechanicalDesign; Separator delegates to us
    double vesselID = innerDiameter;
    double vesselLen = tantanLength;

    StringBuilder sb = new StringBuilder();
    String line = "======================================================================";
    String sep2 = "----------------------------------------------------------------------";

    sb.append(line).append('\n');
    sb.append("  SCRUBBER MECHANICAL DESIGN: ").append(sep.getName()).append('\n');
    sb.append(line).append('\n');

    // Vessel geometry
    sb.append('\n');
    sb.append("  VESSEL GEOMETRY\n");
    sb.append(sep2).append('\n');
    appendRow(sb, "Internal Diameter", String.format("%.0f mm", vesselID * 1000.0));
    appendRow(sb, "Tan-Tan Length", String.format("%.0f mm", vesselLen * 1000.0));
    appendRow(sb, "Orientation", sep.getOrientation());
    appendRow(sb, "Inlet Nozzle ID", String.format("%.1f mm", getInletNozzleID() * 1000.0));
    if (getGasOutletNozzleID() > 0) {
      appendRow(sb, "Gas Outlet Nozzle ID", String.format("%.1f mm",
          getGasOutletNozzleID() * 1000.0));
    }

    // Internals
    sb.append('\n');
    sb.append("  INTERNALS\n");
    sb.append(sep2).append('\n');
    if (hasInletCyclones) {
      appendRow(sb, "Inlet Device", "Inlet Cyclones");
      appendRow(sb, "  Count", String.valueOf(numberOfInletCyclones));
      appendRow(sb, "  Cyclone Diameter", String.format("%.0f mm",
          inletCycloneDiameterM * 1000.0));
    } else {
      appendRow(sb, "Inlet Device", "Schoepentoeter / Inlet Vane (via Separator)");
    }
    if (hasMeshPad) {
      appendRow(sb, "Mesh Pad", "Installed");
      appendRow(sb, "  Area", String.format("%.3f m2", meshPadAreaM2));
      appendRow(sb, "  Thickness", String.format("%.0f mm", meshPadThicknessMm));
    }
    if (hasVanePack) {
      appendRow(sb, "Vane Pack", "Installed");
      appendRow(sb, "  Area", String.format("%.3f m2", vanePackAreaM2));
    }
    if (hasDemistingCyclones) {
      appendRow(sb, "Demisting Cyclones", "Installed");
      appendRow(sb, "  Count", String.valueOf(numberOfDemistingCyclones));
      appendRow(sb, "  Cyclone Diameter", String.format("%.0f mm",
          demistingCycloneDiameterM * 1000.0));
      appendRow(sb, "  Deck Elevation (BTL)", String.format("%.0f mm",
          cycloneDeckElevationM * 1000.0));
      appendRow(sb, "  Euler Number", String.format("%.1f", cycloneEulerNumber));
      appendRow(sb, "  DP to Drain", String.format("%.0f %%", cycloneDpToDrainPct));
    }
    if (drainPipeDiameterM > 0) {
      appendRow(sb, "Drain Pipe Equiv. ID", String.format("%.1f mm",
          drainPipeDiameterM * 1000.0));
    }

    // Liquid levels
    sb.append('\n');
    sb.append("  LIQUID LEVELS (from BTL)\n");
    sb.append(sep2).append('\n');
    if (laLLElevationM > 0) {
      appendRow(sb, "LA(LL)", String.format("%.0f mm", laLLElevationM * 1000.0));
    }
    if (laLElevationM > 0) {
      appendRow(sb, "LA(L)", String.format("%.0f mm", laLElevationM * 1000.0));
    }
    if (laHElevationM > 0) {
      appendRow(sb, "LA(H)", String.format("%.0f mm", laHElevationM * 1000.0));
    }
    if (laHHElevationM > 0) {
      appendRow(sb, "LA(HH)", String.format("%.0f mm", laHHElevationM * 1000.0));
    }
    if (laLLElevationM == 0 && laLElevationM == 0 && laHElevationM == 0
        && laHHElevationM == 0) {
      appendRow(sb, "(none set)", "");
    }

    // Drainage summary
    if (hasDemistingCyclones && laHHElevationM > 0) {
      sb.append('\n');
      sb.append("  DRAINAGE CHECK (per API 12J / TR3500)\n");
      sb.append(sep2).append('\n');
      double drainageHeight = (cycloneDeckElevationM - laHHElevationM) * 1000.0;
      appendRow(sb, "Reference Level", "LA(HH) (most conservative)");
      appendRow(sb, "Cyclone Deck Bottom", String.format("%.0f mm",
          cycloneDeckElevationM * 1000.0));
      appendRow(sb, "LA(HH)", String.format("%.0f mm", laHHElevationM * 1000.0));
      appendRow(sb, "Height Available", String.format("%.0f mm", drainageHeight));
    }

    sb.append('\n');
    sb.append(line).append('\n');
    return sb.toString();
  }

  /**
   * Appends a formatted row to the text report.
   *
   * @param sb    the StringBuilder to append to
   * @param label the row label
   * @param value the row value
   */
  private void appendRow(StringBuilder sb, String label, String value) {
    sb.append(String.format("  %-25s : %s%n", label, value));
  }
}
