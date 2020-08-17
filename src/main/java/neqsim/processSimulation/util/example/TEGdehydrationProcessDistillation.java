package neqsim.processSimulation.util.example;

import java.io.ObjectInputStream.GetField;

import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.Condenser;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.distillation.Reboiler;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.stream.EnergyStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Calculator;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermodynamicOperations.flashOps.SaturateWithWater;
import neqsim.processSimulation.processEquipment.separator.Separator;

public class TEGdehydrationProcessDistillation {

	public static void main(String[] args) {

		// Create the input fluid to the TEG process and saturate it with water at
		// scrubber conditions
		neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 42.0,
				10.00);
		feedGas.addComponent("nitrogen", 1.03);
		feedGas.addComponent("CO2", 1.42);
		feedGas.addComponent("methane", 83.88);
		feedGas.addComponent("ethane", 8.07);
		feedGas.addComponent("propane", 3.54);
		feedGas.addComponent("i-butane", 0.54);
		feedGas.addComponent("n-butane", 0.84);
		feedGas.addComponent("i-pentane", 0.21);
		feedGas.addComponent("n-pentane", 0.19);
		feedGas.addComponent("n-hexane", 0.28);
		feedGas.addComponent("water", 0.0);
		feedGas.addComponent("TEG", 0);
		feedGas.createDatabase(true);
		feedGas.setMixingRule(10);
		feedGas.setMultiPhaseCheck(true);

