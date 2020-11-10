package neqsim.processSimulation.util.example;

import java.io.ObjectInputStream.GetField;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.distillation.Reboiler;
import neqsim.processSimulation.processEquipment.expander.Expander;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;

public class OnshoreMEGprocess {

	public static void main(String[] args) {

		neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0, 10.00);
		feedGas.addComponent("nitrogen", 0.4);
		feedGas.addComponent("CO2", 0.00042);
		feedGas.addComponent("methane", 99.0);
		feedGas.addComponent("ethane", 0.07);
		feedGas.addComponent("propane", 0.054);
		feedGas.addComponent("i-butane", 0.0054);
		feedGas.addComponent("n-butane", 0.0084);
		feedGas.addComponent("water", 0.0);
		feedGas.addComponent("MEG", 0);
		feedGas.createDatabase(true);
		feedGas.setMixingRule(10);
		feedGas.setMultiPhaseCheck(true);

		Stream dryFeedGas = new Stream("dry feed gas", feedGas);
		dryFeedGas.setFlowRate(10.5, "MSm3/day");
		dryFeedGas.setTemperature(6.0, "C");
		dryFeedGas.setPressure(53.0, "bara");

		StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
		saturatedFeedGas.setName("water saturator");

		Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
		waterSaturatedFeedGas.setName("water saturated feed gas");
		
		Compressor inletCompressor = new Compressor("1st stage compressor", waterSaturatedFeedGas);
		inletCompressor.setOutletPressure(70.0, "bara");
		
		Cooler interstageGasCooler = new Cooler(inletCompressor.getOutStream());
		interstageGasCooler.setOutTemperature(40.0, "C");
		interstageGasCooler.setName("1st stage compressor after cooler");

		Compressor inletCompressor2ndstage = new Compressor("2nd stage compressor", interstageGasCooler.getOutStream());
		inletCompressor2ndstage.setOutletPressure(105.0, "bara");
		
		Stream streamFromGasExpander = new Stream(inletCompressor2ndstage.getOutStream());
		streamFromGasExpander.setName("stream from inlet gas compressor");
		
		neqsim.thermo.system.SystemInterface feedMEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		feedMEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.4, 0.6});

		Stream MEGFeed = new Stream("lean MEG for distribution", feedMEG);
		MEGFeed.setFlowRate(200.0, "kg/hr");
		MEGFeed.setTemperature(20.0, "C");
		MEGFeed.setPressure(105.0, "bara");

		Splitter MEGsplitter1 = new Splitter(MEGFeed);
		MEGsplitter1.setName("MEG splitter");
		MEGsplitter1.setSplitFactors(new double[] { 0.6, 0.3 , 0.1});

		StaticMixer MEGmixer1 = new StaticMixer("MEG mixer 1");
		MEGmixer1.addStream(streamFromGasExpander);
		MEGmixer1.addStream(MEGsplitter1.getSplitStream(0));

		Cooler inletGasCooler = new Cooler(MEGmixer1.getOutStream());
		inletGasCooler.setOutTemperature(10.0, "C");
		inletGasCooler.setName("inlet gas cooler");
		
		StaticMixer MEGmixer2 = new StaticMixer("MEG mixer 1");
		MEGmixer2.addStream(inletGasCooler.getOutStream());
		MEGmixer2.addStream(MEGsplitter1.getSplitStream(1));
		
		Cooler crossGasCooler = new Cooler(MEGmixer2.getOutStream());
		crossGasCooler.setOutTemperature(-10.0, "C");
		crossGasCooler.setName("cross gas cooler");
		/*
		HeatExchanger heatEx = new HeatExchanger(MEGmixer2.getOutStream());
		heatEx.setName("cross gas cooler");
		heatEx.setGuessOutTemperature(273.15-10.0);
		heatEx.setUAvalue(30000.0);
		*/
		StaticMixer MEGmixer3 = new StaticMixer("MEG mixer 3");
		MEGmixer3.addStream(crossGasCooler.getOutStream());
		MEGmixer3.addStream(MEGsplitter1.getSplitStream(2));
		
		ThrottlingValve presRedValveLT = new ThrottlingValve("low temperature JT valve", MEGmixer3.getOutStream());
		presRedValveLT.setOutletPressure(92.0);

		ThreePhaseSeparator mpseparator = new ThreePhaseSeparator("low temperature separator", presRedValveLT.getOutStream());

		Stream coldGasFromSep = new Stream(mpseparator.getGasOutStream());
		coldGasFromSep.setName("gas from cold sep");
		
		//heatEx.setFeedStream(1, coldGasFromSep);
		
	//	Stream heatedGasFromLPSep = new Stream(heatEx.getOutStream(1));
	//	heatedGasFromLPSep.setName("heated gas from LP sep");

		Stream richMEGstream = new Stream(mpseparator.getWaterOutStream());
		richMEGstream.setName("rich MEG stream");
		
		Heater richMEGstreamHeater = new Heater(richMEGstream);
		richMEGstreamHeater.setOutTemperature(15.0, "C");
		richMEGstreamHeater.setName("rich MEG pre heater");

		ThrottlingValve presRedValve3 = new ThrottlingValve("LP pressure reduction valve", richMEGstreamHeater.getOutStream());
		presRedValve3.setOutletPressure(10.0);

		Separator flashDrumSep = new Separator("cold flash drum", presRedValve3.getOutStream());
		
		Stream flashGasStream = new Stream(flashDrumSep.getGasOutStream());
		flashGasStream.setName("flash gas");
		
		Stream flashLiquidStream = new Stream(flashDrumSep.getLiquidOutStream());
		flashGasStream.setName("flash liquid");
