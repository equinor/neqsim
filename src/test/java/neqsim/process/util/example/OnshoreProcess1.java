package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.SimpleTPoutPipeline;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.MolarMassAnalyser;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.TemperatureTransmitter;
import neqsim.process.measurementdevice.VolumeFlowTransmitter;
import neqsim.process.measurementdevice.WaterContentAnalyser;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * OnshoreProcess1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class OnshoreProcess1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OnshoreProcess1.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    try {
      double totalProductionRate = 222.5808099 * 3600.0; // kg/time
      double formationWater = 0.02; // m^3/day
      double productionSplit = 0.999; // 1.0 means only snohvit

      // MEG injection rate
      double injectionRate = 6.3; // m^3/time
      double wtFracMEG = 0.9;
      double molMEG = wtFracMEG * injectionRate * 1.117 * 1000.0 / 3600.0 / 0.062;
      double molwater = (1.0 - wtFracMEG) * injectionRate * 1.0 * 1000.0 / 3600.0 / 0.018;

      // Snohvit reservoir system
      double reservoirGasMolarMassSnohvit = 0.02186; // kg/mol
      double reservoirGasFlowSnohvit = productionSplit * totalProductionRate; // kg/time
                                                                              // Designbasis:
                                                                              // 223.04
                                                                              // kg/sek
      double reservoirTemperatureSnohvit = 273.15 + 91.4; // K
      double reservoirPressureSnohvit = 264.4; // bar
      double snohvitFormationWaterFlowRate = formationWater * 1000.0 / 3600.0 / 0.018 / 24.0;
      // molefractions
      double nitrogen_s = 2.525;
      double CO2_s = 5.262;
      double methane_s = 81.006;
      double ethane_s = 5.027;
      double propane_s = 2.534;
      double ibutane_s = 0.4;
      double nbutane_s = 0.83;
      double benzene_s = 0.078;
      double toluene_s = 0.09;
      double mXylene_s = 0.061;
      double water_s = 4.0;
      double C6_s = 0.352;
      double C7_s = 0.469;
      double C8_s = 0.407;
      double C9_s = 0.203;
      double C10_s = 0.086;
      double C11_s = 0.063;
      double C12_s = 0.062;
      double C13_s = 0.049;
      double C14_s = 0.033;
      double C15_s = 0.025;
      double C16_s = 0.015;
      double C17_s = 0.015;
      double C18_s = 0.010;
      double C19_s = 0.007;
      double C20plus_s = 0.031;
      double C6_M_s = 85.0;
      double C7_M_s = 90.7;
      double C8_M_s = 104.1;
      double C9_M_s = 119.8;
      double C10_M_s = 133.0;
      double C11_M_s = 147.0;
      double C12_M_s = 159.0;
      double C13_M_s = 173.0;
      double C14_M_s = 188.0;
      double C15_M_s = 202.0;
      double C16_M_s = 213.0;
      double C17_M_s = 230.0;
      double C18_M_s = 247.0;
      double C19_M_s = 258.0;
      double C20plus_M_s = 315.0;
      double C6_d_s = 0.668;

      double C7_d_s = 0.745;
      double C8_d_s = 0.767;
      double C9_d_s = 0.776;
      double C10_d_s = 0.783;
      double C11_d_s = 0.787;
      double C12_d_s = 0.799;
      double C13_d_s = 0.811;
      double C14_d_s = 0.821;
      double C15_d_s = 0.828;
      double C16_d_s = 0.836;
      double C17_d_s = 0.834;
      double C18_d_s = 0.835;
      double C19_d_s = 0.845;
      double C20plus_d_s = 0.872;
      // Albatross reservoir system
      double reservoirGasMolarMassAlbatross = 0.022; // kg/mol
      double reservoirGasFlowAlbatross = (1.0 - productionSplit) * totalProductionRate; // kg/time
      double reservoirTemperatureAlbatross = 273.15 + 65.0; // K
      double reservoirPressureAlbatross = 210.0; // bar
      // molefractions
      double nitrogen_a = 0.1;
      double CO2_a = 7.0;
      double methane_a = 80.43;
      double ethane_a = 4.99;
      double propane_a = 2.51;
      double ibutane_a = 0.39;
      double nbutane_a = 0.82;
      double benzene_a = 0.077;
      double toluene_a = 0.089;
      double mXylene_a = 0.06;
      double water_a = 2.0;
      double C6_a = 0.352;
      double C7_a = 0.91;
      double C8_a = 0.317;
      double C9_a = 0.142;
      double C10_a = 0.141;
      double C11_a = 0.063;
      double C12_a = 0.062;
      double C6_M_a = 85.0;
      double C7_M_a = 90.7;
      double C8_M_a = 104.1;
      double C9_M_a = 119.8;
      double C10_M_a = 133.0;
      double C11_M_a = 147.0;
      double C12_M_a = 159.0;
      double C6_d_a = 0.668;

      double C7_d_a = 0.745;
      double C8_d_a = 0.767;
      double C9_d_a = 0.776;
      double C10_d_a = 0.783;
      double C11_d_a = 0.787;
      double C12_d_a = 0.799;
      neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(
          reservoirTemperatureSnohvit, reservoirPressureSnohvit);
      neqsim.thermo.system.SystemInterface testSystem3 = new neqsim.thermo.system.SystemSrkEos(
          reservoirTemperatureSnohvit, reservoirPressureSnohvit);
      neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos(
          reservoirTemperatureAlbatross, reservoirPressureAlbatross);
      neqsim.thermo.system.SystemInterface testSystem4 = new neqsim.thermo.system.SystemSrkEos(
          reservoirTemperatureAlbatross, reservoirPressureAlbatross);
      neqsim.thermo.system.SystemInterface snohvitFormationWater =
          new neqsim.thermo.system.SystemSrkEos(reservoirTemperatureSnohvit,
              reservoirPressureSnohvit);

      testSystem.addComponent("nitrogen",
          nitrogen_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("CO2",
          CO2_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("methane",
          methane_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("ethane",
          ethane_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("propane",
          propane_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("i-butane",
          ibutane_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("n-butane",
          nbutane_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("benzene",
          benzene_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("toluene",
          toluene_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      testSystem.addComponent("m-Xylene",
          mXylene_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);

      testSystem.addTBPfraction("C6",
          C6_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit, C6_M_s / 1000.0,
          C6_d_s);
      testSystem.addTBPfraction("C7",
          C7_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit, C7_M_s / 1000.0,
          C7_d_s);
      testSystem.addTBPfraction("C8",
          C8_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit, C8_M_s / 1000.0,
          C8_d_s);
      testSystem.addTBPfraction("C9",
          C9_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit, C9_M_s / 1000.0,
          C9_d_s);
      testSystem.addTBPfraction("C10",
          C10_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C10_M_s / 1000.0, C10_d_s);
      testSystem.addTBPfraction("C11",
          C11_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C11_M_s / 1000.0, C11_d_s);
      testSystem.addTBPfraction("C12",
          C12_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C12_M_s / 1000.0, C12_d_s);
      testSystem.addTBPfraction("C13",
          C13_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C13_M_s / 1000.0, C13_d_s);
      testSystem.addTBPfraction("C14",
          C14_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C14_M_s / 1000.0, C14_d_s);
      testSystem.addTBPfraction("C15",
          C15_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C15_M_s / 1000.0, C15_d_s);
      testSystem.addTBPfraction("C16",
          C16_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C16_M_s / 1000.0, C16_d_s);
      testSystem.addTBPfraction("C17",
          C17_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C17_M_s / 1000.0, C17_d_s);
      testSystem.addTBPfraction("C18",
          C18_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C18_M_s / 1000.0, C18_d_s);
      testSystem.addTBPfraction("C19",
          C19_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C19_M_s / 1000.0, C19_d_s);
      testSystem.addTBPfraction("C20",
          C20plus_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit,
          C20plus_M_s / 1000.0, C20plus_d_s);

      testSystem.addComponent("water",
          water_s * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);
      // //testSystem.addComponent("Fe++",
      // 1e-10*reservoirGasFlowSnohvit/360000.0/reservoirGasMolarMassSnohvit);
      // //testSystem.addComponent("Na+",
      // 1e-10*reservoirGasFlowSnohvit/360000.0/reservoirGasMolarMassSnohvit);
      // //testSystem.addComponent("OH-",
      // 3e-10*reservoirGasFlowSnohvit/360000.0/reservoirGasMolarMassSnohvit);
      testSystem.addComponent("MEG",
          1e-10 * reservoirGasFlowSnohvit / 360000.0 / reservoirGasMolarMassSnohvit);

      // if(testSystem.characterizePlusFraction()){
      // testSystem.getCharacterization().setPseudocomponents(true);
      // testSystem.getCharacterization().setNumberOfPseudocomponents(5);
      // testSystem.getCharacterization().addCharacterizedPlusFraction();
      // }

      testSystem.createDatabase(true);
      testSystem.setMixingRule(2);

      testSystem2.addComponent("nitrogen",
          nitrogen_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("CO2",
          CO2_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("methane",
          methane_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("ethane",
          ethane_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("propane",
          propane_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("i-butane",
          ibutane_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("n-butane",
          nbutane_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("benzene",
          benzene_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("toluene",
          toluene_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("m-Xylene",
          mXylene_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);

      testSystem2.addComponent("water",
          water_a * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      // testSystem2.addComponent("Fe++",
      // 1e-10*reservoirGasFlowAlbatross/360000.0/reservoirGasMolarMassAlbatross);
      // testSystem2.addComponent("Na+",
      // 1e-10*reservoirGasFlowAlbatross/360000.0/reservoirGasMolarMassAlbatross);
      // testSystem2.addComponent("OH-",
      // 3e-10*reservoirGasFlowAlbatross/360000.0/reservoirGasMolarMassAlbatross);
      testSystem2.addComponent("MEG",
          1e-10 * reservoirGasFlowAlbatross / 360000.0 / reservoirGasMolarMassAlbatross);
      testSystem2.setMixingRule(2);

      testSystem3.addComponent("water", molwater);
      // testSystem3.addComponent("Na+", (molwater+molMEG)/5000.0);
      // testSystem3.addComponent("OH-", (molwater+molMEG)/5000.0);
      testSystem3.addComponent("MEG", molMEG);
      testSystem3.setMixingRule(2);

      snohvitFormationWater.addComponent("water", snohvitFormationWaterFlowRate);
      snohvitFormationWater.setMixingRule(2);

      StreamInterface stream_1 = new Stream("Stream1", testSystem);

      ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator 1", stream_1);
      MeasurementDeviceInterface reservoirTemperaturTransmitter =
          new TemperatureTransmitter(stream_1);
      MeasurementDeviceInterface reservoirPressureTransmitter = new PressureTransmitter(stream_1);
      WaterContentAnalyser waterAnalyser =
          new WaterContentAnalyser("Snohvit Total Water Analyser", separator.getGasOutStream());
      MolarMassAnalyser molarMassAnalyser =
          new MolarMassAnalyser("Snohvit molar mass analyser", separator.getGasOutStream());
      StreamInterface stream_2 = separator.getGasOutStream();
      stream_2.setName("Gas From Snohvit Reservoir");

      StreamInterface MEGstream_1 = new Stream("MEG Stream1", testSystem3);
      StreamInterface snohvitFormationWaterStream =
          new Stream("snohvitFormationWaterStream", snohvitFormationWater);

      Mixer MEGmixer1 = new Mixer("MEG Mixer 1");
      MEGmixer1.addStream(stream_2);
      MEGmixer1.addStream(MEGstream_1);
      MEGmixer1.addStream(snohvitFormationWaterStream);

      ThrottlingValve valve1 = new ThrottlingValve("snohvit valve", MEGmixer1.getOutletStream());
      valve1.setOutletPressure(125.0);

      // Albatross reservoir stream
      Stream stream_3 = new Stream("Stream2", testSystem2);
      Separator separator2 = new Separator("Separator 2", stream_3);
      WaterContentAnalyser waterAnalyser2 =
          new WaterContentAnalyser("Albatross Total Water Analyser", separator2.getGasOutStream());
      MolarMassAnalyser molarMassAnalyser2 =
          new MolarMassAnalyser("Albatross molar mass analyser", separator2.getGasOutStream());

      StreamInterface stream_4 = separator2.getGasOutStream();
      stream_4.setName("Gas From Albatross Reservoir");

      ThrottlingValve valve2 = new ThrottlingValve("albatross valve", stream_4);
      valve2.setOutletPressure(125.0);

      // Field mixer
      Mixer mixer1 = new Mixer("Field Mixer");
      mixer1.addStream(valve1.getOutletStream());
      mixer1.addStream(valve2.getOutletStream());

      WaterContentAnalyser waterAnalyser3 =
          new WaterContentAnalyser("Total Water Analyser", mixer1.getOutletStream());

      // Pipeline
      SimpleTPoutPipeline pipeLine1 =
          new SimpleTPoutPipeline("snohvit pipeline", mixer1.getOutletStream());
      pipeLine1.setOutPressure(55.0);
      pipeLine1.setOutTemperature(273.15 + 5.0);
      pipeLine1.setNumberOfLegs(1);
      pipeLine1.setPipeDiameters(new double[] {1.2, 1.2});
      pipeLine1.setLegPositions(new double[] {0, 150000.0});
      pipeLine1.setOuterTemperatures(new double[] {0.0, 0.0});

      // IronIonSaturationStream ironSatStream = new
      // IronIonSaturationStream(pipeLine1.getOutStream());

      // // Land plant
      ThreePhaseSeparator slugCatcher =
          new ThreePhaseSeparator("slugCatcher", pipeLine1.getOutletStream());

      VolumeFlowTransmitter volumeTransmitter2 = new VolumeFlowTransmitter(
          "Condensate Volume Flow From Slug Catcher", slugCatcher.getOilOutStream());
      volumeTransmitter2.setMeasuredPhaseNumber(0);

      VolumeFlowTransmitter volumeTransmitter1 = new VolumeFlowTransmitter(
          "MEG Volume FLow From Slug Catcher", slugCatcher.getWaterOutStream());
      volumeTransmitter1.setMeasuredPhaseNumber(0);

      VolumeFlowTransmitter volumeTransmitter3 = new VolumeFlowTransmitter(
          "Gas Volume FLow From Slug Catcher", slugCatcher.getGasOutStream());
      volumeTransmitter3.setMeasuredPhaseNumber(0);

      // Stream stream_5 = new Stream("MEG stream from
      // slugcatcher",slugCatcher.getWaterOutStream());

      Heater condensateheater1 = new Heater("Condensate heater1", slugCatcher.getOilOutStream());
      condensateheater1.setdT(23.4);

      // Heater gasHeater = new Heater("Gas heater after slugcatcher",
      // slugCatcher.getGasOutStream());
      // gasHeater.setdT(30.0);

      ThreePhaseSeparator condensateSeparator =
          new ThreePhaseSeparator("condensateSeparator", condensateheater1.getOutletStream());

      // Heater MEGheater1 = new Heater("MEG heater1",stream_5);
      // MEGheater1.setdT(23.4);

      // SnohvitCO2RemovalModule co2Module = new SnohvitCO2RemovalModule();
      // co2Module.addInputStream("streamToAbsorber", gasHeater.getOutStream());

      // Heater MEGheater2 = new Heater("MEG heater2", MEGheater1.getOutStream());
      // MEGheater2.setdT(20.0);

      // ThrottlingValve valve3 = new ThrottlingValve("MEG flash valve 1",
      // MEGheater2.getOutStream());
      // valve3.setOutletPressure(1.4);

      // ThreePhaseSeparator separator3 = new ThreePhaseSeparator("MEG LP
      // separator", valve3.getOutStream());

      // VolumeFlowTransmitter volumeTransmitter5= new
      // VolumeFlowTransmitter("MEG Valve 1 Gas Volume FLow",separator3.getGasOutStream());

      // Heater MEGheater3 = new Heater("MEG heater3", separator3.getWaterOutStream());
      // MEGheater3.setdT(-35.0);

      // ThrottlingValve valve4 = new ThrottlingValve("MEG flash valve 2",
      // MEGheater3.getOutStream());
      // valve4.setOutletPressure(1.05);

      // //ScalePotentialCheckStream stream_6 = new
      // ScalePotentialCheckStream(valve4.getOutStream());

      // Stream stream_7 = new Stream(valve4.getOutStream());

      // ThreePhaseSeparator separator4 = new ThreePhaseSeparator("MEG Storage
      // tank 13-TA-101", stream_7);

      // Stream stream_8 = new
      // Stream(co2Module.getOutputStream("streamFromAbsorber"));

      // GasHeater.setName("Gas heater");
      // GasHeater.setdT(-35.0);

      // CO2-removal
      // Stream streamToCO2removal = new Stream("Gas to CO2 removal",slugCatcher.getGasOutStream());
      // VolumeFlowTransmitter volumeTransmitter_StreamToCO2removal = new
      // VolumeFlowTransmitter("Stream to CO2 removal Volume FLow",streamToCO2removal);
      // volumeTransmitter_StreamToCO2removal.setUnit("Nm^3/day");

      neqsim.process.processmodel.ProcessSystem operations =
          new neqsim.process.processmodel.ProcessSystem();
      operations.add(stream_1);
      operations.add(reservoirTemperaturTransmitter);
      operations.add(reservoirPressureTransmitter);
      operations.add(separator);
      operations.add(stream_2);
      operations.add(MEGstream_1);
      operations.add(snohvitFormationWaterStream);
      operations.add(MEGmixer1);
      operations.add(valve1);

      operations.add(stream_3);
      operations.add(separator2);
      operations.add(stream_4);
      operations.add(valve2);

      operations.add(mixer1);
      operations.add(pipeLine1);
      // operations.add(ironSatStream);
      operations.add(slugCatcher);
      operations.add(condensateheater1);
      // operations.add(gasHeater);
      operations.add(condensateSeparator);
      // operations.add(stream_5);

      // operations.add(MEGheater1);
      // operations.add(co2Module);
      // operations.add(MEGheater2);

      // operations.add(valve3);
      // operations.add(separator3);

      // operations.add(streamToCO2removal);

      // operations.add(MEGheater3);
      // operations.add(valve4);

      // operations.add(stream_6);
      // operations.add(stream_7);
      // operations.add(separator4);
      // operations.add(stream_8);

      // add transmitters
      // operations.add(waterAnalyser);
      // operations.add(waterAnalyser2);
      // operations.add(waterAnalyser3);
      // operations.add(molarMassAnalyser);
      // operations.add(molarMassAnalyser2);
      operations.add(volumeTransmitter1);
      operations.add(volumeTransmitter2);
      operations.add(volumeTransmitter3);
      // operations.add(volumeTransmitter_StreamToCO2removal);

      operations.run();
      operations.displayResult();
      operations.reportMeasuredValues();

      double wateraqphase =
          slugCatcher.getWaterOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getWaterOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getMolarMass()
              * slugCatcher.getWaterOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getx()
              * 3600.0;

      System.out.println("kg water in gas phase from slug catcher "
          + slugCatcher.getGasOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getMolarMass()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getx()
              * 3600.0
          + " kg/hr");
      System.out.println("kg water in condensate phase from slug catcher "
          + slugCatcher.getOilOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getOilOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getMolarMass()
              * slugCatcher.getOilOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getx()
              * 3600.0
          + " kg/hr");
      System.out.println("kg water in aqueous phase from slug catcher " + wateraqphase + " kg/hr");

      double MEGaqphase = slugCatcher.getWaterOutStream().getThermoSystem().getTotalNumberOfMoles()
          * slugCatcher.getWaterOutStream().getThermoSystem().getPhase(0).getComponent("MEG")
              .getMolarMass()
          * slugCatcher.getWaterOutStream().getThermoSystem().getPhase(0).getComponent("MEG").getx()
          * 3600.0;
      System.out.println("kg MEG in gas phase from slug catcher "
          + slugCatcher.getGasOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("MEG")
                  .getMolarMass()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("MEG")
                  .getx()
              * 3600.0
          + " kg/hr");
      System.out.println("kg MEG in condensate phase from slug catcher "
          + slugCatcher.getOilOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getOilOutStream().getThermoSystem().getPhase(0).getComponent("MEG")
                  .getMolarMass()
              * slugCatcher.getOilOutStream().getThermoSystem().getPhase(0).getComponent("MEG")
                  .getx()
              * 3600.0
          + " kg/hr");
      System.out.println("kg MEG in aqueous phase from slug catcher " + MEGaqphase + " kg/hr");

      System.out.println("wt% MEG " + MEGaqphase / (wateraqphase + MEGaqphase) * 100);

      volumeTransmitter3.setUnit("Sm^3/day");
      System.out.println("gas rate from slug catcher "
          + volumeTransmitter3.getMeasuredValue() / 1.0e6 + " MSm^3/day");
      System.out.println("gas rate from slug catcher "
          + volumeTransmitter3.getMeasuredValue() / 1.0e6 + " MSm^3/day");
      System.out.println("water in gas phase from slug catcher "
          + slugCatcher.getGasOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getMolarMass()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getComponent("water")
                  .getx()
              * 3600.0 / (volumeTransmitter3.getMeasuredValue() / 24.0) * 1000.0
          + " gr water/Sm^3 gas");
      System.out.println("gas rate "
          + slugCatcher.getGasOutStream().getThermoSystem().getTotalNumberOfMoles()
              * slugCatcher.getGasOutStream().getThermoSystem().getPhase(0).getMolarMass() * 3600.0
          + " kg gas/hr");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
