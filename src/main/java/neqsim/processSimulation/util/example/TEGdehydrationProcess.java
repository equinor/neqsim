package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.absorber.SimpleTEGAbsorber;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermodynamicOperations.flashOps.SaturateWithWater;
import neqsim.processSimulation.processEquipment.separator.Separator;

public class TEGdehydrationProcess {

	public static void main(String[] args) {
		
		//Create the input fluid to the TEG process and saturate it with water at scrubber conditions
        neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15+42.0,10.00);
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
        dryFeedGas.setPressure(51.2, "bara");
        StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
        Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
        
        
        
        
        
        neqsim.thermo.system.SystemInterface feedTEG = (neqsim.thermo.system.SystemInterface)feedGas.clone();
        feedTEG.setMolarComposition(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.95});
        
        Stream TEGFeed = new Stream("feed TEG", feedTEG);
        TEGFeed.setFlowRate(6.9e3, "kg/hr");
        TEGFeed.setTemperature(35.4, "C");
        TEGFeed.setPressure(51.2, "bara");
        
        SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("SimpleTEGAbsorber");
        absorber.addGasInStream(waterSaturatedFeedGas);
        absorber.addSolventInStream(TEGFeed);
        absorber.setNumberOfStages(3);
        absorber.setStageEfficiency(1);
        absorber.setWaterDewPointTemperature(243.15, 70.0);
        
        
        Stream dehydratedGas = new Stream(absorber.getGasOutStream());
        Stream richTEG = new Stream(absorber.getSolventOutStream());
        
        ThrottlingValve glycol_flash_valve = new ThrottlingValve("Flash valve", richTEG);
        glycol_flash_valve.setOutletPressure(4.9);
        
        Heater richGLycolHeater = new Heater(glycol_flash_valve.getOutStream());
        richGLycolHeater.setOutTemperature(331.0);
        
        Separator flashSep = new Separator(richGLycolHeater.getOutStream());
        
        Stream flashGas = new Stream(flashSep.getGasOutStream());
        Stream flashLiquid = new Stream(flashSep.getLiquidOutStream());
        
        ThrottlingValve glycol_flash_valve2 = new ThrottlingValve("Flash valve2", flashLiquid);
        glycol_flash_valve2.setOutletPressure(1.01325);
        
        Heater heaterToReboiler = new Heater(glycol_flash_valve2.getOutStream());
        heaterToReboiler.setOutTemperature(273.15+204.0);
        
        neqsim.thermo.system.SystemInterface stripGas = (neqsim.thermo.system.SystemInterface)feedGas.clone();
        stripGas.setMolarComposition(new double[] {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        
        Stream strippingGas = new Stream("stripGas", stripGas);
        strippingGas.setFlowRate(400.0, "kg/hr");
        strippingGas.setTemperature(204.0, "C");
        strippingGas.setPressure(1.01325, "bara");
        
        Separator regenerator2 = new Separator(heaterToReboiler.getOutStream());
        regenerator2.addStream(strippingGas);
        
        Heater coolerRegenGas = new Heater(regenerator2.getGasOutStream());
        coolerRegenGas.setOutTemperature(273.15+35.5);
        
        Separator sepregenGas = new Separator(coolerRegenGas.getOutStream());
        
        Stream gasToFlare = new Stream(sepregenGas.getGasOutStream());
        
        Stream hotLeanTEG = new Stream(regenerator2.getLiquidOutStream());
        
        Pump hotLeanTEGPump = new Pump(hotLeanTEG);
        hotLeanTEGPump.setOutletPressure(51.2);
        
        Heater coolerhOTteg = new Heater(hotLeanTEGPump.getOutStream());
        coolerhOTteg.setOutTemperature(273.15+35.5);
        
        // Setting up the process simulation
        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(dryFeedGas);
        operations.add(saturatedFeedGas);
        operations.add(waterSaturatedFeedGas);
        operations.add(TEGFeed);
        operations.add(absorber);
        operations.add(dehydratedGas);
        operations.add(richTEG);
        operations.add(glycol_flash_valve);
        operations.add(richGLycolHeater);
        operations.add(flashSep);
        operations.add(flashGas);
        operations.add(flashLiquid);
        operations.add(glycol_flash_valve2);
        operations.add(heaterToReboiler);
        operations.add(strippingGas);
        operations.add(regenerator2);
        operations.add(coolerRegenGas);
        operations.add(sepregenGas);
        operations.add(gasToFlare);
        operations.add(hotLeanTEG);
        operations.add(hotLeanTEGPump);
        operations.add(coolerhOTteg);
        
      
        for(int i=0;i<1;i++) {
        	System.out.println("run " + i);
        	//TEGFeed.setFlowRate((6.0+i)*1e3, "kg/hr");
        	operations.run();
        	
        	 double waterInWetGaskghr = waterSaturatedFeedGas.getFluid().getPhase("gas").getComponent("water").getNumberOfmoles()*waterSaturatedFeedGas.getFluid().getPhase("gas").getComponent("water").getMolarMass()*3600.0;
             double waterInWetGaskgMSm3 = waterSaturatedFeedGas.getFluid().getPhase("gas").getComponent("water").getNumberOfmoles()*waterSaturatedFeedGas.getFluid().getPhase("gas").getComponent("water").getMolarMass()*3600.0*24.0/waterSaturatedFeedGas.getFlowRate("MSm3/day");
             double waterInDryGaskghr = dehydratedGas.getFluid().getPhase("gas").getComponent("water").getNumberOfmoles()*dehydratedGas.getFluid().getPhase("gas").getComponent("water").getMolarMass()*3600.0;
             double waterInDryGaskgMSm3 = dehydratedGas.getFluid().getPhase("gas").getComponent("water").getNumberOfmoles()*dehydratedGas.getFluid().getPhase("gas").getComponent("water").getMolarMass()*3600.0*24.0/dehydratedGas.getFlowRate("MSm3/day");
            
             double flowRateDryGasm3hr = dryFeedGas.getFlowRate("m3/hr");
             double flowRateDryGaskghr = dryFeedGas.getFlowRate("kg/hr");
             double end = 0.0;
             double wtPleanTEG = coolerhOTteg.getOutStream().getFluid().getPhase("aqueous").getWtFrac("TEG");
             double strippingGasRate = gasToFlare.getFluid().getFlowRate("kg/hr");
             double reboilerDuty = heaterToReboiler.getDuty()/1.0e6;
             System.out.println("water in wet gas " + waterInWetGaskgMSm3 + " kg/MSm3");
             System.out.println("water in dry gas " + waterInDryGaskgMSm3 + " kg/MSm3");
             System.out.println("wt TEG " + wtPleanTEG*100);
             System.out.println("stripping gas rate " + strippingGasRate + " kg/hr");
             System.out.println("reboiler duty " + reboilerDuty + " MW");
        	
        }

   
        //dryFeedGas.displayResult();
     //   waterSaturatedFeedGas.displayResult();
     //   TEGFeed.displayResult();
     //   dehydratedGas.displayResult();
    //    hotLeanTEGPump.displayResult();
      //  sepregenGas.displayResult();
	}

}
