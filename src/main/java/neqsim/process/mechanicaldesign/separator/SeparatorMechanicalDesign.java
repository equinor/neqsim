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
  /** Volume safety factor for design flow calculations. Default 1.0 (no margin). */
  double volumeSafetyFactor = 1.0;
  /** Gas area fraction (1 - liquid level fraction). Default 0.5 for 50% liquid level. */
  double Fg = 0.5;
  /** Liquid retention time in seconds. Default 120s (2 minutes). */
  double retentionTime = 120.0;

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
}
