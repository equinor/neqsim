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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.mechanicalDesign.separator;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.processSimulation.costEstimation.separator.SeparatorCostEstimate;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.MaterialPlateDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.PressureVesselDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.SeparatorDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 *
 * @author esol
 */
public class SeparatorMechanicalDesign extends MechanicalDesign {

    private static final long serialVersionUID = 1000;

    double wallThickness = 0.0;
    private double outerDiameter = 0.0;
    double gasLoadFactor = 1.0, volumeSafetyFactor = 1.0, Fg = 1.0, retentionTime = 60.0;

    public SeparatorMechanicalDesign(ProcessEquipmentInterface equipment) {
        super(equipment);
        costEstimate = new SeparatorCostEstimate(this);
    }

    @Override
	public void readDesignSpecifications() {

        super.readDesignSpecifications();
        if (getDesignStandard().containsKey("material plate design codes")) {
            ((MaterialPlateDesignStandard) getDesignStandard().get("material plate design codes"))
                    .readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "55", 1);
        } else {
            System.out.println("material plate design codes specified......");
        }
        if (getDesignStandard().containsKey("pressure vessel design code")) {
            System.out.println("pressure vessel code standard: "
                    + getDesignStandard().get("pressure vessel design code").getStandardName());
            wallThickness = ((PressureVesselDesignStandard) getDesignStandard().get("pressure vessel design code"))
                    .calcWallThickness();
        } else {
            System.out.println("no pressure vessel code standard specified......");
        }

        if (getDesignStandard().containsKey("separator process design")) {
            System.out.println("separator process design: "
                    + getDesignStandard().get("separator process design").getStandardName());
            gasLoadFactor = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
                    .getGasLoadFactor();
            Fg = ((SeparatorDesignStandard) getDesignStandard().get("separator process design")).getFg();
            volumeSafetyFactor = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
                    .getVolumetricDesignFactor();
            retentionTime = 120.0;// ((SeparatorDesignStandard) getDesignStandard().get("separator process
                                  // design")).getLiquidRetentionTime("API12J", this);
        } else {
            System.out.println("no separator process design specified......");
            return;
        }

    }

    @Override
	public void displayResults() {

        JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new BorderLayout());

        String[] names = { "Name", "Value", "Unit" };
        String[][] table = new String[16][3];// createTable(getProcessEquipment().getName());

        table[1][0] = "Separator Inner Diameter";
        table[1][1] = Double.toString(getInnerDiameter());
        table[1][2] = "m";

        table[2][0] = "Separator TanTan Length";
        table[2][1] = Double.toString(getTantanLength());
        table[2][2] = "m";

        table[3][0] = "Wall thickness";
        table[3][1] = Double.toString(getWallThickness());
        table[3][2] = "m";

        table[4][0] = "Empty Vessel Weight Weight";
        table[4][1] = Double.toString(getWeigthVesselShell());
        table[4][2] = "kg";

        table[5][0] = "Internals+Nozzle Weight";
        table[5][1] = Double.toString(getWeigthInternals());
        table[5][2] = "kg";

        table[8][0] = "Module Length";
        table[8][1] = Double.toString(getModuleLength());
        table[8][2] = "m";

        table[9][0] = "Module Height";
        table[9][1] = Double.toString(getModuleHeight());
        table[9][2] = "m";

        table[10][0] = "Module Width";
        table[10][1] = Double.toString(getModuleWidth());
        table[10][2] = "m";

        table[11][0] = "Module Total Weight";
        table[11][1] = Double.toString(getWeightTotal());
        table[11][2] = "kg";

        // table[5][0] = "Module Total Cost";
        // // table[5][1] = Double.toString(getMod());
        // table[5][2] = "kg";
        JTable Jtab = new JTable(table, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.setSize(800, 600); // pack();
        // dialog.pack();
        dialog.setVisible(true);
    }

    @Override
	public void calcDesign() {
        super.calcDesign();

        Separator separator = (Separator) getProcessEquipment();
        separator.getThermoSystem().initPhysicalProperties();
        separator.setDesignLiquidLevelFraction(Fg);

        double emptyVesselWeight = 0.0, internalsWeight = 0.0, externalNozzelsWeight = 0.0;
        double pipingWeight = 0.0, structualWeight = 0.0, electricalWeight = 0.0;
        double totalSkidWeight = 0.0;

        // double moduleWidth = 0.0, moduleHeight = 0.0, moduleLength = 0.0;
        double materialsCost = 0.0;
        double gasDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0)
                .getPhysicalProperties().getDensity();
        double liqDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(1)
                .getPhysicalProperties().getDensity();
        double liqViscosity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(1)
                .getPhysicalProperties().getViscosity();

        maxDesignVolumeFlow = volumeSafetyFactor
                * ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getPhase(0).getVolume() / 1e5;

        double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);

        innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
                / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
        outerDiameter = innerDiameter + 2.0 * wallThickness;
