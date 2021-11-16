package neqsim.processSimulation.mechanicalDesign.adsorber;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.mechanicalDesign.designStandards.PressureVesselDesignStandard;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.adsorber.SimpleAdsorber;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;

/**
 *
 * @author esol
 */
public class AdsorberMechanicalDesign extends MechanicalDesign {
    private static final long serialVersionUID = 1000;

    private double wallThickness = 0.0;
    private double outerDiameter = 0.0;
    double molecularSieveWaterCapacity = 10.0;

    public AdsorberMechanicalDesign(ProcessEquipmentInterface equipment) {
        super(equipment);
    }

    @Override
    public void readDesignSpecifications() {
        super.readDesignSpecifications();

        if (getDesignStandard().containsKey("pressure vessel design code")) {
            System.out.println("pressure vessel code standard: "
                    + getDesignStandard().get("pressure vessel design code").getStandardName());
            wallThickness = ((PressureVesselDesignStandard) getDesignStandard()
                    .get("pressure vessel design code")).calcWallThickness();
        } else {
            System.out.println("no pressure vessel code standard specified......");
            return;
        }

        if (getDesignStandard().containsKey("adsorption dehydration process design")) {
            // molecularSieveWaterCapacity = ((SeparatorDesignStandard)
            // getDesignStandard().get("adsorption dehydration process
            // design")).getMolecularSieveWaterCapacity();
        } else {
            System.out.println("no separator process design specified......");
        }
    }

    @Override
    public void calcDesign() {
        super.calcDesign();
        SimpleAdsorber separator = (SimpleAdsorber) getProcessEquipment();
        double Fg = 1.0;

        double emptyVesselWeight = 0.0, internalsWeight = 0.0, externalNozzelsWeight = 0.0;
        double pipingWeight = 0.0, structualWeight = 0.0, electricalWeight = 0.0;
        double totalSkidWeight = 0.0;

        double moduleWidth = 0.0, moduleHeight = 0.0, moduleLength = 0.0;

        double materialsCost = 0.0;
        double sepLength = 0.0;

        double gasDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem()
                .getPhase(0).getPhysicalProperties().getDensity();
        double liqDensity = ((SeparatorInterface) getProcessEquipment()).getThermoSystem()
                .getPhase(1).getPhysicalProperties().getDensity();

        // maxDesignVolumeFlow = volumeSafetyFactor * ((Separator)
        // getProcessEquipment()).getThermoSystem().getPhase(0).getVolume() / 1e5;

        // double maxGasVelocity = gasLoadFactor * Math.sqrt((liqDensity - gasDensity) /
        // gasDensity);
        // innerDiameter = Math.sqrt(4.0 * getMaxDesignVolumeFlow() /
        // (thermo.ThermodynamicConstantsInterface.pi * maxGasVelocity * Fg));
        tantanLength = innerDiameter * 5.0;
        System.out.println("inner Diameter " + innerDiameter);

        // calculating from standard codes

        emptyVesselWeight = 0.032 * getWallThickness() * innerDiameter * 1e3 * tantanLength;
        // for (SeparatorSection sep : separator.getSeparatorSections()) {
        // sep.getMechanicalDesign().calcDesign();
        // internalsWeight += sep.getMechanicalDesign().getTotalWeight();
        // }

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
        moduleHeight = innerDiameter * 2;
        // }

        setOuterDiameter(innerDiameter * 2.0 * getWallThickness());

        System.out.println(
                "wall thickness: " + separator.getName() + " " + getWallThickness() + " mm");
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