		Stream dryFeedGas = new Stream("dry feed gas", feedGas);
		dryFeedGas.setFlowRate(11.23, "MSm3/day");
		dryFeedGas.setTemperature(30.4, "C");
		dryFeedGas.setPressure(52.21, "bara");
		StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
		saturatedFeedGas.setName("water saturator");
		Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
		waterSaturatedFeedGas.setName("water saturated feed gas");
		neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		feedTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.03, 0.97 });

		Stream TEGFeed = new Stream("lean TEG to absorber", feedTEG);
		TEGFeed.setFlowRate(6.1 * 1100.0, "kg/hr");
		TEGFeed.setTemperature(35.4, "C");
		TEGFeed.setPressure(52.21, "bara");

		SimpleTEGAbsorber absorber = new SimpleTEGAbsorber();
		absorber.setName("TEG absorber");
		absorber.addGasInStream(waterSaturatedFeedGas);
		absorber.addSolventInStream(TEGFeed);
		absorber.setNumberOfStages(10);
		absorber.setStageEfficiency(0.3);
		absorber.setWaterDewPointTemperature(223.15, 70.0);

		Stream dehydratedGas = new Stream(absorber.getGasOutStream());
		dehydratedGas.setName("dry gas from absorber");
		Stream richTEG = new Stream(absorber.getSolventOutStream());
		richTEG.setName("rich TEG from absorber");
		
		ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", richTEG);
		glycol_flash_valve.setName("Rich TEG HP flash valve");
		glycol_flash_valve.setOutletPressure(4.9);

		Heater richGLycolHeaterCondenser = new Heater(glycol_flash_valve.getOutStream());
		richGLycolHeaterCondenser.setName("rich TEG preheater");
		richGLycolHeaterCondenser.setOutTemperature(273.15 + 36.5);

		Heater richGLycolHeater = new Heater(richGLycolHeaterCondenser.getOutStream());
		richGLycolHeater.setName("rich TEG heater HP");
		richGLycolHeater.setOutTemperature(273.15 + 62.0);

		Separator flashSep = new Separator(richGLycolHeater.getOutStream());
		flashSep.setName("degasing separator");
		Stream flashGas = new Stream(flashSep.getGasOutStream());
		flashGas.setName("gas from degasing separator");
		Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
		flashLiquid.setName("liquid from degasing separator");
		
		Heater richGLycolHeater2 = new Heater(flashLiquid);
		richGLycolHeater2.setName("LP rich glycol heater");
		richGLycolHeater2.setOutTemperature(273.15 + 139.0);
		richGLycolHeater2.setOutPressure(1.23);
		
		neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		stripGas.setMolarComposition(new double[] { 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 });

		Stream strippingGas = new Stream("stripGas", stripGas);
		strippingGas.setFlowRate(90.0, "kg/hr");
		strippingGas.setTemperature(206.6, "C");
		strippingGas.setPressure(1.23, "bara");

		Stream gasToReboiler = (Stream)strippingGas.clone();
		gasToReboiler.setName("gas to reboiler");
		
		DistillationColumn column = new DistillationColumn(2, true, true);
		column.setName("TEG regeneration column");
		column.addFeedStream(richGLycolHeater2.getOutStream(), 2);
		column.getReboiler().setOutTemperature(273.15 + 206.0);
		column.getCondenser().setEnergyStream(richGLycolHeaterCondenser.getEnergyStream());
		column.getReboiler().addStream(gasToReboiler);

		Heater coolerRegenGas = new Heater(column.getGasOutStream());
		coolerRegenGas.setName("regen gas cooler");
		coolerRegenGas.setOutTemperature(273.15 + 36.5);

		Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
		sepregenGas.setName("regen gas separator");

		Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
		gasToFlare.setName("gas to flare");
		
		Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());
		liquidToTrreatment.setName("water to treatment");
		
		WaterStripperColumn stripper = new WaterStripperColumn("TEG stripper");
		stripper.addSolventInStream(column.getLiquidOutStream());
		stripper.addGasInStream(strippingGas);
		stripper.setNumberOfStages(4);
		stripper.setStageEfficiency(0.5);

		
		Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
		recycleGasFromStripper.addStream(stripper.getGasOutStream());
		recycleGasFromStripper.setOutletStream(gasToReboiler);
		
		
		Pump hotLeanTEGPump = new Pump(stripper.getSolventOutStream());//stripper.getSolventOutStream());
		hotLeanTEGPump.setName("hot lean TEG pump");
		hotLeanTEGPump.setOutletPressure(20.0);

		Heater coolerhOTteg = new Heater(hotLeanTEGPump.getOutStream());
		coolerhOTteg.setName("hot lean TEG cooler");
		coolerhOTteg.setOutTemperature(273.15 + 116.8);

		Heater coolerhOTteg2 = new Heater(coolerhOTteg.getOutStream());
		coolerhOTteg2.setName("medium hot lean TEG cooler");
		coolerhOTteg2.setOutTemperature(273.15 + 89.3);

		Heater coolerhOTteg3 = new Heater(coolerhOTteg2.getOutStream());
		coolerhOTteg3.setName("lean TEG cooler");
		coolerhOTteg3.setOutTemperature(273.15 + 35.4);

		Pump hotLeanTEGPump2 = new Pump(coolerhOTteg3.getOutStream());
		hotLeanTEGPump2.setName("lean TEG HP pump");
		hotLeanTEGPump2.setOutletPressure(52.21);
		
		Stream leanTEGtoabs = new Stream(hotLeanTEGPump2.getOutStream());
		leanTEGtoabs.setName("lean TEG to absorber");
		
		neqsim.thermo.system.SystemInterface pureTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		pureTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 });

		Stream makeupTEG = new Stream("makeup TEG", pureTEG);
		makeupTEG.setFlowRate(1e-6, "kg/hr");
		makeupTEG.setTemperature(35.4, "C");
		makeupTEG.setPressure(52.21, "bara");
		
		Calculator makeupCalculator = new Calculator("makeup calculator");
		makeupCalculator.addInputVariable(dehydratedGas);
		makeupCalculator.addInputVariable(flashGas);
		makeupCalculator.addInputVariable(gasToFlare);
		makeupCalculator.addInputVariable(liquidToTrreatment);
		makeupCalculator.setOutputVariable(makeupTEG);
		
		StaticMixer makeupMixer = new StaticMixer("makeup mixer");
		makeupMixer.addStream(leanTEGtoabs);
		makeupMixer.addStream(makeupTEG);
		
		Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
		resycleLeanTEG.addStream(makeupMixer.getOutStream());
		resycleLeanTEG.setOutletStream(TEGFeed);
		resycleLeanTEG.setPriority(200);
		resycleLeanTEG.setDownstreamProperty("flow rate");

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(dryFeedGas);
		operations.add(saturatedFeedGas);
		operations.add(waterSaturatedFeedGas);
		operations.add(TEGFeed);
		operations.add(absorber);
		operations.add(dehydratedGas);
		operations.add(richTEG);
		
		operations.add(glycol_flash_valve);
		operations.add(richGLycolHeaterCondenser);
		operations.add(richGLycolHeater);
		operations.add(flashSep);
		operations.add(flashGas);
		operations.add(flashLiquid);
		operations.add(richGLycolHeater2);
		operations.add(gasToReboiler);
		operations.add(column);
		operations.add(coolerRegenGas);
		operations.add(sepregenGas);
		operations.add(gasToFlare);
		operations.add(liquidToTrreatment);
		operations.add(strippingGas);
		operations.add(stripper);
		operations.add(recycleGasFromStripper);
		operations.add(hotLeanTEGPump);
		operations.add(coolerhOTteg);
		operations.add(coolerhOTteg2);
		operations.add(coolerhOTteg3);
		operations.add(hotLeanTEGPump2);
		operations.add(leanTEGtoabs);
		operations.add(makeupCalculator);
		operations.add(makeupTEG);
		operations.add(makeupMixer);
		operations.add(resycleLeanTEG);
	
		operations.run();
		
		operations.save("c:/temp/TEGprocess.neqsim");
		
		
		double waterInWetGasppm = waterSaturatedFeedGas.getFluid().getPhase(0).getComponent("water").getz()*1.0e6;
		double waterInWetGaskgMSm3 = waterInWetGasppm*0.01802*101325.0/(8.314*288.15);
		double TEGfeedwt = TEGFeed.getFluid().getPhase("aqueous").getWtFrac("TEG");
		double TEGfeedflw = TEGFeed.getFlowRate("kg/hr");
		double waterInDehydratedGasppm = dehydratedGas.getFluid().getPhase(0).getComponent("water").getz()*1.0e6;
		double waterInDryGaskgMSm3 = waterInDehydratedGasppm*0.01802*101325.0/(8.314*288.15);
		double richTEG2 = richTEG.getFluid().getPhase("aqueous").getWtFrac("TEG");
		System.out.println("reboiler duty (KW) "
				+ ((Reboiler)column.getReboiler()).getDuty()/1.0e3);
		System.out.println("flow rate from reboiler " + ((Reboiler)column.getReboiler()).getLiquidOutStream().getFlowRate("kg/hr"));
		System.out.println("flow rate from stripping column " + stripper.getLiquidOutStream().getFlowRate("kg/hr"));
		System.out.println("flow rate from pump2  " + hotLeanTEGPump2.getOutStream().getFluid().getFlowRate("kg/hr"));
		System.out.println("makeup TEG  " + makeupTEG.getFluid().getFlowRate("kg/hr"));
		
		System.out.println("pump power " + hotLeanTEGPump.getDuty());
		System.out.println("pump2 power " + hotLeanTEGPump2.getDuty());
		System.out.println("wt lean TEG after reboiler "
		+ column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));
		System.out.println("temperature from pump " + (hotLeanTEGPump2.getOutStream().getTemperature()-273.15));

		System.out.println("flow rate from reboiler " + ((Reboiler)column.getReboiler()).getLiquidOutStream().getFlowRate("kg/hr"));
		System.out.println("flow rate from pump2  " + hotLeanTEGPump2.getOutStream().getFluid().getFlowRate("kg/hr"));

	}

}
