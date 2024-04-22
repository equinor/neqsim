package neqsim.processSimulation.mechanicalDesign.separator;






import neqsim.processSimulation.costEstimation.separator.SeparatorCostEstimate;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.MaterialPlateDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.PressureVesselDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.SeparatorDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 * <p>
 * SeparatorMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorMechanicalDesign extends MechanicalDesign {
  private static final long serialVersionUID = 1000;
  double gasLoadFactor = 1.0;
  double volumeSafetyFactor = 1.0;
  double Fg = 1.0;
  double retentionTime = 60.0;

  /**
   * <p>
   * Constructor for SeparatorMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface}
   *        object
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
  public void displayResults() {}

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
      // .println("Fg need to be modified ... L/D separator= " + sepratorLength / innerDiameter);
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

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    ((SeparatorInterface) getProcessEquipment()).setInternalDiameter(innerDiameter);
    ((Separator) getProcessEquipment()).setSeparatorLength(tantanLength);
    // this method will be implemented to set calculated design...
  }
}
