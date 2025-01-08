package neqsim.thermo.util.readwrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * EclipseFluidReadWrite class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class EclipseFluidReadWrite {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(EclipseFluidReadWrite.class);

  /** Constant <code>pseudoName=""</code> */
  public static String pseudoName = "";

  /**
   * <p>
   * setComposition.
   * </p>
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   * @param inputFile a {@link java.lang.String} object
   * @param pseudoNameIn a {@link java.lang.String} object
   */
  public static void setComposition(SystemInterface fluid, String inputFile, String pseudoNameIn) {
    pseudoName = pseudoNameIn;
    setComposition(fluid, inputFile);
  }

  /**
   * <p>
   * setComposition.
   * </p>
   *
   * @param fluid a {@link neqsim.thermo.system.SystemInterface} object
   * @param inputFile a {@link java.lang.String} object
   */
  public static void setComposition(SystemInterface fluid, String inputFile) {
    fluid.setEmptyFluid();
    try (BufferedReader br = new BufferedReader(new FileReader(new File(inputFile)))) {
      String st;
      ArrayList<String> names = new ArrayList<String>();
      ArrayList<Double> ZI = new ArrayList<Double>();
      while ((st = br.readLine()) != null) {
        if (st.equals("CNAMES")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--")) {
              break;
            }
            names.add(st);
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
      }
      for (int counter = 0; counter < names.size(); counter++) {
        String name = names.get(counter);
        if (name.equals("C1")) {
          name = "methane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C2")) {
          name = "ethane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("N2")) {
          name = "nitrogen";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("iC4")) {
          name = "i-butane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C4")) {
          name = "n-butane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("iC5")) {
          name = "i-pentane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C5")) {
          name = "n-pentane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C6")) {
          name = "n-hexane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C3")) {
          name = "propane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("CO2")) {
          name = "CO2";
          fluid.addComponent(name, ZI.get(counter));
        } else {
          fluid.addComponent(name + pseudoName, ZI.get(counter));
        }
      }
    } catch (RuntimeException ex) {
      logger.error(ex.getMessage());
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
  }

  /**
   * <p>
   * read.
   * </p>
   *
   * @param inputFile a {@link java.lang.String} object
   * @param pseudoNameIn a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public static SystemInterface read(String inputFile, String pseudoNameIn) {
    pseudoName = pseudoNameIn;
    return read(inputFile);
  }

  /**
   * <p>
   * read.
   * </p>
   *
   * @param inputFile a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public static SystemInterface read(String inputFile) {
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(288.15,
        ThermodynamicConstantsInterface.referencePressure);

    Double[][] kij = null;
    try (BufferedReader br = new BufferedReader(new FileReader(new File(inputFile)))) {
      String st;

      ArrayList<String> names = new ArrayList<String>();
      ArrayList<Double> TC = new ArrayList<Double>();
      ArrayList<Double> PC = new ArrayList<Double>();
      ArrayList<Double> ACF = new ArrayList<Double>();
      ArrayList<Double> MW = new ArrayList<Double>();
      ArrayList<Double> SSHIFT = new ArrayList<Double>();
      ArrayList<Double> SSHIFTS = new ArrayList<Double>();
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
            fluid = new neqsim.thermo.system.SystemSrkEos(288.15,
                ThermodynamicConstantsInterface.referencePressure);
          } else if (EOS.contains("PR")) {
            String corr = br.readLine().replace("/", "");
            if (corr.equals("PRCORR")) {
              fluid = new neqsim.thermo.system.SystemPrEos1978(288.15,
                  ThermodynamicConstantsInterface.referencePressure);
            } else {
              fluid = new neqsim.thermo.system.SystemPrEos(288.15,
                  ThermodynamicConstantsInterface.referencePressure);
            }
          } else {
            fluid = new neqsim.thermo.system.SystemPrEos(288.15,
                ThermodynamicConstantsInterface.referencePressure);
          }
        }
        if (st.equals("CNAMES")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            names.add(st);
          }
        }
        if (st.equals("TCRIT")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TC.add(Double.parseDouble(st));
          }
        }
        if (st.equals("PCRIT")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PC.add(Double.parseDouble(st));
          }
        }
        if (st.equals("ACF")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ACF.add(Double.parseDouble(st));
          }
        }
        if (st.equals("MW")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            MW.add(Double.parseDouble(st));
          }
        }
        if (st.equals("TBOIL")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TBOIL.add(Double.parseDouble(st));
          }
        }
        if (st.equals("VCRIT")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            VCRIT.add(Double.parseDouble(st));
          }
        }
        if (st.equals("SSHIFT")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            SSHIFT.add(Double.parseDouble(st));
          }
        }
        if (st.equals("PARACHOR")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PARACHOR.add(Double.parseDouble(st));
          }
        }
        if (st.equals("ZI")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ZI.add(Double.parseDouble(st));
          }
        }
        if (st.equals("BIC")) {
          int addedComps = 0;
          kij = new Double[names.size()][names.size()];
          for (Double[] row : kij) {
            Arrays.fill(row, 0.0);
          }
          int lengthLastLine = 0;
          List<String> list = new ArrayList<String>();
          while ((st = br.readLine().replace("/", "")) != null && addedComps < names.size() - 1) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            String[] arr = st.split("  ");
            List<String> templist = new ArrayList<String>(Arrays.asList(arr));
            list.addAll(templist);
            list.removeAll(Arrays.asList("", null));
            if (lengthLastLine >= list.size()) {
              continue;
            }
            lengthLastLine = list.size();
            for (int i = 0; i < list.size(); i++) {
              BIC.add(Double.parseDouble(list.get(i)));
              kij[i][addedComps + 1] = Double.parseDouble(list.get(i));
              kij[addedComps + 1][i] = kij[i][addedComps + 1];
            }
            addedComps++;
            list.clear();
          }
        }
        if (st.equals("SSHIFTS")) {
          String line;
          while ((line = br.readLine()) != null) {
            st = line.replace("/", "");
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            try {
              SSHIFTS.add(Double.parseDouble(st));
            } catch (NumberFormatException e) {
              System.out.println("Error parsing double value: " + e.getMessage());
            }
          }
        }
      }
      for (int counter = 0; counter < names.size(); counter++) {
        String name = names.get(counter);
        if (name.equals("C1") || TC.get(counter) < 00.0) {
          name = "methane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C2") || TC.get(counter) < 00.0) {
          name = "ethane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("N2") || TC.get(counter) < 00.0) {
          name = "nitrogen";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("iC4") || TC.get(counter) < 00.0) {
          name = "i-butane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C4") || TC.get(counter) < 00.0) {
          name = "n-butane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("iC5") || TC.get(counter) < 00.0) {
          name = "i-pentane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C5") || TC.get(counter) < 00.0) {
          name = "n-pentane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C6") || TC.get(counter) < 00.0) {
          name = "n-hexane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("C3") || TC.get(counter) < 00.0) {
          name = "propane";
          fluid.addComponent(name, ZI.get(counter));
        } else if (name.equals("CO2") || TC.get(counter) < 00.0) {
          name = "CO2";
          fluid.addComponent(name, ZI.get(counter));
        } else if (TC.get(counter) >= 00.0) {
          name = names.get(counter);
          Double stddensity = 0.5046 * MW.get(counter) / 1000.0 + 0.668468;
          fluid.addTBPfraction(name, ZI.get(counter), MW.get(counter) / 1000.0, stddensity);
          name = name + "_PC";
        } else {
          name = names.get(counter);
          Double stddensity = 0.5046 * MW.get(counter) / 1000.0 + 0.668468;
          fluid.addTBPfraction(name, ZI.get(counter), MW.get(counter) / 1000.0, stddensity);
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
          fluid.getPhase(i).getComponent(name).setParachorParameter(PARACHOR.get(counter));
          if (SSHIFTS.size() > 0) {
            fluid.getPhase(i).getComponent(name).setVolumeCorrectionConst(SSHIFTS.get(counter));
          } else {
            fluid.getPhase(i).getComponent(name).setVolumeCorrectionConst(SSHIFT.get(counter));
          }
          fluid.getPhase(i).getComponent(name).setRacketZ(0.29056 - 0.08775 * ACF.get(counter));
        }
        if (fluid.getPhase(0).getComponent(name).isIsTBPfraction()) {
          fluid.changeComponentName(name, names.get(counter).replaceAll("_PC", "") + pseudoName);
        } else {
          // fluid.changeComponentName(name, names.get(counter));
        }
      }

      // System.out.println(st);
      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.init(0);
      for (int i = 0; i < names.size(); i++) {
        for (int j = i; j < names.size(); j++) {
          for (int phaseNum = 0; phaseNum < fluid.getMaxNumberOfPhases(); phaseNum++) {
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getMixingRule()
                .setBinaryInteractionParameter(i, j, kij[i][j].doubleValue());
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getMixingRule()
                .setBinaryInteractionParameter(j, i, kij[i][j]);
          }
        }
      }

      // fluid.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return fluid;
  }
}
