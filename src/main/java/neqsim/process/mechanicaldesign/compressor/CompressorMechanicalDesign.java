package neqsim.process.mechanicaldesign.compressor;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.compressor.CompressorCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * CompressorMechanicalDesign class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompressorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double compressorFactor = 0.0;

  /**
   * <p>
   * Constructor for CompressorMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
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
  @ExcludeFromJacocoGeneratedReport
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
    if (getWallThickness() <= 0.0) {
      setWallThickness(0.01);
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
