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
        st = st.trim();
        if (st.equals("CNAMES")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--")) {
              break;
            }
            names.add(st);
          }
        }
        if (st.equals("ZI")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
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
        st = st.trim();

        // System.out.println("EOS " +EOS );
        if (st.trim().equals("EOS")) {
          EOS = br.readLine().trim().replace("/", "");
          if (EOS.contains("SRK")) {
            fluid = new neqsim.thermo.system.SystemSrkEos(288.15,
                ThermodynamicConstantsInterface.referencePressure);
          } else if (EOS.contains("PR")) {
            String corr = br.readLine().trim().replace("/", "");
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
        if (st.trim().equals("CNAMES")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            names.add(st);
          }
        }
        if (st.trim().equals("TCRIT")) {
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TC.add(Double.parseDouble(st));
          }
        }
        if (st.equals("PCRIT")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PC.add(Double.parseDouble(st));
          }
        }
        if (st.equals("ACF")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ACF.add(Double.parseDouble(st));
          }
        }
        if (st.equals("MW")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            MW.add(Double.parseDouble(st));
          }
        }
        if (st.equals("TBOIL")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TBOIL.add(Double.parseDouble(st));
          }
        }
        if (st.equals("VCRIT")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            VCRIT.add(Double.parseDouble(st));
          }
        }
        if (st.equals("SSHIFT")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            SSHIFT.add(Double.parseDouble(st));
          }
        }
        if (st.equals("PARACHOR")) {
          st = st.trim();
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PARACHOR.add(Double.parseDouble(st));
          }
        }
        if (st.equals("ZI")) {
          st = st.trim();
          while ((st = br.readLine().replace("/", "")) != null) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ZI.add(Double.parseDouble(st));
          }
        }
        if (st.equals("BIC")) {
          st = st.trim();
          int addedComps = 0;
          kij = new Double[names.size()][names.size()];
          for (Double[] row : kij) {
            Arrays.fill(row, 0.0);
          }
          int lengthLastLine = 0;
          List<String> list = new ArrayList<String>();
          while ((st = br.readLine().replace("/", "")) != null && addedComps < names.size() - 1) {
            st = st.trim();
            if (st.startsWith("--") || st.isEmpty() || st.trim().startsWith("/")
                || st.trim().startsWith(" ")) {
              break;
            }

            String[] arr = st.trim().split("\\s+");
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
        if (st.trim().equals("SSHIFTS")) {
          String line;
          while ((line = br.readLine()) != null) {
            line = line.trim();
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
        } else if (name.trim().equals("H2O") || TC.get(counter) < 00.0) {
          name = "water";
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
        }
      }

      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.init(0);
      for (int i = 0; i < names.size(); i++) {
        for (int j = i; j < names.size(); j++) {
          for (int phaseNum = 0; phaseNum < fluid.getMaxNumberOfPhases(); phaseNum++) {
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getEosMixingRule()
                .setBinaryInteractionParameter(i, j, kij[i][j].doubleValue());
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getEosMixingRule()
                .setBinaryInteractionParameter(j, i, kij[i][j]);
          }
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return fluid;
  }

  /**
   * <p>
   * read.
   * </p>
   *
   * @param inputFile a {@link java.lang.String} object representing the path to the input file
   * @param fluidNames an array of {@link java.lang.String} objects representing the names of the
   *        fluids
   * @return a {@link neqsim.thermo.system.SystemInterface} object representing the thermodynamic
   *         system
   */
  public static SystemInterface read(String inputFile, String[] fluidNames) {
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
        st = st.trim();
        // System.out.println("EOS " +EOS );
        if (st.equals("EOS")) {
          st = st.trim();
          EOS = br.readLine().trim().replace("/", "");
          if (EOS.contains("SRK")) {
            fluid = new neqsim.thermo.system.SystemSrkEos(288.15,
                ThermodynamicConstantsInterface.referencePressure);
          } else if (EOS.contains("PR")) {
            String corr = br.readLine().trim().replace("/", "");
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
        if (st.trim().equals("CNAMES")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            names.add(st);
          }
        }
        if (st.trim().equals("TCRIT")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TC.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("PCRIT")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PC.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("ACF")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ACF.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("MW")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            MW.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("TBOIL")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            TBOIL.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("VCRIT")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            VCRIT.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("SSHIFT")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            SSHIFT.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("PARACHOR")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            PARACHOR.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("ZI")) {
          while ((st = br.readLine().trim().replace("/", "")) != null) {
            if (st.startsWith("--") || st.isEmpty()) {
              break;
            }
            ZI.add(Double.parseDouble(st));
          }
        }
        if (st.trim().equals("BIC")) {
          int addedComps = 0;
          kij = new Double[names.size()][names.size()];
          for (Double[] row : kij) {
            Arrays.fill(row, 0.0);
          }
          int lengthLastLine = 0;
          List<String> list = new ArrayList<String>();
          while ((st = br.readLine().trim().replace("/", "")) != null
              && addedComps < names.size() - 1) {
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
        if (st.trim().equals("SSHIFTS")) {
          String line;
          while ((line = br.readLine().trim()) != null) {
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

      for (String fluidName : fluidNames) {
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
          } else if (name.trim().equals("H2O") || TC.get(counter) < 00.0) {
            name = "water";
            fluid.addComponent(name, ZI.get(counter));
          } else if (TC.get(counter) >= 0.0) {
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

          fluid.changeComponentName(name, name + "_" + fluidName);
        }
      }

      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.init(0);

      int nCompsPerFluid = names.size(); // base number of components
      int nFluids = fluidNames.length; // number of times you replicate

      // We end up with N * nFluids total components in the fluid
      // Suppose we want to replicate the same kij block for each fluid
      // and across the same fluid. Typically you'd do:
      for (int i = 0; i < nCompsPerFluid * nFluids; i++) {
        // figure out which base component i corresponds to
        // and which fluid-block it belongs to
        int fluidIndexI = i / nCompsPerFluid;
        int baseIndexI = i % nCompsPerFluid;

        for (int j = 0; j < nCompsPerFluid * nFluids; j++) {
          int fluidIndexJ = j / nCompsPerFluid;
          int baseIndexJ = j % nCompsPerFluid;

          // Then, to replicate the original kij,
          // we just pick the old kij[baseIndexI][baseIndexJ].
          // System.out.println("i: " + i + " j: " + j);
          // System.out.println("baseIndexI: " + baseIndexI + " baseIndexJ: " +
          // baseIndexJ);
          double kijVal = kij[baseIndexI][baseIndexJ];

          // Finally set it in the fluid

          for (int phaseNum = 0; phaseNum < fluid.getMaxNumberOfPhases(); phaseNum++) {
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getEosMixingRule()
                .setBinaryInteractionParameter(i, j, kijVal);
            ((PhaseEosInterface) fluid.getPhase(phaseNum)).getEosMixingRule()
                .setBinaryInteractionParameter(j, i, kijVal);
          }
        }
      }
    } catch (

    Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return fluid;
  }

  public static SystemInterface readE300File(String inputFile) {
    neqsim.thermo.system.SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(288.15,
        ThermodynamicConstantsInterface.referencePressure);

    try (BufferedReader br = new BufferedReader(new FileReader(new File(inputFile)))) {
      String line;
      ArrayList<String> names = new ArrayList<>();
      ArrayList<Double> TC = new ArrayList<>();
      ArrayList<Double> PC = new ArrayList<>();
      ArrayList<Double> ACF = new ArrayList<>();
      ArrayList<Double> MW = new ArrayList<>();
      ArrayList<Double> ZI = new ArrayList<>();

      while ((line = br.readLine().trim()) != null) {
        line = line.trim(); // Ensure trimming of whitespace

        if (line.equals("CNAMES")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              names.add(line.substring(0, line.length() - 1).trim());
              break;
            }
            names.add(line);
          }
        } else if (line.equals("TCRIT")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              TC.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            TC.add(Double.parseDouble(line));
          }
        } else if (line.equals("PCRIT")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              PC.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            PC.add(Double.parseDouble(line));
          }
        } else if (line.equals("ACF")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              ACF.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            ACF.add(Double.parseDouble(line));
          }
        } else if (line.equals("MW")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              MW.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            MW.add(Double.parseDouble(line));
          }
        } else if (line.equals("ZI")) {
          while ((line = br.readLine().trim()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              ZI.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            ZI.add(Double.parseDouble(line));
          }
        }
      }

      // Add components to the fluid
      for (int i = 0; i < names.size(); i++) {
        String name = names.get(i);
        double zi = ZI.get(i);
        fluid.addComponent(name, zi);

        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          fluid.getPhase(phase).getComponent(name).setTC(TC.get(i));
          fluid.getPhase(phase).getComponent(name).setPC(PC.get(i));
          fluid.getPhase(phase).getComponent(name).setAcentricFactor(ACF.get(i));
          fluid.getPhase(phase).getComponent(name).setMolarMass(MW.get(i) / 1000.0);
        }
      }

      fluid.setMixingRule(2);
      fluid.useVolumeCorrection(true);
      fluid.init(0);

    } catch (Exception ex) {
      logger.error("Error reading E300 file: " + ex.getMessage(), ex);
    }

    return fluid;
  }
}
