package neqsim.process.util.example;

import neqsim.process.equipment.separator.GasScrubberSimple;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * simpleGasScrubber class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class simpleGasScrubber {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
    testSystem.addComponent("methane", 1200.00);
    testSystem.addComponent("water", 1200.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    Stream stream_1 = new Stream("Stream1", testSystem);

    GasScrubberSimple gasScrubber = new GasScrubberSimple("Scrubber", stream_1);
    // gasScrubber.addScrubberSection("mesh");
    // gasScrubber.addScrubberSection("mesh2");
    Stream stream_2 = new Stream("gas from scrubber", gasScrubber.getGasOutStream());
    Stream stream_3 = new Stream("liquid from scrubber", gasScrubber.getLiquidOutStream());

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(gasScrubber);
    operations.add(stream_2);
    operations.add(stream_3);
    operations.run();
    // operations.displayResult();

    // operations.getSystemMechanicalDesign().setCompanySpecificDesignStandards("StatoilTR");

    // gasScrubber.getMechanicalDesign().calcDesign();
    // gasScrubber.getMechanicalDesign().displayResults();
    // operations.getSystemMechanicalDesign().runDesignCalculation();
    // double vol = operations.getSystemMechanicalDesign().getTotalVolume();
  }
}
