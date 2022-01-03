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
 * <p>MechanicalDesign class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MechanicalDesign implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Getter for the field <code>materialPipeDesignStandard</code>.</p>
     *
     * @return the materialPipeDesignStandard
     */
    public MaterialPipeDesignStandard getMaterialPipeDesignStandard() {
        return materialPipeDesignStandard;
    }

    /**
     * <p>Setter for the field <code>materialPipeDesignStandard</code>.</p>
     *
     * @param materialPipeDesignStandard the materialPipeDesignStandard to set
     */
    public void setMaterialPipeDesignStandard(
            MaterialPipeDesignStandard materialPipeDesignStandard) {
        this.materialPipeDesignStandard = materialPipeDesignStandard;
    }

    /**
     * <p>getMaterialDesignStandard.</p>
     *
     * @return the materialDesignStandard
     */
    public MaterialPlateDesignStandard getMaterialDesignStandard() {
        return materialPlateDesignStandard;
    }

    /**
     * <p>setMaterialDesignStandard.</p>
     *
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

    /**
     * <p>Constructor for MechanicalDesign.</p>
     *
     * @param processEquipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     */
    public MechanicalDesign(ProcessEquipmentInterface processEquipment) {
        this.processEquipment = processEquipment;
        costEstimate = new UnitCostEstimateBaseClass(this);
    }

    /**
     * <p>Getter for the field <code>maxOperationPressure</code>.</p>
     *
     * @return the maxPressure
     */
    public double getMaxOperationPressure() {
        return maxOperationPressure;
    }

    /**
     * <p>getMaxDesignPressure.</p>
     *
     * @return a double
     */
    public double getMaxDesignPressure() {
        return getMaxOperationPressure() * (1.0 + pressureMarginFactor);
    }

    /**
     * <p>getMinDesignPressure.</p>
     *
     * @return a double
     */
    public double getMinDesignPressure() {
        return getMinOperationPressure() * (1.0 - pressureMarginFactor);
    }

    /**
     * <p>readDesignSpecifications.</p>
     */
    public void readDesignSpecifications() {}

    /**
     * <p>Setter for the field <code>maxOperationPressure</code>.</p>
     *
     * @param maxPressure the maxPressure to set
     */
    public void setMaxOperationPressure(double maxPressure) {
        this.maxOperationPressure = maxPressure;
    }

    /**
     * <p>Getter for the field <code>minOperationPressure</code>.</p>
     *
     * @return the minPressure
     */
    public double getMinOperationPressure() {
        return minOperationPressure;
    }

    /**
     * <p>Setter for the field <code>minOperationPressure</code>.</p>
     *
     * @param minPressure the minPressure to set
     */
    public void setMinOperationPressure(double minPressure) {
        this.minOperationPressure = minPressure;
    }

    /**
     * <p>Getter for the field <code>maxOperationTemperature</code>.</p>
     *
     * @return the maxTemperature
     */
    public double getMaxOperationTemperature() {
        return maxOperationTemperature;
    }

    /**
     * <p>Setter for the field <code>maxOperationTemperature</code>.</p>
     *
     * @param maxTemperature the maxTemperature to set
     */
    public void setMaxOperationTemperature(double maxTemperature) {
        this.maxOperationTemperature = maxTemperature;
    }

    /**
     * <p>Getter for the field <code>minOperationTemperature</code>.</p>
     *
     * @return the minTemperature
     */
    public double getMinOperationTemperature() {
        return minOperationTemperature;
    }

    /**
     * <p>Setter for the field <code>minOperationTemperature</code>.</p>
     *
     * @param minTemperature the minTemperature to set
     */
    public void setMinOperationTemperature(double minTemperature) {
        this.minOperationTemperature = minTemperature;
    }

    /**
     * <p>Getter for the field <code>processEquipment</code>.</p>
     *
     * @return the processEquipment
     */
    public ProcessEquipmentInterface getProcessEquipment() {
        return processEquipment;
    }

    /**
     * <p>Setter for the field <code>processEquipment</code>.</p>
     *
     * @param processEquipment the processEquipment to set
     */
    public void setProcessEquipment(ProcessEquipmentInterface processEquipment) {
        this.processEquipment = processEquipment;
    }

    /**
     * <p>calcDesign.</p>
     */
    public void calcDesign() {
        System.out.println("reading design paramters for: " + processEquipment.getName());
        if (!hasSetCompanySpecificDesignStandards) {
            setCompanySpecificDesignStandards("default");
        }
        readDesignSpecifications();

    }

    /**
     * <p>setDesign.</p>
     */
    public void setDesign() {
        System.out.println("reading design paramters for: " + processEquipment.getName());
        readDesignSpecifications();

    }

    /**
     * <p>Getter for the field <code>tensileStrength</code>.</p>
     *
     * @return the tensileStrength
     */
    public double getTensileStrength() {
        return tensileStrength;
    }

    /**
     * <p>Setter for the field <code>tensileStrength</code>.</p>
     *
     * @param tensileStrength the tensileStrength to set
     */
    public void setTensileStrength(double tensileStrength) {
        this.tensileStrength = tensileStrength;
    }

    /**
     * <p>Getter for the field <code>construtionMaterial</code>.</p>
     *
     * @return the construtionMaterial
     */
    public String getConstrutionMaterial() {
        return construtionMaterial;
    }

    /**
     * <p>Setter for the field <code>construtionMaterial</code>.</p>
     *
     * @param construtionMaterial the construtionMaterial to set
     */
    public void setConstrutionMaterial(String construtionMaterial) {
        this.construtionMaterial = construtionMaterial;
    }

    /**
     * <p>Getter for the field <code>jointEfficiency</code>.</p>
     *
     * @return the jointEfficiency
     */
    public double getJointEfficiency() {
        return jointEfficiency;
    }

    /**
     * <p>getMaxAllowableStress.</p>
     *
     * @return a double
     */
    public double getMaxAllowableStress() {
        return tensileStrength / 3.5;
    }

    /**
     * <p>Setter for the field <code>jointEfficiency</code>.</p>
     *
     * @param jointEfficiency the jointEfficiency to set
     */
    public void setJointEfficiency(double jointEfficiency) {
        this.jointEfficiency = jointEfficiency;
    }

    /**
     * <p>Getter for the field <code>corrosionAllowanse</code>.</p>
     *
     * @return the corrosionAllowanse
     */
    public double getCorrosionAllowanse() {
        return corrosionAllowanse;
    }

    /**
     * <p>Setter for the field <code>corrosionAllowanse</code>.</p>
     *
     * @param corrosionAllowanse the corrosionAllowanse to set
     */
    public void setCorrosionAllowanse(double corrosionAllowanse) {
        this.corrosionAllowanse = corrosionAllowanse;
    }

    /**
     * <p>Getter for the field <code>pressureMarginFactor</code>.</p>
     *
     * @return the pressureMarginFactor
     */
    public double getPressureMarginFactor() {
        return pressureMarginFactor;
    }

    /**
     * <p>Setter for the field <code>pressureMarginFactor</code>.</p>
     *
     * @param pressureMarginFactor the pressureMarginFactor to set
     */
    public void setPressureMarginFactor(double pressureMarginFactor) {
        this.pressureMarginFactor = pressureMarginFactor;
    }

    /**
     * <p>Getter for the field <code>outerDiameter</code>.</p>
     *
     * @return a double
     */
    public double getOuterDiameter() {
        return 1.0;// processEquipment.getInternalDiameter();
    }

    /**
     * <p>Getter for the field <code>companySpecificDesignStandards</code>.</p>
     *
     * @return the companySpecificDesignStandards
     */
    public String getCompanySpecificDesignStandards() {
        return companySpecificDesignStandards;
    }

    /**
     * <p>Setter for the field <code>companySpecificDesignStandards</code>.</p>
     *
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
     * <p>Getter for the field <code>innerDiameter</code>.</p>
     *
     * @return the innerDiameter
     */
    public double getInnerDiameter() {
        return innerDiameter;
    }

    /**
     * <p>Setter for the field <code>innerDiameter</code>.</p>
     *
     * @param innerDiameter the innerDiameter to set
     */
    public void setInnerDiameter(double innerDiameter) {
        this.innerDiameter = innerDiameter;
    }

    /**
     * <p>Setter for the field <code>outerDiameter</code>.</p>
     *
     * @param outerDiameter the outerDiameter to set
     */
    public void setOuterDiameter(double outerDiameter) {
        this.outerDiameter = outerDiameter;
    }

    /**
     * <p>Getter for the field <code>wallThickness</code>.</p>
     *
     * @return the wallThickness
     */
    public double getWallThickness() {
        return wallThickness;
    }

    /**
     * <p>Setter for the field <code>wallThickness</code>.</p>
     *
     * @param wallThickness the wallThickness to set
     */
    public void setWallThickness(double wallThickness) {
        this.wallThickness = wallThickness;
    }

    /**
     * <p>Getter for the field <code>tantanLength</code>.</p>
     *
     * @return the tantanLength
     */
    public double getTantanLength() {
        return tantanLength;
    }

    /**
     * <p>Setter for the field <code>tantanLength</code>.</p>
     *
     * @param tantanLength the tantanLength to set
     */
    public void setTantanLength(double tantanLength) {
        this.tantanLength = tantanLength;
    }

    /**
     * <p>Getter for the field <code>weightTotal</code>.</p>
     *
     * @return the weightTotal
     */
    public double getWeightTotal() {
        return weightTotal;
    }

    /**
     * <p>Setter for the field <code>weightTotal</code>.</p>
     *
     * @param weightTotal the weightTotal to set
     */
    public void setWeightTotal(double weightTotal) {
        this.weightTotal = weightTotal;
    }

    /**
     * <p>Getter for the field <code>weigthInternals</code>.</p>
     *
     * @return the wigthInternals
     */
    public double getWeigthInternals() {
        return weigthInternals;
    }

    /**
     * <p>Setter for the field <code>weigthInternals</code>.</p>
     *
     * @param weigthInternals the weigthInternals to set
     */
    public void setWeigthInternals(double weigthInternals) {
        this.weigthInternals = weigthInternals;
    }

    /**
     * <p>Getter for the field <code>weightVessel</code>.</p>
     *
     * @return the weightShell
     */
    public double getWeightVessel() {
        return weightVessel;
    }

    /**
     * <p>Setter for the field <code>weightVessel</code>.</p>
     *
     * @param weightVessel the weightShell to set
     */
    public void setWeightVessel(double weightVessel) {
        this.weightVessel = weightVessel;
    }

    /**
     * <p>Getter for the field <code>weightNozzle</code>.</p>
     *
     * @return the weightNozzle
     */
    public double getWeightNozzle() {
        return weightNozzle;
    }

    /**
     * <p>Setter for the field <code>weightNozzle</code>.</p>
     *
     * @param weightNozzle the weightNozzle to set
     */
    public void setWeightNozzle(double weightNozzle) {
        this.weightNozzle = weightNozzle;
    }

    /**
     * <p>Getter for the field <code>weightPiping</code>.</p>
     *
     * @return the weightPiping
     */
    public double getWeightPiping() {
        return weightPiping;
    }

    /**
     * <p>Setter for the field <code>weightPiping</code>.</p>
     *
     * @param weightPiping the weightPiping to set
     */
    public void setWeightPiping(double weightPiping) {
        this.weightPiping = weightPiping;
    }

    /**
     * <p>Getter for the field <code>weightElectroInstrument</code>.</p>
     *
     * @return the weightElectroInstrument
     */
    public double getWeightElectroInstrument() {
        return weightElectroInstrument;
    }

    /**
     * <p>Setter for the field <code>weightElectroInstrument</code>.</p>
     *
     * @param weightElectroInstrument the weightElectroInstrument to set
     */
    public void setWeightElectroInstrument(double weightElectroInstrument) {
        this.weightElectroInstrument = weightElectroInstrument;
    }

    /**
     * <p>Getter for the field <code>weightStructualSteel</code>.</p>
     *
     * @return the weightStructualSteel
     */
    public double getWeightStructualSteel() {
        return weightStructualSteel;
    }

    /**
     * <p>Setter for the field <code>weightStructualSteel</code>.</p>
     *
     * @param weightStructualSteel the weightStructualSteel to set
     */
    public void setWeightStructualSteel(double weightStructualSteel) {
        this.weightStructualSteel = weightStructualSteel;
    }

    /**
     * <p>Getter for the field <code>weigthVesselShell</code>.</p>
     *
     * @return the weigthVesselShell
     */
    public double getWeigthVesselShell() {
        return weigthVesselShell;
    }

    /**
     * <p>Setter for the field <code>weigthVesselShell</code>.</p>
     *
     * @param weigthVesselShell the weigthVesselShell to set
     */
    public void setWeigthVesselShell(double weigthVesselShell) {
        this.weigthVesselShell = weigthVesselShell;
    }

    /**
     * <p>Getter for the field <code>moduleHeight</code>.</p>
     *
     * @return the moduleHeight
     */
    public double getModuleHeight() {
        return moduleHeight;
    }

    /**
     * <p>Setter for the field <code>moduleHeight</code>.</p>
     *
     * @param moduleHeight the moduleHeight to set
     */
    public void setModuleHeight(double moduleHeight) {
        this.moduleHeight = moduleHeight;
    }

    /**
     * <p>Getter for the field <code>moduleWidth</code>.</p>
     *
     * @return the moduleWidth
     */
    public double getModuleWidth() {
        return moduleWidth;
    }

    /**
     * <p>Setter for the field <code>moduleWidth</code>.</p>
     *
     * @param moduleWidth the moduleWidth to set
     */
    public void setModuleWidth(double moduleWidth) {
        this.moduleWidth = moduleWidth;
    }

    /**
     * <p>Getter for the field <code>moduleLength</code>.</p>
     *
     * @return the moduleLength
     */
    public double getModuleLength() {
        return moduleLength;
    }

    /**
     * <p>Setter for the field <code>moduleLength</code>.</p>
     *
     * @param moduleLength the moduleLength to set
     */
    public void setModuleLength(double moduleLength) {
        this.moduleLength = moduleLength;
    }

    /**
     * <p>Getter for the field <code>designStandard</code>.</p>
     *
     * @return the designStandard
     */
    public Hashtable<String, DesignStandard> getDesignStandard() {
        return designStandard;
    }

    /**
     * <p>Setter for the field <code>designStandard</code>.</p>
     *
     * @param designStandard the designStandard to set
     */
    public void setDesignStandard(Hashtable<String, DesignStandard> designStandard) {
        this.designStandard = designStandard;
    }

    /**
     * <p>Getter for the field <code>maxDesignVolumeFlow</code>.</p>
     *
     * @return the maxDesignVolumeFlow
     */
    public double getMaxDesignVolumeFlow() {
        return maxDesignVolumeFlow;
    }

    /**
     * <p>Setter for the field <code>maxDesignVolumeFlow</code>.</p>
     *
     * @param maxDesignVolumeFlow the maxDesignVolumeFlow to set
     */
    public void setMaxDesignVolumeFlow(double maxDesignVolumeFlow) {
        this.maxDesignVolumeFlow = maxDesignVolumeFlow;
    }

    /**
     * <p>Getter for the field <code>minDesignVolumeFLow</code>.</p>
     *
     * @return the minDesignVolumeFLow
     */
    public double getMinDesignVolumeFLow() {
        return minDesignVolumeFLow;
    }

    /**
     * <p>Setter for the field <code>minDesignVolumeFLow</code>.</p>
     *
     * @param minDesignVolumeFLow the minDesignVolumeFLow to set
     */
    public void setMinDesignVolumeFLow(double minDesignVolumeFLow) {
        this.minDesignVolumeFLow = minDesignVolumeFLow;
    }

    /**
     * <p>Getter for the field <code>maxDesignGassVolumeFlow</code>.</p>
     *
     * @return the maxDesignGassVolumeFlow
     */
    public double getMaxDesignGassVolumeFlow() {
        return maxDesignGassVolumeFlow;
    }

    /**
     * <p>Setter for the field <code>maxDesignGassVolumeFlow</code>.</p>
     *
     * @param maxDesignGassVolumeFlow the maxDesignGassVolumeFlow to set
     */
    public void setMaxDesignGassVolumeFlow(double maxDesignGassVolumeFlow) {
        this.maxDesignGassVolumeFlow = maxDesignGassVolumeFlow;
    }

    /**
     * <p>Getter for the field <code>minDesignGassVolumeFLow</code>.</p>
     *
     * @return the minDesignGassVolumeFLow
     */
    public double getMinDesignGassVolumeFLow() {
        return minDesignGassVolumeFLow;
    }

    /**
     * <p>Setter for the field <code>minDesignGassVolumeFLow</code>.</p>
     *
     * @param minDesignGassVolumeFLow the minDesignGassVolumeFLow to set
     */
    public void setMinDesignGassVolumeFLow(double minDesignGassVolumeFLow) {
        this.minDesignGassVolumeFLow = minDesignGassVolumeFLow;
    }

    /**
     * <p>Getter for the field <code>maxDesignOilVolumeFlow</code>.</p>
     *
     * @return the maxDesignOilVolumeFlow
     */
    public double getMaxDesignOilVolumeFlow() {
        return maxDesignOilVolumeFlow;
    }

    /**
     * <p>Setter for the field <code>maxDesignOilVolumeFlow</code>.</p>
     *
     * @param maxDesignOilVolumeFlow the maxDesignOilVolumeFlow to set
     */
    public void setMaxDesignOilVolumeFlow(double maxDesignOilVolumeFlow) {
        this.maxDesignOilVolumeFlow = maxDesignOilVolumeFlow;
    }

    /**
     * <p>Getter for the field <code>minDesignOilFLow</code>.</p>
     *
     * @return the minDesignOilFLow
     */
    public double getMinDesignOilFLow() {
        return minDesignOilFLow;
    }

    /**
     * <p>Setter for the field <code>minDesignOilFLow</code>.</p>
     *
     * @param minDesignOilFLow the minDesignOilFLow to set
     */
    public void setMinDesignOilFLow(double minDesignOilFLow) {
        this.minDesignOilFLow = minDesignOilFLow;
    }

    /**
     * <p>Getter for the field <code>maxDesignWaterVolumeFlow</code>.</p>
     *
     * @return the maxDesignWaterVolumeFlow
     */
    public double getMaxDesignWaterVolumeFlow() {
        return maxDesignWaterVolumeFlow;
    }

    /**
     * <p>Setter for the field <code>maxDesignWaterVolumeFlow</code>.</p>
     *
     * @param maxDesignWaterVolumeFlow the maxDesignWaterVolumeFlow to set
     */
    public void setMaxDesignWaterVolumeFlow(double maxDesignWaterVolumeFlow) {
        this.maxDesignWaterVolumeFlow = maxDesignWaterVolumeFlow;
    }

    /**
     * <p>Getter for the field <code>minDesignWaterVolumeFLow</code>.</p>
     *
     * @return the minDesignWaterVolumeFLow
     */
    public double getMinDesignWaterVolumeFLow() {
        return minDesignWaterVolumeFLow;
    }

    /**
     * <p>Setter for the field <code>minDesignWaterVolumeFLow</code>.</p>
     *
     * @param minDesignWaterVolumeFLow the minDesignWaterVolumeFLow to set
     */
    public void setMinDesignWaterVolumeFLow(double minDesignWaterVolumeFLow) {
        this.minDesignWaterVolumeFLow = minDesignWaterVolumeFLow;
    }

    /**
     * <p>displayResults.</p>
     */
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
     * <p>Getter for the field <code>volumeTotal</code>.</p>
     *
     * @return the volumeTotal
     */
    public double getVolumeTotal() {
        return volumeTotal;
    }

    /**
     * <p>isHasSetCompanySpecificDesignStandards.</p>
     *
     * @return the hasSetCompanySpecificDesignStandards
     */
    public boolean isHasSetCompanySpecificDesignStandards() {
        return hasSetCompanySpecificDesignStandards;
    }

    /**
     * <p>Setter for the field <code>hasSetCompanySpecificDesignStandards</code>.</p>
     *
     * @param hasSetCompanySpecificDesignStandards the hasSetCompanySpecificDesignStandards to set
     */
    public void setHasSetCompanySpecificDesignStandards(
            boolean hasSetCompanySpecificDesignStandards) {
        this.hasSetCompanySpecificDesignStandards = hasSetCompanySpecificDesignStandards;
    }

    /**
     * <p>Getter for the field <code>costEstimate</code>.</p>
     *
     * @return the costEstimate
     */
    public UnitCostEstimateBaseClass getCostEstimate() {
        return costEstimate;
    }

}
