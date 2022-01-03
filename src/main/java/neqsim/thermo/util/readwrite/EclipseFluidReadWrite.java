package neqsim.thermo.util.readwrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>EclipseFluidReadWrite class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EclipseFluidReadWrite {
    static Logger logger = LogManager.getLogger(EclipseFluidReadWrite.class);

    /**
     * <p>read.</p>
     *
     * @param inputFile a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public static SystemInterface read(String inputFile) {
        neqsim.thermo.system.SystemInterface fluid =
                new neqsim.thermo.system.SystemSrkEos(288.15, 1.01325);

        double[][] kij = null;
        try {
            File file = new File(inputFile);
            BufferedReader br = new BufferedReader(new FileReader(file));
            String st;

            ArrayList<String> names = new ArrayList<String>();
            ArrayList<Double> TC = new ArrayList<Double>();
            ArrayList<Double> PC = new ArrayList<Double>();
            ArrayList<Double> ACF = new ArrayList<Double>();
            ArrayList<Double> MW = new ArrayList<Double>();
            ArrayList<Double> SSHIFT = new ArrayList<Double>();
            ArrayList<Double> TBOIL = new ArrayList<Double>();
            ArrayList<Double> VCRIT = new ArrayList<Double>();
            ArrayList<Double> PARACHOR = new ArrayList<Double>();
            ArrayList<Double> ZI = new ArrayList<Double>();
            ArrayList<Double> BIC = new ArrayList<Double>();
            String EOS;

            while ((st = br.readLine()) != null) {
                // System.out.println("EOS " +EOS );
                if (st.trim().equals("EOS")) {
                    EOS = br.readLine().replace("/", "");
                    if (EOS.contains("SRK")) {
                        fluid = new neqsim.thermo.system.SystemSrkEos(288.15, 100.01325);
                    } else if (EOS.contains("PR")) {
                        fluid = new neqsim.thermo.system.SystemPrEos(288.15, 1.01325);
                    } else if (EOS.contains("PR78")) {
                        fluid = new neqsim.thermo.system.SystemPrEos1978(288.15, 1.01325);
                    }
                }
                if (st.equals("CNAMES")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        names.add(st);
                    }
                }
                if (st.equals("TCRIT")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        TC.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("PCRIT")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        PC.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("ACF")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        ACF.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("MW")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        MW.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("TBOIL")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        TBOIL.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("VCRIT")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        VCRIT.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("SSHIFT")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        SSHIFT.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("PARACHOR")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        PARACHOR.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("ZI")) {
                    while ((st = br.readLine().replace("/", "")) != null) {
                        if (st.startsWith("--")) {
                            break;
                        }
                        ZI.add(Double.parseDouble(st));
                    }
                }
                if (st.equals("BIC")) {
                    int numb = 0;
                    // kij = new double[ZI.size()][ZI.size()];
                    kij = new double[names.size()][names.size()];
                    while ((st = br.readLine().replace("/", "")) != null) {
                        numb++;
                        if (st.startsWith("--")) {
                            break;
                        }

                        // String[] arr = st.replace(" ","").split(" ");
                        String[] arr = st.split("  ");
                        if (arr.length == 1) {
                            break;
                        }
                        List<String> list = Arrays.asList(arr);
                        for (int i = 0; i < arr.length - 1; i++) {
                            BIC.add(Double.parseDouble(arr[i + 1]));
                            kij[numb][i] = Double.parseDouble(arr[i + 1]);
                            kij[i][numb] = kij[numb][i];
                            // kij[numb-1][i] = Double.parseDouble(arr[i+1]);
                            // kij[i][numb-1] = kij[numb-1][i] ;
                        }
                        // numb++;
                        Double.parseDouble(arr[1]);
                        // System.out.println(list.size());
                        // System.out.println(st);
                        // BIC.add(Double.parseDouble(st));
                    }

                    /*
                     * numb =0;
                     * 
                     * for (int i = 0; i < names.size(); i++) { for (int j = i; j < names.size();
                     * j++) { if(i==j) continue; //System.out.println("ij " + i + " " + j+ " " +
                     * BIC.get(numb)); System.out.println("ij " + i + " " + j+ " " + kij[i][j] );
                     * //kij[i][j] = BIC.get(numb); //kij[j][i] = kij[i][j]; numb++; } }
                     */
                }
            }
            for (int counter = 0; counter < names.size(); counter++) {
                String name = "methane";
                if (name.equals("C1") || TC.get(counter) < 00.0) {
                    name = "methane";
                    fluid.addComponent(name, ZI.get(counter));
                } else if (name.equals("C2") || TC.get(counter) < 00.0) {
                    name = "ethane";
                    fluid.addComponent(name, ZI.get(counter));
                } else if (name.equals("C3") || TC.get(counter) < 00.0) {
                    name = "propane";
                    fluid.addComponent(name, ZI.get(counter));
                } else if (name.equals("CO2") || TC.get(counter) < 00.0) {
                    name = "CO2";
                    fluid.addComponent(name, ZI.get(counter));
                } else if (TC.get(counter) >= 00.0) {
                    name = names.get(counter);
                    fluid.addTBPfraction(name, ZI.get(counter), MW.get(counter) / 1000.0, 0.9);
                    name = name + "_PC";
                } else {
                    name = names.get(counter);
                    fluid.addTBPfraction(name, ZI.get(counter), MW.get(counter) / 1000.0, 0.9);
                    name = name + "_PC";
                    // fluid.changeComponentName(name+"_PC", names.get(counter));
                }
                // fluid.addComponent(name, ZI.get(counter));
                for (int i = 0; i < fluid.getMaxNumberOfPhases(); i++) {
                    fluid.getPhase(i).getComponent(name).setTC(TC.get(counter));
                    fluid.getPhase(i).getComponent(name).setPC(PC.get(counter));
                    fluid.getPhase(i).getComponent(name).setAcentricFactor(ACF.get(counter));
                    fluid.getPhase(i).getComponent(name).setMolarMass(MW.get(counter) / 1000.0);
                    fluid.getPhase(i).getComponent(name).setNormalBoilingPoint(TBOIL.get(counter));
                    fluid.getPhase(i).getComponent(name).setCriticalVolume(VCRIT.get(counter));
                    fluid.getPhase(i).getComponent(name)
                            .setParachorParameter(PARACHOR.get(counter));
                    fluid.getPhase(i).getComponent(name)
                            .setVolumeCorrectionConst(SSHIFT.get(counter));
                }
                fluid.changeComponentName(name, names.get(counter));

            }
            // System.out.println(st);
            fluid.setMixingRule(2);
            fluid.useVolumeCorrection(true);
            fluid.init(0);
            for (int i = 0; i < names.size(); i++) {
                for (int j = i; j < names.size(); j++) {
                    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
                        ((PhaseEosInterface) fluid.getPhase(phase)).getMixingRule()
                                .setBinaryInteractionParameter(i, j, kij[i][j]);
                        ((PhaseEosInterface) fluid.getPhase(phase)).getMixingRule()
                                .setBinaryInteractionParameter(j, i, kij[i][j]);
                    }
                }
            }

            // fluid.display();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fluid;
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     * @throws java.lang.Exception if any.
     */
    public static void main(String[] args) throws Exception {
        neqsim.thermo.system.SystemInterface fluid = EclipseFluidReadWrite.read(
                "C:\\\\Users\\\\esol\\\\OneDrive - Equinor\\\\programming\\\\neqsim\\\\src\\\\main\\\\java\\\\neqsim\\\\thermo\\\\util\\\\readwrite\\\\examplefileMet.txt");

        ThermodynamicOperations flash = new ThermodynamicOperations(fluid);// System.out.println(st);
        flash.TPflash();
        fluid.display();
        // neqsim.thermo.ThermodynamicModelTest testModel =
        // new neqsim.thermo.ThermodynamicModelTest(fluid);
        // testModel.runTest();
    }

}
