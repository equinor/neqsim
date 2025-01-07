package neqsim.process.mechanicaldesign.pipeline;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PipelineDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PipelineMechanicalDesign class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipelineMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double innerDiameter = 1.0;
  String designStandardCode = "ANSI/ASME Standard B31.8";

  /**
   * <p>
   * Constructor for PipelineMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public PipelineMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("material pipe design codes")) {
      ((MaterialPipeDesignStandard) getDesignStandard().get("material pipe design codes"))
          .getDesignFactor();
    }
    if (getDesignStandard().containsKey("pipeline design codes")) {
      System.out.println("pressure vessel code standard: "
          + getDesignStandard().get("pipeline design codes").getStandardName());
      wallThickness = ((PipelineDesignStandard) getDesignStandard().get("pipeline design codes"))
          .calcPipelineWallThickness();
    } else {
      System.out.println("no pressure vessel code standard specified......");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    /*
     * Pipeline pipeline = (Pipeline) getProcessEquipment(); double flow = ((AdiabaticPipe)
     * getProcessEquipment()).getOutStream().getThermoSystem().getVolume() / 1e5;
     *
     * double innerArea = Math.PI * innerDiameter * innerDiameter / 4.0; double gasVelocity = flow /
     * innerArea; double wallThickness = 0.0;
     */

    // ASME/ANSI Code B31.8
    if (designStandardCode.equals("ANSI/ASME Standard B31.8")) {
      wallThickness = getMaxOperationPressure() * innerDiameter
          / (2.0 * getMaterialPipeDesignStandard().getDesignFactor()
              * getMaterialPipeDesignStandard().getEfactor()
              * getMaterialPipeDesignStandard().getTemperatureDeratingFactor()
              * getMaterialPipeDesignStandard().getMinimumYeildStrength());
    } else if (designStandardCode.equals("ANSI/ASME Standard B31.3")) {
      wallThickness = 0.0001; // to be implemented
      // ((AdiabaticPipe)
      // getProcessEquipment()).getMechanicalDesign().getMaxOperationPressure() *
      // innerDiameter / (2.0 * ((AdiabaticPipe)
      // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getDesignFactor()
      // * ((AdiabaticPipe)
      // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getEfactor()
      // * ((AdiabaticPipe)
      // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getTemperatureDeratingFactor()
      // * ((AdiabaticPipe)
      // getProcessEquipment()).getMechanicalDesign().getMaterialPipeDesignStandard().getMinimumYeildStrength());
    }
    // iterate to find correct diameter -> between 60-80 ft/sec9

    // double length = pipeline.getLength();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 90.00);
    testSystem.addComponent("methane", 600e3, "kg/hr");
    testSystem.addComponent("ethane", 7.00e3, "kg/hr");
    testSystem.addComponent("propane", 12.0e3, "kg/hr");

    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);

    AdiabaticPipe pipe = new AdiabaticPipe("pipe", stream_1);
    pipe.setDiameter(1.0);
    pipe.setLength(1000.0);
    pipe.getMechanicalDesign().setMaxOperationPressure(100.0);
    pipe.getMechanicalDesign().setMaxOperationTemperature(273.155 + 60.0);
    pipe.getMechanicalDesign().setMinOperationPressure(50.0);
    pipe.getMechanicalDesign().setMaxDesignGassVolumeFlow(100.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);

    // operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("Statoil");
    // operations.getSystemMechanicalDesign().runDesignCalculation();
    // operations.getSystemMechanicalDesign().setDesign();
    operations.run();
  }
}
