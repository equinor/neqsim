package neqsim.process.mechanicaldesign.separator;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
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
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * SeparatorMechanicalDesign class.
 * </p>
 *
 * Provides mechanical design calculations for separators including sizing, wall
 * thickness,
 * weight estimation, and management of demisting internals.
 *
 * <p>
 * For detailed documentation on separator internals and carry-over
 * calculations, see:
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/separators_and_internals.md">
 * Separators and Internals Wiki</a> and
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/carryover_calculations.md">
 * Carry-Over Calculations Wiki</a>
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @see neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign
 * @see neqsim.process.mechanicaldesign.separator.internals.DemistingInternal
 */
public class SeparatorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SeparatorMechanicalDesign.class);
  double gasLoadFactor = 1.0;
  double volumeSafetyFactor = 1.0;
  double Fg = 1.0;
  double retentionTime = 60.0;
  double nozzleInnerDiameter = 0.0;

  /** List of deisting internals in the separator. */
  private List<DemistingInternal> demistingInternals = new ArrayList<>();

  /**
   * <p>
   * Constructor for SeparatorMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface}
   *                  object
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
      logger.debug("no material plate design codes specified");
    }
    if (getDesignStandard().containsKey("pressure vessel design code")) {
      logger.debug("pressure vessel code standard: {}",
          getDesignStandard().get("pressure vessel design code").getStandardName());
      wallThickness = ((PressureVesselDesignStandard) getDesignStandard().get("pressure vessel design code"))
          .calcWallThickness();
    } else {
      logger.debug("no pressure vessel code standard specified");
    }

    if (getDesignStandard().containsKey("separator process design")) {
      logger.debug("separator process design: {}",
          getDesignStandard().get("separator process design").getStandardName());
      gasLoadFactor = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
          .getGasLoadFactor();
      Fg = ((SeparatorDesignStandard) getDesignStandard().get("separator process design")).getFg();
      volumeSafetyFactor = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
          .getVolumetricDesignFactor();
      retentionTime = 120.0; // ((SeparatorDesignStandard)
                             // getDesignStandard().get("separator process
                             // design")).getLiquidRetentionTime("API12J", this);
    } else {
      logger.debug("no separator process design specified");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = { "Name", "Value", "Unit" };
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
    double bubVelocity = 9.82 * Math.pow(bubbleDiameter, 2.0) * (liqDensity - gasDensity) / 18.0 / liqViscosity;
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

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    ((SeparatorInterface) getProcessEquipment()).setInternalDiameter(innerDiameter);
    ((Separator) getProcessEquipment()).setSeparatorLength(tantanLength);
    // this method will be implemented to set calculated design...
  }

  /**
   * Add a demisting internal to the separator.
   *
   * @param internal the DemistingInternal to add
   */
  public void addDemistingInternal(DemistingInternal internal) {
    demistingInternals.add(internal);
    // Set separator reference if equipment is a Separator
    if (getProcessEquipment() instanceof neqsim.process.equipment.separator.Separator) {
      neqsim.process.equipment.separator.Separator separator = (neqsim.process.equipment.separator.Separator) getProcessEquipment();
      internal.setSeparator(separator);
    }
  }

  /**
   * Remove a deisting internal from the separator.
   *
   * @param internal the DemistingInternal to remove
   * @return true if the internal was removed, false otherwise
   */
  public boolean removeDemistingInternal(DemistingInternal internal) {
    return demistingInternals.remove(internal);
  }

  /**
   * Get all deisting internals in the separator.
   *
   * @return list of DemistingInternal objects
   */
  public List<DemistingInternal> getDemistingInternals() {
    return new ArrayList<>(demistingInternals);
  }

  /**
   * Get the number of deisting internals.
   *
   * @return number of internals
   */
  public int getNumberOfDemistingInternals() {
    return demistingInternals.size();
  }

  /**
   * Calculate total deisting area from all internals.
   *
   * @return total area in m²
   */
  public double getTotalDemistingArea() {
    return demistingInternals.stream().mapToDouble(DemistingInternal::getArea).sum();
  }

  /**
   * Calculate total liquid carry-over from all deisting internals.
   *
   * @param gasDensity         gas density in kg/m³
   * @param liquidDensity      liquid density in kg/m³
   * @param inletLiquidContent inlet liquid content (mass fraction)
   * @return total liquid carry-over (mass fraction)
   */
  public double calcTotalLiquidCarryOver(double gasDensity, double liquidDensity,
      double inletLiquidContent) {
    double totalCarryOver = inletLiquidContent;

    for (DemistingInternal internal : demistingInternals) {
      // Use the separator-aware carry-over calculation
      totalCarryOver *= (1.0 - internal.calcEfficiency());
    }

    return totalCarryOver;
  }

  /**
   * Calculate total pressure drop across all deisting internals.
   *
   * @param gasDensity gas density in kg/m³
   * @return total pressure drop in Pa
   */
  public double calcTotalPressureDrop(double gasDensity) {
    double totalPressureDrop = 0.0;

    double totalArea = getTotalDemistingArea();
    if (totalArea == 0) {
      return 0.0;
    }

    double volumetricFlow = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0).getVolume()
        / 1e5;

    for (DemistingInternal internal : demistingInternals) {
      double gasVelocity = internal.calcGasVelocity(volumetricFlow);
      totalPressureDrop += internal.calcPressureDrop(gasDensity, gasVelocity);
    }

    return totalPressureDrop;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a separator-specific response with additional fields for vessel
   * sizing, internals, and
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
   * Get volume safety factor.
   *
   * @return volume safety factor
   */
  public double getVolumeSafetyFactor() {
    return volumeSafetyFactor;
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
   * Get retention time in seconds.
   *
   * @return retention time
   */
  public double getRetentionTime() {
    return retentionTime;
  }
}
