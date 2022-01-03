package neqsim.processSimulation.mechanicalDesign.separator;

import neqsim.processSimulation.mechanicalDesign.designStandards.GasScrubberDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;

/**
 * <p>GasScrubberMechanicalDesign class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class GasScrubberMechanicalDesign extends SeparatorMechanicalDesign {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for GasScrubberMechanicalDesign.</p>
     *
     * @param equipment a {@link neqsim.processSimulation.processEquipment.ProcessEquipmentInterface} object
     */
    public GasScrubberMechanicalDesign(ProcessEquipmentInterface equipment) {
        super(equipment);
    }

    /** {@inheritDoc} */
    @Override
    public void readDesignSpecifications() {

        super.readDesignSpecifications();

        if (getDesignStandard().containsKey("gas scrubber process design")) {
            System.out.println("gas scrubber process design: "
                    + getDesignStandard().get("gas scrubber process design").getStandardName());
            gasLoadFactor = ((GasScrubberDesignStandard) getDesignStandard()
                    .get("gas scrubber process design")).getGasLoadFactor();
            volumeSafetyFactor = ((GasScrubberDesignStandard) getDesignStandard()
                    .get("gas scrubber process design")).getVolumetricDesignFactor();
        } else {
            System.out.println("no separator process design specified......");
        }

    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        super.calcDesign();
        Separator separator = (Separator) getProcessEquipment();
        double Fg = 1.0;
        if (separator.getOrientation().equals("horizontal")) {
            Fg = 1.0 - separator.getDesignLiquidLevelFraction();
        }

        double emptyVesselWeight = 0.0, internalsWeight = 0.0, externalNozzelsWeight = 0.0;
        double pipingWeight = 0.0, structualWeight = 0.0, electricalWeight = 0.0;
        double totalSkidWeight = 0.0;

        // double moduleWidth = 0.0, moduleHeight = 0.0, moduleLength = 0.0;
        double materialsCost = 0.0;
        double gasDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem()
                .getPhase(0).getPhysicalProperties().getDensity();
        double liqDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem()
                .getPhase(1).getPhysicalProperties().getDensity();

        maxDesignVolumeFlow = volumeSafetyFactor * ((SeparatorInterface) getProcessEquipment())
                .getThermoSystem().getPhase(0).getVolume() / 1e5;

        double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
        innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow()
                / (neqsim.thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
        tantanLength = innerDiameter * 5.0;
        System.out.println("inner Diameter " + innerDiameter);

        // calculating from standard codes
        // sepLength = innerDiameter * 2.0;
        emptyVesselWeight = 0.032 * getWallThickness() * 1e3 * innerDiameter * 1e3 * tantanLength;
        for (SeparatorSection sep : separator.getSeparatorSections()) {
            sep.getMechanicalDesign().calcDesign();
            internalsWeight += sep.getMechanicalDesign().getTotalWeight();
        }

        System.out.println("internal weight " + internalsWeight);

        externalNozzelsWeight = 0.0;
        double Wv = emptyVesselWeight + internalsWeight + externalNozzelsWeight;
        pipingWeight = Wv * 0.4;
        structualWeight = Wv * 0.1;
        electricalWeight = Wv * 0.08;
        totalSkidWeight = Wv + pipingWeight + structualWeight + electricalWeight;
        materialsCost = totalSkidWeight / 1000.0 * (1000 * 6.0) / 1000.0; // kNOK
        moduleWidth = innerDiameter * 2;
        moduleLength = innerDiameter * 2.5;
        moduleLength = tantanLength * 1.5;
        moduleHeight = innerDiameter * 2;
        // }

        setOuterDiameter(innerDiameter + 2.0 * getWallThickness());

        System.out.println(
                "wall thickness: " + separator.getName() + " " + getWallThickness() + " m");
        System.out.println("separator dry weigth: " + emptyVesselWeight + " kg");
        System.out.println("total skid weigth: " + totalSkidWeight + " kg");
        System.out.println("foot print: width:" + moduleWidth + " length " + moduleLength
                + " height " + moduleHeight + " meter.");
        System.out.println("mechanical price: " + materialsCost + " kNOK");

        setWeigthVesselShell(emptyVesselWeight);

        tantanLength = innerDiameter * 5;
        setInnerDiameter(innerDiameter);

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

    /** {@inheritDoc} */
    @Override
    public void setDesign() {
        ((SeparatorInterface) getProcessEquipment()).setInternalDiameter(innerDiameter);
        ((Separator) getProcessEquipment()).setSeparatorLength(tantanLength);
        // this method will be implemented to set calculated design...
    }

}
