/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;

import neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.sectionType.ManwaySection;
import neqsim.processSimulation.processEquipment.separator.sectionType.MeshSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.NozzleSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.ValveSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author  Even Solbraa
 * @version
 */
public class Separator extends ProcessEquipmentBaseClass implements SeparatorInterface {

    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned, thermoSystem2;
    private String orientation = "horizontal";
    StreamInterface gasOutStream;
    StreamInterface liquidOutStream;
    private double pressureDrop = 0.0;
    private double internalDiameter = 1.0;
    public int numberOfInputStreams = 0;
    Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
    private double efficiency = 1.0;
    private double liquidCarryoverFraction = 0.0;
    private double gasCarryunderFraction = 0.0;
    private double separatorLength = 5.0;
    double liquidVolume = 1.0, gasVolume = 18.0;
    private double liquidLevel = liquidVolume / (liquidVolume + gasVolume);
    private double designLiquidLevelFraction = 0.8;
    ArrayList<SeparatorSection> separatorSection = new ArrayList<SeparatorSection>();

    /**
     * Creates new Separator
     */
    public Separator() {
        super();
        mechanicalDesign = new SeparatorMechanicalDesign(this);
    }

    public Separator(StreamInterface inletStream) {
        this();
        addStream(inletStream);
    }

    public Separator(String name, StreamInterface inletStream) {
        this(inletStream);
        this.name = name;
    }

    public void setInletStream(StreamInterface inletStream) {
        inletStreamMixer.addStream(inletStream);
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
        liquidOutStream = new Stream(liquidSystem);

    }

    public void addStream(StreamInterface newStream) {
        if (numberOfInputStreams == 0) {
            setInletStream(newStream);
        } else {
            inletStreamMixer.addStream(newStream);
        }
        numberOfInputStreams++;
    }

    public StreamInterface getLiquidOutStream() {
        return liquidOutStream;
    }

    public StreamInterface getGasOutStream() {
        return gasOutStream;
    }

    public StreamInterface getGas() {
        return getGasOutStream();
    }

    public StreamInterface getLiquid() {
        return getLiquidOutStream();
    }

