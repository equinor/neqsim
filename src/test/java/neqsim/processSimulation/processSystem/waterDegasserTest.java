package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class waterDegasserTest {

  @Test
  public void runProcess() throws InterruptedException {
    neqsim.thermo.system.SystemSrkCPAstatoil fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    fluid1.addComponent("water", 0.129064569343353);
    fluid1.addComponent("nitrogen", 2.08503464752491e-003);
    fluid1.addComponent("CO2", 5.18541155388192e-002);
    fluid1.addComponent("methane", 0.642060306038335);
    fluid1.addComponent("ethane", 6.18486148336841e-002);
    fluid1.addComponent("propane", 3.00056835824721e-002);
    fluid1.addComponent("i-butane", 3.80588549711585e-003);
    fluid1.addComponent("n-butane", 1.09512595091539e-002);
    fluid1.addComponent("i-pentane", 3.68079747291908e-003);
    fluid1.addComponent("n-pentane", 5.22448994708524e-003);
    fluid1.addTBPfraction("nC6", 6.17521448341365e-003, 86.1800003051758 / 1000,
        86.1800003051758 / (1000 * 0.131586722637079));
    fluid1.addTBPfraction("C7", 1.04929815621011e-002, 94.8470001220703 / 1000,
        94.8470001220703 / (1000 * 0.130402631747591));
    fluid1.addTBPfraction("C8", 0.010589123136316, 106.220001220703 / 1000,
        106.220001220703 / (1000 * 0.141086913827126));
    fluid1.addTBPfraction("C9", 6.46674774929045e-003, 120.457000732422 / 1000,
        120.457000732422 / (1000 * 0.156630031108116));
    fluid1.addTBPfraction("C10-C11", 6.80218048553199e-003, 140.369003295898 / 1000,
        140.369003295898 / (1000 * 0.178710051949529));
    fluid1.addTBPfraction("C12-C13", 4.33960157130810e-003, 167.561996459961 / 1000,
        167.561996459961 / (1000 * 0.208334072812978));
    fluid1.addTBPfraction("C14-C15", 3.40122075476944e-003, 197.501007080078 / 1000,
        197.501007080078 / (1000 * 0.240670271622303));
    fluid1.addTBPfraction("C16-C17", 2.36077988165126e-003, 229.033996582031 / 1000,
        229.033996582031 / (1000 * 0.274302534479916));
    fluid1.addTBPfraction("C18-C20", 2.59750535903308e-003, 262.010986328125 / 1000,
        262.010986328125 / (1000 * 0.308134346902454));
    fluid1.addTBPfraction("C21-C23", 1.69428615721243e-003, 303.558990478516 / 1000,
        303.558990478516 / (1000 * 0.350224115520606));
    fluid1.addTBPfraction("C24-C28", 1.80310576675310e-003, 355.920013427734 / 1000,
        355.920013427734 / (1000 * 0.402198101307449));
    fluid1.addTBPfraction("C29-C35", 1.38447523691741e-003 + 1.29512096948304e-003,
        437.281005859375 / 1000, 437.281005859375 / (1000.0 * 0.481715346021770));
    // fluid1.addTBPfraction("C36-C80",1.29512096948304e-003,608.036010742188/1000,608.036010742188/(1000*0.642772477456171));
    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);
    fluid1.init(0);

    neqsim.processSimulation.processEquipment.stream.Stream inlet_stream_VA_01 =
        new neqsim.processSimulation.processEquipment.stream.Stream("INLET_STREAM_1ST_SEPARATOR",
            fluid1);
    inlet_stream_VA_01.setTemperature(79.36872863769531, "C");
    inlet_stream_VA_01.setPressure(66.97672112018574, "bara");
    inlet_stream_VA_01.setFlowRate(273050.4311185292, "kg/hr");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator separator_VA_01 =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "FIRST_SEPARATOR", inlet_stream_VA_01);

    neqsim.processSimulation.processEquipment.heatExchanger.Heater TP_setter_hydrocyclone =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
            "TP_SETTER_HYDROCYCLONE_AFTER_1ST_SEPARATOR", separator_VA_01.getWaterOutStream());
    TP_setter_hydrocyclone.setOutPressure(30.047772073489377, "bara");

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator hydrocyclone_main =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "HYDROCYCLONE_AFTER_THE_1ST_SEPARATOR", TP_setter_hydrocyclone.getOutletStream());
    // hydrocyclone_main.setEntrainment(0.0, "mole", "feed", "aqueous", "gas");

    neqsim.processSimulation.processEquipment.heatExchanger.Heater heater_TP_setter_main_stream =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
            "TP_SETTER_FOR_THE_DEGASSER_MAIN_STREAM", hydrocyclone_main.getWaterOutStream());
    heater_TP_setter_main_stream.setOutPressure(4.076956398010254, "bara");
    heater_TP_setter_main_stream.setOutTemperature(76.51406860351562, "C");

    neqsim.processSimulation.processSystem.ProcessSystem process =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    process.add(inlet_stream_VA_01);
    process.add(separator_VA_01);
    process.add(TP_setter_hydrocyclone);
    process.add(hydrocyclone_main);
    process.add(heater_TP_setter_main_stream);
    process.run();

    System.out.println("Finished TEST 1");
    System.out.println(heater_TP_setter_main_stream.getOutletStream().getFluid().getPhase("gas")
        .getFlowRate("kg/hr"));
  }

  @Test
  public void runProcess2() throws InterruptedException {
    neqsim.thermo.system.SystemSrkCPAstatoil fluid1 =
        new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
    fluid1.getCharacterization().setPlusFractionModel("heavyOil");
    fluid1.addComponent("water", 0.129064569343353);
    fluid1.addComponent("nitrogen", 2.08503464752491e-003);
    fluid1.addComponent("CO2", 5.18541155388192e-002);
    fluid1.addComponent("methane", 0.642060306038335);
    fluid1.addComponent("ethane", 6.18486148336841e-002);
    fluid1.addComponent("propane", 3.00056835824721e-002);
    fluid1.addComponent("i-butane", 3.80588549711585e-003);
    fluid1.addComponent("n-butane", 1.09512595091539e-002);
    fluid1.addComponent("i-pentane", 3.68079747291908e-003);
    fluid1.addComponent("n-pentane", 5.22448994708524e-003);

    fluid1.addTBPfraction("nC6", 6.17521448341365e-003, 86.1800003051758 / 1000,
        86.1800003051758 / (1000 * 0.131586722637079));
    fluid1.addTBPfraction("C7", 1.04929815621011e-002, 94.8470001220703 / 1000,
        94.8470001220703 / (1000 * 0.130402631747591));
    fluid1.addTBPfraction("C8", 0.010589123136316, 106.220001220703 / 1000,
        106.220001220703 / (1000 * 0.141086913827126));
    fluid1.addTBPfraction("C9", 6.46674774929045e-003, 120.457000732422 / 1000,
        120.457000732422 / (1000 * 0.156630031108116));
    fluid1.addTBPfraction("C10-C11", 6.80218048553199e-003, 140.369003295898 / 1000,
        140.369003295898 / (1000 * 0.178710051949529));
    fluid1.addTBPfraction("C12-C13", 4.33960157130810e-003, 167.561996459961 / 1000,
        167.561996459961 / (1000 * 0.208334072812978));
    fluid1.addTBPfraction("C14-C15", 3.40122075476944e-003, 197.501007080078 / 1000,
        197.501007080078 / (1000 * 0.240670271622303));
    fluid1.addTBPfraction("C16-C17", 2.36077988165126e-003, 229.033996582031 / 1000,
        229.033996582031 / (1000 * 0.274302534479916));
    fluid1.addTBPfraction("C18-C20", 2.59750535903308e-003, 262.010986328125 / 1000,
        262.010986328125 / (1000 * 0.308134346902454));

    fluid1.setMixingRule(10);
    fluid1.setMultiPhaseCheck(true);
    fluid1.init(0);

    neqsim.processSimulation.processEquipment.stream.Stream inlet_stream_VA_01 =
        new neqsim.processSimulation.processEquipment.stream.Stream("INLET_STREAM_1ST_SEPARATOR",
            fluid1);
    inlet_stream_VA_01.setTemperature(79.21922658197582, "C");
    inlet_stream_VA_01.setPressure(62.3388000793457, "bara");
    inlet_stream_VA_01.setFlowRate(450261.1588574318, "kg/hr");
    inlet_stream_VA_01.run();

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator separator_VA_01 =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "FIRST_SEPARATOR", inlet_stream_VA_01);
    separator_VA_01.run();

    neqsim.processSimulation.processEquipment.heatExchanger.Heater TP_setter_hydrocyclone =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
            "TP_SETTER_HYDROCYCLONE_AFTER_1ST_SEPARATOR", separator_VA_01.getWaterOutStream());
    TP_setter_hydrocyclone.setOutPressure(61.0700675688386, "bara");
    TP_setter_hydrocyclone.setOutTemperature(79.21922658197582, "C");
    TP_setter_hydrocyclone.run();

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator hydrocyclone_main =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "HYDROCYCLONE_AFTER_THE_1ST_SEPARATOR", TP_setter_hydrocyclone.getOutletStream());
    hydrocyclone_main.setEntrainment(0.01, "mole", "feed", "gas", "aqueous");
    hydrocyclone_main.run();

    hydrocyclone_main.getFluid().prettyPrint();

    // Bug here : received only water phase -> but produces only oil phase ?
    Assertions.assertEquals(37206.866622,
        hydrocyclone_main.getWaterOutStream().getFlowRate("kg/hr"), 1e-6);

    neqsim.processSimulation.processEquipment.heatExchanger.Heater heater_TP_setter_main_stream =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
            "TP_SETTER_FOR_THE_DEGASSER_MAIN_STREAM", hydrocyclone_main.getWaterOutStream());
    heater_TP_setter_main_stream.setOutPressure(6.22176469039917, "bara");
    heater_TP_setter_main_stream.setOutTemperature(77.92657470703125, "C");
    heater_TP_setter_main_stream.run();
    // System.out.println("Water in aqueous phase to the degasser in kg/day: " +
    // heater_TP_setter_main_stream.getOutStream().getFlowRate( "kg/hr")*24);

    neqsim.thermo.system.SystemSrkCPAstatoil fluid_test_separator = fluid1.clone();
    fluid_test_separator.setMolarComposition(new double[] {0.15, 2.35521735969531e-003,
        4.08789934579643e-002, 0.368428826847070, 0.05236965335060, 3.05388164099689e-002,
        3.79517339704697e-003, 1.07659554327202e-002, 3.34941594776651e-003, 4.44976204442086e-003,
        5.12045113841502e-003, 8.61084195264582e-003, 1.12202354604739e-002, 6.84786693345152e-003,
        7.66033733147483e-003, 3.61717376417156e-003, 2.69924953579736e-003, 1.71199871320840e-003,
        1.63521808584951e-003});

    neqsim.processSimulation.processEquipment.stream.Stream inlet_stream_test_sep =
        new neqsim.processSimulation.processEquipment.stream.Stream(
            "TEST_SEPARATOR_INLET,fluid_test_separator", fluid_test_separator);
    inlet_stream_test_sep.setTemperature(39.92721557617188, "C");
    inlet_stream_test_sep.setPressure(1.4343990154266357, "bara");
    inlet_stream_test_sep.setFlowRate(472.5621656362427, "kg/hr");
    inlet_stream_test_sep.run();

    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator test_separator =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator(
            "TEST_SEPARATOR", inlet_stream_test_sep);
    test_separator.run();

    neqsim.processSimulation.processEquipment.heatExchanger.Heater heater_TP_setter_test_stream =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
            "TP_SETTER_FOR_THE_DEGASSER_TEST_SEP_STREAM", test_separator.getWaterOutStream());
    heater_TP_setter_test_stream.setOutPressure(6.22176469039917, "bara");
    heater_TP_setter_test_stream.setOutTemperature(77.92657470703125, "C");
    heater_TP_setter_test_stream.run();

    neqsim.processSimulation.processEquipment.mixer.StaticMixer mixing_degasser =
        new neqsim.processSimulation.processEquipment.mixer.StaticMixer(
            "MIXING_BEFORE_THE_DEGASSER");
    mixing_degasser.addStream(heater_TP_setter_main_stream.getOutletStream());
    mixing_degasser.addStream(heater_TP_setter_test_stream.getOutletStream());
    mixing_degasser.run();

    // System.out.println("Total from degasser is in kg/day: " +
    // mixing_degasser.getOutStream().getFluid().getPhase("gas").getFlowRate("kg/hr")*24);
    // System.out.println("CO2 from degasser is in kg/day: " +
    // mixing_degasser.getOutStream().getFluid().getPhase("gas").getComponent("CO2").getFlowRate("kg/hr")*24);


    neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator degasser =
        new neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator("degasser",
            mixing_degasser.getOutletStream());
    test_separator.run();

    neqsim.processSimulation.processEquipment.heatExchanger.Heater heater_TP_setter_CFU =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater("TP_SETTER_CFU",
            degasser.getWaterOutStream());
    heater_TP_setter_CFU.setOutPressure(6.22176469039917 - 0.5, "bara");
    heater_TP_setter_CFU.setOutTemperature(77.92657470703125, "C");
    heater_TP_setter_CFU.run();

    neqsim.processSimulation.processEquipment.separator.Separator separator_CFU =
        new neqsim.processSimulation.processEquipment.separator.Separator("SEPARATOR_CFU",
            heater_TP_setter_CFU.getOutletStream());
    separator_CFU.run();
    System.out.println("Total flow rate to separator in kg/day: "
        + heater_TP_setter_CFU.getOutletStream().getFlowRate("kg/hr") * 24);// here is the bug
    System.out.println("Total gas flow rate from separator in kg/day: "
        + separator_CFU.getGasOutStream().getFlowRate("kg/hr") * 24);// here is the bug
    System.out.println("Total liquid flow rate from separator in kg/day: "
        + separator_CFU.getLiquidOutStream().getFlowRate("kg/hr") * 24);

    // System.out.println("CO2 from CFU is in kg/day: " +
    // separator_CFU.getGasOutStream().getFluid().getComponent("CO2").getFlowRate("kg/hr")*24);
    // System.out.println("Water in gas phase from CFU is in kg/day: " +
    // separator_CFU.getGasOutStream().getFluid().getComponent("water").getFlowRate("kg/hr")*24);



    neqsim.processSimulation.processEquipment.heatExchanger.Heater heater_TP_setter_CAISSON =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater("TP_SETTER_CAISSON",
            separator_CFU.getLiquidOutStream());
    heater_TP_setter_CAISSON.setOutPressure(1.2, "bara");
    heater_TP_setter_CAISSON.run();


    neqsim.processSimulation.processEquipment.separator.Separator separator_CAISSON =
        new neqsim.processSimulation.processEquipment.separator.Separator("SEPARATOR_CAISSON",
            heater_TP_setter_CAISSON.getOutletStream());
    separator_CAISSON.run(); //

    separator_CAISSON.getGasOutStream().getFluid().prettyPrint();
    System.out.println("Total from _CAISSON is in kg/day: "
        + separator_CAISSON.getGasOutStream().getFluid().getFlowRate("kg/hr") * 24);
    System.out.println("CO2 from _CAISSON is in kg/day: "
        + separator_CAISSON.getGasOutStream().getFluid().getComponent("CO2").getFlowRate("kg/hr")
            * 24);
    System.out.println("Water in gas phase from _CAISSON is in kg/day: "
        + separator_CAISSON.getGasOutStream().getFluid().getComponent("water").getFlowRate("kg/hr")
            * 24);

  }
}
