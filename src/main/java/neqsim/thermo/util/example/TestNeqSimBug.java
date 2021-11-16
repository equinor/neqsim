package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class TestNeqSimBug {
        public static void main(String[] args) {
                String[] phaseName = {"gas", "oil", "aqueous"};
                int phaseNumber;

                double[][] fluidProperties = new double[3][67];

                double[] spec1 = {73.22862045673597}; // grane Pressure
                double[] spec2 = {-62179.7247076579}; // Enthalpy

                // double[] spec1 = {1.0}; // salt water Pressure
                // double[] spec2 = { -39678.555}; // salt water Enthalpy

                SystemInterface fluid = new SystemSrkEos(273.15 + 55.0, 10.0);
                ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);
                // fluid.addComponent("seawater", 1.0);

                /*
                 * fluid.addComponent("methane", 1.0); fluid.addComponent("nC10", 1.0);
                 * fluid.addComponent("water", 1.0);
                 */

                fluid.addComponent("water", 0.0386243104934692);
                fluid.addComponent("nitrogen", 1.08263303991407E-05);
                fluid.addComponent("CO2", 0.00019008457660675);
                fluid.addComponent("methane", 0.00305547803640366);
                fluid.addComponent("ethane", 0.00200786963105202);
                fluid.addComponent("propane", 0.00389420658349991);
                fluid.addComponent("i-butane", 0.00179276615381241);
                fluid.addComponent("n-butane", 0.00255768150091171);
                fluid.addComponent("i-pentane", 0.00205287128686905);
                fluid.addComponent("n-pentane", 0.00117853358387947);
                fluid.addTBPfraction("CHCmp_1", 0.000867870151996613, 0.0810000000000000,
                                0.72122997045517);
                fluid.addTBPfraction("CHCmp_2", 0.048198757171630900, 0.0987799987792969,
                                0.754330039024353);
                fluid.addTBPfraction("CHCmp_3", 0.097208471298217800, 0.1412200012207030,
                                0.81659996509552);
                fluid.addTBPfraction("CHCmp_4", 0.165174083709717000, 0.1857899932861330,
                                0.861050009727478);
                fluid.addTBPfraction("CHCmp_5", 0.279571933746338000, 0.2410899963378910,
                                0.902539968490601);
                fluid.addTBPfraction("CHCmp_6", 0.240494251251221000, 0.4045100097656250,
                                0.955269992351531);
                fluid.addTBPfraction("CHCmp_7", 0.113120021820068000, 0.9069699707031250,
                                1.0074599981308);

                fluid.createDatabase(true);
                fluid.setMixingRule(2);
                fluid.useVolumeCorrection(true);
                fluid.setMultiPhaseCheck(true);

                for (int t = 0; t < 1; t++) {
                        fluid.setPressure(spec1[t]);
                        fluidOps.PHflash(spec2[t], "J/mol");
                        // fluidOps.TPflash();
                        fluid.init(2);
                        fluid.initPhysicalProperties();

                        int k = 0;
                        fluidProperties[t][k++] = fluid.getNumberOfPhases(); // Mix Number of Phases
                        fluidProperties[t][k++] = fluid.getPressure("Pa"); // Mix Pressure [Pa]
                        fluidProperties[t][k++] = fluid.getTemperature("K"); // Mix Temperature [K]
                        fluidProperties[t][k++] = fluid.getNumberOfMoles() * 100; // Mix Mole
                                                                                  // Percent
                        fluidProperties[t][k++] = 100.0; // Mix Weight Percent
                        fluidProperties[t][k++] = 1.0 / fluid.getDensity("mol/m3"); // Mix Molar
                                                                                    // Volume
                                                                                    // [m3/mol]
                        fluidProperties[t][k++] = 100.0; // Mix Volume Percent
                        fluidProperties[t][k++] = fluid.getDensity("kg/m3"); // Mix Density [kg/m3]
                        fluidProperties[t][k++] = fluid.getZ(); // Mix Z Factor
                        fluidProperties[t][k++] = fluid.getMolarMass() * 1000; // Mix Molecular
                                                                               // Weight [g/mol]
                        // fluidProperties[t][k++] = fluid.getEnthalpy()/fluid.getNumberOfMoles();

                        // Mix Enthalpy [J/mol]
                        fluidProperties[t][k++] = fluid.getEnthalpy("J/mol");
                        // fluidProperties[t][k++] = fluid.getEntropy()/fluid.getNumberOfMoles();
                        // Mix
                        // Entropy [J/molK]
                        fluidProperties[t][k++] = fluid.getEntropy("J/molK");
                        fluidProperties[t][k++] = fluid.getCp("J/molK"); // Mix Heat Capacity-Cp
                                                                         // [J/molK]
                        fluidProperties[t][k++] = fluid.getCv("J/molK");// Mix Heat Capacity-Cv
                                                                        // [J/molK]
                        // fluidProperties[t][k++] = fluid.Cp()/fluid.getCv();// Mix Kappa (Cp/Cv)
                        fluidProperties[t][k++] = fluid.getKappa();// Mix Kappa (Cp/Cv)
                        fluidProperties[t][k++] = Double.NaN; // Mix JT Coefficient [K/Pa]
                        fluidProperties[t][k++] = Double.NaN; // Mix Velocity of Sound [m/s]
                        fluidProperties[t][k++] = fluid.getViscosity("kg/msec"); // Mix Viscosity
                                                                                 // [Pa s] or
                                                                                 // [kg/(m*s)]
                        fluidProperties[t][k++] = fluid.getThermalConductivity("W/mK"); // Mix
                                                                                        // Thermal
                                                                                        // Conductivity
                                                                                        // [W/mK]
                        // fluidProperties[t][0] = fluid.getInterfacialTension("gas","oil");
                        // Surface
                        // Tension(N/m) between gas and oil phase** NOT USED
                        // fluidProperties[t][0] = fluid.getInterfacialTension("gas","aqueous");
                        // Surface Tension(N/m) between gas and aqueous phase** NOT USED
                        // Phase properties (phase: gas=0, liquid=1, Aqueous=2)
                        for (int j = 0; j < 3; j++) {
                                if (fluid.hasPhaseType(phaseName[j])) {
                                        phaseNumber = fluid.getPhaseNumberOfPhase(phaseName[j]);
                                        fluidProperties[t][k++] =
                                                        fluid.getMoleFraction(phaseNumber) * 100; // Phase
                                                                                                  // Mole
                                                                                                  // Percent
                                        fluidProperties[t][k++] =
                                                        fluid.getWtFraction(phaseNumber) * 100; // Phase
                                                                                                // Weight
                                                                                                // Percent
                                        fluidProperties[t][k++] = 1.0 / fluid.getPhase(phaseNumber)
                                                        .getDensity("mol/m3"); // Phase Molar
                                                                               // Volume
                                                                               // [m3/mol]
                                        fluidProperties[t][k++] = fluid.getCorrectedVolumeFraction(
                                                        phaseNumber) * 100;// Phase
                                                                           // Volume
                                                                           // Percent
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getDensity("kg/m3"); // Phase
                                                                              // Density
                                                                              // [kg/m3]
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getZ(); // Phase
                                                                                            // Z
                                                                                            // Factor
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getMolarMass()
                                                                        * 1000; // Phase
                                                                                // Molecular
                                                                                // Weight
                                                                                // [g/mol]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getEnthalpy() /
                                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase();
                                        // Phase Enthalpy
                                        // [J/mol]
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getEnthalpy("J/mol"); // Phase
                                                                               // Enthalpy
                                                                               // [J/mol]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getEntropy() /
                                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase();
                                        // Phase Entropy
                                        // [J/molK]
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getEntropy("J/molK"); // Phase
                                                                               // Entropy
                                                                               // [J/molK]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getCp() /
                                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase();
                                        // Phase Heat
                                        // Capacity-Cp [J/molK]
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getCp("J/molK"); // Phase
                                                                                                     // Heat
                                                                                                     // Capacity-Cp
                                                                                                     // [J/molK]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getCv() /
                                        // fluid.getPhase(phaseNumber).getNumberOfMolesInPhase();
                                        // Phase Heat
                                        // Capacity-Cv [J/molK]
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getCv("J/molK"); // Phase
                                                                                                     // Heat
                                                                                                     // Capacity-Cv
                                                                                                     // [J/molK]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getCp() /
                                        // fluid.getPhase(phaseNumber).getCv(); // Phase Kappa
                                        // (Cp/Cv)
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getKappa(); // Phase
                                                                                                // Kappa
                                                                                                // (Cp/Cv)
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getJouleThomsonCoefficient() / 1e5; // Phase
                                                                                             // JT
                                                                                             // Coefficient
                                                                                             // [K/Pa]
                                        fluidProperties[t][k++] =
                                                        fluid.getPhase(phaseNumber).getSoundSpeed(); // Phase
                                                                                                     // Velocity
                                                                                                     // of
                                                                                                     // Sound
                                                                                                     // [m/s]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getPhysicalProperties().getViscosity();
                                        // Phase
                                        // Viscosity [Pa s]
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getViscosity("kg/msec");// Phase
                                                                                 // Viscosity
                                                                                 // [Pa
                                                                                 // s]
                                                                                 // or
                                                                                 // [kg/msec]
                                        // fluidProperties[t][k++] =
                                        // fluid.getPhase(phaseNumber).getPhysicalProperties().getConductivity();

                                        // Phase Thermal Conductivity [W/mK]
                                        fluidProperties[t][k++] = fluid.getPhase(phaseNumber)
                                                        .getConductivity("W/mK"); // Phase
                                                                                  // Thermal
                                                                                  // Conductivity
                                                                                  // [W/mK]
                                        // Phase Surface Tension(N/m) ** NOT USED
                                } else {
                                        fluidProperties[t][k++] = Double.NaN; // Phase Mole Percent
                                        fluidProperties[t][k++] = Double.NaN; // Phase Weight
                                                                              // Percent
                                        fluidProperties[t][k++] = Double.NaN; // Phase Molar Volume
                                                                              // [m3/mol]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Volume
                                                                              // Percent
                                        fluidProperties[t][k++] = Double.NaN; // Phase Density
                                                                              // [kg/m3]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Z Factor
                                        fluidProperties[t][k++] = Double.NaN; // Phase Molecular
                                                                              // Weight [g/mol]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Enthalpy
                                                                              // [J/mol]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Entropy
                                                                              // [J/molK]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Heat
                                                                              // Capacity-Cp
                                                                              // [J/molK]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Heat
                                                                              // Capacity-Cv
                                                                              // [J/molK]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Kappa (Cp/Cv)
                                        fluidProperties[t][k++] = Double.NaN; // Phase JT
                                                                              // Coefficient K/Pa]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Velocity of
                                                                              // Sound [m/s]
                                        fluidProperties[t][k++] = Double.NaN;// Phase Viscosity [Pa
                                                                             // s]
                                        fluidProperties[t][k++] = Double.NaN; // Phase Thermal
                                                                              // Conductivity [W/mK]
                                }
                        }
                }

                int t = 0;
                int k = 0;
                System.out.printf("Mix Number of Phases                      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Pressure [Pa]                         %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Temperature [K]                       %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Mole Percent                          %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Weight Percent                        %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Molar Volume               [m3/mol]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Volume Percent                        %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Density                    [kg/m3]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Z Factor                              %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Molecular Weight           [g/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Enthalpy                   [J/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Entropy                    [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Heat Capacity-Cp           [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Heat Capacity-Cv           [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Mix Kappa (Cp/Cv)                     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix JT Coefficient             [K/Pa]     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Velocity of Sound          [m/s]      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Viscosity [Pa s] eller     [kg/(m*s)] %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Mix Thermal Conductivity        W/mK]     %.10f\n",
                                fluidProperties[t][k++]);
                // System.out.printf("\n");
                System.out.printf("Gas Mole Percent                          %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Weight Percent                        %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Molar Volume               [m3/mol]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Volume Percent                        %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Density                    [kg/m3]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Z Factor                              %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Molecular Weight           [g/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Enthalpy                   [J/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Entropy                    [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Heat Capacity-Cp           [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Heat Capacity-Cv           [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Gas Kappa (Cp/Cv)                     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas JT Coefficient             [K/Pa]     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Velocity of Sound          [m/s]      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Viscosity [Pa s] eller     [kg/(m*s)] %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Gas Thermal Conductivity       [W/mK]     %.10f\n",
                                fluidProperties[t][k++]);
                // System.out.printf("\n");
                System.out.printf("Liquid Mole Percent                       %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Weight Percent                     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Molar Volume            [m3/mol]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Volume Percent                     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Density                 [kg/m3]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Z Factor                           %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Molecular Weight        [g/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Enthalpy                [J/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Entropy                 [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Heat Capacity-Cp        [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Heat Capacity-Cv        [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Liquid Kappa (Cp/Cv)               %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid JT Coefficient          [K/Pa]     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Velocity of Sound       [m/s]      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Viscosity [Pa s] eller  [kg/(m*s)] %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Liquid Thermal Conductivity    [W/mK]     %.10f\n",
                                fluidProperties[t][k++]);
                // System.out.printf("\n");
                System.out.printf("Aqueous Mole Percent                      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Weight Percent                    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Molar Volume           [m3/mol]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Volume Percent                    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Density                [kg/m3]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Z Factor                          %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Molecular Weight       [g/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Enthalpy               [J/mol]    %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Entropy                [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Heat Capacity-Cp       [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Heat Capacity-Cv       [J/molK]   %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Aqueous Kappa (Cp/Cv)             %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous JT Coefficient         [K/Pa]     %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Velocity of Sound      [m/s]      %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Viscosity [Pa s] eller [kg/(m*s)] %.10f\n",
                                fluidProperties[t][k++]);
                System.out.printf("Aqueous Thermal Conductivity   [W/mK]     %.10f\n",
                                fluidProperties[t][k++]);

                fluid.display();
        }
}