/*
		HeatExchanger columnPreHeater = new HeatExchanger(flashLiquidStream);
		columnPreHeater.setName("rich MEG heater");
		columnPreHeater.setGuessOutTemperature(273.15+120.0);
		columnPreHeater.setUAvalue(500.0);
		*/
		Heater xolumnHea = new Heater(flashLiquidStream);
		xolumnHea.setOutTemperature(110.0, "C");
		
		ThrottlingValve presRedValve4 = new ThrottlingValve("LP pressure reduction valve", xolumnHea.getOutStream());
		presRedValve4.setOutletPressure(1.23);

		DistillationColumn column = new DistillationColumn(2, true, true);
		column.setName("MEG regeneration column");
		column.addFeedStream(presRedValve4.getOutStream(), 1);
		column.getReboiler().setOutTemperature(273.15 + 135.0);
		column.getCondenser().setOutTemperature(273.15 + 125.0);
		column.setTopPressure(1.1);
		column.setBottomPressure(1.23);

		Cooler coolerRegenGas = new Cooler(column.getGasOutStream());
		coolerRegenGas.setName("regen gas cooler");
		coolerRegenGas.setOutTemperature(273.15 + 20.0);

		Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
		sepregenGas.setName("regen gas separator");

		Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
		gasToFlare.setName("gas to flare");
		
		Stream waterToSea = new Stream(sepregenGas.getLiquidOutStream());
		waterToSea.setName("water to sea");
		
		Cooler bufferTank = new Cooler("MEG buffer tank", column.getLiquidOutStream());
		bufferTank.setOutTemperature(273.15+130.0);
		
		Pump hotLeanMEGPump = new Pump(bufferTank.getOutStream());
		hotLeanMEGPump.setName("hot lean MEG pump");
		hotLeanMEGPump.setOutletPressure(20.0);
		hotLeanMEGPump.setIsentropicEfficiency(0.75);
		
		Stream streamHotPump = new Stream(hotLeanMEGPump.getOutStream());
		streamHotPump.setName("hot stream from MEG pump");
		
		//columnPreHeater.setFeedStream(1, streamHotPump);
		
		Heater xolumnCooler = new Heater(streamHotPump);
		xolumnCooler.setOutTemperature(40.0, "C");

		Pump hotLeanMEGPump2 = new Pump(xolumnCooler.getOutStream());
		hotLeanMEGPump2.setName("lean MEG HP pump");
		hotLeanMEGPump2.setOutletPressure(105.0);
		hotLeanMEGPump2.setIsentropicEfficiency(0.75);
		
		Cooler coolerHotMEG2 = new Cooler(hotLeanMEGPump2.getOutStream());
		coolerHotMEG2.setName("lean MEG cooler");
		coolerHotMEG2.setOutTemperature(273.15 + 20.0);

		Stream leanMEGtoMixer = new Stream(coolerHotMEG2.getOutStream());
		leanMEGtoMixer.setName("lean MEG to absorber");

		neqsim.thermo.system.SystemInterface pureMEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		pureMEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 });

		Stream makeupMEG = new Stream("makeup MEG", pureMEG);
		makeupMEG.setFlowRate(1e-6, "kg/hr");
		makeupMEG.setTemperature(20.0, "C");
		makeupMEG.setPressure(105.0, "bara");

		Calculator makeupCalculator = new Calculator("MEG makeup calculator");
		makeupCalculator.addInputVariable(coldGasFromSep);
		makeupCalculator.addInputVariable(waterToSea);
		makeupCalculator.addInputVariable(gasToFlare);
		makeupCalculator.addInputVariable(flashGasStream);
		makeupCalculator.setOutputVariable(makeupMEG);

		StaticMixer makeupMixer = new StaticMixer("makeup mixer");
		makeupMixer.addStream(leanMEGtoMixer);
		makeupMixer.addStream(makeupMEG);
		
		Stream streamToResycle = new Stream(makeupMixer.getOutStream());
		
		Recycle resycleLeanMEG = new Recycle("lean MEG resycle");
		resycleLeanMEG.addStream(streamToResycle);
		resycleLeanMEG.setOutletStream(MEGFeed);
		//resycleLeanMEG.setPriority(200);
		resycleLeanMEG.setDownstreamProperty("flow rate");

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(dryFeedGas);
		operations.add(saturatedFeedGas);
		operations.add(waterSaturatedFeedGas);
		operations.add(inletCompressor);
		operations.add(interstageGasCooler);
		operations.add(inletCompressor2ndstage);
		operations.add(streamFromGasExpander);
		operations.add(MEGFeed);
		operations.add(MEGsplitter1);
		operations.add(MEGmixer1);
		operations.add(inletGasCooler);
		operations.add(MEGmixer2);
		operations.add(crossGasCooler);
	//	operations.add(heatEx);
		operations.add(MEGmixer3);
		operations.add(presRedValveLT);
		operations.add(mpseparator);
		operations.add(coldGasFromSep);
	//	operations.add(heatedGasFromLPSep);
		operations.add(richMEGstream);
		operations.add(richMEGstreamHeater);
		operations.add(presRedValve3);
		operations.add(flashDrumSep);
		operations.add(flashGasStream);
		operations.add(flashLiquidStream);
		//operations.add(columnPreHeater);
		operations.add(xolumnHea);
		operations.add(presRedValve4);	
		operations.add(column);
		operations.add(coolerRegenGas);
		operations.add(sepregenGas);
		operations.add(gasToFlare);
		operations.add(waterToSea);
		operations.add(bufferTank);
		operations.add(hotLeanMEGPump);
		operations.add(streamHotPump);
		//operations.add(columnPreHeater);
		operations.add(xolumnCooler);
		operations.add(hotLeanMEGPump2);
		operations.add(coolerHotMEG2);
		operations.add(leanMEGtoMixer);
		operations.add(makeupCalculator);
		operations.add(makeupMEG);
		operations.add(makeupMixer);
		operations.add(streamToResycle);
		operations.add(resycleLeanMEG);
		
		
		//operations = ProcessSystem.open("c:/temp/onshoreMEGprocess.neqsim");
		operations.run();
		//presRedValve4.displayResult();
		System.out.println(
				"temperature after expander  " +streamFromGasExpander.getTemperature("C"));
	//	System.out.println(
