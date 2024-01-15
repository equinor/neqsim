package neqsim.processSimulation.processSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser;
import neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser;
import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.filter.Filter;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * Test class for GlycolRig.
 */
public class GlycolRigTest2 extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(GlycolRigTest.class);

  ProcessSystem p;
  String _name = "TestProcess";

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
    p.setName(_name);
  }

  @Test
  public void runTEGProcessTest() {
    double N2 = 0.675;
    double CO2 = 2.773;
    double methane = 83.55;
    double ethane = 6.967;
    double propane = 3.709;
    double ibutane = 0.524;
    double nbutane = 1.02;
    double ipentane = 0.216;
    double npentane = 0.235;
    double nc6_plus = 0.328;

    double absorber_feed_gas_temperature = 28.0;
    double absorber_feed_gas_pressure = 77.0;
    double lean_teg_flow_rate = 7740.0;
    double lean_teg_temperature = 35.0;
    double flash_drum_pressure = 6.0;
    double reboiler_pressure = 1.1;
    double reboiler_temperature = 202.0;
    double condenser_pressure = 1.1;
    double condenser_temperature = 98.0;
    double regeneration_gas_cooler_temperature = 25.0;
    double stripping_gas_rate = 190.0;
    double stripping_gas_feed_temperature = 180.0;
    double fine_filter_delta_p = 0.0;
    int number_of_stages_TEG_absorber = 3;
    double stage_efficiency_TEG_absorber = 1.0;
    int number_of_stages_stripper = 3;
    double stage_efficiency_stripper = 1.0;
    double mp_TEG_temperature = 64.0;
    double lp_TEG_temperature = 105.0;
    double TEG_pump_isentropic_efficiency = 0.9;
    double inlet_flow_rate_ms_m3_day = 14.0;
    double inlet_temperature_c = 24.0;
    double inlet_pressure_bara = 77.0;

    neqsim.thermo.system.SystemSrkCPAstatoil feed_gas =
        new neqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.01325);
    feed_gas.addComponent("N2", N2);
    feed_gas.addComponent("CO2", CO2);
    feed_gas.addComponent("methane", methane);
    feed_gas.addComponent("ethane", ethane);
    feed_gas.addComponent("propane", propane);
    feed_gas.addComponent("i-butane", ibutane);
    feed_gas.addComponent("n-butane", nbutane);
    feed_gas.addComponent("i-pentane", ipentane);
    feed_gas.addComponent("n-pentane", npentane);
    feed_gas.addComponent("2-m-C5", 0.000494805 * nc6_plus);
    feed_gas.addComponent("3-m-C5", 0.000257775 * nc6_plus);
    feed_gas.addComponent("n-hexane", 0.000650852 * nc6_plus);
    feed_gas.addComponent("c-hexane", 0.000616629 * nc6_plus);
    feed_gas.addComponent("n-heptane", 0.000385438 * nc6_plus);
    feed_gas.addComponent("n-octane", 0.000762738 * nc6_plus);
    feed_gas.addComponent("benzene", 0.000168484 * nc6_plus);
    feed_gas.addComponent("toluene", 1.00E-04 * nc6_plus);
    feed_gas.addComponent("water", 0.0);
    feed_gas.addComponent("TEG", 0.0);
    feed_gas.setMixingRule(10);
    feed_gas.setMultiPhaseCheck(false);

    List<String> components_list = new ArrayList<>();

    for (int i = 0; i < feed_gas.getNumberOfComponents(); i++) {
      components_list.add(feed_gas.getComponent(i).getName());
    }

    Map<String, Double> components_binary_parameters_change = new HashMap<>();
    components_binary_parameters_change.put("methane", 0.13);
    components_binary_parameters_change.put("i-butane", 0.112);
    components_binary_parameters_change.put("n-butane", 0.123);
    components_binary_parameters_change.put("i-pentane", 0.074);
    components_binary_parameters_change.put("n-pentane", 0.107);
    components_binary_parameters_change.put("n-hexane", 0.105);
    components_binary_parameters_change.put("benzene", 0.087);

    neqsim.processSimulation.processEquipment.stream.Stream dry_feed_gas =
        new neqsim.processSimulation.processEquipment.stream.Stream(feed_gas);
    dry_feed_gas.setName("dry feed gas");
    dry_feed_gas.setFlowRate(inlet_flow_rate_ms_m3_day, "MSm3/day");
    dry_feed_gas.setTemperature(inlet_temperature_c, "C");
    dry_feed_gas.setPressure(inlet_pressure_bara, "bara");
    dry_feed_gas.run();

    neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil saturated_feed_gas =
        new neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil(dry_feed_gas);
    saturated_feed_gas.setName("water saturator");
    saturated_feed_gas.run();

    neqsim.processSimulation.processEquipment.stream.Stream water_saturated_feed_gas =
        new neqsim.processSimulation.processEquipment.stream.Stream("water saturated feed gas",
            saturated_feed_gas.getOutStream());
    water_saturated_feed_gas.run();

    neqsim.processSimulation.processEquipment.heatExchanger.Heater feed_tp_setter_to_absorber =
        new neqsim.processSimulation.processEquipment.heatExchanger.Heater("TP of gas to absorber",
            water_saturated_feed_gas);
    feed_tp_setter_to_absorber.setOutPressure(absorber_feed_gas_pressure, "bara");
    feed_tp_setter_to_absorber.setOutTemperature(absorber_feed_gas_temperature, "C");
    feed_tp_setter_to_absorber.run();

    neqsim.processSimulation.processEquipment.stream.Stream feed_to_absorber =
        new neqsim.processSimulation.processEquipment.stream.Stream("feed to TEG absorber",
            feed_tp_setter_to_absorber.getOutletStream());
    feed_to_absorber.run();

    neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser hydrate_t_analyser2 =
        new neqsim.processSimulation.measurementDevice.HydrateEquilibriumTemperatureAnalyser(
            feed_to_absorber);
    hydrate_t_analyser2.setName("hydrate temperature gas to absorber");

    neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser water_dew_point_analyser_to_absorber =
        new neqsim.processSimulation.measurementDevice.WaterDewPointAnalyser(feed_to_absorber);
    water_dew_point_analyser_to_absorber.setMethod("multiphase");
    water_dew_point_analyser_to_absorber.setReferencePressure(absorber_feed_gas_pressure);
    water_dew_point_analyser_to_absorber.setName("water dew point gas to absorber");

    neqsim.thermo.system.SystemInterface feedTEG = dry_feed_gas.getFluid().clone();
    double[] molarComp = new double[components_list.size()];
    for (int i = 0; i < components_list.size(); i++) {
      String component = components_list.get(i);
      if (component.equals("water")) {
        molarComp[i] = 0.01;
      } else if (component.equals("TEG")) {
        molarComp[i] = 0.99;
      } else {
        molarComp[i] = 0.0;
      }
    }
    feedTEG.setMolarComposition(molarComp);

    Stream teg_feed = new Stream("lean TEG to absorber", feedTEG);
    teg_feed.setFlowRate(lean_teg_flow_rate, "kg/hr");
    teg_feed.setTemperature(lean_teg_temperature, "C");
    teg_feed.setPressure(absorber_feed_gas_pressure, "bara");
    teg_feed.run();

    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");
    absorber.addGasInStream(feed_to_absorber);
    absorber.addSolventInStream(teg_feed);
    absorber.setNumberOfStages(number_of_stages_TEG_absorber);
    absorber.setStageEfficiency(stage_efficiency_TEG_absorber);
    absorber.setInternalDiameter(3.65);
    absorber.run();

    Stream dehydrated_gas = new Stream(absorber.getGasOutStream());
    dehydrated_gas.setName("dry gas from absorber");

    Stream rich_teg = new Stream(absorber.getLiquidOutStream());
    rich_teg.setName("rich TEG from absorber");
    rich_teg.run();

    HydrateEquilibriumTemperatureAnalyser water_dew_point_analyser =
        new HydrateEquilibriumTemperatureAnalyser(dehydrated_gas);
    water_dew_point_analyser.setReferencePressure(70.0);
    water_dew_point_analyser.setName("hydrate dew point analyser");

    WaterDewPointAnalyser water_dew_point_analyser2 = new WaterDewPointAnalyser(dehydrated_gas);
    water_dew_point_analyser2.setReferencePressure(70.0);
    water_dew_point_analyser2.setName("water dew point analyser");

    Heater cond_heat = new Heater(rich_teg);
    cond_heat.setName("Condenser heat exchanger");
    cond_heat.run();

    ThrottlingValve glycol_flash_valve =
        new ThrottlingValve("Flash valve", cond_heat.getOutStream());
    glycol_flash_valve.setName("Rich TEG HP flash valve");
    glycol_flash_valve.setOutletPressure(flash_drum_pressure);
    glycol_flash_valve.run();

    Heater heat_ex2 = new Heater(glycol_flash_valve.getOutStream());
    heat_ex2.setName("rich TEG heat exchanger 1");
    heat_ex2.setOutTemperature(273.15 + mp_TEG_temperature);
    heat_ex2.run();

    Separator flash_sep = new Separator(heat_ex2.getOutStream());
    flash_sep.setName("degasing separator");
    flash_sep.setInternalDiameter(1.2);
    flash_sep.run();

    Stream flash_gas = new Stream(flash_sep.getGasOutStream());
    flash_gas.setName("gas from degasing separator");

    Stream flash_liquid = new Stream(flash_sep.getLiquidOutStream());
    flash_liquid.setName("liquid from degasing separator");
    flash_liquid.run();

    Filter fine_filter = new Filter(flash_liquid);
    fine_filter.setName("TEG fine filter");
    fine_filter.setDeltaP(fine_filter_delta_p, "bara");
    fine_filter.run();

    Heater heat_ex = new Heater(fine_filter.getOutStream());
    heat_ex.setName("lean/rich TEG heat-exchanger");
    heat_ex.setOutTemperature(273.15 + lp_TEG_temperature);
    heat_ex.run();

    ThrottlingValve glycol_flash_valve2 =
        new ThrottlingValve("LP flash valve", heat_ex.getOutStream());
    glycol_flash_valve2.setName("Rich TEG LP flash valve");
    glycol_flash_valve2.setOutletPressure(reboiler_pressure);
    glycol_flash_valve2.run();

    neqsim.thermo.system.SystemInterface strip_gas = dry_feed_gas.getFluid().clone();

    Stream stripping_gas = new Stream("stripGas", strip_gas);
    stripping_gas.setFlowRate(stripping_gas_rate, "kg/hr");
    stripping_gas.setTemperature(stripping_gas_feed_temperature, "C");
    stripping_gas.setPressure(reboiler_pressure, "bara");
    stripping_gas.run();

    Stream gas_to_reboiler = stripping_gas.clone();
    gas_to_reboiler.setName("gas to reboiler");
    gas_to_reboiler.run();

    DistillationColumn column = new DistillationColumn(1, true, true);
    column.setName("TEG regeneration column");
    column.addFeedStream(glycol_flash_valve2.getOutStream(), 1);
    column.getReboiler().setOutTemperature(273.15 + reboiler_temperature);
    column.getCondenser().setOutTemperature(273.15 + condenser_temperature);
    column.getTray(1).addStream(gas_to_reboiler);
    column.setTopPressure(condenser_pressure);
    column.setBottomPressure(reboiler_pressure);
    column.setInternalDiameter(0.56);
    column.setMaxNumberOfIterations(5);
    column.run();

    Heater cooler_regen_gas = new neqsim.processSimulation.processEquipment.heatExchanger.Heater(
        column.getGasOutStream());
    cooler_regen_gas.setName("regen gas cooler");
    cooler_regen_gas.setOutTemperature(273.15 + regeneration_gas_cooler_temperature);
    cooler_regen_gas.run();

    Separator sep_regen_gas = new neqsim.processSimulation.processEquipment.separator.Separator(
        cooler_regen_gas.getOutStream());
    sep_regen_gas.setName("regen gas separator");
    sep_regen_gas.run();

    Stream gasToFlare = new Stream(sep_regen_gas.getGasOutStream());
    gasToFlare.setName("gas to flare");
    gasToFlare.run();

    Splitter splitterGasToFlare = new Splitter(gasToFlare);
    splitterGasToFlare.setSplitNumber(2);
    splitterGasToFlare.setFlowRates(new double[] {stripping_gas_rate, -1}, "kg/hr");

    Heater strippingFlareGasTPsetter =
        new Heater("TP of stripping gas + flare", splitterGasToFlare.getSplitStream(0));
    strippingFlareGasTPsetter.setOutPressure(reboiler_pressure, "bara");
    strippingFlareGasTPsetter.setOutTemperature(stripping_gas_feed_temperature, "C");
    strippingFlareGasTPsetter.run();

    Stream liquid_to_treatment = new Stream(sep_regen_gas.getLiquidOutStream());
    liquid_to_treatment.setName("water to treatment");

    WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
    stripper.addSolventInStream(column.getLiquidOutStream());
    stripper.addGasInStream(stripping_gas);
    stripper.setNumberOfStages(number_of_stages_stripper);
    stripper.setStageEfficiency(stage_efficiency_stripper);
    stripper.run();

    Recycle recycle_gas_from_stripper = new Recycle("stripping gas recirc");
    recycle_gas_from_stripper.addStream(stripper.getGasOutStream());
    recycle_gas_from_stripper.setOutletStream(gas_to_reboiler);

    StreamInterface recycleFlareGasStream = strippingFlareGasTPsetter.getOutletStream();

    Recycle recycleFlareGas = new Recycle("stripping gas recirc Flare Gas");
    recycleFlareGas.addStream(recycleFlareGasStream);
    recycleFlareGas.setOutletStream(stripping_gas);

    neqsim.thermo.system.SystemInterface pure_teg = dry_feed_gas.getFluid().clone();
    double[] molarComp2 = new double[components_list.size()];
    for (int i = 0; i < components_list.size(); i++) {
      String component = components_list.get(i);
      if (component.equals("TEG")) {
        molarComp2[i] = 1.0;
      } else {
        molarComp2[i] = 0.0;
      }
    }
    pure_teg.setMolarComposition(molarComp2);

    Stream makeup_teg = new Stream("makeup TEG", pure_teg);
    makeup_teg.setFlowRate(1e-6, "kg/hr");
    makeup_teg.setTemperature(lean_teg_temperature, "C");
    makeup_teg.setPressure(reboiler_pressure, "bara");

    Calculator makeup_calculator = new Calculator("TEG makeup calculator");
    makeup_calculator.addInputVariable(dehydrated_gas);
    makeup_calculator.addInputVariable(flash_gas);
    makeup_calculator.addInputVariable(gasToFlare);
    makeup_calculator.addInputVariable(liquid_to_treatment);
    makeup_calculator.setOutputVariable(makeup_teg);

    StaticMixer makeup_mixer = new StaticMixer("makeup mixer");
    makeup_mixer.addStream(stripper.getLiquidOutStream());
    makeup_mixer.addStream(makeup_teg);

    Pump hotLeanTEGPump = new Pump(makeup_mixer.getOutStream());
    hotLeanTEGPump.setName("lean TEG LP pump");
    hotLeanTEGPump.setOutletPressure(absorber_feed_gas_pressure);
    hotLeanTEGPump.setIsentropicEfficiency(TEG_pump_isentropic_efficiency);

    Heater cooler_hot_teg3 = new Heater(hotLeanTEGPump.getOutStream());
    cooler_hot_teg3.setName("lean TEG cooler");
    cooler_hot_teg3.setOutTemperature(273.15 + lean_teg_temperature);

    cond_heat.setEnergyStream(column.getCondenser().getEnergyStream());
    StreamInterface lean_TEG_to_abs = cooler_hot_teg3.getOutStream();
    lean_TEG_to_abs.setName("lean TEG to absorber");

    Recycle recycle_lean_teg = new Recycle("lean TEG resycle");
    recycle_lean_teg.addStream(lean_TEG_to_abs);
    recycle_lean_teg.setOutletStream(teg_feed);

    ProcessSystem operations = new ProcessSystem();
    operations.add(dry_feed_gas);
    operations.add(saturated_feed_gas);
    operations.add(water_saturated_feed_gas);
    operations.add(feed_tp_setter_to_absorber);
    operations.add(hydrate_t_analyser2);
    operations.add(teg_feed);
    operations.add(feed_to_absorber);
    operations.add(absorber);
    operations.add(rich_teg);
    operations.add(cond_heat);
    operations.add(glycol_flash_valve);
    operations.add(heat_ex2);
    operations.add(flash_sep);
    operations.add(flash_gas);
    operations.add(flash_liquid);
    operations.add(fine_filter);
    operations.add(heat_ex);
    operations.add(stripping_gas);
    operations.add(gas_to_reboiler);
    operations.add(glycol_flash_valve2);
    operations.add(column);
    operations.add(stripper);
    operations.add(splitterGasToFlare);
    operations.add(strippingFlareGasTPsetter);
    operations.add(recycle_gas_from_stripper);
    operations.add(cooler_regen_gas);
    operations.add(sep_regen_gas);
    operations.add(gasToFlare);
    operations.add(liquid_to_treatment);
    operations.add(recycleFlareGasStream);
    operations.add(recycleFlareGas);
    operations.add(makeup_teg);
    operations.add(makeup_calculator);
    operations.add(makeup_mixer);
    operations.add(hotLeanTEGPump);
    operations.add(cooler_hot_teg3);
    operations.add(lean_TEG_to_abs);
    operations.add(recycle_lean_teg);
    operations.add(dehydrated_gas);
    operations.add(water_dew_point_analyser);
    operations.add(water_dew_point_analyser2);
    operations.add(water_dew_point_analyser_to_absorber);
    operations.run();

    double waterOut = ((Stream) dehydrated_gas).getFluid().getComponent("water").getx() * 1E6;
    System.out.println("Water in the dehydrated stream after first run " + waterOut + " ppm");

    operations.run();

    double waterOut2 = ((Stream) dehydrated_gas).getFluid().getComponent("water").getx() * 1E6;
    System.out.println("Water in the dehydrated stream after second run " + waterOut2 + " ppm");

    operations.run();
    operations.run();
    operations.run();

    double waterOut3 = ((Stream) dehydrated_gas).getFluid().getComponent("water").getx() * 1E6;
    System.out.println("Water in the dehydrated stream after 5 run " + waterOut3 + " ppm");

    // // Check recycles
    // double reboilerStripperMethane =
    // ((Stream) gas_to_reboiler).getFluid().getComponent("methane").getx() * 1E6;
    // double StripperMethane =
    // ((Stream) stripper.getGasOutStream()).getFluid().getComponent("methane").getx() * 1E6;
    // System.out.println(
    // "Recycle stripper - reboiler is solved: " + recycle_gas_from_stripper.solved() + "");
    // System.out.println(reboilerStripperMethane + " = " + StripperMethane);
    // System.out.println(" ######### ");

    // // Check recycles
    // double recycleFlareGasStreamMethane =
    // ((Stream) recycleFlareGasStream).getFluid().getComponent("methane").getx() * 1E6;
    // double StrippingGasStripperMethane =
    // ((Stream) stripping_gas).getFluid().getComponent("methane").getx() * 1E6;
    // System.out.println("Recycle stripper - flare gas is solved: " + recycleFlareGas.solved() +
    // "");
    // System.out.println(recycleFlareGasStreamMethane + " = " + StrippingGasStripperMethane);
    // System.out.println(" ######### ");
  }



}

