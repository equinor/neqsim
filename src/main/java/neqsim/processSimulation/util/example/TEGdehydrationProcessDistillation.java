package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.absorber.WaterStripperColumn;
import neqsim.processSimulation.processEquipment.distillation.DistillationColumn;
import neqsim.processSimulation.processEquipment.distillation.Reboiler;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.stream.EnergyStream;
import neqsim.processSimulation.processEquipment.stream.Stream;
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

		Stream dryFeedGas = new Stream("input stream", feedGas);
		dryFeedGas.setFlowRate(11.23, "MSm3/day");
		dryFeedGas.setTemperature(30.4, "C");
		dryFeedGas.setPressure(52.21, "bara");
		StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
		Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());

		neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		feedTEG.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.95 });

		Stream TEGFeed = new Stream("feed TEG", feedTEG);
		TEGFeed.setFlowRate(6.1 * 1100.0, "kg/hr");
		TEGFeed.setTemperature(35.4, "C");
		TEGFeed.setPressure(52.21, "bara");

		SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("SimpleTEGAbsorber");
		absorber.addGasInStream(waterSaturatedFeedGas);
		absorber.addSolventInStream(TEGFeed);
		absorber.setNumberOfStages(10);
		absorber.setStageEfficiency(0.35);
		absorber.setWaterDewPointTemperature(223.15, 70.0);

		Stream dehydratedGas = new Stream(absorber.getGasOutStream());
		Stream richTEG = new Stream(absorber.getSolventOutStream());

		ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", richTEG);
		glycol_flash_valve.setOutletPressure(4.9);

		Heater richGLycolHeaterCondeser = new Heater(glycol_flash_valve.getOutStream());
		richGLycolHeaterCondeser.setOutTemperature(273.15 + 33.5);

		Heater richGLycolHeater = new Heater(richGLycolHeaterCondeser.getOutStream());
		richGLycolHeater.setOutTemperature(273.15 + 62.0);

		Separator flashSep = new Separator(richGLycolHeater.getOutStream());

		Stream flashGas = new Stream(flashSep.getGasOutStream());
		Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());

		Heater richGLycolHeater2 = new Heater(flashLiquid);
		richGLycolHeater2.setOutTemperature(273.15 + 139.0);

		ThrottlingValve glycol_flash_valve2 = new ThrottlingValve("Flash valve2", richGLycolHeater2.getOutStream());
		glycol_flash_valve2.setOutletPressure(1.23);

		DistillationColumn column = new DistillationColumn(2, true, true);
		column.addFeedStream(glycol_flash_valve2.getOutStream(), 2);
		column.getReboiler().setOutTemperature(273.15 + 206.0);
		column.getCondenser().setEnergyStream(richGLycolHeaterCondeser.getEnergyStream());
		
		Heater coolerRegenGas = new Heater(column.getGasOutStream());
		coolerRegenGas.setOutTemperature(273.15 + 35.5);

		Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());

		Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
		
		Stream liquidToTrreatment = new Stream(sepregenGas.getLiquidOutStream());

		neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		stripGas.setMolarComposition(new double[] { 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 });

		Stream strippingGas = new Stream("stripGas", stripGas);
		strippingGas.setFlowRate(50.0, "kg/hr");
		strippingGas.setTemperature(206.6, "C");
		strippingGas.setPressure(1.23, "bara");

		WaterStripperColumn stripper = new WaterStripperColumn("SimpleTEGstripper");
		stripper.addGasInStream(strippingGas);
		stripper.addSolventInStream(column.getLiquidOutStream());
		stripper.setNumberOfStages(5);
		stripper.setStageEfficiency(0.5);

		Recycle recycleGasFromStripper = new Recycle("stripping gas recirc");
		recycleGasFromStripper.addStream(stripper.getGasOutStream());

		Pump hotLeanTEGPump = new Pump(stripper.getSolventOutStream());
		hotLeanTEGPump.setOutletPressure(20.0);

		Heater coolerhOTteg = new Heater(hotLeanTEGPump.getOutStream());
		coolerhOTteg.setOutTemperature(273.15 + 116.8);

		Heater coolerhOTteg2 = new Heater(coolerhOTteg.getOutStream());
		coolerhOTteg2.setOutTemperature(273.15 + 89.3);

		Heater coolerhOTteg3 = new Heater(coolerhOTteg2.getOutStream());
		coolerhOTteg3.setOutTemperature(273.15 + 44.85);

		Pump hotLeanTEGPump2 = new Pump(coolerhOTteg3.getOutStream());
		hotLeanTEGPump2.setOutletPressure(52.21);

		Recycle resycleLeanTEG = new Recycle("lean TEG resycle");
		resycleLeanTEG.addStream(hotLeanTEGPump2.getOutStream());

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(dryFeedGas);
		operations.add(saturatedFeedGas);
		operations.add(waterSaturatedFeedGas);
		operations.add(TEGFeed);
		operations.add(absorber);
		operations.add(dehydratedGas);
		operations.add(richTEG);
		operations.add(glycol_flash_valve);
		operations.add(richGLycolHeaterCondeser);
		operations.add(richGLycolHeater);
		operations.add(flashSep);
		operations.add(flashGas);
		operations.add(flashLiquid);
		operations.add(richGLycolHeater2);
		operations.add(glycol_flash_valve2);
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
		operations.add(resycleLeanTEG);
		operations.save("c:/temp/TEGprocessFullNoRecircStart.neqsim");
		operations = ProcessSystem.open("c:/temp/TEGprocessFullNoRecircStart.neqsim");
		//operations.save("c:/temp/TEGprocessFullNoRecirc3.neqsim");
		operations.run();
		//operations.save("c:/temp/TEGprocessFullNoRecirc4.neqsim");
		System.out.println("wt lean TEG after reboiler "
				+ column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));

		System.out.println("wt lean TEG after stripper "
				+ stripper.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));

		column.getReboiler().addStream(recycleGasFromStripper.getOutStream());
		absorber.replaceSolventInStream(resycleLeanTEG.getOutStream());
		operations.run();
		

	 //   operations = ProcessSystem.open("c:/temp/TEGprocessFull.neqsim");
		System.out.println("wt lean TEG after reboiler "
				+ column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));

		System.out.println("wt lean TEG after stripper "
				+ stripper.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));

		gasToFlare.getFluid().display();
		/*
		 * 
		 * DistillationColumn column2 = new DistillationColumn(2, true, true);
		 * column2.addFeedStream(glycol_flash_valve2.getOutStream(), 2);
		 * column2.getReboiler().setOutTemperature(273.15+206.0);
		 * column2.getCondenser().setEnergyStream(richGLycolHeaterCondeser.
		 * getEnergyStream()); column2.run();
		 * column2.getReboiler().getFluid().display();
		 * System.out.println("wt lean TEG after reboiler " +
		 * column2.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"))
		 * ; /*
		 * 
		 * WaterStripperColumn stripper = new WaterStripperColumn("SimpleTEGstripper");
		 * stripper.addGasInStream(strippingGas);
		 * stripper.addSolventInStream(column2.getLiquidOutStream());
		 * stripper.setNumberOfStages(5); stripper.setStageEfficiency(0.5);
		 * stripper.run();
		 * 
		 * 
		 * System.out.println("Energy condenser1 " +
		 * richGLycolHeaterCondeser.getEnergyStream().getDuty());
		 * 
		 * // System.out.println("wt lean TEG after reboiler " +
		 * column.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG"));
		 * 
		 * // System.out.println("wt lean TEG after stripper " +
		 * stripper.getLiquidOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG")
		 * );
		 */
	}

}