    @Override
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }

    @Override
    public void run() {
        inletStreamMixer.run();
        thermoSystem2 = (SystemInterface) inletStreamMixer.getOutStream().getThermoSystem().clone();
        thermoSystem2.setPressure(thermoSystem2.getPressure() - pressureDrop);

        if (thermoSystem2.hasPhaseType("gas")) {
            gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
            gasOutStream.getFluid().init(2);
        } else {
            gasOutStream.setThermoSystem(thermoSystem2.getEmptySystemClone());
        }
        if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
            liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "liquid");
            liquidOutStream.getFluid().init(2);
        } else {
            liquidOutStream.setThermoSystem(thermoSystem2.getEmptySystemClone());
        }
        gasOutStream.run();
        liquidOutStream.run();
        // liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "aqueous");
        try {
            thermoSystem = (SystemInterface) thermoSystem2.clone();
            thermoSystem.setTotalNumberOfMoles(1.0e-10);
            thermoSystem.init(1);
            // System.out.println("number of phases " + thermoSystem.getNumberOfPhases());
            double totalliquidVolume = 0.0;
            for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
                double relFact = gasVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);
                if (j >= 1) {
                    relFact = liquidVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);

                    totalliquidVolume += liquidVolume / thermoSystem2.getPhase(j).getMolarVolume();
                }
                for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
                    thermoSystem.addComponent(thermoSystem.getPhase(j).getComponent(i).getComponentNumber(),
                            relFact * thermoSystem2.getPhase(j).getComponent(i).getNumberOfMolesInPhase(), j);
                }
            }

            if (thermoSystem.hasPhaseType("gas")) {
                thermoSystem.setBeta(gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
                        / (gasVolume / thermoSystem2.getPhase(0).getMolarVolume() + totalliquidVolume));
            }
            thermoSystem.initBeta();
            thermoSystem.init(3);
            // System.out.println("moles in separator " + thermoSystem.getNumberOfMoles());
            // double volume1 = thermoSystem.getVolume();
            // System.out.println("volume1 bef " + volume1);
            // System.out.println("beta " + thermoSystem.getBeta());

            liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
            liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
                    * getSeparatorLength();
            gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
                    * getSeparatorLength();
            // System.out.println("moles out" +
            // liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
        } catch (Exception e) {
            e.printStackTrace();
        }
        thermoSystem = thermoSystem2;
    }

    @Override
    public void displayResult() {
        thermoSystem.display();
    }

    @Override
    public String[][] getResultTable() {
        return thermoSystem.getResultTable();
    }

    @Override
    public void runTransient(double dt) {
        inletStreamMixer.run();

        System.out.println("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
        double inMoles = inletStreamMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
        double gasoutMoles = gasOutStream.getThermoSystem().getNumberOfMoles();
        double liqoutMoles = liquidOutStream.getThermoSystem().getNumberOfMoles();
        thermoSystem.init(3);
        gasOutStream.getThermoSystem().init(3);
        liquidOutStream.getThermoSystem().init(3);
        double volume1 = thermoSystem.getVolume();
        System.out.println("volume1 " + volume1);
        double deltaEnergy = inletStreamMixer.getOutStream().getThermoSystem().getEnthalpy()
                - gasOutStream.getThermoSystem().getEnthalpy() - liquidOutStream.getThermoSystem().getEnthalpy();
        System.out.println("enthalph delta " + deltaEnergy);
        double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy;
        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
            double dn = inletStreamMixer.getOutStream().getThermoSystem().getPhase(0).getComponent(i)
                    .getNumberOfMolesInPhase()
                    + inletStreamMixer.getOutStream().getThermoSystem().getPhase(1).getComponent(i)
                            .getNumberOfMolesInPhase()
                    - gasOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase()
                    - liquidOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase();
            System.out.println("dn " + dn);
            thermoSystem.addComponent(
                    inletStreamMixer.getOutStream().getThermoSystem().getPhase(0).getComponent(i).getComponentNumber(),
                    dn * dt);
        }
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.VUflash(volume1, newEnergy);

        setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

        liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
        System.out.println("liquid level " + liquidLevel);
        liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
                * getSeparatorLength();
        gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
                * getSeparatorLength();

    }

    public void setTempPres(double temp, double pres) {
        gasOutStream.getThermoSystem().setTemperature(temp);
        liquidOutStream.getThermoSystem().setTemperature(temp);

        inletStreamMixer.setPressure(pres);
        gasOutStream.getThermoSystem().setPressure(pres);
        liquidOutStream.getThermoSystem().setPressure(pres);

        inletStreamMixer.run();
        gasOutStream.run();
        liquidOutStream.run();
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }

    public double getLiquidCarryoverFraction() {
        return liquidCarryoverFraction;
    }

    public void setLiquidCarryoverFraction(double liquidCarryoverFraction) {
        this.liquidCarryoverFraction = liquidCarryoverFraction;
    }

    public double getGasCarryunderFraction() {
        return gasCarryunderFraction;
    }

    public void setGasCarryunderFraction(double gasCarryunderFraction) {
        this.gasCarryunderFraction = gasCarryunderFraction;
    }

    public double getLiquidLevel() {
        return liquidLevel;
    }

    /**
     * @return the pressureDrop
     */
    public double getPressureDrop() {
        return pressureDrop;
    }

    /**
     * @param pressureDrop the pressureDrop to set
     */
    public void setPressureDrop(double pressureDrop) {
        this.pressureDrop = pressureDrop;
    }

    /**
     * @return the diameter
     */
    public double getInternalDiameter() {
        return internalDiameter;
    }

    /**
     * @param diameter the diameter to set
     */
    @Override
    public void setInternalDiameter(double diameter) {
        this.internalDiameter = diameter;
    }

    public double getGasSuperficialVelocity() {
        return thermoSystem.getPhase(0).getTotalVolume() / 1e5 / (neqsim.thermo.ThermodynamicConstantsInterface.pi
                * getInternalDiameter() * getInternalDiameter() / 4.0);
    }

    public double getGasLoadFactor() {
        thermoSystem.initPhysicalProperties();
        double term1 = (thermoSystem.getPhase(1).getPhysicalProperties().getDensity()
                - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
                / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
        return getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
    }

    public double getGasLoadFactor(int phaseNumber) {
        double gasAreaFraction = 1.0;
        if (orientation.equals("horizontal")) {
            gasAreaFraction = 1.0 - (liquidVolume / (liquidVolume + gasVolume));
        }
        thermoSystem.initPhysicalProperties();
        double term1 = 1.0 / gasAreaFraction
                * (thermoSystem.getPhase(2).getPhysicalProperties().getDensity()
                        - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
                / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
        return getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);

    }

    public double getDeRatedGasLoadFactor() {
        thermoSystem.initPhysicalProperties();
        double derating = 1.0;
        double surfaceTension = thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1);
        if (surfaceTension < 10.0e-3) {
            derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
        }
        System.out.println("derating " + derating);
        double term1 = (thermoSystem.getPhase(1).getPhysicalProperties().getDensity()
                - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
                / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
        return derating * getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
    }

    public double getDeRatedGasLoadFactor(int phase) {
        thermoSystem.initPhysicalProperties();
        double derating = 1.0;
        double surfaceTension = thermoSystem.getInterphaseProperties().getSurfaceTension(phase - 1, phase);
        if (surfaceTension < 10.0e-3) {
            derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
        }
        System.out.println("derating " + derating);
        double term1 = (thermoSystem.getPhase(phase).getPhysicalProperties().getDensity()
                - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
                / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
        return derating * getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
    }

    /**
     * @return the orientation
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * @param orientation the orientation to set
     */
    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    /**
     * @return the separatorLength
     */
    public double getSeparatorLength() {
        return separatorLength;
    }

    /**
     * @param separatorLength the separatorLength to set
     */
    public void setSeparatorLength(double separatorLength) {
        this.separatorLength = separatorLength;
    }

    public SeparatorSection getSeparatorSection(int i) {
        return separatorSection.get(i);
    }

    public SeparatorSection getSeparatorSection(String name) {
        for (SeparatorSection sec : separatorSection) {
            if (sec.getName().equals(name)) {
                return sec;
            }
        }
        System.out.println("no section with name: " + name + " found.....");
        return null;
    }

    public ArrayList<SeparatorSection> getSeparatorSections() {
        return separatorSection;
    }

    public void addSeparatorSection(String name, String type) {
        if (type.equals("vane")) {
            separatorSection.add(new SeparatorSection(name, type, this));
        } else if (type.equals("meshpad")) {
            separatorSection.add(new MeshSection(name, type, this));
        } else if (type.equals("manway")) {
            separatorSection.add(new ManwaySection(name, type, this));
        } else if (type.equals("valve")) {
            separatorSection.add(new ValveSection(name, type, this));
        } else if (type.equals("nozzle")) {
            separatorSection.add(new NozzleSection(name, type, this));
        } else {
            separatorSection.add(new SeparatorSection(name, type, this));
        }
    }

    /**
     * @return the designGasLevelFraction
     */
    public double getDesignLiquidLevelFraction() {
        return designLiquidLevelFraction;
    }

    /**
     * @param designGasLevelFraction the designGasLevelFraction to set
     */
    public void setDesignLiquidLevelFraction(double designLiquidLevelFraction) {
        this.designLiquidLevelFraction = designLiquidLevelFraction;
    }

    @Override
    public double getPressure() {
        return getThermoSystem().getPressure();
    }

    @Override
    public double getEntropyProduction(String unit) {
        //
        double entrop = 0.0;
        for (int i = 0; i < numberOfInputStreams; i++) {
            inletStreamMixer.getStream(i).getFluid().init(3);
            entrop += inletStreamMixer.getStream(i).getFluid().getEntropy(unit);
        }
        if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
            try {
                getLiquidOutStream().getThermoSystem().init(3);
            } catch (Exception e) {

            }
        }
        if (thermoSystem.hasPhaseType("gas")) {
            getGasOutStream().getThermoSystem().init(3);
        }

        return getLiquidOutStream().getThermoSystem().getEntropy(unit)
                + getGasOutStream().getThermoSystem().getEntropy(unit) - entrop;
    }

    @Override
    public double getMassBalance(String unit) {
        //
        double flow = 0.0;
        for (int i = 0; i < numberOfInputStreams; i++) {
            inletStreamMixer.getStream(i).getFluid().init(3);
            flow += inletStreamMixer.getStream(i).getFluid().getFlowRate(unit);
        }
        getLiquidOutStream().getThermoSystem().init(3);
        getGasOutStream().getThermoSystem().init(3);
        return getLiquidOutStream().getThermoSystem().getFlowRate(unit)
                + getGasOutStream().getThermoSystem().getFlowRate(unit) - flow;
    }

    @Override
    public double getExergyChange(String unit, double sourrondingTemperature) {

        //
        double exergy = 0.0;
        for (int i = 0; i < numberOfInputStreams; i++) {
            inletStreamMixer.getStream(i).getFluid().init(3);
            exergy += inletStreamMixer.getStream(i).getFluid().getExergy(sourrondingTemperature, unit);
        }
        getLiquidOutStream().getThermoSystem().init(3);
        getGasOutStream().getThermoSystem().init(3);
        return getLiquidOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit)
                + getGasOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit) - exergy;
    }
    
    /*
    private class SeparatorReport extends Object{
    	public Double gasLoadFactor;
    	SeparatorReport(){
    		gasLoadFactor = getGasLoadFactor();
        }
    }
    
    public SeparatorReport getReport(){
        return this.new SeparatorReport();
    }*/
}
