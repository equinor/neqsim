
package neqsim.api.ioc;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * 
 * @author jo.lyshoel
 */
public class NeqSim {

    private static final Logger LOGGER = LogManager.getLogger(NeqSim.class.getName());
    private static final String[] phaseName = {"gas", "oil", "aqueous"};;

    public CalculationResult doCalculation(CalcRequest req) throws NeqSimException {
        Double[][] fluidProperties = new Double[req.Sp1.size()][70]; // 70 cols
        String[] calculationError = new String[req.Sp1.size()];

        SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 22.0);
        NeqSimFluidManager.addComponents(req.fn, fluid);
        ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);

        if (req.isStaticFractions() && req.fractions != null && !req.fractions.isEmpty()) {
            fluid.setMolarComposition(NeqSimFluidManager.getPreparedFractions(req.fn,
                    req.components != null ? req.components.toArray(new String[0]) : null,
                    req.getFractionsAsArray(), false));
        }

        for (int t = 0; t < req.Sp1.size(); t++) {
            int k = 0;
            try {
                Double Sp1 = req.Sp1.get(t);
                Double Sp2 = req.Sp2.get(t);

                if (Sp1 == null || Sp2 == null || Double.isNaN(Sp1) || Double.isNaN(Sp2)) {
                    calculationError[t] = "Sp1 or Sp2 is NaN";
                    LOGGER.info("Sp1 or Sp2 is NULL for datapoint {}", t);
                    continue;
                }

                if (req.isOnlineFractions()) {
                    req.validateOnlineFractions(t);

                    fluid.setMolarComposition(NeqSimFluidManager.getPreparedFractions(req.fn,
                            req.components != null ? req.components.toArray(new String[0]) : null,
                            req.getOnlineFractionsAsArray(t), true));
                }

                double pressureInPa = Sp1 / 1e5;
                fluid.setPressure(pressureInPa);

                if (req.FlashMode == 1) {
                    fluid.setTemperature(Sp2);
                    fluidOps.TPflash();
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                } else if (req.FlashMode == 2) {
                    fluidOps.PHflash(Sp2, "J/mol");
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                } else if (req.FlashMode == 3) {
                    fluidOps.PSflash(Sp2, "J/molK");
                    fluid.init(2);
                    fluid.initPhysicalProperties();
                }

                int numberOfMole = Math.round((float) fluid.getNumberOfMoles());

                if (numberOfMole != 1) {
                    calculationError[t] = "Number of moles is " + fluid.getNumberOfMoles()
                            + " and not 1. Check input fragments.";
                    LOGGER.info("Number of moles is " + fluid.getNumberOfMoles()
                            + " and not 1. Check input fragments.", t);
                    continue;
                }

                fluidProperties[t][k++] = (double) fluid.getNumberOfPhases(); // Mix Number of
                                                                              // Phases
                fluidProperties[t][k++] = fluid.getPressure("Pa"); // Mix Pressure [Pa]
                fluidProperties[t][k++] = fluid.getTemperature("K"); // Mix Temperature [K]
                fluidProperties[t][k++] = fluid.getNumberOfMoles() * 100; // Mix Mole Percent
                fluidProperties[t][k++] = 100.0; // Mix Weight Percent
                fluidProperties[t][k++] = 1.0 / fluid.getDensity("mol/m3"); // Mix Molar Volume
                                                                            // [m3/mol]
                fluidProperties[t][k++] = 100.0; // Mix Volume Percent
                fluidProperties[t][k++] = fluid.getDensity("kg/m3"); // Mix Density [kg/m3]
                fluidProperties[t][k++] = fluid.getZ(); // Mix Z Factor
                fluidProperties[t][k++] = fluid.getMolarMass() * 1000; // Mix Molecular Weight
                                                                       // [g/mol]
                // fluidProperties[t][k++] = fluid.getEnthalpy()/fluid.getNumberOfMoles(); // Mix
                // Enthalpy [J/mol]
                fluidProperties[t][k++] = fluid.getEnthalpy("J/mol");
                // fluidProperties[t][k++] = fluid.getEntropy()/fluid.getNumberOfMoles(); // Mix
                // Entropy [J/molK]
                fluidProperties[t][k++] = fluid.getEntropy("J/molK");
                fluidProperties[t][k++] = fluid.getCp("J/molK"); // Mix Heat Capacity-Cp [J/molK]
                fluidProperties[t][k++] = fluid.getCv("J/molK");// Mix Heat Capacity-Cv [J/molK]
                // fluidProperties[t][k++] = fluid.getCp()/fluid.getCv();// Mix Gamma (Cp/Cv)
                fluidProperties[t][k++] = fluid.getGamma();// Mix Gamma (Cp/Cv)
                fluidProperties[t][k++] = Double.NaN; // Mix JT Coefficient [K/Pa]
                fluidProperties[t][k++] = Double.NaN; // Mix Velocity of Sound [m/s]
                fluidProperties[t][k++] = fluid.getViscosity("kg/msec"); // Mix Viscosity [Pa s] or
                                                                         // [kg/(m*s)]
                fluidProperties[t][k++] = fluid.getThermalConductivity("W/mK"); // Mix Thermal
                                                                                // Conductivity
                // [W/mK]
                // fluidProperties[t][0] = fluid.getInterfacialTension("gas","oil"); // Surface
                // Tension(N/m) between gas and oil phase** NOT USED
                // fluidProperties[t][0] = fluid.getInterfacialTension("gas","aqueous"); // Surface
                // Tension(N/m) between gas and aqueous phase** NOT USED

                // Phase properties (phase: gas=0, liquid=1, Aqueous=2)
                for (int j = 0; j < 3; j++) {
                    if (fluid.hasPhaseType(phaseName[j])) {
                        int phaseNumber = fluid.getPhaseNumberOfPhase(phaseName[j]);
                        fluidProperties[t][k++] = fluid.getMoleFraction(phaseNumber) * 100; // Phase
                                                                                            // Mole
                                                                                            // Percent
                        fluidProperties[t][k++] = fluid.getWtFraction(phaseNumber) * 100; // Phase
                                                                                          // Weight
                                                                                          // Percent
                        fluidProperties[t][k++] =
                                1.0 / fluid.getPhase(phaseNumber).getDensity("mol/m3"); // Phase
                                                                                        // Molar
                                                                                        // Volume
                                                                                        // [m3/mol]
                        fluidProperties[t][k++] =
                                fluid.getCorrectedVolumeFraction(phaseNumber) * 100;// Phase Volume
                                                                                    // Percent
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getDensity("kg/m3"); // Phase
                                                                                                   // Density
                                                                                                   // [kg/m3]

                        if (Objects.equals(phaseName[j], "oil")
                                || Objects.equals(phaseName[j], "aqueous")) {
                            // Phase doesn't calculate correct result for these properties. See
                            // specs
                            fluidProperties[t][k++] = Double.NaN; // Phase Z Factor
                        } else {
                            fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getZ(); // Phase Z
                                                                                          // Factor
                        }

                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getMolarMass() * 1000; // Phase
                                                                                                     // Molecular
                                                                                                     // Weight
                                                                                                     // [g/mol]
                        // fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getEnthalpy() /
                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase(); // Phase Enthalpy
                        // [J/mol]
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getEnthalpy("J/mol"); // Phase
                                                                                                    // Enthalpy
                                                                                                    // [J/mol]
                        // fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getEntropy() /
                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase(); // Phase Entropy
                        // [J/molK]
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getEntropy("J/molK"); // Phase
                                                                                                    // Entropy
                                                                                                    // [J/molK]
                        // fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getCp() /
                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase(); // Phase Heat
                        // Capacity-Cp [J/molK]
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getCp("J/molK"); // Phase
                                                                                               // Heat
                                                                                               // Capacity-Cp
                                                                                               // [J/molK]
                        // fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getCv() /
                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase(); // Phase Heat
                        // Capacity-Cv [J/molK]
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getCv("J/molK"); // Phase
                                                                                               // Heat
                                                                                               // Capacity-Cv
                                                                                               // [J/molK]
                        // fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getCp() /
                        // fluid.getPhase(phaseNumber).getCv(); // Phase Kappa (Cp/Cv)
                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getGamma(); // Phase
                                                                                          // Gamma
                                                                                          // (Cp/Cv)

                        if (Objects.equals(phaseName[j], "oil")
                                || Objects.equals(phaseName[j], "aqueous")) {
                            // Phase doesn't calculate correct result for these properties. See
                            // specs
                            fluidProperties[t][k++] = Double.NaN; // Phase JT Coefficient [K/Pa]
                            fluidProperties[t][k++] = Double.NaN; // Phase Velocity of Sound [m/s]
                        } else {
                            fluidProperties[t][k++] =
                                    fluid.getPhase(phaseNumber).getJouleThomsonCoefficient() / 1e5; // Phase
                                                                                                    // JT
                                                                                                    // Coefficient
                                                                                                    // [K/Pa]
                            fluidProperties[t][k++] = fluid.getPhase(phaseNumber).getSoundSpeed(); // Phase
                                                                                                   // Velocity
                                                                                                   // of
                                                                                                   // Sound
                                                                                                   // [m/s]
                        }

                        // fluidProperties[t][k++] =
                        // fluid.getPhase(phaseNumber).getPhysicalProperties().getViscosity();//
                        // Phase Viscosity [Pa s]
                        fluidProperties[t][k++] =
                                fluid.getPhase(phaseNumber).getViscosity("kg/msec");// Phase
                                                                                    // Viscosity [Pa
                                                                                    // s] or
                                                                                    // [kg/msec]
                        // fluidProperties[t][k++] =
                        // fluid.getPhase(phaseNumber).getPhysicalProperties().getConductivity(); //
                        // Phase Thermal Conductivity [W/mK]
                        fluidProperties[t][k++] =
                                fluid.getPhase(phaseNumber).getConductivity("W/mK"); // Phase
                                                                                     // Thermal
                                                                                     // Conductivity
                                                                                     // [W/mK]
                        // Phase Surface Tension(N/m) ** NOT USED
                    } else {
                        fluidProperties[t][k++] = Double.NaN; // Phase Mole Percent
                        fluidProperties[t][k++] = Double.NaN; // Phase Weight Percent
                        fluidProperties[t][k++] = Double.NaN; // Phase Molar Volume [m3/mol]
                        fluidProperties[t][k++] = Double.NaN; // Phase Volume Percent
                        fluidProperties[t][k++] = Double.NaN; // Phase Density [kg/m3]
                        fluidProperties[t][k++] = Double.NaN; // Phase Z Factor
                        fluidProperties[t][k++] = Double.NaN; // Phase Molecular Weight [g/mol]
                        fluidProperties[t][k++] = Double.NaN; // Phase Enthalpy [J/mol]
                        fluidProperties[t][k++] = Double.NaN; // Phase Entropy [J/molK]
                        fluidProperties[t][k++] = Double.NaN; // Phase Heat Capacity-Cp [J/molK]
                        fluidProperties[t][k++] = Double.NaN; // Phase Heat Capacity-Cv [J/molK]
                        fluidProperties[t][k++] = Double.NaN; // Phase Kappa (Cp/Cv)
                        fluidProperties[t][k++] = Double.NaN; // Phase JT Coefficient K/Pa]
                        fluidProperties[t][k++] = Double.NaN; // Phase Velocity of Sound [m/s]
                        fluidProperties[t][k++] = Double.NaN;// Phase Viscosity [Pa s]
                        fluidProperties[t][k++] = Double.NaN; // Phase Thermal Conductivity [W/mK]
                    }
                }
                if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil")) {
                    fluidProperties[t][k++] = fluid.getInterfacialTension("gas", "oil"); // Interfacial
                                                                                         // Tension
                                                                                         // Gas/Oil
                                                                                         // [N/m]
                } else {
                    fluidProperties[t][k++] = Double.NaN;
                }
                if (fluid.hasPhaseType("gas") && fluid.hasPhaseType("aqueous")) {
                    fluidProperties[t][k++] = fluid.getInterfacialTension("gas", "aqueous"); // Interfacial
                                                                                             // Tension
                                                                                             // Gas/Aqueous
                                                                                             // [N/m]
                } else {
                    fluidProperties[t][k++] = Double.NaN;
                }
                if (fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous")) {
                    fluidProperties[t][k++] = fluid.getInterfacialTension("oil", "aqueous"); // Interfacial
                                                                                             // Tension
                                                                                             // Oil/Aqueous
                                                                                             // [N/m]
                } else {
                    fluidProperties[t][k++] = Double.NaN;
                }
            } catch (Exception ex) {
                calculationError[t] = ex.getMessage();
                LOGGER.warn("Single calculation failed", ex);
            }
        }

        return new CalculationResult(fluidProperties, calculationError);
    }
}
