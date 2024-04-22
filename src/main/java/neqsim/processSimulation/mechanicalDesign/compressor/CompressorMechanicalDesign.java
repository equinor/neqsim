package neqsim.processSimulation.mechanicalDesign.compressor;






import neqsim.processSimulation.costEstimation.compressor.CompressorCostEstimate;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.CompressorDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;

/**
 * <p>
 * CompressorMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompressorMechanicalDesign extends MechanicalDesign {
  private static final long serialVersionUID = 1000;

  double compressorFactor = 0.0;

  /**
   * <p>
   * Constructor for CompressorMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface}
   *        object
   */
  public CompressorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new CompressorCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("compressor design codes")) {
      System.out.println("compressor code standard: "
          + getDesignStandard().get("compressor design codes").getStandardName());
      compressorFactor =
          ((CompressorDesignStandard) getDesignStandard().get("compressor design codes"))
              .getCompressorFactor();
    } else {
      System.out.println("no pressure vessel code standard specified......");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayResults() {}

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    Compressor compressor = (Compressor) getProcessEquipment();
    double Fg = 1.0;

    double emptyVesselWeight = 0.0;
    double internalsWeight = 0.0;
    double externalNozzelsWeight = 0.0;
    double pipingWeight = 0.0;
    double structualWeight = 0.0;
    double electricalWeight = 0.0;
    double totalSkidWeight = 0.0;

    // double moduleWidth = 0.0, moduleHeight = 0.0, moduleLength = 0.0;
    double materialsCost = 0.0;

    // double molecularWeight = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getMolarMass() * 1000.0;
    // double flowRate = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getVolume() / 1.0e5;
    // double gasDensity = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    // double liqDensity = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getPhase(1).getPhysicalProperties().getDensity();
    // double liqViscosity = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getPhase(1).getPhysicalProperties().getViscosity();

    // maxDesignVolumeFlow = ((Compressor)
    // getProcessEquipment()).getThermoSystem().getPhase(0).getVolume() / 1e5;

    double maxGasVelocity = 1; // Math.sqrt((liqDensity - gasDensity) / gasDensity);
    innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
    tantanLength = innerDiameter * 5.0;
    System.out.println("inner Diameter " + innerDiameter);

    // alternative design
    // double bubbleDiameter = 250.0e-6;
    // double bubVelocity = 1; // 9.82 * Math.pow(bubbleDiameter, 2.0) * (liqDensity
    double Ar = 1.0; // ((Separator)
                     // getProcessEquipment()).getThermoSystem().getPhase(1).getVolume()
                     // / 1e5 / bubVelocity;
    double Daim = Math.sqrt(Ar / 4.0);
    double Length2 = 4.0 * Daim;

    if (Daim > innerDiameter) {
      innerDiameter = Daim;
      tantanLength = Length2;
    }
    // calculating from standard codes
    // sepLength = innerDiameter * 2.0;
    emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;

    System.out.println("internal weight " + internalsWeight);

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
    // }

    setOuterDiameter(innerDiameter + 2.0 * getWallThickness());

    System.out.println("wall thickness: " + compressor.getName() + " " + getWallThickness() + " m");
    System.out.println("separator dry weigth: " + emptyVesselWeight + " kg");
    System.out.println("total skid weigth: " + totalSkidWeight + " kg");
    System.out.println("foot print: width:" + moduleWidth + " length " + moduleLength + " height "
        + moduleHeight + " meter.");
    System.out.println("mechanical price: " + materialsCost + " kNOK");

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
    // this method will be implemented to set calculated design...
  }

  /** {@inheritDoc} */
  @Override
  public double getOuterDiameter() {
    return outerDiameter;
  }

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
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }
}
