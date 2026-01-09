package neqsim.thermo.util.readwrite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
      double[] composition = ZI.stream().mapToDouble(Double::doubleValue).toArray();
      String nameDef = pseudoName.startsWith("_") ? pseudoName.substring(1) : pseudoName;
      fluid.setMolarCompositionOfNamedComponents(nameDef, composition);
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

  /**
   * <p>
   * readE300File.
   * </p>
   *
   * @param inputFile a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
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

      while ((line = br.readLine()) != null) {
        line = line.trim(); // Ensure trimming of whitespace

        if (line.equals("CNAMES")) {
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              names.add(line.substring(0, line.length() - 1).trim());
              break;
            }
            names.add(line);
          }
        } else if (line.equals("TCRIT")) {
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              TC.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            TC.add(Double.parseDouble(line));
          }
        } else if (line.equals("PCRIT")) {
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              PC.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            PC.add(Double.parseDouble(line));
          }
        } else if (line.equals("ACF")) {
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              ACF.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            ACF.add(Double.parseDouble(line));
          }
        } else if (line.equals("MW")) {
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
              MW.add(Double.parseDouble(line.substring(0, line.length() - 1).trim()));
              break;
            }
            MW.add(Double.parseDouble(line));
          }
        } else if (line.equals("ZI")) {
          while ((line = br.readLine()) != null) {
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

  /**
   * Write a NeqSim fluid to Eclipse E300 compositional EOS file format.
   *
   * <p>
   * The exported file contains all EOS parameters needed to recreate the fluid in Eclipse 300 or
   * read it back into NeqSim, including: component names, critical properties, acentric factors,
   * molecular weights, volume shifts, parachors, mole fractions, and binary interaction
   * coefficients.
   * </p>
   *
   * @param fluid the fluid to export
   * @param outputFile path to output file (e.g., "myfluid.e300")
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, String outputFile) throws IOException {
    write(fluid, Path.of(outputFile), 100.0);
  }

  /**
   * Write a NeqSim fluid to Eclipse E300 compositional EOS file format.
   *
   * @param fluid the fluid to export
   * @param outputFile path to output file
   * @param reservoirTempC reservoir temperature in Celsius for RTEMP keyword
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, String outputFile, double reservoirTempC)
      throws IOException {
    write(fluid, Path.of(outputFile), reservoirTempC);
  }

  /**
   * Write a NeqSim fluid to Eclipse E300 compositional EOS file format.
   *
   * @param fluid the fluid to export
   * @param outputPath output file path
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if writing fails
   */
  public static void write(SystemInterface fluid, Path outputPath, double reservoirTempC)
      throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
      writeToWriter(fluid, writer, reservoirTempC);
    }
  }

  /**
   * Convert a NeqSim fluid to Eclipse E300 format string.
   *
   * @param fluid the fluid to export
   * @return E300 format content as string
   */
  public static String toE300String(SystemInterface fluid) {
    return toE300String(fluid, 100.0);
  }

  /**
   * Convert a NeqSim fluid to Eclipse E300 format string.
   *
   * @param fluid the fluid to export
   * @param reservoirTempC reservoir temperature in Celsius
   * @return E300 format content as string
   */
  public static String toE300String(SystemInterface fluid, double reservoirTempC) {
    StringBuilder sb = new StringBuilder();
    try {
      writeToWriter(fluid, new java.io.StringWriter() {
        @Override
        public void write(String str) {
          sb.append(str);
        }

        @Override
        public void write(String str, int off, int len) {
          sb.append(str, off, off + len);
        }
      }, reservoirTempC);
    } catch (IOException e) {
      logger.error("Error converting fluid to E300 string: " + e.getMessage(), e);
    }
    return sb.toString();
  }

  /**
   * Internal method to write E300 content to a Writer.
   *
   * @param fluid the fluid to export
   * @param writer output writer
   * @param reservoirTempC reservoir temperature in Celsius
   * @throws IOException if writing fails
   */
  private static void writeToWriter(SystemInterface fluid, java.io.Writer writer,
      double reservoirTempC) throws IOException {
    int nComps = fluid.getNumberOfComponents();
    String timestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // Header
    writer.write("-- Eclipse 300 Compositional EOS File\n");
    writer.write("-- Generated by NeqSim on " + timestamp + "\n");
    writer.write("--\n");

    // Units
    writer.write("METRIC\n");

    // Number of components
    writer.write("-- Number of components:\n");
    writer.write("NCOMPS\n");
    writer.write(nComps + " /\n");

    // Equation of state - detect from fluid class name
    writer.write("-- Equation of state\n");
    writer.write("EOS\n");
    String eosType = getEOSType(fluid);
    writer.write(eosType + " /\n");

    // Reservoir temperature
    writer.write("-- Reservoir temperature (C)\n");
    writer.write("RTEMP\n");
    writer.write(String.format(java.util.Locale.US, "    %.2f /\n", reservoirTempC));

    // Standard conditions
    writer.write("-- Standard Conditions (C and bara)\n");
    writer.write("STCOND\n");
    writer.write("   15.00000    1.01325  /\n");

    // Component names
    writer.write("-- Component names\n");
    writer.write("CNAMES\n");
    for (int i = 0; i < nComps; i++) {
      String name = shortenComponentName(fluid.getComponent(i).getComponentName());
      writer.write(name + "\n");
    }
    writer.write("/\n");

    // Critical temperatures (K)
    writer.write("-- Tc (K)\n");
    writer.write("TCRIT\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.3f\n", fluid.getComponent(i).getTC()));
    }
    writer.write("/\n");

    // Critical pressures (bar)
    writer.write("-- Pc (Bar)\n");
    writer.write("PCRIT\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.4f\n", fluid.getComponent(i).getPC()));
    }
    writer.write("/\n");

    // Acentric factors
    writer.write("-- Omega\n");
    writer.write("ACF\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.5f\n",
          fluid.getComponent(i).getAcentricFactor()));
    }
    writer.write("/\n");

    // Molecular weights (g/mol)
    writer.write("-- Molecular Weight (g/mol)\n");
    writer.write("MW\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.4f\n",
          fluid.getComponent(i).getMolarMass() * 1000.0));
    }
    writer.write("/\n");

    // Normal boiling points (K)
    writer.write("-- Normal Boiling Point (K)\n");
    writer.write("TBOIL\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.3f\n",
          fluid.getComponent(i).getNormalBoilingPoint()));
    }
    writer.write("/\n");

    // Critical volumes (m3/kmol)
    writer.write("-- Critical Volume (m3/kmol)\n");
    writer.write("VCRIT\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.6f\n",
          fluid.getComponent(i).getCriticalVolume()));
    }
    writer.write("/\n");

    // Volume shift (dimensionless)
    writer.write("-- Volume Translation\n");
    writer.write("SSHIFT\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.6f\n",
          fluid.getComponent(i).getVolumeCorrectionConst()));
    }
    writer.write("/\n");

    // Parachors
    writer.write("-- Parachor\n");
    writer.write("PARACHOR\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.4f\n",
          fluid.getComponent(i).getParachorParameter()));
    }
    writer.write("/\n");

    // Molar compositions (mole fractions)
    writer.write("-- Mole Fractions\n");
    writer.write("ZI\n");
    for (int i = 0; i < nComps; i++) {
      writer.write(String.format(java.util.Locale.US, "   %.10f\n", fluid.getComponent(i).getz()));
    }
    writer.write("/\n");

    // Binary Interaction Coefficients
    writer.write("-- Binary Interaction Coefficients (lower triangular)\n");
    writer.write("BIC\n");
    double[][] kij = getBinaryInteractionParameters(fluid);
    for (int i = 1; i < nComps; i++) {
      StringBuilder line = new StringBuilder();
      for (int j = 0; j < i; j++) {
        line.append(String.format(java.util.Locale.US, "  %.6f", kij[i][j]));
      }
      writer.write(line.toString() + "\n");
    }
    writer.write("/\n");
  }

  /**
   * Determines the EOS type string for the fluid.
   *
   * @param fluid the fluid
   * @return EOS type string (SRK, PR, etc.)
   */
  private static String getEOSType(SystemInterface fluid) {
    String className = fluid.getClass().getSimpleName().toLowerCase();
    if (className.contains("srk")) {
      return "SRK";
    } else if (className.contains("pr")) {
      return "PR";
    } else {
      return "SRK"; // Default
    }
  }

  /**
   * Shortens component names to E300 compatible format.
   *
   * @param name original component name
   * @return shortened name
   */
  private static String shortenComponentName(String name) {
    String lowerName = name.toLowerCase();
    if (lowerName.equals("nitrogen")) {
      return "N2";
    } else if (lowerName.equals("methane")) {
      return "C1";
    } else if (lowerName.equals("ethane")) {
      return "C2";
    } else if (lowerName.equals("propane")) {
      return "C3";
    } else if (lowerName.equals("i-butane")) {
      return "iC4";
    } else if (lowerName.equals("n-butane")) {
      return "C4";
    } else if (lowerName.equals("i-pentane")) {
      return "iC5";
    } else if (lowerName.equals("n-pentane")) {
      return "C5";
    } else if (lowerName.equals("n-hexane")) {
      return "C6";
    } else if (lowerName.equals("water")) {
      return "H2O";
    } else {
      // For pseudo-components, remove "_PC" suffix if present
      return name.replace("_PC", "");
    }
  }

  /**
   * Gets binary interaction parameters from fluid.
   *
   * @param fluid the fluid
   * @return 2D array of kij values
   */
  private static double[][] getBinaryInteractionParameters(SystemInterface fluid) {
    int n = fluid.getNumberOfComponents();
    double[][] kij = new double[n][n];

    if (fluid.getPhase(0) instanceof PhaseEosInterface) {
      PhaseEosInterface phase = (PhaseEosInterface) fluid.getPhase(0);
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          kij[i][j] = phase.getEosMixingRule().getBinaryInteractionParameter(i, j);
        }
      }
    }
    return kij;
  }
}
