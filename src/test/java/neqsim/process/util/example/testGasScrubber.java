package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.separator.GasScrubberSimple;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * testGasScrubber class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class testGasScrubber {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(testGasScrubber.class);

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
        new neqsim.thermo.system.SystemSrkEos((273.15 + 20.0), 66.00);
    testSystem.addComponent("methane", 600e3, "kg/hr");
    testSystem.addComponent("ethane", 7.00e3, "kg/hr");
    testSystem.addComponent("propane", 12.0e3, "kg/hr");
    testSystem.addComponent("water", 10.0e3, "kg/hr");
    // testSystem.addComponent("water", 20.00e3, "kg/hr");
    // testSystem.addComponent("water", 2000.00, "kg/hr");
    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(2);

    Stream stream_1 = new Stream("Stream1", testSystem);

    GasScrubberSimple scrubber = new GasScrubberSimple("scrubber", stream_1);
    scrubber.setInternalDiameter(3.750);
    scrubber.setSeparatorLength(4.0);
    scrubber.getMechanicalDesign().setMaxOperationPressure(70.0);

    scrubber.addSeparatorSection("bottom manway", "manway");
    scrubber.addSeparatorSection("dp nozzle 1", "nozzle");
    scrubber.getSeparatorSection("dp nozzle 1").getMechanicalDesign().setNominalSize("DN 100");
    scrubber.addSeparatorSection("dp nozzle 2", "nozzle");
    scrubber.getSeparatorSection("dp nozzle 2").getMechanicalDesign().setNominalSize("DN 100");
    scrubber.addSeparatorSection("inlet vane", "vane");
    scrubber.getSeparatorSection("inlet vane").setCalcEfficiency(true);
    scrubber.addSeparatorSection("top mesh", "meshpad");
    scrubber.getSeparatorSection(1).setCalcEfficiency(true);
    scrubber.addSeparatorSection("top manway", "manway");
    scrubber.getSeparatorSection("top manway").getMechanicalDesign().setANSIclass(300);
    scrubber.getSeparatorSection("top manway").getMechanicalDesign().setNominalSize("DN 500");

    Stream stream_2 = new Stream("stream_2", scrubber.getGasOutStream());

    AdiabaticPipe pipe = new AdiabaticPipe("pipe", stream_2);
    pipe.setDiameter(0.4);
    pipe.setLength(10);

    StreamInterface stream_3 = pipe.getOutletStream();

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(scrubber);
    operations.add(stream_2);
    operations.add(pipe);
    operations.add(stream_3);
    operations.run();
    operations.displayResult();
    scrubber.getGasLoadFactor();

    // do design of process
    // operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("StatoilTR");

    // operations.runProcessDesignCalculation();
    // prosessdesign do: calculate flow rates of TEG, amine, calculates heat duty
    // requirements, compressor power requirements, number of trays, packing height,
    // numner of theoretical stages,
    // operations.runMechanicalDesignCalculation();
    // calculates diameters of colums, wall thickness, weight, size of equipment,
    // tray spacing
    // operations.getSystemMechanicalDesign().runDesignCalculation();
    // operations.getSystemMechanicalDesign().setDesign();
    operations.run();
    scrubber.getGasLoadFactor();
    // operations.calcDesign();
    scrubber.getMechanicalDesign().calcDesign();
    // scrubber.getMechanicalDesign().setDesignStandard("ASME - Pressure Vessel
    // Code");
    // scrubber.getMechanicalDesign().setDesignStandard("BS 5500 - Pressure
    // Vessel");

    logger.info("vane top veight "
        + scrubber.getSeparatorSection("inlet vane").getMechanicalDesign().getTotalWeight());

    logger.info("curryover " + scrubber.calcLiquidCarryoverFraction());
    logger.info("gas vel " + scrubber.getGasSuperficialVelocity());
    logger.info("gas load factor oil " + scrubber.getGasLoadFactor());
    // logger.info("gas load factor water " + scrubber.getGasLoadFactor(2));
    logger.info("derated gas load factor oil " + scrubber.getDeRatedGasLoadFactor());
    // logger.info("derated gas load factor water " +
    // scrubber.getDeRatedGasLoadFactor(2));

    logger.info("minimum liquid seal height "
        + scrubber.getSeparatorSection(0).getMinimumLiquidSealHeight());
    scrubber.getMechanicalDesign().displayResults();
  }
}
