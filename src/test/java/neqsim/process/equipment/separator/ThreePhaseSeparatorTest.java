package neqsim.process.equipment.separator;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class ThreePhaseSeparatorTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  /**
   * Set up fluid for testing.
   *
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(243.15, 300.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 0.0);
    testSystem.addComponent("propane", 0.0);
    testSystem.addComponent("i-butane", 0.0);
    testSystem.addComponent("n-butane", 0.0);
    testSystem.addComponent("i-pentane", 0.0);
    testSystem.addComponent("n-pentane", 0.0);
    testSystem.addComponent("n-hexane", 0.0);
    testSystem.addComponent("nitrogen", 10.0);
    testSystem.setMixingRule("classic");
  }

  /**
   * Test method for run method.
   */
  @Disabled
  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);

    fluid1.addComponent("nitrogen", 0.110282450914383);
    fluid1.addComponent("CO2", 8.92014980316162);
    fluid1.addComponent("methane", 72.3870849609375);
    fluid1.addComponent("ethane", 5.19349813461304);
    fluid1.addComponent("propane", 5.20273065567017);
    fluid1.addComponent("i-butane", 0.436239510774612);
    fluid1.addComponent("n-butane", 1.39356422424316);
    fluid1.addComponent("i-pentane", 0.769362509250641);
    fluid1.addComponent("n-pentane", 0.543137490749359);
    fluid1.addComponent("n-hexane", 3.90587639808655);
    fluid1.addComponent("n-heptane", 3.90587639808655);
    fluid1.addComponent("water", 40.0);
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid1);

    testOps.TPflash();

    double[] intParameter = {-0.24, // "CO2"
        -0.721, // "methane"
        0.11, // "ethane"
        0.205, // "propane"
        0.081, // "i-butane"
        0.17, // "n-butane"
        0.051, // "i-pentane"
        0.1135, // "n-pentane"
        0.0832, // "n-hexane"
        0.0535 // "n-heptane"
    };

    String[] componentNames = fluid1.getComponentNames();

    for (int i = 0; i < intParameter.length; i++) {
      // except nitrogen 0
      int componentIndex = findComponentIndex(componentNames, componentNames[i + 1]);
      int waterIndex = findComponentIndex(componentNames, "water");

      if (componentIndex != -1 && waterIndex != -1) {
        ((PhaseEosInterface) fluid1.getPhases()[0]).getEosMixingRule()
            .setBinaryInteractionParameter(componentIndex, waterIndex, intParameter[i]);

        ((PhaseEosInterface) fluid1.getPhases()[1]).getEosMixingRule()
            .setBinaryInteractionParameter(componentIndex, waterIndex, intParameter[i]);
      } else {
      }
    }

    testOps.TPflash();
    // fluid1.prettyPrint();

    List<Double> molarComposition = new ArrayList<>();
    molarComposition.add(0.07649963805789309);
    molarComposition.add(10.028287212684818);
    molarComposition.add(49.52052228615394);
    molarComposition.add(3.64093888905641);
    molarComposition.add(3.6620992636511893);
    molarComposition.add(0.2995511776378937);
    molarComposition.add(0.9605423088257289);
    molarComposition.add(0.5032398365065283);
    molarComposition.add(0.36145746378993904);
    molarComposition.add(0.2364703087561068);
    molarComposition.add(2.732003176453634);
    molarComposition.add(27.978388438425913);

    double[] molarCompositionArray =
        molarComposition.stream().mapToDouble(Double::doubleValue).toArray();

    neqsim.thermo.system.SystemInterface fluid_test_separator = fluid1.clone();
    fluid_test_separator.setMolarComposition(molarCompositionArray);

    Stream inlet_stream_test_sep = new Stream("TEST_SEPARATOR_INLET", fluid_test_separator);
    inlet_stream_test_sep.setTemperature(72.6675872802734, "C");
    inlet_stream_test_sep.setPressure(10.6767892837524, "bara");
    inlet_stream_test_sep.setFlowRate(721.3143271348611, "kg/hr");
    inlet_stream_test_sep.run();

    ThreePhaseSeparator test_separator =
        new ThreePhaseSeparator("TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.run();
    test_separator.getWaterOutStream().getThermoSystem().prettyPrint();

    Heater heater_TP_setter_test_stream = new Heater("TP_SETTER_FOR_THE_DEGASSER_TEST_SEP_STREAM",
        test_separator.getWaterOutStream());
    heater_TP_setter_test_stream.setOutPressure(5.9061164855957 - 0.01, "bara");
    heater_TP_setter_test_stream.setOutTemperature(79.8487854003906, "C");
    heater_TP_setter_test_stream.run();
    // System.out.println("Gas out from degasser " +
    // heater_TP_setter_test_stream.getOutStream()
    // .getFluid().getPhase("gas").getFlowRate("kg/hr"));
    heater_TP_setter_test_stream.getOutletStream().getThermoSystem().prettyPrint();

    Heater heater_TP_setter_test_stream2 = new Heater("TP_SETTER_FOR_THE_DEGASSER_TEST_SEP_STREAM",
        test_separator.getWaterOutStream());
    heater_TP_setter_test_stream2.setOutPressure(5.9061164855957, "bara");
    heater_TP_setter_test_stream2.setOutTemperature(79.8487854003906, "C");
    heater_TP_setter_test_stream2.run();

    // System.out.println("Gas out from degasser2 " +
    // heater_TP_setter_test_stream2.getOutStream()
    // .getFluid().getPhase("gas").getFlowRate("kg/hr"));
  }

  private int findComponentIndex(String[] componentNames, String componentName) {
    for (int i = 0; i < componentNames.length; i++) {
      if (componentNames[i].equals(componentName)) {
        return i;
      }
    }

    return -1; // Component not found
  }

  /**
   * Test method for run method.
   */
  @Disabled
  void testRun2() {
    /*
     * XStream xstream = new XStream(); xstream.addPermission(AnyTypePermission.ANY); // Specify the
     * file path to read Path filePath = Paths.get(
     * "/workspaces/neqsim/src/test/java/neqsim/thermodynamicOperations/flashOps/my_process.xml" );
     * String xmlContents = ""; try { //xmlContents = Files.readString(filePath); } catch
     * (IOException e) { logger.error(e.getMessage());; }
     *
     * // Deserialize from xml neqsim.processSimulation.processSystem.ProcessSystem operationsCopy =
     * (neqsim.processSimulation.processSystem.ProcessSystem) xstream.fromXML(xmlContents);
     * operationsCopy.run(); neqsim.processSimulation.processEquipment.separator.Separator
     * VD02Separator = (neqsim.processSimulation.processEquipment.separator.Separator)
     * operationsCopy .getUnit("Separator after CFU gas");
     * neqsim.processSimulation.processEquipment.separator.Separator VD01Separator =
     * (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
     * .getUnit("Separator after degasser gas");
     * neqsim.processSimulation.processEquipment.separator.Separator Degasser =
     * (neqsim.processSimulation.processEquipment.separator.Separator) operationsCopy
     * .getUnit("Degasser"); System.out.
     * println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * Degasser.getGasOutStream().getFlowRate("kg/hr")); System.out.
     * println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * VD01Separator.getGasOutStream().getFlowRate("kg/hr")); VD02Separator.getGasOutStream().run();
     * System.out. println("The gas flow rate should be < 200 kg/hr, the actual value is " +
     * VD02Separator.getGasOutStream().getFlowRate("kg/hr"));
     */
  }
}
