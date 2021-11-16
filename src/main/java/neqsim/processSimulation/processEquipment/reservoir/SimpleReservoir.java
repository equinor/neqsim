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
 * SimpleReservoir.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.reservoir;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author Even Solbraa
 * @version
 */
public class SimpleReservoir extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem;

    double oilVolume = 0.0, gasVolume = 0.0, waterVolume = 0.0;
    ArrayList<Well> gasProducer = new ArrayList<Well>();
    ArrayList<Well> oilProducer = new ArrayList<Well>();
    ArrayList<Well> gasInjector = new ArrayList<Well>();
    ArrayList<Well> waterInjector = new ArrayList<Well>();

    double gasProductionTotal = 0.0;
    double oilProductionTotal = 0.0;
    double OOIP = 0.0;
    double OGIP = 0.0;

    // StreamInterface gasOutStream;
    // StreamInterface oilOutStream;
    double reservoirVolume = 0.0;
    double lowPressureLimit = 50.0;
    double time = 0.0;

    /**
     * Creates new SimpleReservoir
     */
    public SimpleReservoir() {
        super();
    }

    public SystemInterface getReservoirFluid() {
        return thermoSystem;
    }

    /*
     * public StreamInterface getGasOutStream() { return gasOutStream; }
     * 
     * 
     * 
     * public StreamInterface getOilOutStream() { return oilOutStream; }
     */

    public SimpleReservoir(String name) {
        super(name);
    }

    @Override
    public SystemInterface getFluid() {
        return thermoSystem;
    }

    public StreamInterface addGasProducer(String name) {
        Well newWell = new Well(name);
        gasProducer.add(newWell);
        StreamInterface gasOutStream = new Stream();
        gasOutStream.setFluid(thermoSystem.phaseToSystem("gas"));
        gasOutStream.getFluid().setTotalFlowRate(1.0e-1, "kg/sec");
        newWell.setStream(gasOutStream);
        return newWell.getStream();
    }

    public StreamInterface addOilProducer(String name) {
        Well newWell = new Well(name);
        oilProducer.add(newWell);
        StreamInterface oilOutStream = new Stream();
        oilOutStream.setFluid(thermoSystem.phaseToSystem("oil"));
        oilOutStream.getFluid().setTotalFlowRate(1.0e-1, "kg/sec");
        newWell.setStream(oilOutStream);
        return newWell.getStream();
    }

    public StreamInterface addWaterInjector(String name) {
        Well newWell = new Well(name);
        waterInjector.add(newWell);
        StreamInterface waterInStream = new Stream();
        waterInStream.setFluid(thermoSystem.phaseToSystem("aqueous"));
        // waterInStream.init(0);
        waterInStream.getFluid().setTotalFlowRate(1.0e-1, "kg/sec");
        newWell.setStream(waterInStream);
        return newWell.getStream();
    }

    public StreamInterface addGasInjector(String name) {
        Well newWell = new Well(name);
        gasInjector.add(newWell);
        StreamInterface gasInStream = new Stream();
        gasInStream.setFluid(thermoSystem.phaseToSystem("gas"));
        gasInStream.getFluid().setTotalFlowRate(1.0e-1, "kg/sec");
        newWell.setStream(gasInStream);
        return newWell.getStream();
    }

    public double getGasInPlace(String unit) {
        SystemInterface locStream = (SystemInterface) (thermoSystem).clone();
        locStream.setTemperature(288.15);
        locStream.setPressure(1.01325);
        ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
        ops.TPflash();
        locStream.initProperties();
        double volume = Double.NaN;
        if (locStream.hasPhaseType("gas")) {
            volume = locStream.getPhase("gas").getVolume("m3");
        }
        if (unit.equals("GSm3")) {
            return volume / 1.0e9;
        }
        return volume;
    }

    public double getOilInPlace(String unit) {
        SystemInterface locStream = (SystemInterface) (thermoSystem).clone();
        locStream.setTemperature(288.15);
        locStream.setPressure(1.01325);
        ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
        ops.TPflash();
        locStream.initProperties();
        double volume = Double.NaN;
        if (locStream.hasPhaseType("oil")) {
            volume = locStream.getPhase("oil").getVolume("m3");
        }
        if (unit.equals("MSm3")) {
            return volume / 1.0e6;
        }
        return volume;
    }

    public Well getGasProducer(int i) {
        return gasProducer.get(i);
    }

    public Well getOilProducer(int i) {
        return oilProducer.get(i);
    }

    public Well getOilProducer(String name) {
        for (int i = 0; i < oilProducer.size(); i++) {
            if (oilProducer.get(i).getName().equals(name)) {
                return oilProducer.get(i);
            }
        }
        return null;
    }

    public void setReservoirFluid(SystemInterface thermoSystem, double gasVolume, double oilVolume,
            double waterVolume) {
        this.thermoSystem = thermoSystem;
        this.oilVolume = oilVolume;
        this.gasVolume = gasVolume;
        this.waterVolume = waterVolume;
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TPflash();

        // thermoSystem.display();
        SystemInterface thermoSystem2 = (SystemInterface) thermoSystem.clone();

        thermoSystem.removeMoles();// (1.0e-10);
        // thermoSystem.init(1);
        // thermoSystem.display();
        // System.out.println("number of phases " + thermoSystem.getNumberOfPhases());
        double totalliquidVolume = 0.0;
        for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
            double relFact = gasVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);
            if (j >= 1) {
                if (j == 1) {
                    relFact = oilVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);
                    totalliquidVolume += oilVolume / thermoSystem2.getPhase(j).getMolarVolume();
                } else if (j == 2) {
                    relFact = waterVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);
                    totalliquidVolume += waterVolume / thermoSystem2.getPhase(j).getMolarVolume();
                }
            }
            for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
                thermoSystem.addComponent(
                        thermoSystem.getPhase(j).getComponent(i).getComponentNumber(),
                        relFact * thermoSystem2.getPhase(j).getComponent(i)
                                .getNumberOfMolesInPhase(),
                        j);
            }
        }
        /*
         * if (thermoSystem.hasPhaseType("gas")) { thermoSystem.setBeta(gasVolume /
         * thermoSystem2.getPhase(0).getMolarVolume() / (gasVolume /
         * thermoSystem2.getPhase(0).getMolarVolume() + oilVolume /
         * thermoSystem2.getPhase(1).getMolarVolume())); }
         */
        ThermodynamicOperations ops2 = new ThermodynamicOperations(thermoSystem);
        ops2.TPflash();
        thermoSystem.initProperties();
        // thermoSystem.display();
        // gasOutStream = new Stream();
        // gasOutStream.setFluid(thermoSystem.phaseToSystem("gas"));
        reservoirVolume = gasVolume + oilVolume + waterVolume;

        OOIP = getOilInPlace("Sm3");
        OGIP = getGasInPlace("Sm3");
        // oilOutStream = new Stream();
        // oilOutStream.setFluid(thermoSystem.phaseToSystem("oil"));

        lowPressureLimit = 50.0;
    }

    @Override
    public void run() {
        System.out.println("gas volume " + thermoSystem.getPhase("gas").getVolume("m3"));
        System.out.println("oil volume " + thermoSystem.getPhase("oil").getVolume("m3"));
        System.out.println("water volume " + thermoSystem.getPhase("aqueous").getVolume("m3"));

        for (int i = 0; i < gasProducer.size(); i++) {
            gasProducer.get(i).getStream().run();
        }
        for (int i = 0; i < oilProducer.size(); i++) {
            oilProducer.get(i).getStream().run();
        }
        for (int i = 0; i < waterInjector.size(); i++) {
            waterInjector.get(i).getStream().run();
        }
        for (int i = 0; i < gasInjector.size(); i++) {
            gasInjector.get(i).getStream().run();
        }

        // gasOutStream.setFluid(thermoSystem.phaseToSystem("gas"));
        // gasOutStream.run();
    }

    public double GORprodution() {
        double GOR = 0.0;
        double flow = 0.0;
        for (int i = 0; i < gasProducer.size(); i++) {
            flow += gasProducer.get(i).getStream().getFluid().getNumberOfMoles();
            GOR += gasProducer.get(i).getGOR()
                    * gasProducer.get(i).getStream().getFluid().getNumberOfMoles();
        }
        for (int i = 0; i < oilProducer.size(); i++) {
            flow += oilProducer.get(i).getStream().getFluid().getNumberOfMoles();
            GOR += oilProducer.get(i).getGOR()
                    * oilProducer.get(i).getStream().getFluid().getNumberOfMoles();
        }
        return GOR / flow;
    }

    public double getGasProdution(String unit) {
        double volume = 0.0;
        for (int i = 0; i < gasProducer.size(); i++) {
            volume += gasProducer.get(i).getStdGasProduction();
        }
        for (int i = 0; i < oilProducer.size(); i++) {
            volume += oilProducer.get(i).getStdGasProduction();
        }
        if (unit.equals("Sm3/sec")) {
        } else if (unit.equals("Sm3/day")) {
            volume = volume * 60.0 * 60 * 24;
        }
        return volume;
    }

    public double getOilProdution(String unit) {
        double volume = 0.0;
        for (int i = 0; i < gasProducer.size(); i++) {
            volume += gasProducer.get(i).getStdOilProduction();
        }
        for (int i = 0; i < oilProducer.size(); i++) {
            volume += oilProducer.get(i).getStdOilProduction();
        }
        if (unit.equals("Sm3/sec")) {
        } else if (unit.equals("Sm3/day")) {
            volume = volume * 60.0 * 60 * 24;
        }
        return volume;
    }

    @Override
    public void runTransient(double dt) {
        time = getTime() + dt;
        if (thermoSystem.getPressure("bara") < lowPressureLimit) {
            System.out.println("low pressure reservoir limit reached");
            return;
        }
        gasProductionTotal += getGasProdution("Sm3/sec") * dt;
        oilProductionTotal += getOilProdution("Sm3/sec") * dt;
        thermoSystem.init(0);
        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
            // thermoSystem.addComponent(i, -10000000000.001);
            for (int k = 0; k < gasProducer.size(); k++) {
                thermoSystem.addComponent(i, -gasProducer.get(k).getStream().getFluid()
                        .getComponent(i).getNumberOfmoles() * dt);
            }

            for (int k = 0; k < oilProducer.size(); k++) {
                thermoSystem.addComponent(i, -oilProducer.get(k).getStream().getFluid()
                        .getComponent(i).getNumberOfmoles() * dt);
            }

            for (int k = 0; k < waterInjector.size(); k++) {
                thermoSystem.addComponent(i, waterInjector.get(k).getStream().getFluid()
                        .getComponent(i).getNumberOfmoles() * dt);
            }
            for (int k = 0; k < gasInjector.size(); k++) {
                thermoSystem.addComponent(i,
                        gasInjector.get(k).getStream().getFluid().getComponent(i).getNumberOfmoles()
                                * dt);
            }
        }

        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TVflash(reservoirVolume, "m3");
        thermoSystem.initProperties();

        /*
         * if (thermoSystem.hasPhaseType("gas")) System.out.println("gas volume " +
         * thermoSystem.getPhase("gas").getVolume("m3")); if (thermoSystem.hasPhaseType("oil"))
         * System.out.println("oil volume " + thermoSystem.getPhase("oil").getVolume("m3")); if
         * (thermoSystem.hasPhaseType("aqueous")) System.out.println("water volume " +
         * thermoSystem.getPhase("aqueous").getVolume("m3"));
         */
        // System.out.println("pressure " + thermoSystem.getPressure("bara"));

        if (thermoSystem.hasPhaseType("gas")) {
            for (int k = 0; k < gasProducer.size(); k++) {
                gasProducer.get(k).getStream().getFluid()
                        .setMolarComposition(thermoSystem.getPhase("gas").getMolarComposition());
                gasProducer.get(k).getStream().run();
            }
        }
        if (thermoSystem.hasPhaseType("oil")) {
            for (int k = 0; k < oilProducer.size(); k++) {
                oilProducer.get(k).getStream().getFluid()
                        .setMolarComposition(thermoSystem.getPhase("oil").getMolarComposition());
                oilProducer.get(k).getStream().run();
            }
        }
        /*
         * if(thermoSystem.hasPhaseType("aqueous")) { for (int k = 0; k < waterInjector.size(); k++)
         * { waterInjector.get(k).getStream().getFluid()
         * .setMolarComposition(thermoSystem.getPhase("aqueous").getMolarComposition());
         * waterInjector.get(k).getStream().run(); }
         */
        for (int k = 0; k < waterInjector.size(); k++) {
            waterInjector.get(k).getStream().run();
        }
        for (int k = 0; k < gasInjector.size(); k++) {
            gasInjector.get(k).getStream().run();
        }

        double totalVolume = 0;
        for (int i = 0; i < getReservoirFluid().getNumberOfPhases(); i++) {
            totalVolume += getReservoirFluid().getPhase(i).getVolume("m3");
        }
        // System.out.println("tot volume " + totalVolume/1.0e6 + " gas volume "+
        // getReservoirFluid().getPhase("gas").getVolume("m3")/1.0e6 + " oil volume "+
        // getReservoirFluid().getPhase("oil").getVolume("m3")/1.0e6 + " water volume "+
        // getReservoirFluid().getPhase("aqueous").getVolume("m3")/1.0e6);
        // oilOutStream.getFluid().setMolarComposition(thermoSystem.getPhase("oil").getMolarComposition());
        // oilOutStream.run();

        // thermoSystem.display();
        for (int k = 0; k < gasProducer.size(); k++) {
            gasProducer.get(k).getStream().getFluid().setPressure(thermoSystem.getPressure());
        }

        for (int k = 0; k < oilProducer.size(); k++) {
            oilProducer.get(k).getStream().getFluid().setPressure(thermoSystem.getPressure());
        }

        for (int k = 0; k < waterInjector.size(); k++) {
            waterInjector.get(k).getStream().setPressure(thermoSystem.getPressure());
        }
        for (int k = 0; k < gasInjector.size(); k++) {
            gasInjector.get(k).getStream().setPressure(thermoSystem.getPressure());
        }
    }

    @Override
    public void displayResult() {
        thermoSystem.display();
    }

    public static void main(String[] args) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 100.0), 200.00);
        testSystem.addComponent("nitrogen", 0.100);
        testSystem.addComponent("methane", 30.00);
        testSystem.addComponent("ethane", 1.0);
        testSystem.addComponent("propane", 1.0);
        testSystem.addComponent("i-butane", 1.0);
        testSystem.addComponent("n-butane", 1.0);
        testSystem.addComponent("n-hexane", 0.1);
        testSystem.addComponent("n-heptane", 0.1);
        testSystem.addComponent("n-nonane", 1.0);
        testSystem.addComponent("nC10", 1.0);
        testSystem.addComponent("nC12", 3.0);
        testSystem.addComponent("nC15", 3.0);
        testSystem.addComponent("nC20", 3.0);
        testSystem.addComponent("water", 11.0);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);

        SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
        reservoirOps.setReservoirFluid(testSystem, 5.0 * 1e7, 552.0 * 1e6, 10.0e6);

        StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
        StreamInterface injectorGasStream = reservoirOps.addGasInjector("gasproducer_1");
        StreamInterface producedGasStream = reservoirOps.addGasProducer("SLP_A32562G");
        StreamInterface waterInjectorStream = reservoirOps.addWaterInjector("SLP_WI32562O");

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(reservoirOps);
        operations.save("c:/temp/resmode1.neqsim");

        System.out.println("gas in place (GIP) " + reservoirOps.getGasInPlace("GSm3") + " GSm3");
        System.out.println("oil in place (OIP) " + reservoirOps.getOilInPlace("MSm3") + " MSm3");

        // StreamInterface producedGasStream =
        // reservoirOps.addGasProducer("SLP_A32562G");
        producedGasStream.setFlowRate(0.01, "MSm3/day");

        // StreamInterface injectorGasStream =
        // reservoirOps.addGasInjector("SLP_A32562GI");
        injectorGasStream.setFlowRate(0.01, "MSm3/day");
        // injectorGasStream.getFluid().setMolarComposition(.new double[0.1, 0.1]);

        // StreamInterface producedGasStream2 =
        // reservoirOps.addGasProducer("SLP_A32562G2");
        // producedGasStream2.setFlowRate(0.011, "MSm3/hr");

        producedOilStream.setFlowRate(50000000.0, "kg/day");

        waterInjectorStream.setFlowRate(12100000.0, "kg/day");

        // StreamInterface producedGasStream = reservoirOps.getGasOutStream();
        // producedGasStream.setFlowRate(1.0e-3, "MSm3/day");

        // StreamInterface producedOilStream = reservoirOps.getOilOutStream();
        // producedOilStream.setFlowRate(1500000.0, "kg/hr");

        reservoirOps.run();

        reservoirOps.runTransient(60 * 60 * 24 * 3);
        for (int i = 0; i < 1; i++) {
            reservoirOps.runTransient(60 * 60 * 24 * 15);
            System.out.println("water volume"
                    + reservoirOps.getReservoirFluid().getPhase("aqueous").getVolume("m3") / 1.0e6);
            System.out.println(
                    "oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
            System.out.println(
                    "total produced  " + reservoirOps.getProductionTotal("MSm3 oe") + " MSm3 oe");
            if (reservoirOps.getFluid().getPressure() < 50.0)
                break;
        }
        System.out.println("GOR gas  " + reservoirOps.getGasProducer(0).getGOR());
        // System.out.println("GOR oil " + reservoirOps.getOilProducer(0).getGOR());
        System.out.println("GOR production " + reservoirOps.GORprodution());

        // System.out.println("GOR oil " + reservoirOps.getOilProducer(0).getGOR());

        System.out
                .println("gas production  " + reservoirOps.getGasProdution("Sm3/day") + " Sm3/day");
        System.out
                .println("oil production  " + reservoirOps.getOilProdution("Sm3/day") + " Sm3/day");

        System.out.println(
                "oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");

        // reservoirOps.runTransient(60 * 60 * 24 * 365);
        // reservoirOps.runTransient(60 * 60 * 24 * 365);
        // reservoirOps.runTransient(60 * 60 * 24 * 365);
        System.out.println(
                "gas production  total" + reservoirOps.getGasProductionTotal("GSm3") + " GSm3");

        System.out.println(
                "oil production  total" + reservoirOps.getOilProductionTotal("MSm3") + " MSm3");
        System.out.println("gas in place (GIP) " + reservoirOps.getGasInPlace("GSm3") + " GSm3");
        System.out.println("oil in place (OIP) " + reservoirOps.getOilInPlace("MSm3") + " MSm3");
        System.out
                .println("original oil in place (OOIP) " + reservoirOps.getOOIP("MSm3") + " MSm3");
        System.out
                .println("original gas in place (OGIP) " + reservoirOps.getOGIP("GSm3") + " GSm3");

        // reservoirOps.runTransient(60 * 60 * 24 * 365);
        // producedGasStream.setFlowRate(4.0, "MSm3/day");
        // producedOilStream.setFlowRate(1.0, "kg/hr");

        for (int i = 0; i < 300; i++) {
            // reservoirOps.runTransient(60 * 60 * 24 * 365);
        }
        System.out.println("oil flow " + producedOilStream.getFlowRate("kg/hr"));

        AdiabaticTwoPhasePipe testPipe = new AdiabaticTwoPhasePipe(producedOilStream);
        testPipe.setLength(3300.0);
        testPipe.setInletElevation(0.0);
        testPipe.setOutletElevation(2200.0);
        testPipe.setDiameter(2.69);
        testPipe.setPressureOutLimit(80.0);
        testPipe.setFlowLimit(producedOilStream.getFlowRate("kg/hr") * 0.4, "kg/hr");
        testPipe.setOutTemperature(273.15 + 50.0);
        testPipe.run();
        System.out.println(" flow limit " + producedOilStream.getFlowRate("kg/hr") * 0.4);
        System.out.println("oil flow " + producedOilStream.getFlowRate("kg/hr"));
        // testPipe.getOutStream().displayResult();

        for (int i = 0; i < 60; i++) {
            reservoirOps.runTransient(60 * 60 * 24 * 25);
            testPipe.run();
            System.out.println("oil flow " + producedOilStream.getFlowRate("kg/hr") + " pressure "
                    + producedOilStream.getPressure("bara") + " pipe out pres "
                    + testPipe.getOutStream().getFluid().getPressure());
        }
    }

    public double getGasProductionTotal(String unit) {
        if (unit.equals("MSm3"))
            return gasProductionTotal / 1e6;
        if (unit.equals("GSm3"))
            return gasProductionTotal / 1e9;
        return gasProductionTotal;
    }

    public double getOilProductionTotal(String unit) {
        if (unit.equals("MSm3"))
            return oilProductionTotal / 1e6;
        return oilProductionTotal;
    }

    public double getProductionTotal(String unit) {
        double prod = getOilProductionTotal("Sm3") + getGasProductionTotal("Sm3") / 1.0e3;
        if (unit.equals("MSm3 oe"))
            return prod / 1e6;
        return prod;
    }

    public double getOOIP(String unit) {
        if (unit.equals("MSm3"))
            return OOIP / 1.0e6;
        return OOIP;
    }

    public double getOGIP(String unit) {
        if (unit.equals("GSm3"))
            return OGIP / 1.0e9;
        return OGIP;
    }

    public double getTime() {
        return time;
    }
}
