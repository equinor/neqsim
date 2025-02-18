package neqsim.process.mechanicaldesign.absorber;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.mechanicaldesign.designstandards.PressureVesselDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;

/**
 * <p>
 * AbsorberMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AbsorberMechanicalDesign extends SeparatorMechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Wall thickness in mm. */
  private double wallThickness = 0.02;
  private double outerDiameter = 0.0;
  double gasLoadFactor = 1.0;
  double volumeSafetyFactor = 1.0;

  /**
   * <p>
   * Constructor for AbsorberMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public AbsorberMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("pressure vessel design code")) {
      System.out.println("pressure vessel code standard: "
          + getDesignStandard().get("pressure vessel design code").getStandardName());
      wallThickness =
          ((PressureVesselDesignStandard) getDesignStandard().get("pressure vessel design code"))
              .calcWallThickness();
    } else {
      System.out.println("no pressure vessel code standard specified......");
      return;
    }

    if (getDesignStandard().containsKey("absorption dehydration process design")) {
      // molecularSieveWaterCapacity = ((SeparatorDesignStandard)
      // getDesignStandard().get("adsorption dehydration process
      // design")).getMolecularSieveWaterCapacity();
    } else {
      System.out.println("no separator process design specified......");
      return;
    }

    if (getDesignStandard().containsKey("separator process design")) {
      System.out.println("separator process design: "
          + getDesignStandard().get("separator process design").getStandardName());
      gasLoadFactor =
          ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
              .getGasLoadFactor();
      volumeSafetyFactor =
          ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
              .getVolumetricDesignFactor();
    } else {
      System.out.println("no separator process design specified......");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    SimpleTEGAbsorber separator = (SimpleTEGAbsorber) getProcessEquipment();
    double Fg = 1.0;

    double emptyVesselWeight = 0.0;
    double internalsWeight = 0.0;
    double externalNozzelsWeight = 0.0;
    double pipingWeight = 0.0;
    double structualWeight = 0.0;
    double electricalWeight = 0.0;
    double totalSkidWeight = 0.0;

    double moduleWidth = 0.0;

    double moduleHeight = 0.0;
    double moduleLength = 0.0;
    double materialsCost = 0.0;
    // double sepLength = 0.0;

    double gasDensity = ((Separator) getProcessEquipment()).getGasOutStream().getThermoSystem()
        .getPhase(0).getPhysicalProperties().getDensity();
    double liqDensity = 1000.0; // ((SimpleTEGAbsorber)
                                // getProcessEquipment()).getLiquidOutStream().getThermoSystem().getPhase(1).getPhysicalProperties().getDensity();

    // maxDesignVolumeFlow = volumeSafetyFactor * ((Separator)
    // getProcessEquipment()).getThermoSystem().getPhase(0).getVolume() / 1e5;

    double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
    innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
    tantanLength = innerDiameter * 5.0;
    System.out.println("inner Diameter " + innerDiameter);

    // calculating from standard codes

    emptyVesselWeight = 0.032 * getWallThickness() * innerDiameter * 1e3 * tantanLength;
    for (SeparatorSection sep : separator.getSeparatorSections()) {
      sep.getMechanicalDesign().calcDesign();
      internalsWeight += sep.getMechanicalDesign().getTotalWeight();
    }

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
    moduleHeight = innerDiameter * 2;
    // }

    setOuterDiameter(innerDiameter * 2.0 * getWallThickness());

    System.out.println("wall thickness: " + separator.getName() + " " + getWallThickness() + " mm");
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
