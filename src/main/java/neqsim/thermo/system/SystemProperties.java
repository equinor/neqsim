package neqsim.thermo.system;

import java.util.Objects;
import neqsim.thermo.phase.PhaseInterface;

public class SystemProperties {
    public Double[] values;
    protected String[] names;

    final int nCols = 70;

    public SystemProperties(Double[] values, String[] names) {
        this.names = names;
        this.values = values;
    }

    public SystemProperties(SystemInterface fluid) {
        final String[] phaseName = {"gas", "oil", "aqueous"};

        values = new Double[nCols];
        names = new String[nCols];
        int k = 0;

        names[k++] = "Mix Number of Phases";
        values[k] = (double) fluid.getNumberOfPhases();
        names[k++] = "Mix Pressure [Pa]";
        values[k] = fluid.getPressure("Pa");
        names[k++] = "Mix Temperature [K]";
        values[k] = fluid.getTemperature("K");
        names[k++] = "Mix Mole Percent";
        values[k] = fluid.getNumberOfMoles() * 100;
        names[k++] = "Mix Weight Percent";
        values[k] = 100.0;
        names[k++] = "Mix Molar Volume [m3/mol]";
        values[k] = 1.0 / fluid.getDensity("mol/m3");
        names[k++] = "Mix Volume Percent";
        values[k] = 100.0;
        names[k++] = "Mix Density [kg/m3]";
        values[k] = fluid.getDensity("kg/m3");
        names[k++] = "Mix Z Factor";
        values[k] = fluid.getZ();
        names[k++] = "Mix Molecular Weight [g/mol]";
        values[k] = fluid.getMolarMass() * 1000;
        names[k++] = "Mix Enthalpy [J/mol]";
        values[k] = fluid.getEnthalpy("J/mol");
        names[k++] = "Mix Entropy [J/molK]";
        values[k] = fluid.getEntropy("J/molK");
        names[k++] = "Mix Heat Capacity-Cp [J/molK]";
        values[k] = fluid.getCp("J/molK");
        names[k++] = "Mix Heat Capacity-Cv [J/molK]";
        values[k] = fluid.getCv("J/molK");
        // names[k++] = "Mix Gamma (Cp/Cv)";
        // values[k] = getCp()/getCv();
        names[k++] = "Mix Gamma (Cp/Cv)";
        values[k] = fluid.getGamma();
        names[k++] = "Mix JT Coefficient [K/Pa]";
        values[k] = Double.NaN;
        names[k++] = "Mix Velocity of Sound [m/s]";
        values[k] = Double.NaN;
        names[k++] = "Mix Viscosity [Pa s] or [kg/(m*s)]";
        values[k] = fluid.getViscosity("kg/msec");
        names[k++] = "Mix Thermal Conductivity [W/mK]";
        values[k] = fluid.getThermalConductivity("W/mK");
        // names[k++] = "Surface Tension(N/m) between gas and oil phase** NOT USED";
        // values[k] = getInterfacialTension("gas","oil");
        // names[k++] = "Surface Tension(N/m) between gas and aqueous phase** NOT USED";
        // values[k] = getInterfacialTension("gas","aqueous");

        // Phase properties (phase: gas=0, liquid=1, Aqueous=2)
        for (int j = 0; j < 3; j++) {
            String currPhaseName = phaseName[j];

            if (fluid.hasPhaseType(phaseName[j])) {
                int phaseNumber = fluid.getPhaseNumberOfPhase(phaseName[j]);
                PhaseInterface currPhase = fluid.getPhase(phaseNumber);
                names[k++] = currPhaseName + " Mole Percent";
                values[k] = fluid.getMoleFraction(phaseNumber) * 100;
                names[k++] = currPhaseName + " Weight Percent";
                values[k] = fluid.getWtFraction(phaseNumber) * 100;
                names[k++] = currPhaseName + " Molar Volume [m3/mol]";
                values[k] = 1.0 / currPhase.getDensity("mol/m3");
                names[k++] = currPhaseName + " Volume Percent";
                values[k] = fluid.getCorrectedVolumeFraction(phaseNumber) * 100;
                names[k++] = currPhaseName + " Density [kg/m3]";
                values[k] = currPhase.getDensity("kg/m3");

                names[k++] = currPhaseName + " Z Factor";
                if (Objects.equals(phaseName[j], "oil")
                        || Objects.equals(phaseName[j], "aqueous")) {
                    // Phase doesn't calculate correct result for these properties. See specs
                    values[k] = Double.NaN;
                } else {
                    values[k] = currPhase.getZ();
                }

                names[k++] = currPhaseName + "Molecular Weight [g/mol]";
                values[k] = currPhase.getMolarMass() * 1000;
                // names[k++] = currPhaseName + "Phase Enthalpy [J/mol]";
                // values[k] = currPhase.getEnthalpy() /
                // currPhase.getNumberOfMolesInPhase();
                names[k++] = currPhaseName + "Enthalpy [J/mol]";
                values[k] = currPhase.getEnthalpy("J/mol");
                // names[k++] = currPhaseName + "Phase Entropy [J/molK]";
                // values[k] = currPhase.getEntropy() / currPhase.getNumberOfMolesInPhase();
                names[k++] = currPhaseName + "Entropy [J/molK]";
                values[k] = currPhase.getEntropy("J/molK");
                // names[k++] = currPhaseName + "Heat Capacity-Cp [J/molK]";
                // values[k] = currPhase.getCp() / currPhase.getNumberOfMolesInPhase();
                names[k++] = currPhaseName + "Heat Capacity-Cp [J/molK]";
                values[k] = currPhase.getCp("J/molK");
                // names[k++] = currPhaseName + "Phase Heat Capacity-Cv [J/molK]";
                // values[k] = currPhase.getCv() / currPhase.getNumberOfMolesInPhase();
                names[k++] = currPhaseName + " Heat Capacity-Cv [J/molK]";
                values[k] = currPhase.getCv("J/molK");
                // names[k++] = currPhaseName + " Phase Kappa (Cp/Cv)";
                // values[k] = currPhase.getCp() / currPhase.getCv();
                names[k++] = currPhaseName + " Gamma (Cp/Cv)";
                values[k] = currPhase.getGamma();

                names[k++] = currPhaseName + " JT Coefficient [K/Pa]";
                if (Objects.equals(phaseName[j], "oil")
                        || Objects.equals(phaseName[j], "aqueous")) {
                    // Phase doesn't calculate correct result for these properties. See specs
                    values[k] = Double.NaN;
                    names[k++] = currPhaseName + " Velocity of Sound [m/s]";
                    values[k] = Double.NaN;
                } else {
                    names[k++] = currPhaseName + " JT Coefficient [K/Pa]";
                    values[k++] = currPhase.getJouleThomsonCoefficient() / 1e5;
                    names[k++] = currPhaseName + " Velocity of Sound [m/s]";
                    values[k] = currPhase.getSoundSpeed();
                }

                names[k++] = currPhaseName + " Viscosity [Pa s] or [kg/msec]";
                values[k] = currPhase.getViscosity("kg/msec");
                names[k++] = currPhaseName + " Thermal Conductivity [W/mK]";
                values[k] = currPhase.getConductivity("W/mK");
            } else {
                names[k++] = currPhaseName + " Mole Percent";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Weight Percent";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Molar Volume [m3/mol]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Volume Percent";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Density [kg/m3]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Z Factor";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Molecular Weight [g/mol]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Enthalpy [J/mol]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Entropy [J/molK]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Heat Capacity-Cp [J/molK]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Heat Capacity-Cv [J/molK]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Kappa (Cp/Cv)";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " JT Coefficient K/Pa]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Velocity of Sound [m/s]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Viscosity [Pa s]";
                values[k] = Double.NaN;
                names[k++] = currPhaseName + " Thermal Conductivity [W/mK]";
                values[k] = Double.NaN;
            }
        }

        names[k++] = "Interfacial Tension Gas/Oil [N/m]";
        if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
            values[k] = fluid.getInterfacialTension("gas", "oil");
        } else {
            values[k] = Double.NaN;
        }

        names[k++] = "Interfacial Tension Gas/Aqueous [N/m]";
        if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("aqueous")) {
            values[k++] = fluid.getInterfacialTension("gas", "aqueous");
        } else {
            values[k] = Double.NaN;
        }

        names[k++] = "Interfacial Tension Oil/Aqueous [N/m]";
        if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
            values[k++] = fluid.getInterfacialTension("oil", "aqueous");
        } else {
            values[k] = Double.NaN;
        }
    }
}
