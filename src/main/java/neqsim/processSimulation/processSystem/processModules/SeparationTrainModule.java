/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * To change this license header, choose License Headers in Project Properties. To change this
 * template file, choose Tools | Templates and open the template in the editor.
 */
package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Cooler;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 *
 * @author esol
 */
public class SeparationTrainModule extends ProcessModuleBaseClass {
        private static final long serialVersionUID = 1000;

        protected StreamInterface feedStream = null, gasExitStream = null, oilExitStream = null;
        // ThreePhaseSeparator thirdStageSeparator = null;
        Separator gasInletScrubber = null;
        Cooler oilCooler;
        double secondstagePressure = 15.00; // bar'
        double thirdstagePressure = 1.50; // bar
        double heatedOilTemperature = 273.15 + 50;
        double exitGasScrubberTemperature = 273.15 + 30;
        double firstStageCompressorAfterCoolerTemperature = 273.15 + 30;
        double exportOilTemperature = 273.15 + 30;

        @Override
        public void addInputStream(String streamName, StreamInterface stream) {
                if (streamName.equals("feed stream")) {
                        this.feedStream = stream;
                }
        }

        @Override
        public StreamInterface getOutputStream(String streamName) {
                if (!isInitializedStreams) {
                        initializeStreams();
                }
                if (streamName.equals("gas exit stream")) {
                        return this.gasExitStream;
                } else if (streamName.equals("oil exit stream")) {
                        return this.oilExitStream;
                } else {
                        return null;
                }
        }

        @Override
        public void run() {
                if (!isInitializedModule) {
                        initializeModule();
                }
                getOperations().run();

                gasExitStream = gasInletScrubber.getGasOutStream();
                oilExitStream = oilCooler.getOutStream();
        }

        @Override
        public void initializeModule() {
                isInitializedModule = true;
                double inletPressure = feedStream.getPressure();
                Separator inletSeparator = new Separator("Inlet separator", feedStream);

                Heater liquidOutHeater =
                                new Heater("oil/water heater", inletSeparator.getLiquidOutStream());
                liquidOutHeater.setOutTemperature(heatedOilTemperature);

                ThreePhaseSeparator firstStageSeparator = new ThreePhaseSeparator(
                                "1st stage separator", liquidOutHeater.getOutStream());

                ThrottlingValve valve1 = new ThrottlingValve("1stTo2ndStageOilValve",
                                firstStageSeparator.getOilOutStream());
                valve1.setOutletPressure(secondstagePressure);

                ThreePhaseSeparator secondStageSeparator = new ThreePhaseSeparator(
                                "2nd stage Separator", valve1.getOutStream());

                ThrottlingValve thirdStageValve = new ThrottlingValve("2-3stageOilValve",
                                secondStageSeparator.getLiquidOutStream());
                thirdStageValve.setOutletPressure(thirdstagePressure);

                ThreePhaseSeparator thirdStageSeparator = new ThreePhaseSeparator(
                                "3rd stage Separator", thirdStageValve.getOutStream());

                oilCooler = new Cooler("export oil cooler",
                                thirdStageSeparator.getLiquidOutStream());
                oilCooler.setOutTemperature(exportOilTemperature);

                Compressor thirdStageCompressor = new Compressor("3rd stage recompressor",
                                thirdStageSeparator.getGasOutStream());
                thirdStageCompressor.setOutletPressure(secondstagePressure);

                Cooler thirdSstageCoooler =
                                new Cooler("3rd stage cooler", thirdStageCompressor.getOutStream());
                thirdSstageCoooler.setOutTemperature(firstStageCompressorAfterCoolerTemperature);

                Mixer thirdStageMixer = new Mixer("1st and 2nd stage gas mixer");
                thirdStageMixer.addStream(thirdSstageCoooler.getOutStream());
                thirdStageMixer.addStream(secondStageSeparator.getGasOutStream());

                Separator thirdStageScrubber = new Separator("recompression scrubber",
                                thirdStageMixer.getOutStream());
                secondStageSeparator.addStream(thirdStageScrubber.getLiquidOutStream());

                Compressor secondStageCompressor = new Compressor("2nd stage recompressor",
                                thirdStageScrubber.getGasOutStream());
                secondStageCompressor.setOutletPressure(inletPressure);

                Mixer HPgasMixer = new Mixer("HPgas mixer");
                HPgasMixer.addStream(firstStageSeparator.getGasOutStream());
                HPgasMixer.addStream(secondStageCompressor.getOutStream());
                HPgasMixer.addStream(inletSeparator.getGasOutStream());

                Cooler inletGasCooler = new Cooler("HP gas cooler", HPgasMixer.getOutStream());
                inletGasCooler.setOutTemperature(exitGasScrubberTemperature);

                gasInletScrubber = new Separator("HP gas scrubber", inletGasCooler.getOutStream());

                Recycle HPliquidRecycle = new Recycle("Resycle");
                double tolerance = 1e-10;
                HPliquidRecycle.setTolerance(tolerance);
                HPliquidRecycle.addStream(gasInletScrubber.getLiquidOutStream());
                inletSeparator.addStream(HPliquidRecycle.getOutStream());

                getOperations().add(inletSeparator);
                getOperations().add(liquidOutHeater);
                getOperations().add(firstStageSeparator);
                getOperations().add(valve1);
                getOperations().add(secondStageSeparator);
                getOperations().add(thirdStageValve);
                getOperations().add(thirdStageSeparator);
                getOperations().add(thirdStageCompressor);
                getOperations().add(thirdStageMixer);
                getOperations().add(thirdSstageCoooler);
                getOperations().add(thirdStageScrubber);
                getOperations().add(HPliquidRecycle);
                getOperations().add(secondStageCompressor);
                getOperations().add(oilCooler);
                getOperations().add(HPgasMixer);
                getOperations().add(inletGasCooler);
                getOperations().add(gasInletScrubber);

                // gasExitStream = gasInletScrubber.getGasOutStream();
                // oilExitStream = thirdStageSeparator.getOilOutStream();
        }