//        tantanLength = innerDiameter * 5.0;
        retentionTime = ((SeparatorDesignStandard) getDesignStandard().get("separator process design"))
                .getLiquidRetentionTime("API12J", this);

        tantanLength = Math.sqrt(
                4.0 * retentionTime * ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getLiquidVolume()
                        / 1e5 / (Math.PI * innerDiameter * innerDiameter * (1 - Fg)));
        double sepratorLength = tantanLength + innerDiameter;

        if (sepratorLength / innerDiameter > 6 || sepratorLength / innerDiameter < 3) {
            System.out.println("Fg need to be modified ... L/D separator= " + sepratorLength / innerDiameter);
            tantanLength = innerDiameter * 5.0;
            sepratorLength = tantanLength + innerDiameter;
        }
        System.out.println("inner Diameter " + innerDiameter);

        // alternative design
        double bubbleDiameter = 250.0e-6;
        double bubVelocity = 9.82 * Math.pow(bubbleDiameter, 2.0) * (liqDensity - gasDensity) / 18.0 / liqViscosity;
        double Ar = ((SeparatorInterface) getProcessEquipment()).getThermoSystem().getLiquidVolume() / 1e5
                / bubVelocity;
        double Daim = Math.sqrt(Ar / 4.0);
        double Length2 = 4.0 * Daim;

        if (Daim > innerDiameter) {
            innerDiameter = Daim;
            tantanLength = Length2;
        }
        // calculating from standard codes
        // sepLength = innerDiameter * 2.0;
        emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;

        for (SeparatorSection sep : separator.getSeparatorSections()) {
            sep.getMechanicalDesign().calcDesign();
            internalsWeight += sep.getMechanicalDesign().getTotalWeight();
        }

        System.out.println("internal weight " + internalsWeight);

        externalNozzelsWeight = 0.0; // need to be implemented
        double Wv = emptyVesselWeight + internalsWeight + externalNozzelsWeight;
        pipingWeight = Wv * 0.4;
        structualWeight = Wv * 0.1;
        electricalWeight = Wv * 0.08;
        totalSkidWeight = Wv + pipingWeight + structualWeight + electricalWeight;
        materialsCost = totalSkidWeight / 1000.0 * (1000 * 6.0) / 1000.0; // kNOK
        moduleWidth = innerDiameter * 2;
        moduleLength = tantanLength * 1.5;
        moduleHeight = innerDiameter * 2 + 1.0;
        // }

        setOuterDiameter(innerDiameter + 2.0 * getWallThickness());

        System.out.println("wall thickness: " + separator.getName() + " " + getWallThickness() + " m");
        System.out.println("separator dry weigth: " + emptyVesselWeight + " kg");
        System.out.println("total skid weigth: " + totalSkidWeight + " kg");
        System.out.println(
                "foot print: width:" + moduleWidth + " length " + moduleLength + " height " + moduleHeight + " meter.");
        System.out.println("mechanical price: " + materialsCost + " kNOK");

        setWeigthVesselShell(emptyVesselWeight);

        // tantanLength = innerDiameter * 5;
        setInnerDiameter(innerDiameter);

        setOuterDiameter(outerDiameter);

        setWeightElectroInstrument(electricalWeight);

        setWeightNozzle(externalNozzelsWeight);

        setWeightPiping(pipingWeight);

        setWeightStructualSteel(structualWeight);

        setWeightTotal(totalSkidWeight);

        setWeigthInternals(internalsWeight);

        setWallThickness(wallThickness);

        setModuleHeight(moduleHeight);

        setModuleWidth(moduleWidth);

        setModuleLength(moduleLength);
    }

    @Override
	public void setDesign() {
        ((SeparatorInterface) getProcessEquipment()).setInternalDiameter(innerDiameter);
        ((Separator) getProcessEquipment()).setSeparatorLength(tantanLength);
        // this method will be implemented to set calculated design...
    }

    @Override
	public double getOuterDiameter() {
        return outerDiameter;
    }

    /**
     * @return the wallThickness
     */
    @Override
	public double getWallThickness() {
        return wallThickness;
    }

    /**
     * @param wallThickness the wallThickness to set
     */
    @Override
	public void setWallThickness(double wallThickness) {
        this.wallThickness = wallThickness;
    }

    /**
     * @param outerDiameter the outerDiameter to set
     */
    @Override
	public void setOuterDiameter(double outerDiameter) {
        this.outerDiameter = outerDiameter;
    }

}