//				"temperature after cross cooler  " +heatEx.getOutStream(0).getTemperature("C"));
		System.out.println(
				"MEG flow rate " + richMEGstream.getFluid().getFlowRate("kg/hr"));
		System.out.println(
				"MEG feed to column rate " + presRedValve4.getOutStream().getFluid().getFlowRate("kg/hr"));
		
		System.out.println(
				"MEG flow rate " + resycleLeanMEG.getFluid().getFlowRate("kg/hr"));
		System.out.println(
				"Reboiler duty [kW] " + ((Reboiler)column.getReboiler()).getDuty()/1.0e3);
		System.out.println(
				"wt% lean MEG  " + MEGFeed.getFluid().getPhase("aqueous").getWtFrac("MEG") * 100.0);
	//	System.out.println("heat ex out temperature " + heatEx.getOutStream(0).getTemperature("C"));
		System.out.println("cold gas temperature " + coldGasFromSep.getTemperature("C"));
		//System.out.println("column glycol pre heater temperature " + columnPreHeater.getOutStream(0).getTemperature("C"));
		//columnPreHeater.run();
		//System.out.println("column glycol pre heater temperature " + columnPreHeater.getOutStream(0).getTemperature("C"));
		//columnPreHeater.run();
		//System.out.println("column glycol pre heater temperature " + columnPreHeater.getOutStream(0).getTemperature("C"));
		//columnPreHeater.run();
		//System.out.println("column glycol pre heater temperature " + columnPreHeater.getOutStream(0).getTemperature("C"));
		//System.out.println("column glycol pre heater temperature " + columnPreHeater.getOutStream(1).getTemperature("C"));
		
		// presRedValve4.getOutStream().displayResult();
		gasToFlare.displayResult();
		waterToSea.displayResult();
		System.out.println(
				"lean MEG wt% " + column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("MEG") * 100.0);
		System.out.println("hydrate temperature 1 "
				+ (inletGasCooler.getOutStream().getHydrateEquilibriumTemperature() - 273.15) + " wt% MEG "
				+ inletGasCooler.getOutStream().getFluid().getPhase("aqueous").getWtFrac("MEG") * 100.0);
	
		operations.save("c:/temp/onshoreMEGprocess.neqsim");
		
	}

}