        @Override
        public void initializeStreams() {
                isInitializedStreams = true;
        }

        @Override
        public void runTransient(double dt) {
                getOperations().runTransient();
        }

        @Override
        public void calcDesign() {
                // design is done here
        }

        @Override
        public void setDesign() {
                // set design is done here
        }

        @Override
        public void setSpecification(String specificationName, double value) {
                if (specificationName.equals("Second stage pressure")) {
                        secondstagePressure = value;
                }
                if (specificationName.equals("heated oil temperature")) {
                        heatedOilTemperature = value;
                }
                if (specificationName.equals("Third stage pressure")) {
                        thirdstagePressure = value;
                }
                if (specificationName.equals("Gas exit temperature")) {
                        exitGasScrubberTemperature = value;
                }
                if (specificationName.equals("First stage compressor after cooler temperature")) {
                        firstStageCompressorAfterCoolerTemperature = value;
                }
                if (specificationName.equals("Export oil temperature")) {
                        exportOilTemperature = value;
                }
        }

        @SuppressWarnings("unused")
        public static void main(String[] args) {
                neqsim.thermo.system.SystemInterface testSystem =
                                new neqsim.thermo.system.SystemSrkEos(273.15 + 50, 65);

                // testSystem.addComponent("CO2", 1);
                // testSystem.addComponent("nitrogen", 1);
                testSystem.addComponent("methane", 95);
                // testSystem.addComponent("ethane", 1);
                // testSystem.addTBPfraction("C7", 1.0, 187.0 / 1000.0, 0.84738);

                // testSystem.addComponent("propane", 5);
                // testSystem.addComponent("n-octane", 2);
                testSystem.addComponent("nC10", 6);
                // testSystem.setHeavyTBPfractionAsPlusFraction();
                // testSystem.getCharacterization().characterisePlusFraction();

                testSystem.addComponent("water", 12);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(2);
                testSystem.setMultiPhaseCheck(true);
                testSystem.init(0);
                testSystem.init(3);
                double a = testSystem.getTotalNumberOfMoles();

                Stream wellStream = new Stream("Well stream", testSystem);
                // wellStream.getThermoSystem().setTotalFlowRate(5.0, "MSm^3/day");

                SeparationTrainModule separationModule = new SeparationTrainModule();
                separationModule.addInputStream("feed stream", wellStream);
                separationModule.setSpecification("Second stage pressure", 15.0);
                separationModule.setSpecification("heated oil temperature", 273.15 + 55.0);
                separationModule.setSpecification("Third stage pressure", 1.0);
                separationModule.setSpecification("Gas exit temperature", 273.15 + 25.0);
                separationModule.setSpecification("First stage compressor after cooler temperature",
                                273.15 + 25.0);
                separationModule.setSpecification("Export oil temperature", 273.15 + 25.0);

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();

                operations.add(wellStream);
                operations.add(separationModule);
                // separationModule.getUnit("")
                // ((Recycle) operations.getUnit("Resycle")).setTolerance(1e-9);

                operations.run();

                // ArrayList names2 = operations.getAllUnitNames();
                // processSimulation.processEquipment.ProcessEquipmentInterface tempStr =
                // (ProcessEquipmentBaseClass) operations.getUnit("2nd stage recompressor");
                // tempStr.displayResult();
                // wellStream.displayResult();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("Inlet separator")).getMechanicalDesign().calcDesign();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("Inlet separator")).getMechanicalDesign().displayResults();

                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("1st stage separator")).getMechanicalDesign().calcDesign();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("1st stage separator")).getMechanicalDesign()
                                                .displayResults();

                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("2nd stage Separator")).getMechanicalDesign().calcDesign();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("2nd stage Separator")).getMechanicalDesign()
                                                .displayResults();

                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("3rd stage Separator")).getMechanicalDesign().calcDesign();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("3rd stage Separator")).getMechanicalDesign()
                                                .displayResults();

                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("2nd stage recompressor")).getMechanicalDesign()
                                                .calcDesign();
                ((ProcessEquipmentInterface) separationModule.getOperations()
                                .getUnit("2nd stage recompressor")).getMechanicalDesign()
                                                .displayResults();

                operations.getSystemMechanicalDesign().runDesignCalculation();
                operations.getSystemMechanicalDesign().getTotalPlotSpace();
                System.out.println("Modules "
                                + operations.getSystemMechanicalDesign().getTotalVolume());

                System.out.println("Modules "
                                + operations.getSystemMechanicalDesign().getTotalNumberOfModules());
                System.out.println("Weight "
                                + operations.getSystemMechanicalDesign().getTotalWeight());
                System.out.println("Plot space "
                                + operations.getSystemMechanicalDesign().getTotalPlotSpace());
                System.out.println("CAPEX "
                                + operations.getCostEstimator().getWeightBasedCAPEXEstimate());
                System.out.println("CAPEX " + operations.getCostEstimator().getCAPEXestimate());

                /*
                 * separationModule.getOutputStream("Inlet separator").displayResult();
                 * separationModule.getOutputStream("oil exit stream").displayResult();
                 * System.out.println("third stage compressor power " + ((Compressor)
                 * separationModule.getOperations().getUnit("3rd stage recompressor")).getPower( ) +
                 * " W"); System.out.println("secondstage compressor  power " + ((Compressor)
                 * separationModule.getOperations().getUnit("2nd stage recompressor")).getPower( ) +
                 * " W"); System.out.println("third stage cooler duty " + ((Cooler)
                 * separationModule.getOperations().getUnit("3rd stage cooler")).getEnergyInput( ) +
                 * " W"); System.out.println("HP gas cooler duty " + ((Cooler)
                 * separationModule.getOperations().getUnit("HP gas cooler")).getEnergyInput() +
                 * " W"); System.out.println("Export oil flow " +
                 * separationModule.getOutputStream("oil exit stream").getThermoSystem().
                 * getTotalNumberOfMoles() *
                 * separationModule.getOutputStream("oil exit stream").getThermoSystem().
                 * getMolarMass() /
                 * separationModule.getOutputStream("oil exit stream").getThermoSystem().
                 * getPhase(0).getPhysicalProperties().getDensity() * 3600.0 + " m^3/hr");
                 * System.out.println("Export gas flow " +
                 * separationModule.getOutputStream("gas exit stream").getThermoSystem().
                 * getTotalNumberOfMoles() *
                 * separationModule.getOutputStream("gas exit stream").getThermoSystem().
                 * getMolarMass() /
                 * separationModule.getOutputStream("gas exit stream").getThermoSystem().
                 * getPhase(0).getPhysicalProperties().getDensity() * 3600.0 + " m^3/hr");
                 * System.out.println("Export gas flow " +
                 * separationModule.getOutputStream("gas exit stream").getThermoSystem().
                 * getTotalNumberOfMoles() * 8.314 * (273.15 + 15.0) / 101325.0 * 3600.0 * 24 /
                 * 1.0e6 + " MSm^3/day"); System.out.println("oil/water heater duty " + ((Heater)
                 * separationModule.getOperations().getUnit("oil/water heater")).getEnergyInput( ) +
                 * " W"); System.out.println("Export oil cooler duty " + ((Cooler)
                 * separationModule.getOperations().getUnit("export oil cooler")).getEnergyInput ()
                 * + " W");
                 */
        }
}
