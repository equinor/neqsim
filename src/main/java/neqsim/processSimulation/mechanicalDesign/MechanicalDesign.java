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

package neqsim.processSimulation.mechanicalDesign;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.Hashtable;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.processSimulation.costEstimation.UnitCostEstimateBaseClass;
import neqsim.processSimulation.mechanicalDesign.designStandards.AdsorptionDehydrationDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.CompressorDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.DesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.GasScrubberDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.JointEfficiencyPlateStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.MaterialPipeDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.MaterialPlateDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.PipelineDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.PressureVesselDesignStandard;
import neqsim.processSimulation.mechanicalDesign.designStandards.SeparatorDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;

/**
 * @author esol
 */
public class MechanicalDesign implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    /**
     * @return the materialPipeDesignStandard
     */
    public MaterialPipeDesignStandard getMaterialPipeDesignStandard() {
        return materialPipeDesignStandard;
    }

    /**
     * @param materialPipeDesignStandard the materialPipeDesignStandard to set
     */
    public void setMaterialPipeDesignStandard(
            MaterialPipeDesignStandard materialPipeDesignStandard) {
        this.materialPipeDesignStandard = materialPipeDesignStandard;
    }

    /**
     * @return the materialDesignStandard
     */
    public MaterialPlateDesignStandard getMaterialDesignStandard() {
        return materialPlateDesignStandard;
    }

    /**
     * @param materialDesignStandard the materialDesignStandard to set
     */
    public void setMaterialDesignStandard(MaterialPlateDesignStandard materialDesignStandard) {
        this.materialPlateDesignStandard = materialDesignStandard;
    }

    private boolean hasSetCompanySpecificDesignStandards = false;
    private double maxOperationPressure = 100.0;
    private double minOperationPressure = 1.0;
    private double maxOperationTemperature = 100.0;
    private double minOperationTemperature = 1.0;
    public double maxDesignVolumeFlow = 0.0, minDesignVolumeFLow = 0.0; // m^3/sec
    public double maxDesignGassVolumeFlow = 0.0, minDesignGassVolumeFLow = 0.0; // m^3/sec
    public double maxDesignOilVolumeFlow = 0.0, minDesignOilFLow = 0.0;// m^3/sec
    public double maxDesignWaterVolumeFlow = 0.0, minDesignWaterVolumeFLow = 0.0;// m^3/sec
    private String companySpecificDesignStandards = "Statoil";
    private ProcessEquipmentInterface processEquipment = null;
    // private String pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
    // private String pipingDesignStandard="Statoil";
    // private String valveDesignStandard="Statoil";
    private double tensileStrength = 483; // MPa
    private double jointEfficiency = 1.0; // fully radiographed
    private MaterialPlateDesignStandard materialPlateDesignStandard =
            new MaterialPlateDesignStandard();
    private MaterialPipeDesignStandard materialPipeDesignStandard =
            new MaterialPipeDesignStandard();
    private String construtionMaterial = "steel";
    private double corrosionAllowanse = 0.0; // mm
    private double pressureMarginFactor = 0.1;
    public double innerDiameter = 0.0;
    public double outerDiameter = 0.0;
    public double wallThickness = 0.0;
    public double tantanLength = 0.0; // tantan is same as seamtoseam length
    private double weightTotal = 0.0, volumeTotal = 2.0;
    public double weigthInternals = 0.0, weightNozzle = 0.0, weightPiping = 0.0,
            weightElectroInstrument = 0.0, weightStructualSteel = 0.0, weightVessel = 0.0,
            weigthVesselShell = 0.0;
    public double moduleHeight = 0.0, moduleWidth = 0, moduleLength = 0.0;
    public Hashtable<String, DesignStandard> designStandard =
            new Hashtable<String, DesignStandard>();
    public UnitCostEstimateBaseClass costEstimate = null;

    public MechanicalDesign(ProcessEquipmentInterface processEquipment) {
        this.processEquipment = processEquipment;
        costEstimate = new UnitCostEstimateBaseClass(this);
    }

    /**
     * @return the maxPressure
     */
    public double getMaxOperationPressure() {
        return maxOperationPressure;
    }

    public double getMaxDesignPressure() {
        return getMaxOperationPressure() * (1.0 + pressureMarginFactor);
    }

    public double getMinDesignPressure() {
        return getMinOperationPressure() * (1.0 - pressureMarginFactor);
    }

    public void readDesignSpecifications() {}

    /**
     * @param maxPressure the maxPressure to set
     */
    public void setMaxOperationPressure(double maxPressure) {
        this.maxOperationPressure = maxPressure;
    }

    /**
     * @return the minPressure
     */
    public double getMinOperationPressure() {
        return minOperationPressure;
    }

    /**
     * @param minPressure the minPressure to set
     */
    public void setMinOperationPressure(double minPressure) {
        this.minOperationPressure = minPressure;
    }

    /**
     * @return the maxTemperature
     */
    public double getMaxOperationTemperature() {
        return maxOperationTemperature;
    }

    /**
     * @param maxTemperature the maxTemperature to set
     */
    public void setMaxOperationTemperature(double maxTemperature) {
        this.maxOperationTemperature = maxTemperature;
    }

    /**
     * @return the minTemperature
     */
    public double getMinOperationTemperature() {
        return minOperationTemperature;
    }

    /**
     * @param minTemperature the minTemperature to set
     */
    public void setMinOperationTemperature(double minTemperature) {
        this.minOperationTemperature = minTemperature;
    }

    /**
     * @return the processEquipment
     */
    public ProcessEquipmentInterface getProcessEquipment() {
        return processEquipment;
    }

    /**
     * @param processEquipment the processEquipment to set
     */
    public void setProcessEquipment(ProcessEquipmentInterface processEquipment) {
        this.processEquipment = processEquipment;
    }

    public void calcDesign() {
        System.out.println("reading design paramters for: " + processEquipment.getName());
        if (!hasSetCompanySpecificDesignStandards) {
            setCompanySpecificDesignStandards("default");
        }
        readDesignSpecifications();
    }

    public void setDesign() {
        System.out.println("reading design paramters for: " + processEquipment.getName());
        readDesignSpecifications();
    }

    /**
     * @return the tensileStrength
     */
    public double getTensileStrength() {
        return tensileStrength;
    }

    /**
     * @param tensileStrength the tensileStrength to set
     */
    public void setTensileStrength(double tensileStrength) {
        this.tensileStrength = tensileStrength;
    }

    /**
     * @return the construtionMaterial
     */
    public String getConstrutionMaterial() {
        return construtionMaterial;
    }

    /**
     * @param construtionMaterial the construtionMaterial to set
     */
    public void setConstrutionMaterial(String construtionMaterial) {
        this.construtionMaterial = construtionMaterial;
    }

    /**
     * @return the jointEfficiency
     */
    public double getJointEfficiency() {
        return jointEfficiency;
    }

    public double getMaxAllowableStress() {
        return tensileStrength / 3.5;
    }

    /**
     * @param jointEfficiency the jointEfficiency to set
     */
    public void setJointEfficiency(double jointEfficiency) {
        this.jointEfficiency = jointEfficiency;
    }

    /**
     * @return the corrosionAllowanse
     */
    public double getCorrosionAllowanse() {
        return corrosionAllowanse;
    }

    /**
     * @param corrosionAllowanse the corrosionAllowanse to set
     */
    public void setCorrosionAllowanse(double corrosionAllowanse) {
        this.corrosionAllowanse = corrosionAllowanse;
    }

    /**
     * @return the pressureMarginFactor
     */
    public double getPressureMarginFactor() {
        return pressureMarginFactor;
    }

    /**
     * @param pressureMarginFactor the pressureMarginFactor to set
     */
    public void setPressureMarginFactor(double pressureMarginFactor) {
        this.pressureMarginFactor = pressureMarginFactor;
    }

    public double getOuterDiameter() {
        return 1.0;// processEquipment.getInternalDiameter();
    }

    /**
     * @return the companySpecificDesignStandards
     */
    public String getCompanySpecificDesignStandards() {
        return companySpecificDesignStandards;
    }

    /**
     * @param companySpecificDesignStandards the companySpecificDesignStandards to set
     */
    public void setCompanySpecificDesignStandards(String companySpecificDesignStandards) {
        this.companySpecificDesignStandards = companySpecificDesignStandards;

        if (companySpecificDesignStandards.equals("StatoilTR")) {
            getDesignStandard().put("pressure vessel design code",
                    new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
            getDesignStandard().put("separator process design",
                    new SeparatorDesignStandard("StatoilTR", this));
            getDesignStandard().put("gas scrubber process design",
                    new GasScrubberDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("adsorption dehydration process design",
                    new AdsorptionDehydrationDesignStandard("", this));
            getDesignStandard().put("pipeline design codes",
                    new PipelineDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("compressor design codes",
                    new CompressorDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("material plate design codes",
                    new MaterialPlateDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("plate Joint Efficiency design codes",
                    new JointEfficiencyPlateStandard("Statoil_TR1414", this));
            getDesignStandard().put("material pipe design codes",
                    new MaterialPipeDesignStandard("Statoil_TR1414", this));

            // pressureVesselDesignStandard = "ASME - Pressure Vessel Code";
            // setPipingDesignStandard("TR1945_Statoil");
            // setValveDesignStandard("TR1903_Statoil");
        } else {
            System.out.println("using default mechanical design standards...no design standard "
                    + companySpecificDesignStandards);
            getDesignStandard().put("pressure vessel design code",
                    new PressureVesselDesignStandard("ASME - Pressure Vessel Code", this));
            getDesignStandard().put("separator process design",
                    new SeparatorDesignStandard("StatoilTR", this));
            getDesignStandard().put("gas scrubber process design",
                    new GasScrubberDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("adsorption dehydration process design",
                    new AdsorptionDehydrationDesignStandard("", this));
            getDesignStandard().put("pipeline design codes",
                    new PipelineDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("compressor design codes",
                    new CompressorDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("material plate design codes",
                    new MaterialPlateDesignStandard("Statoil_TR1414", this));
            getDesignStandard().put("plate Joint Efficiency design codes",
                    new JointEfficiencyPlateStandard("Statoil_TR1414", this));
            getDesignStandard().put("material pipe design codes",
                    new MaterialPipeDesignStandard("Statoil_TR1414", this));
        }
        hasSetCompanySpecificDesignStandards = true;
    }

    /**
     * @return the innerDiameter
     */
    public double getInnerDiameter() {
        return innerDiameter;
    }

    /**
     * @param innerDiameter the innerDiameter to set
     */
    public void setInnerDiameter(double innerDiameter) {
        this.innerDiameter = innerDiameter;
    }

    /**
     * @param outerDiameter the outerDiameter to set
     */
    public void setOuterDiameter(double outerDiameter) {
        this.outerDiameter = outerDiameter;
    }

    /**
     * @return the wallThickness
     */
    public double getWallThickness() {
        return wallThickness;
    }

    /**
     * @param wallThickness the wallThickness to set
     */
    public void setWallThickness(double wallThickness) {
        this.wallThickness = wallThickness;
    }

    /**
     * @return the tantanLength
     */
    public double getTantanLength() {
        return tantanLength;
    }

    /**
     * @param tantanLength the tantanLength to set
     */
    public void setTantanLength(double tantanLength) {
        this.tantanLength = tantanLength;
    }

    /**
     * @return the weightTotal
     */
    public double getWeightTotal() {
        return weightTotal;
    }

    /**
     * @param weightTotal the weightTotal to set
     */
    public void setWeightTotal(double weightTotal) {
        this.weightTotal = weightTotal;
    }

    /**
     * @return the wigthInternals
     */
    public double getWeigthInternals() {
        return weigthInternals;
    }

    /**
     * @param weigthInternals the weigthInternals to set
     */
    public void setWeigthInternals(double weigthInternals) {
        this.weigthInternals = weigthInternals;
    }

    /**
     * @return the weightShell
     */
    public double getWeightVessel() {
        return weightVessel;
    }

    /**
     * @param weightVessel the weightShell to set
     */
    public void setWeightVessel(double weightVessel) {
        this.weightVessel = weightVessel;
    }

    /**
     * @return the weightNozzle
     */
    public double getWeightNozzle() {
        return weightNozzle;
    }

    /**
     * @param weightNozzle the weightNozzle to set
     */
    public void setWeightNozzle(double weightNozzle) {
        this.weightNozzle = weightNozzle;
    }

    /**
     * @return the weightPiping
     */
    public double getWeightPiping() {
        return weightPiping;
    }

    /**
     * @param weightPiping the weightPiping to set
     */
    public void setWeightPiping(double weightPiping) {
        this.weightPiping = weightPiping;
    }

    /**
     * @return the weightElectroInstrument
     */
    public double getWeightElectroInstrument() {
        return weightElectroInstrument;
    }

    /**
     * @param weightElectroInstrument the weightElectroInstrument to set
     */
    public void setWeightElectroInstrument(double weightElectroInstrument) {
        this.weightElectroInstrument = weightElectroInstrument;
    }

    /**
     * @return the weightStructualSteel
     */
    public double getWeightStructualSteel() {
        return weightStructualSteel;
    }

    /**
     * @param weightStructualSteel the weightStructualSteel to set
     */
    public void setWeightStructualSteel(double weightStructualSteel) {
        this.weightStructualSteel = weightStructualSteel;
    }

    /**
     * @return the weigthVesselShell
     */
    public double getWeigthVesselShell() {
        return weigthVesselShell;
    }

    /**
     * @param weigthVesselShell the weigthVesselShell to set
     */
    public void setWeigthVesselShell(double weigthVesselShell) {
        this.weigthVesselShell = weigthVesselShell;
    }

    /**
     * @return the moduleHeight
     */
    public double getModuleHeight() {
        return moduleHeight;
    }

    /**
     * @param moduleHeight the moduleHeight to set
     */
    public void setModuleHeight(double moduleHeight) {
        this.moduleHeight = moduleHeight;
    }

    /**
     * @return the moduleWidth
     */
    public double getModuleWidth() {
        return moduleWidth;
    }

    /**
     * @param moduleWidth the moduleWidth to set
     */
    public void setModuleWidth(double moduleWidth) {
        this.moduleWidth = moduleWidth;
    }

    /**
     * @return the moduleLength
     */
    public double getModuleLength() {
        return moduleLength;
    }

    /**
     * @param moduleLength the moduleLength to set
     */
    public void setModuleLength(double moduleLength) {
        this.moduleLength = moduleLength;
    }

    /**
     * @return the designStandard
     */
    public Hashtable<String, DesignStandard> getDesignStandard() {
        return designStandard;
    }

    /**
     * @param designStandard the designStandard to set
     */
    public void setDesignStandard(Hashtable<String, DesignStandard> designStandard) {
        this.designStandard = designStandard;
    }

    /**
     * @return the maxDesignVolumeFlow
     */
    public double getMaxDesignVolumeFlow() {
        return maxDesignVolumeFlow;
    }

    /**
     * @param maxDesignVolumeFlow the maxDesignVolumeFlow to set
     */
    public void setMaxDesignVolumeFlow(double maxDesignVolumeFlow) {
        this.maxDesignVolumeFlow = maxDesignVolumeFlow;
    }

    /**
     * @return the minDesignVolumeFLow
     */
    public double getMinDesignVolumeFLow() {
        return minDesignVolumeFLow;
    }

    /**
     * @param minDesignVolumeFLow the minDesignVolumeFLow to set
     */
    public void setMinDesignVolumeFLow(double minDesignVolumeFLow) {
        this.minDesignVolumeFLow = minDesignVolumeFLow;
    }

    /**
     * @return the maxDesignGassVolumeFlow
     */
    public double getMaxDesignGassVolumeFlow() {
        return maxDesignGassVolumeFlow;
    }

    /**
     * @param maxDesignGassVolumeFlow the maxDesignGassVolumeFlow to set
     */
    public void setMaxDesignGassVolumeFlow(double maxDesignGassVolumeFlow) {
        this.maxDesignGassVolumeFlow = maxDesignGassVolumeFlow;
    }

    /**
     * @return the minDesignGassVolumeFLow
     */
    public double getMinDesignGassVolumeFLow() {
        return minDesignGassVolumeFLow;
    }

    /**
     * @param minDesignGassVolumeFLow the minDesignGassVolumeFLow to set
     */
    public void setMinDesignGassVolumeFLow(double minDesignGassVolumeFLow) {
        this.minDesignGassVolumeFLow = minDesignGassVolumeFLow;
    }

    /**
     * @return the maxDesignOilVolumeFlow
     */
    public double getMaxDesignOilVolumeFlow() {
        return maxDesignOilVolumeFlow;
    }

    /**
     * @param maxDesignOilVolumeFlow the maxDesignOilVolumeFlow to set
     */
    public void setMaxDesignOilVolumeFlow(double maxDesignOilVolumeFlow) {
        this.maxDesignOilVolumeFlow = maxDesignOilVolumeFlow;
    }

    /**
     * @return the minDesignOilFLow
     */
    public double getMinDesignOilFLow() {
        return minDesignOilFLow;
    }

    /**
     * @param minDesignOilFLow the minDesignOilFLow to set
     */
    public void setMinDesignOilFLow(double minDesignOilFLow) {
        this.minDesignOilFLow = minDesignOilFLow;
    }

    /**
     * @return the maxDesignWaterVolumeFlow
     */
    public double getMaxDesignWaterVolumeFlow() {
        return maxDesignWaterVolumeFlow;
    }

    /**
     * @param maxDesignWaterVolumeFlow the maxDesignWaterVolumeFlow to set
     */
    public void setMaxDesignWaterVolumeFlow(double maxDesignWaterVolumeFlow) {
        this.maxDesignWaterVolumeFlow = maxDesignWaterVolumeFlow;
    }

    /**
     * @return the minDesignWaterVolumeFLow
     */
    public double getMinDesignWaterVolumeFLow() {
        return minDesignWaterVolumeFLow;
    }

    /**
     * @param minDesignWaterVolumeFLow the minDesignWaterVolumeFLow to set
     */
    public void setMinDesignWaterVolumeFLow(double minDesignWaterVolumeFLow) {
        this.minDesignWaterVolumeFLow = minDesignWaterVolumeFLow;
    }

    public void displayResults() {
        JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new BorderLayout());

        String[] names = {"", "Volume", "Weight"};
        String[][] table = new String[3][3];// createTable(getProcessEquipment().getName());
        table[1][0] = getProcessEquipment().getName();
        table[1][1] = Double.toString(getWeightTotal());
        table[1][2] = Double.toString(getVolumeTotal());
        JTable Jtab = new JTable(table, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.setSize(800, 600); // pack();
        // dialog.pack();
        dialog.setVisible(true);
    }

    /**
     * @return the volumeTotal
     */
    public double getVolumeTotal() {
        return volumeTotal;
    }

    /**
     * @return the hasSetCompanySpecificDesignStandards
     */
    public boolean isHasSetCompanySpecificDesignStandards() {
        return hasSetCompanySpecificDesignStandards;
    }

    /**
     * @param hasSetCompanySpecificDesignStandards the hasSetCompanySpecificDesignStandards to set
     */
    public void setHasSetCompanySpecificDesignStandards(
            boolean hasSetCompanySpecificDesignStandards) {
        this.hasSetCompanySpecificDesignStandards = hasSetCompanySpecificDesignStandards;
    }

    /**
     * @return the costEstimate
     */
    public UnitCostEstimateBaseClass getCostEstimate() {
        return costEstimate;
    }
}
