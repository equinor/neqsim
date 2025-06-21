/*
 * EosMixingRuleHandler.java
 *
 * Created on 4. juni 2000, 12:38
 */

package neqsim.thermo.mixingrule;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.phase.PhaseGENRTLmodifiedHV;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseGEUnifacPSRK;
import neqsim.thermo.phase.PhaseGEUnifacUMRPRU;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseSoreideWhitson;
import neqsim.thermo.phase.PhaseType;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * EosMixingRuleHandler class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class EosMixingRuleHandler extends MixingRuleHandler {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(EosMixingRuleHandler.class);

  public String mixingRuleGEModel = "NRTL";

  public double Atot = 0;
  public double Btot = 0;
  public double Ai = 0;
  public double Bi = 0;
  public double A;
  public double B;

  public double[][] intparam;
  public double[][] intparamT;
  public double[][] WSintparam;
  public double[][] intparamij;
  public double[][] intparamji;
  public int[][] intparamTType;

  double[][] HVDij;
  double[][] HVDijT;

  double[][] HValpha;

  double[][] NRTLDij;
  double[][] NRTLDijT;

  double[][] NRTLalpha;

  double[][][] wij;
  int[][] wijCalcOrFitted;
  String[][] classicOrHV;
  String[][] classicOrWS;

  public double nEOSkij = 3.0;
  /** Constant <code>calcEOSInteractionParameters=false</code>. */
  public static boolean calcEOSInteractionParameters = false;
  private int bmixType = 0;

  /**
   * <p>
   * Constructor for EosMixingRules.
   * </p>
   */
  public EosMixingRuleHandler() {
    this.mixingRuleName = "no (kij=0)";
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @param mr a int
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface getMixingRule(int mr) {
    if (mr == 1) {
      return new ClassicVdW();
    } else if (mr == 2) {
      return new ClassicSRK();
    } else if (mr == 3) {
      return new ClassicVdW();
    } else {
      // TODO: not matching the initialization in getMixingRule(int mr, PhaseInterface phase)
      return new ClassicVdW();
    }
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @param mr a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface getMixingRule(int mr, PhaseInterface phase) {
    this.wij = new double[3][phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    intparam = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];

    if (mr == 1) {
      mixingRuleName = "no (kij=0)";
      return new ClassicVdW();
    }
    intparamji = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    intparamij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    intparamT = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    intparamTType = new int[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    HVDij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    HVDijT = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    NRTLDij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    NRTLDijT = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    WSintparam = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    HValpha = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    NRTLalpha = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    classicOrHV = new String[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    classicOrWS = new String[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    wijCalcOrFitted = new int[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      for (int k = 0; k < phase.getNumberOfComponents(); k++) {
        String component_name = phase.getComponent(k).getComponentName();

        for (int l = k; l < phase.getNumberOfComponents(); l++) {
          String component_name2 = phase.getComponent(l).getComponentName();
          if (k == l) {
            classicOrHV[k][l] = "Classic";
            classicOrWS[k][l] = "Classic";
            classicOrHV[l][k] = classicOrHV[k][l];
            classicOrWS[l][k] = classicOrWS[k][l];
          } else {
            java.sql.ResultSet dataSet = null;
            try {
              int underscoreIndex = component_name.indexOf("__"); // double underscore
              if (underscoreIndex != -1) {
                component_name = component_name.substring(0, underscoreIndex);
              }
              int underscoreIndex2 = component_name2.indexOf("__");
              if (underscoreIndex2 != -1) {
                component_name2 = component_name2.substring(0, underscoreIndex2);
              }
              if (phase.getComponent(k).isIsTBPfraction()
                  || phase.getComponent(l).isIsTBPfraction()) {
                throw new Exception("no interaction coefficient for TBP fractions");
              }
              int templ = l;
              int tempk = k;

              if (NeqSimDataBase.createTemporaryTables()) {
                dataSet = database.getResultSet("SELECT * FROM intertemp WHERE (comp1='"
                    + component_name + "' AND comp2='" + component_name2 + "') OR (comp1='"
                    + component_name2 + "' AND comp2='" + component_name + "')");
              } else {
                dataSet = database.getResultSet("SELECT * FROM inter WHERE (comp1='"
                    + component_name + "' AND comp2='" + component_name2 + "') OR (comp1='"
                    + component_name2 + "' AND comp2='" + component_name + "')");
              }
              dataSet.next();
              if (dataSet.getString("comp1").trim().equals(component_name2)) {
                templ = k;
                tempk = l;
              }

              classicOrHV[k][l] = dataSet.getString("HVTYPE").trim();
              classicOrHV[l][k] = classicOrHV[k][l];

              if (isCalcEOSInteractionParameters()) {
                intparam[k][l] =
                    1.0 - Math.pow(
                        (2.0 * Math.sqrt(Math.pow(phase.getComponent(l).getCriticalVolume(), 1 / 3)
                            * Math.pow(phase.getComponent(k).getCriticalVolume(), 1 / 3))
                            / (Math.pow(phase.getComponent(l).getCriticalVolume(), 1 / 3)
                                + Math.pow(phase.getComponent(k).getCriticalVolume(), 1 / 3))),
                        nEOSkij);
                intparamT[k][l] = 0.0;
                // System.out.println("kij " + intparam[k][l]);
              } else {
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhasePrEos")) {
                  // System.out.println("using PR intparams");
                  intparam[k][l] = Double.parseDouble(dataSet.getString("kijpr"));
                  intparamT[k][l] = Double.parseDouble(dataSet.getString("KIJTpr"));
                } else {
                  intparam[k][l] = Double.parseDouble(dataSet.getString("kijsrk"));
                  intparamT[k][l] = Double.parseDouble(dataSet.getString("KIJTSRK"));
                }
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhasePrCPA")) {
                  intparam[k][l] = Double.parseDouble(dataSet.getString("cpakij_PR"));
                  intparamT[k][l] = 0.0;
                } else if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  intparam[k][l] = Double.parseDouble(dataSet.getString("cpakij_SRK"));
                  intparamT[k][l] = Double.parseDouble(dataSet.getString("cpakijT_SRK"));

                  intparamij[tempk][templ] = Double.parseDouble(dataSet.getString("cpakijx_SRK"));
                  intparamji[templ][tempk] = intparamij[tempk][templ];

                  intparamji[tempk][templ] = Double.parseDouble(dataSet.getString("cpakjix_SRK"));
                  intparamij[templ][tempk] = intparamji[tempk][templ];

                } else if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSoreideWhitson")) {

                  intparam[k][l] = Double.parseDouble(dataSet.getString("KIJWhitsonSoriede"));
                  intparam[l][k] = intparam[k][l];

                  intparamij[k][l] = Double.parseDouble(dataSet.getString("KIJWhitsonSoriede"));
                  intparamij[l][k] = intparamij[k][l];
                  
                  String componenti = component_name;
                  String componentj = component_name2;
                  double acentricFactori = phase.getComponent(k).getAcentricFactor();
                  double reducedTemperaturei = phase.getComponent(k).getReducedTemperature();
                  double salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
                  double kij = 0.0;

                  if (componentj.equalsIgnoreCase("water") || componentj.equalsIgnoreCase("H2O")) {
                      if (componenti.equalsIgnoreCase("N2") || componenti.equalsIgnoreCase("nitrogen")) {
                          kij = 0.997*(-1.70235 * (1 + 0.025587 * Math.pow(salinityConcentration, 0.75))
                              + 0.44338 * (1 + 0.08126 * Math.pow(salinityConcentration, 0.75)) * reducedTemperaturei);

                      } else if (componenti.equalsIgnoreCase("CO2")) {

                        double multipK = 1.0;
                        if (salinityConcentration > 2.0) {
                          multipK = 0.9;
                        } else if (salinityConcentration > 3.5) {
                          multipK = 0.8;
                        }

                          kij = multipK*0.989*(-0.31092 * (1 + 0.15587 * Math.pow(salinityConcentration, 0.75))
                              + 0.2358 * (1 + 0.17837 * Math.pow(salinityConcentration, 0.98)) * reducedTemperaturei
                              - 21.2566 * Math.exp(-Math.pow(6.7222,reducedTemperaturei) - salinityConcentration));
                      } else if (componenti.equalsIgnoreCase("water") || componenti.equalsIgnoreCase("H2O")) {
                          kij = 0.0;
                      } else {
                          double a0 = 0.017407;
                          double a1 = 0.033516;
                          double a2 = 0.011478;
                          double A0 = 1.112 - 1.7369 * Math.pow(acentricFactori, -0.1);
                          double A1 = 1.1001 + 0.83 * acentricFactori;
                          double A2 = -0.15742 - 1.0988 * acentricFactori;
                          kij = 0.777*(((1 + a0 * salinityConcentration) * A0
                              + (1 + a1 * salinityConcentration) * A1 * reducedTemperaturei
                              + (1 + a2 * salinityConcentration) * A2 * Math.pow(reducedTemperaturei, 2)));
                      }
                  }

                  intparamji[k][l] = kij;  
                  intparamji[l][k] = intparamji[k][l];

                  intparamT[k][l] = 0.0;
                  intparamT[l][k] = 0.0;

                }
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhasePCSAFTRahmat")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhasePCSAFT")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhasePCSAFTa")) {
                  intparam[k][l] = Double.parseDouble(dataSet.getString("KIJPCSAFT"));
                  intparamT[k][l] = 0.0;
                }
              }

              java.sql.ResultSetMetaData dataSetMD = dataSet.getMetaData();
              int cols = dataSetMD.getColumnCount();
              boolean hasKIJTTypeCPAcol = false;
              String colname = "KIJTTypeCPA";
              for (int x = 1; x <= cols; x++) {
                if (colname.equals(dataSetMD.getColumnName(x))) {
                  hasKIJTTypeCPAcol = true;
                }
              }

              // System.out.println("class name " + phase.getClass().getName());
              if ((!phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                  || !hasKIJTTypeCPAcol) &&  !phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSoreideWhitson")) {
                intparamTType[k][l] = Integer.parseInt(dataSet.getString("KIJTType"));
              } else {
                intparamTType[k][l] = Integer.parseInt(dataSet.getString("KIJTTypeCPA"));
                // TODO: implement in all dbs
              }
              intparamTType[l][k] = intparamTType[k][l];

              HValpha[k][l] = Double.parseDouble(dataSet.getString("HValpha"));
              HValpha[l][k] = HValpha[k][l];

              HVDij[tempk][templ] = Double.parseDouble(dataSet.getString("HVgij"));
              HVDij[templ][tempk] = Double.parseDouble(dataSet.getString("HVgji"));

              wijCalcOrFitted[k][l] = Integer.parseInt(dataSet.getString("CalcWij"));
              wijCalcOrFitted[l][k] = wijCalcOrFitted[k][l];

              wij[0][k][l] = Double.parseDouble(dataSet.getString("w1"));
              wij[0][l][k] = wij[0][k][l];
              wij[1][k][l] = Double.parseDouble(dataSet.getString("w2"));
              wij[1][l][k] = wij[1][k][l];
              wij[2][k][l] = Double.parseDouble(dataSet.getString("w3"));
              wij[2][l][k] = wij[2][k][l];

              classicOrWS[k][l] = dataSet.getString("WSTYPE").trim();
              classicOrWS[l][k] = classicOrWS[k][l];

              WSintparam[k][l] = Double.parseDouble(dataSet.getString("kijWS"));
              WSintparam[k][l] = Double.parseDouble(dataSet.getString("KIJWSunifac"));
              WSintparam[l][k] = WSintparam[k][l];

              NRTLalpha[k][l] = Double.parseDouble(dataSet.getString("NRTLalpha"));
              NRTLalpha[l][k] = NRTLalpha[k][l];

              NRTLDij[tempk][templ] = Double.parseDouble(dataSet.getString("NRTLgij"));
              NRTLDij[templ][tempk] = Double.parseDouble(dataSet.getString("NRTLgji"));

              HVDijT[tempk][templ] = Double.parseDouble(dataSet.getString("HVgijT"));
              HVDijT[templ][tempk] = Double.parseDouble(dataSet.getString("HVgjiT"));

              NRTLDijT[tempk][templ] = Double.parseDouble(dataSet.getString("WSgijT"));
              NRTLDijT[templ][tempk] = Double.parseDouble(dataSet.getString("WSgjiT"));
            } catch (Exception ex) {
              // System.out.println("err in thermo mix.....");
              // System.out.println(ex.toString());
              if (isCalcEOSInteractionParameters()) {
                intparam[k][l] = 1.0 - Math.pow(
                    (2.0 * Math.sqrt(Math.pow(phase.getComponent(l).getCriticalVolume(), 1.0 / 3.0)
                        * Math.pow(phase.getComponent(k).getCriticalVolume(), 1.0 / 3.0))
                        / (Math.pow(phase.getComponent(l).getCriticalVolume(), 1.0 / 3.0)
                            + Math.pow(phase.getComponent(k).getCriticalVolume(), 1.0 / 3.0))),
                    nEOSkij);
                // System.out.println("intparam not defined .... CALCULATING intparam
                // between "
                // +component_name2 + " and " +
                // component_name+ " to " +
                // intparam[k][l]);
              } else if ((component_name.equals("CO2") && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("CO2") && phase.getComponent(k).isIsTBPfraction())) {
                intparam[k][l] = 0.1;
              } else if ((component_name.equals("nitrogen")
                  && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("nitrogen")
                      && phase.getComponent(k).isIsTBPfraction())) {
                intparam[k][l] = 0.08;
              } else if ((component_name.equals("water") && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("water") && phase.getComponent(k).isIsTBPfraction())) {
                intparam[k][l] = 0.2;

                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  // intparam[k][l] = -0.0685; // taken from Riaz et a. 2012

                  double molmassPC = phase.getComponent(l).getMolarMass();
                  if (phase.getComponent(k).isIsTBPfraction()) {
                    molmassPC = phase.getComponents()[k].getMolarMass();
                  }
                  double intparamkPC = -0.1533 * Math.log(1000.0 * molmassPC) + 0.7055;
                  intparam[k][l] = intparamkPC;
                  // System.out.println("kij water-HC " + intparam[k][l]);

                  intparamT[k][l] = 0.0;
                }
              } else if ((component_name.equals("MEG") && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("MEG")
                      && phase.getComponents()[k].isIsTBPfraction())) {
                intparam[k][l] = 0.2;
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  double molmassPC = phase.getComponent(l).getMolarMass();
                  if (phase.getComponents()[k].isIsTBPfraction()) {
                    molmassPC = phase.getComponents()[k].getMolarMass();
                  }
                  double intparamkPC = -0.0701 * Math.log(1000.0 * molmassPC) + 0.3521;
                  intparam[k][l] = intparamkPC;
                  // System.out.println("kij MEG-HC " + intparam[k][l]);
                  // intparam[k][l] = 0.01;
                  intparamT[k][l] = 0.0;
                }
              } else if ((component_name.equals("ethanol")
                  && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("ethanol")
                      && phase.getComponents()[k].isIsTBPfraction())) {
                intparam[k][l] = 0.0;
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  intparam[k][l] = -0.05;
                  intparamT[k][l] = 0.0;
                  if (phase.getComponents()[k].getMolarMass() > (200.0 / 1000.0)
                      || phase.getComponent(l).getMolarMass() > (200.0 / 1000.0)) {
                    intparam[k][l] = -0.1;
                  }
                }
              } else if ((component_name.equals("methanol")
                  && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("methanol")
                      && phase.getComponents()[k].isIsTBPfraction())) {
                intparam[k][l] = 0.0;
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  intparam[k][l] = -0.1;
                  intparamT[k][l] = 0.0;
                  if (phase.getComponents()[k].getMolarMass() > (200.0 / 1000.0)
                      || phase.getComponent(l).getMolarMass() > (200.0 / 1000.0)) {
                    intparam[k][l] = -0.2;
                  }
                }
              } else if ((component_name.equals("TEG") && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("TEG")
                      && phase.getComponents()[k].isIsTBPfraction())) {
                intparam[k][l] = 0.12;
                if (phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPA")
                    || phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSrkCPAs")
                    || phase.getClass().getName()
                        .equals("neqsim.thermo.phase.PhaseElectrolyteCPAstatoil")) {
                  intparam[k][l] = 0.12;
                  intparamT[k][l] = 0.0;
                }
              } else if ((component_name.equals("S8") && phase.getComponent(l).isIsTBPfraction())
                  || (component_name2.equals("S8") && phase.getComponents()[k].isIsTBPfraction())) {
                intparam[k][l] = 0.05;
              } else {
                // if((component_name2.equals("CO2") ||
                // component_name.equals("CO2")) && k!=l)
                // intparam[k][l] = 0.1;
                // else if((component_name2.equals("H2S") ||
                // component_name.equals("H2S")) && k!=l)
                // intparam[k][l] = 0.2;
                // else if((component_name2.equals("water")
                // ||
                // component_name.equals("water")) && k!=l)
                // intparam[k][l] = 0.5;
                // else intparam[k][l] = 0.0;
                // System.out.println("intparam not defined .... setting intparam
                // between " +
                // component_name2 + " and " +
                // component_name + " to " +
                // intparam[k][l]);
              }

              // intparam[l][k] = intparam[k][l];
              // intparamT[l][k] = intparamT[k][l];
              if (!phase.getClass().getName().equals("neqsim.thermo.phase.PhaseSoreideWhitson")){
              intparamij[k][l] = intparam[k][l];
              intparamij[l][k] = intparam[k][l];
              intparamji[k][l] = intparam[k][l];
              intparamji[l][k] = intparam[k][l];
              }
              // System.out.println("kij set to " + intparam[l][k] + " " +
              // component_name2 + " " +
              // component_name);

              classicOrHV[k][l] = "Classic";
              classicOrHV[l][k] = classicOrHV[k][l];

              classicOrWS[k][l] = "Classic";
              classicOrWS[l][k] = classicOrWS[k][l];
            } finally {
              intparam[l][k] = intparam[k][l];
              intparamT[l][k] = intparamT[k][l];
              try {
                if (dataSet != null) {
                  dataSet.close();
                }
              } catch (Exception ex) {
                logger.error("err closing dataSet IN MIX...", ex);
              }
            }
          }
        }
      }
    } catch (Exception ex) {
      logger.error("error reading from database", ex);
    }

    return resetMixingRule(mr, phase);
  }

  /**
   * <p>
   * resetMixingRule.
   * </p>
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.mixingrule.EosMixingRulesInterface} object
   */
  public EosMixingRulesInterface resetMixingRule(int i, PhaseInterface phase) {
    if (i == 1) {
      mixingRuleName = "no (kij=0)";
      return new ClassicVdW();
    } else if (i == 2) {
      mixingRuleName = "classic";
      return new ClassicSRK();
    } else if (i == 3) {
      // Classic Huron-Vidal
      mixingRuleName = "Huron-Vidal";
      return new SRKHuronVidal2(phase, HValpha, HVDij, classicOrHV);
    } else if (i == 4) {
      mixingRuleName = "Huron-Vidal";
      return new SRKHuronVidal2(phase, HValpha, HVDij, HVDijT, classicOrHV);
    } else if (i == 5) {
      mixingRuleName = "Wong-Sandler";
      return new WongSandlerMixingRule(phase, NRTLalpha, NRTLDij, NRTLDijT, classicOrWS);
    } else if (i == 6) {
      // Exactly the same as 5
      mixingRuleName = "Wong-Sandler";
      return new WongSandlerMixingRule(phase, NRTLalpha, NRTLDij, NRTLDijT, classicOrWS);
    } else if (i == 7) {
      mixingRuleName = "classic-CPA";
      return new ClassicSRK();
    } else if (i == 8) {
      mixingRuleName = "classic-T";
      return new ClassicSRKT();
    } else if (i == 9) {
      mixingRuleName = "classic-CPA_T";
      return new ClassicSRKT2();
    } else if (i == 10) {
      // return new ElectrolyteMixRule(phase, HValpha, HVgij, HVgii,
      // classicOrHV,wij);}
      org.ejml.simple.SimpleMatrix mat1 = new org.ejml.simple.SimpleMatrix(intparamij);
      org.ejml.simple.SimpleMatrix mat2 = new org.ejml.simple.SimpleMatrix(intparamji);
      org.ejml.simple.SimpleMatrix mat3 = new org.ejml.simple.SimpleMatrix(intparamT);
      if (mat1.isIdentical(mat2, 1e-8)) {
        if (mat3.elementMaxAbs() < 1e-8) {
          mixingRuleName = "classic-CPA";
          return new ClassicSRK();
        }
        mixingRuleName = "classic-CPA_T";
        return new ClassicSRKT2();
      } else {
        mixingRuleName = "classic-CPA_Tx";
        return new ClassicSRKT2x();
      }
    } else if (i == 11) {
      mixingRuleName = "Whitson-Soreide Mixing Rule";
      return new WhitsonSoreideMixingRule();
    }
    else {
      return new ClassicVdW();
    }
  }

  /** {@inheritDoc} */
  @Override
  public EosMixingRuleHandler clone() {
    EosMixingRuleHandler clonedSystem = null;
    try {
      clonedSystem = (EosMixingRuleHandler) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // clonedSystem.intparam = (double[][]) intparam.clone();
    // clonedSystem.wij = (double[][][]) wij.clone();
    // clonedSystem.WSintparam = (double[][]) WSintparam.clone() ;
    // clonedSystem.HVDij = (double[][]) HVDij.clone();
    // clonedSystem.HValpha = (double[][]) HValpha.clone();
    // clonedSystem.HVDijT = (double[][]) HVDijT.clone();
    // clonedSystem.NRTLDij = (double[][]) NRTLDij.clone();
    // clonedSystem.NRTLalpha = (double[][]) NRTLalpha.clone();
    // clonedSystem.NRTLDijT = (double[][]) NRTLDijT.clone();
    // clonedSystem.classicOrHV = (String[][]) classicOrHV.clone();
    return clonedSystem;
  }

  public class ClassicVdW implements EosMixingRulesInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return mixingRuleName;
    }

    /** {@inheritDoc} */
    @Override
    public PhaseInterface getGEPhase() {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setMixingRuleGEModel(java.lang.String GEmodel) {
      mixingRuleGEModel = GEmodel;
    }

    /** {@inheritDoc} */
    @Override
    public double getBinaryInteractionParameter(int i, int j) {
      if (i == j) {
        return 0.0;
      }
      return intparam[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double[][] getBinaryInteractionParameters() {
      return intparam;
    }

    public void prettyPrintKij() {}

    /** {@inheritDoc} */
    @Override
    public double getBinaryInteractionParameterT1(int i, int j) {
      if (i == j) {
        return 0.0;
      }
      return intparamT[i][j];
    }

    /**
     * @return the bmixType
     */
    @Override
    public int getBmixType() {
      return bmixType;
    }

    /**
     * @param bmixType2 the bmixType to set
     */
    @Override
    public void setBmixType(int bmixType2) {
      bmixType = bmixType2;
    }

    public double getbij(ComponentEosInterface compi, ComponentEosInterface compj) {
      switch (getBmixType()) {
        case 0:
          return (compi.getb() + compj.getb()) * 0.5;
        case 1:
          // return (compi.getb() + compj.getb()) * 0.5;
          double temp = (Math.sqrt(compi.getb()) + Math.sqrt(compj.getb())) * 0.5;
          return temp * temp;
        // return Math.pow((Math.sqrt(compi.getb())+Math.sqrt(compj.getb()))/2.0, 2.0);
        // return
        // Math.pow(0.5*(Math.pow(compi.getb(),1.0/3.0)+Math.pow(compj.getb(),1.0/3.0)),3.0);
        // return
        // Math.sqrt(compi.getb()*compj.getb())*(1.0-intparam[compi.getComponentNumber()][compj.getComponentNumber()]);
        // return
        // Math.pow(0.5*(Math.pow(compi.getb(),3.0/4.0)+Math.pow(compj.getb(),3.0/4.0)),4.0/3.0);
        default:
          return (compi.getb() + compj.getb()) * 0.5;
      }
    }

    /** {@inheritDoc} */
    @Override
    public void setBinaryInteractionParameter(int i, int j, double value) {
      // System.out.println("intparam: " + intparam[i][j] + " value " + value);
      intparam[i][j] = value;
      intparam[j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public void setBinaryInteractionParameterij(int i, int j, double value) {
      intparamij[i][j] = value;
      intparamji[j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public void setBinaryInteractionParameterji(int i, int j, double value) {
      // System.out.println("intparam: " + intparam[i][j] + " value " + value);
      intparamji[i][j] = value;
      intparamij[j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public void setBinaryInteractionParameterT1(int i, int j, double value) {
      // System.out.println("intparam: " + intparam[i][j] + " value " + value);
      intparamT[i][j] = value;
      intparamT[j][i] = value;
    }

    /**
     * Setter for property CalcEOSInteractionParameters.
     *
     * @param CalcEOSInteractionParameters2 New value of property CalcEOSInteractionParameters.
     */
    @Override
    public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters2) {
      calcEOSInteractionParameters = CalcEOSInteractionParameters2;
    }

    /** {@inheritDoc} */
    @Override
    public void setnEOSkij(double n) {
      nEOSkij = n;
    }

    public double getA() {
      return Atot;
    }

    public double getB() {
      return Btot;
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0.0;
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          aij = Math.sqrt(compArray[i].getaT() * compArray[j].getaT());
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      Atot = A;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      B = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          B += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * getbij(compArray[i], compArray[j]); // (compArray[i].getb()+compArray[j].getb())/2;
        }
      }
      B /= phase.getNumberOfMolesInPhase();
      Btot = B;
      return B;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      Ai = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        aij = Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT());
        Ai += compArray[j].getNumberOfMolesInPhase() * aij;
      }

      return 2.0 * Ai;
    }

    /** {@inheritDoc} */
    @Override
    public double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double Bi = 0.0;

      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        Bi += compArray[j].getNumberOfMolesInPhase() * getbij(compArray[compNumb], compArray[j]);
      }

      Bi = (2.0 * Bi - getB()) / phase.getNumberOfMolesInPhase();
      return Bi;
    }

    public double calcBi2(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double Bi = 0.0;

      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double sumk = 0;
      for (int j = 0; j < numbcomp; j++) {
        Bi += compArray[j].getNumberOfMolesInPhase() * getbij(compArray[compNumb], compArray[j]);
        for (int k = 0; k < numbcomp; k++) {
          sumk += compArray[j].getNumberOfMolesInPhase() * compArray[k].getNumberOfMolesInPhase()
              * getbij(compArray[j], compArray[k]);
        }
      }
      double ans1 = phase.getNumberOfMolesInPhase() * Bi - sumk;

      return ans1 / (phase.getNumberOfMolesInPhase() * phase.getNumberOfMolesInPhase());
    }

    /** {@inheritDoc} */
    @Override
    public double calcBij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double bij = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      bij = getbij(compArray[compNumb], compArray[compNumbj]);
      return (2.0 * bij - compArray[compNumb].getBi() - compArray[compNumbj].getBi())
          / phase.getNumberOfMolesInPhase();
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        aij = 0.5 / Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT());
        A += compArray[j].getNumberOfMolesInPhase() * aij;
      }

      return 2.0 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      aij = Math.sqrt(compArray[compNumb].getaT() * compArray[compNumbj].getaT());

      return 2.0 * aij;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        A += compArray[i].getNumberOfMolesInPhase()
            * phase.calcAiT(i, phase, temperature, pressure, numbcomp);
      }

      return 0.5 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      double sqrtaij = 0;
      double tempPow = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          sqrtaij = Math.sqrt(compArray[i].getaT() * compArray[j].getaT());
          tempPow = compArray[i].getaT() * compArray[j].getaDiffT()
              + compArray[j].getaT() * compArray[i].getaDiffT();
          aij = 0.5 * ((2.0 * compArray[i].getaDiffT() * compArray[j].getaDiffT()
              + compArray[i].getaT() * compArray[j].getaDiffDiffT()
              + compArray[j].getaT() * compArray[i].getaDiffDiffT()) / sqrtaij
              - tempPow * tempPow / (2.0 * sqrtaij * compArray[i].getaT() * compArray[j].getaT()));
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public ClassicVdW clone() {
      ClassicVdW clonedSystem = null;
      try {
        clonedSystem = (ClassicVdW) super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
      }

      return clonedSystem;
    }
  }

  public class ClassicSRK extends ClassicVdW {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public double getkij(double temp, int i, int j) {
      // System.out.println("kij " +intparam[i][j] );
      return intparam[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          aij = Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
              * (1.0 - getkij(temperature, i, j));
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      Atot = A;
      return A;
    }

    // public double calcB(PhaseInterface phase, double temperature, double
    // pressure, int numbcomp){
    // B = 0.0;
    // ComponentEosInterface[] compArray = (ComponentEosInterface[])
    // phase.getcomponentArray();

    // for (int i=0;i<numbcomp;i++){
    // for (int j=0;j<numbcomp;j++){
    // B +=
    // compArray[i].getNumberOfMolesInPhase()*compArray[j].getNumberOfMolesInPhase()
    // * (compArray[i].getb()+compArray[j].getb())/2.0;
    // }
    // }
    // Btot = B/phase.getNumberOfMolesInPhase();
    // return Btot;
    // }
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double aij = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        aij = Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (1.0 - getkij(temperature, compNumb, j));
        A += compArray[j].getNumberOfMolesInPhase() * aij;
      }
      return 2.0 * A;
    }

    // public double calcBi(int compNumb, PhaseInterface phase, double temperature,
    // double pressure, int numbcomp){
    // double Bi=0.0;

    // ComponentEosInterface[] compArray = (ComponentEosInterface[])
    // phase.getcomponentArray();

    // for (int j=0;j<numbcomp;j++){
    // Bi += compArray[j].getNumberOfMolesInPhase() *
    // (compArray[compNumb].getb()+compArray[j].getb())/2.0;
    // }

    // return (2*Bi-getB())/phase.getNumberOfMolesInPhase();
    // }

    // public double calcBij(int compNumb, int compNumbj, PhaseInterface phase,
    // double temperature, double pressure, int numbcomp){
    // ComponentEosInterface[] compArray = (ComponentEosInterface[])
    // phase.getcomponentArray();
    // double bij = (compArray[compNumb].getb()+compArray[compNumbj].getb())/2.0;
    // return (2*bij-phase.calcBi(compNumb, phase,temperature, pressure,
    // numbcomp)-calcBi(compNumbj, phase,temperature, pressure,
    // numbcomp))/phase.getNumberOfMolesInPhase();
    // }
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        aij = 0.5 / Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT())
            * (1.0 - getkij(temperature, compNumb, j));
        A += compArray[j].getNumberOfMolesInPhase() * aij;
      }
      // System.out.println("Ait SRK : " + (2*A));
      return 2.0 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      aij = Math.sqrt(compArray[compNumb].getaT() * compArray[compNumbj].getaT())
          * (1.0 - getkij(temperature, compNumb, compNumbj));
      return 2.0 * aij;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        A += compArray[i].getNumberOfMolesInPhase()
            * ((ComponentEosInterface) phase.getComponent(i)).getAiT();
        // phase.calcAiT(i, phase, temperature, pressure, numbcomp);
      }
      // System.out.println("AT SRK: " + (0.5*A));
      return 0.5 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      double temp1;
      double[] sqrtai = new double[numbcomp];
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        sqrtai[i] = Math.sqrt(compArray[i].getaT());
      }

      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getNumberOfmoles() < 1e-100) {
          continue;
        }
        for (int j = 0; j < numbcomp; j++) {
          if (compArray[j].getNumberOfmoles() < 1e-100) {
            continue;
          }
          temp1 = compArray[i].getaT() * compArray[j].getaDiffT()
              + compArray[j].getaT() * compArray[i].getaDiffT();
          aij = 0.5
              * ((2.0 * compArray[i].getaDiffT() * compArray[j].getaDiffT()
                  + compArray[i].getaT() * compArray[j].getaDiffDiffT()
                  + compArray[j].getaT() * compArray[i].getaDiffDiffT()) / sqrtai[i] / sqrtai[j]
                  - temp1 * temp1
                      / (2.0 * sqrtai[i] * sqrtai[j] * compArray[i].getaT() * compArray[j].getaT()))
              * (1.0 - getkij(temperature, i, j));
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public ClassicSRK clone() {
      ClassicSRK clonedSystem = null;
      try {
        clonedSystem = (ClassicSRK) super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
      }

      // clonedSystem.intparam = (double[][]) clonedSystem.intparam.clone();
      return clonedSystem;
    }
  }

  public class ClassicSRKT extends ClassicSRK {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public double getkij(double temperature, int i, int j) {
      return intparam[i][j] + intparamT[i][j] * (temperature / 273.15 - 1.0);

      // impl ttype check
    }

    public double getkijdT(double temperature, int i, int j) {
      return intparamT[i][j] * (1.0 / 273.15);
    }

    public double getkijdTdT(double temperature, int i, int j) {
      return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0.0;
      double aij2 = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        aij = 0.5 / Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT())
            * (1.0 - getkij(temperature, compNumb, j));
        aij2 = Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (-getkijdT(temperature, compNumb, j));
        A += compArray[j].getNumberOfMolesInPhase() * (aij + aij2);
      }
      // System.out.println("Ait SRK : " + (2*A));
      return 2.0 * A;
    }

    public double calcAiTT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0.0;
      double aij2 = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        aij = 0.5 / Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT())
            * (1.0 - getkij(temperature, compNumb, j));
        aij2 = Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (-getkijdT(temperature, compNumb, j));
        A += compArray[j].getNumberOfMolesInPhase() * (aij + aij2);
      }
      // System.out.println("Ait SRK : " + (2*A));
      return 2.0 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0.0;
      double aij2 = 0.0;
      double aij3 = 0.0;
      double aij4 = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          aij = 0.5
              * ((2.0 * compArray[i].getaDiffT() * compArray[j].getaDiffT()
                  + compArray[i].getaT() * compArray[j].getaDiffDiffT()
                  + compArray[j].getaT() * compArray[i].getaDiffDiffT())
                  / Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
                  - Math
                      .pow(compArray[i].getaT() * compArray[j].getaDiffT()
                          + compArray[j].getaT() * compArray[i].getaDiffT(), 2)
                      / (2 * Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
                          * compArray[i].getaT() * compArray[j].getaT()))
              * (1.0 - getkij(temperature, i, j));

          aij2 = 0.5 / Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
              * (compArray[i].getaT() * compArray[j].getaDiffT()
                  + compArray[j].getaT() * compArray[i].getaDiffT())
              * (-getkijdT(temperature, i, j));

          aij3 = 0.5 / Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
              * (compArray[i].getaT() * compArray[j].getaDiffT()
                  + compArray[j].getaT() * compArray[i].getaDiffT())
              * (-getkijdT(temperature, i, j));
          aij4 = Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
              * (-getkijdTdT(temperature, i, j));
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * (aij + aij2 + aij3 + aij4);
        }
      }
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public ClassicSRKT clone() {
      ClassicSRKT clonedSystem = null;
      try {
        clonedSystem = (ClassicSRKT) super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
      }

      return clonedSystem;
    }
  }

  public class ClassicSRKT2x extends ClassicSRKT2 {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public double getkij(PhaseInterface phase, double temperature, int i, int j) {
      if (i == j) {
        return 0.0;
      }
      double tot = phase.getComponent(i).getNumberOfMolesInPhase()
          + phase.getComponent(j).getNumberOfMolesInPhase();
      if (tot < 1e-100) {
        return 0.0;
      }
      double intkijMix = (phase.getComponent(i).getNumberOfMolesInPhase() * intparamij[i][j]
          + phase.getComponent(j).getNumberOfMolesInPhase() * intparamji[i][j]) / tot;
      // System.out.println("kij " + intkijMix + " kijT " +(intkijMix +
      // intparamT[i][j] * temperature));

      if (intparamTType[i][j] == 0) {
        return intkijMix + intparamT[i][j] * temperature;
      } else if (intparamTType[i][j] == 1) {
        // System.out.println("kj mix " + (intkijMix +intparamT[i][j] / temperature));
        return intkijMix + intparamT[i][j] / temperature;
      } else {
        return intkijMix + intparamT[i][j] * temperature;
      }
    }

    public double getkijdn(int k, PhaseInterface phase, double temperature, int i, int j) {
      if (i == j || !((i == k) || (j == k))
          || Math.abs(intparamij[i][j] - intparamji[i][j]) < 1e-10) {
        return 0.0;
      }
      double tot = phase.getComponent(i).getNumberOfMolesInPhase()
          + phase.getComponent(j).getNumberOfMolesInPhase();
      if (tot < 1e-100) {
        return 0.0;
      }
      double intkijMix = (phase.getComponent(i).getNumberOfMolesInPhase() * intparamij[i][j]
          + phase.getComponent(j).getNumberOfMolesInPhase() * intparamji[i][j]) / tot;

      double temp = 0;
      if (i == k) {
        temp = intparamij[i][j];
      } else {
        temp = intparamji[i][j];
      }
      intkijMix = (temp - intkijMix) / tot;

      return intkijMix;
    }

    public double getkijdndn(int k, int l, PhaseInterface phase, double temperature, int i, int j) {
      if (i == j) {
        return 0.0;
      }
      double tot = phase.getComponent(i).getNumberOfMolesInPhase()
          + phase.getComponent(j).getNumberOfMolesInPhase();
      if (tot < 1e-100) {
        return 0.0;
      }
      double temp = 0;
      if ((i == k || j == k) && (i == l || j == l)) {
        if (k == i && l == i) {
          temp = -2.0 * intparamij[i][j] / (tot * tot)
              + 2.0 * phase.getComponent(i).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamij[i][j]
              + 2.0 * phase.getComponent(j).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamji[i][j];
        } else if (k == i && l == j) {
          temp = -intparamij[i][j] / (tot * tot)
              + 2.0 * phase.getComponent(i).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamij[i][j]
              - 1.0 / (tot * tot) * intparamji[i][j]
              + 2.0 * phase.getComponent(j).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamji[i][j];
        } else if (k == j && l == i) {
          temp = -intparamji[i][j] / (tot * tot)
              + 2.0 * phase.getComponent(j).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamji[i][j]
              - 1.0 / (tot * tot) * intparamij[i][j]
              + 2.0 * phase.getComponent(i).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamij[i][j];
        } else if (k == j && l == j) {
          temp = -2.0 * intparamji[i][j] / (tot * tot)
              + 2.0 * phase.getComponent(j).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamji[i][j]
              + 2.0 * phase.getComponent(i).getNumberOfMolesInPhase() / (tot * tot * tot)
                  * intparamij[i][j];
        }
      } else {
        temp = 0.0;
      }
      return temp;
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double[] sqrtai = new double[numbcomp];
      for (int j = 0; j < numbcomp; j++) {
        if (compArray[j].getNumberOfMolesInPhase() > 1e-100) {
          sqrtai[j] = Math.sqrt(compArray[j].getaT());
        }
      }
      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getNumberOfMolesInPhase() < 1e-100) {
          continue;
        }
        for (int j = 0; j < numbcomp; j++) {
          if (compArray[j].getNumberOfMolesInPhase() > 1e-100) {
            aij = sqrtai[i] * sqrtai[j] * (1.0 - getkij(phase, temperature, i, j));
            A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
                * aij;
          }
        }
      }
      Atot = A;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      // if(Math.abs(intparamT[compNumb][numbcomp])<1e-10 &&
      // Math.abs(intparamij[compNumb][numbcomp]-intparamij[numbcomp][compNumb])<1e-10){
      // return super.calcAi(compNumb, phase, temperature, pressure, numbcomp);
      // }
      double aij = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double A1 = 0.0;
      double[] sqrtai = new double[numbcomp];
      for (int j = 0; j < numbcomp; j++) {
        if (compArray[j].getNumberOfMolesInPhase() > 1e-100) {
          sqrtai[j] = Math.sqrt(compArray[j].getaT());
        }
      }
      for (int j = 0; j < numbcomp; j++) {
        if (compArray[j].getNumberOfMolesInPhase() > 1e-100) {
          aij = sqrtai[compNumb] * sqrtai[j] * (1.0 - getkij(phase, temperature, compNumb, j));
          A1 += compArray[j].getNumberOfMolesInPhase() * aij;
        }
      }
      double A2 = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getNumberOfMolesInPhase() < 1e-100) {
          continue;
        }
        for (int j = 0; j < numbcomp; j++) {
          if (compArray[j].getNumberOfMolesInPhase() > 1e-100 && (compNumb == j || compNumb == i)
              && i != j) {
            aij = -sqrtai[i] * sqrtai[j] * getkijdn(compNumb, phase, temperature, i, j);
            A2 += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
                * aij;
          }
        }
      }
      return 2.0 * A1 + A2;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      double A1 = 0;
      for (int j = 0; j < numbcomp; j++) {
        aij = -Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * getkijdn(compNumbj, phase, temperature, compNumb, j);
        A1 += compArray[j].getNumberOfMolesInPhase() * aij;
      }
      for (int j = 0; j < numbcomp; j++) {
        aij = -Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * getkijdn(compNumbj, phase, temperature, j, compNumb);
        A1 += compArray[j].getNumberOfMolesInPhase() * aij;
      }

      double A2 = 0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          aij = -Math.sqrt(compArray[i].getaT() * compArray[j].getaT())
              * getkijdndn(compNumb, compNumbj, phase, temperature, i, j);
          A2 +=
              compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase() * aij;
        }
      }

      double A4 = 0;

      A4 = 2.0 * Math.sqrt(compArray[compNumb].getaT() * compArray[compNumbj].getaT())
          * (1.0 - getkij(phase, temperature, compNumbj, compNumb));

      for (int i = 0; i < numbcomp; i++) {
        A4 += -compArray[i].getNumberOfMolesInPhase()
            * Math.sqrt(compArray[compNumbj].getaT() * compArray[i].getaT())
            * getkijdn(compNumb, phase, temperature, compNumbj, i);
      }
      for (int i = 0; i < numbcomp; i++) {
        A4 += -compArray[i].getNumberOfMolesInPhase()
            * Math.sqrt(compArray[compNumbj].getaT() * compArray[i].getaT())
            * getkijdn(compNumb, phase, temperature, i, compNumbj);
      }

      return A1 + A2 + A4;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0.0;
      double aij2 = 0.0;
      double aij3 = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double[] asqrt = new double[numbcomp];
      for (int j = 0; j < numbcomp; j++) {
        asqrt[j] = Math.sqrt(compArray[j].getaT());
      }

      for (int j = 0; j < numbcomp; j++) {
        if (compArray[j].getNumberOfMolesInPhase() < 1e-100) {
          continue;
        }
        aij = 0.5 / asqrt[compNumb] / asqrt[j]
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT())
            * (1.0 - getkij(phase, temperature, compNumb, j));
        aij2 = asqrt[compNumb] * asqrt[j] * (-getkijdT(temperature, compNumb, j));
        A += compArray[j].getNumberOfMolesInPhase() * (aij + aij2);
      }

      double A2 = 0;
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getNumberOfMolesInPhase() < 1e-100) {
          continue;
        }
        for (int j = 0; j < numbcomp; j++) {
          if (compArray[j].getNumberOfMolesInPhase() > 1e-100 && (compNumb == j || compNumb == i)
              && i != j) {
            aij3 = -0.5 / asqrt[compNumb] / asqrt[j]
                * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                    + compArray[j].getaT() * compArray[compNumb].getaDiffT())
                * getkijdn(compNumb, phase, temperature, i, j);
            A2 += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
                * aij3;
          }
        }
      }

      // System.out.println("Ait SRK : " + (2*A));
      return 2.0 * A + A2;
    }
  }

  public class ClassicSRKT2 extends ClassicSRKT {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public double getkij(double temperature, int i, int j) {
      if (intparamTType[i][j] == 0) {
        return intparam[i][j] + intparamT[i][j] * temperature;
      } else if (intparamTType[i][j] == 1) {
        return intparam[i][j] + intparamT[i][j] / temperature;
      } else {
        return intparam[i][j] + intparamT[i][j] * temperature;
      }
    }

    /** {@inheritDoc} */
    @Override
    public double getkijdT(double temperature, int i, int j) {
      if (intparamTType[i][j] == 0) {
        return intparamT[i][j];
      } else if (intparamTType[i][j] == 1) {
        return -intparamT[i][j] / (temperature * temperature);
      } else {
        return intparamT[i][j];
      }
    }

    /** {@inheritDoc} */
    @Override
    public double getkijdTdT(double temperature, int i, int j) {
      if (intparamTType[i][j] == 0) {
        return 0;
      } else if (intparamTType[i][j] == 1) {
        return 2.0 * intparamT[i][j] / (temperature * temperature * temperature);
      } else {
        return 0;
      }
    }

    /** {@inheritDoc} */
    @Override
    public ClassicSRKT clone() {
      ClassicSRKT clonedSystem = null;
      try {
        clonedSystem = super.clone();
      } catch (Exception ex) {
        logger.error("Cloning failed.", ex);
      }

      return clonedSystem;
    }
  }

  public class WhitsonSoreideMixingRule extends ClassicSRK {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;




    /** {@inheritDoc} */
    @Override
    public double getkij(double temperature, int i, int j) {
        return intparam[i][j]; // PR
      }
      
    
    public double getkijWhitsonSoreideAqueous(ComponentEosInterface[] compArray, double salinityConcentration, double temperature, int i, int j) {
        
        String componenti = (compArray[i]).getComponentName();
        String componentj = compArray[j].getComponentName();
        double acentricFactori = compArray[i].getAcentricFactor();
        double reducedTemperaturei = ((ComponentEos) compArray[i]).getReducedTemperature();
        double kij = intparam[i][j];

      if (componentj.equalsIgnoreCase("water") || componentj.equalsIgnoreCase("H2O")) {
          if (componenti.equalsIgnoreCase("N2") || componenti.equalsIgnoreCase("nitrogen")) {
              kij = 0.997*(-1.70235 * (1 + 0.025587 * Math.pow(salinityConcentration, 0.75))
                  + 0.44338 * (1 + 0.08126 * Math.pow(salinityConcentration, 0.75)) * reducedTemperaturei);

          } else if (componenti.equalsIgnoreCase("CO2")) {
            double multipK = 1.0;
            if (salinityConcentration > 2.0) {
              multipK = 0.9;
            } else if (salinityConcentration > 3.5) {
              multipK = 0.8;
            }
            kij = multipK*0.989*(-0.31092 * (1 + 0.15587 * Math.pow(salinityConcentration, 0.75))
                  + 0.2358 * (1 + 0.17837 * Math.pow(salinityConcentration, 0.98)) * reducedTemperaturei
                  - 21.2566 * Math.exp(-Math.pow(6.7222,reducedTemperaturei) - salinityConcentration));
          } else if (componenti.equalsIgnoreCase("water") || componenti.equalsIgnoreCase("H2O")) {
              kij = 0.0;
          } else {
              double a0 = 0.017407;
              double a1 = 0.033516;
              double a2 = 0.011478;
              double A0 = 1.112 - 1.7369 * Math.pow(acentricFactori, -0.1);
              double A1 = 1.1001 + 0.83 * acentricFactori;
              double A2 = -0.15742 - 1.0988 * acentricFactori;
              kij = 0.777*(((1 + a0 * salinityConcentration) * A0
                  + (1 + a1 * salinityConcentration) * A1 * reducedTemperaturei
                  + (1 + a2 * salinityConcentration) * A2 * Math.pow(reducedTemperaturei, 2)));
          }
      }

      return kij;
      
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      boolean isAqueous = phase.getComponent("water").getx() > 0.8;
      double salinityConcentration = 0.0;
      if (isAqueous) {
        salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
      }
      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          aij = Math.sqrt(compArray[i].getaT() * compArray[j].getaT());
          if (isAqueous) {
            aij *= (1.0 - getkijWhitsonSoreideAqueous(compArray, salinityConcentration, temperature, i, j));
          } else {
            aij *= (1.0 - getkij(temperature, i, j));
          }
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      Atot = A;
      return A;
    }

    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double aij = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = 0.0;
      boolean isAqueous = phase.getComponent("water").getx() > 0.8;
      double salinityConcentration = 0.0;
      if (isAqueous) {
        salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
      }
      for (int j = 0; j < numbcomp; j++) {
        aij = Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT());
        if (isAqueous) {
            aij *= (1.0 - getkijWhitsonSoreideAqueous(compArray, salinityConcentration, temperature, compNumb, j));
          } else {
            aij *= (1.0 - getkij(temperature, compNumb, j));
          }
        A += compArray[j].getNumberOfMolesInPhase() * aij;
      }
      return 2.0 * A;
    }

     @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0;
      boolean isAqueous = phase.getComponent("water").getx() > 0.8;
      double salinityConcentration = 0.0;
      if (isAqueous) {
        salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
      }
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int j = 0; j < numbcomp; j++) {
        aij = 0.5 / Math.sqrt(compArray[compNumb].getaT() * compArray[j].getaT())
            * (compArray[compNumb].getaT() * compArray[j].getaDiffT()
                + compArray[j].getaT() * compArray[compNumb].getaDiffT());
        if (isAqueous) {
            aij *= (1.0 - getkijWhitsonSoreideAqueous(compArray, salinityConcentration, temperature, compNumb, j));
          } else {
            aij *= (1.0 - getkij(temperature, compNumb, j));
        }
        A += compArray[j].getNumberOfMolesInPhase() * aij;
      }
      // System.out.println("Ait SRK : " + (2*A));
      return 2.0 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double aij = 0;
      boolean isAqueous = phase.getComponent("water").getx() > 0.8;
      double salinityConcentration = 0.0;
      if (isAqueous) {
        salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
      }
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      aij = Math.sqrt(compArray[compNumb].getaT() * compArray[compNumbj].getaT());
      if (isAqueous) {
            aij *= (1.0 - getkijWhitsonSoreideAqueous(compArray, salinityConcentration, temperature, compNumb, compNumbj));
          } else {
            aij *= (1.0 - getkij(temperature, compNumb, compNumbj));
        }
      return 2.0 * aij;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      for (int i = 0; i < numbcomp; i++) {
        A += compArray[i].getNumberOfMolesInPhase()
            * ((ComponentEosInterface) phase.getComponent(i)).getAiT();
        // phase.calcAiT(i, phase, temperature, pressure, numbcomp);
      }
      // System.out.println("AT SRK: " + (0.5*A));
      return 0.5 * A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double aij = 0;
      double temp1;
      double[] sqrtai = new double[numbcomp];
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      boolean isAqueous = phase.getComponent("water").getx() > 0.8;
      double salinityConcentration = 0.0;
      if (isAqueous) {
        salinityConcentration = ((PhaseSoreideWhitson) phase).getSalinityConcentration();
      }
      for (int i = 0; i < numbcomp; i++) {
        sqrtai[i] = Math.sqrt(compArray[i].getaT());
      }

      A = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getNumberOfmoles() < 1e-100) {
          continue;
        }
        for (int j = 0; j < numbcomp; j++) {
          if (compArray[j].getNumberOfmoles() < 1e-100) {
            continue;
          }
          temp1 = compArray[i].getaT() * compArray[j].getaDiffT()
              + compArray[j].getaT() * compArray[i].getaDiffT();
          aij = 0.5
              * ((2.0 * compArray[i].getaDiffT() * compArray[j].getaDiffT()
                  + compArray[i].getaT() * compArray[j].getaDiffDiffT()
                  + compArray[j].getaT() * compArray[i].getaDiffDiffT()) / sqrtai[i] / sqrtai[j]
                  - temp1 * temp1
                      / (2.0 * sqrtai[i] * sqrtai[j] * compArray[i].getaT() * compArray[j].getaT()));
          if (isAqueous) {
            aij *= (1.0 - getkijWhitsonSoreideAqueous(compArray, salinityConcentration, temperature, i, j));
          } else {
            aij *= (1.0 - getkij(temperature, i, j));
          }
          A += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * aij;
        }
      }
      return A;
    }

  }

  

  public class SRKHuronVidal extends ClassicSRK implements HVMixingRulesInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    PhaseInterface orgPhase;
    PhaseGENRTLmodifiedHV gePhase;
    double hwfc = 0;
    // double[][] HValpha, HVgij;

    public SRKHuronVidal(PhaseInterface phase, double[][] HValpha, double[][] HVDij,
        String[][] mixRule) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      this.orgPhase = phase;
      hwfc =
          1.0 / (compArray[0].getDeltaEosParameters()[1] - compArray[0].getDeltaEosParameters()[0])
              * Math.log((1.0 + compArray[0].getDeltaEosParameters()[1])
                  / (1.0 + compArray[0].getDeltaEosParameters()[0]));
      gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, mixRule, intparam);
      gePhase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(), phase.getTemperature(),
          phase.getPressure(), PhaseType.GAS);
      gePhase.setProperties(phase);
    }

    public SRKHuronVidal(PhaseInterface phase, double[][] HValpha, double[][] HVDij,
        double[][] HVDijT, String[][] mixRule) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      this.orgPhase = phase;
      hwfc =
          1.0 / (compArray[0].getDeltaEosParameters()[1] - compArray[0].getDeltaEosParameters()[0])
              * Math.log((1.0 + compArray[0].getDeltaEosParameters()[1])
                  / (1.0 + compArray[0].getDeltaEosParameters()[0]));
      gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, HVDijT, mixRule, intparam);
      gePhase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(), phase.getTemperature(),
          phase.getPressure(), PhaseType.GAS);
      gePhase.setProperties(phase);
    }

    /** {@inheritDoc} */
    @Override
    public void setHVDijParameter(int i, int j, double value) {
      HVDij[i][j] = value;
      // System.out.println("hv " + value);
      // HVDij[j][i] = value;
      gePhase.setDij(HVDij);
    }

    /** {@inheritDoc} */
    @Override
    public double getHVDijParameter(int i, int j) {
      return HVDij[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setHVDijTParameter(int i, int j, double value) {
      HVDijT[i][j] = value;
      // HVDijT[j][i] = value;
      gePhase.setDijT(HVDijT);
    }

    /** {@inheritDoc} */
    @Override
    public double getHVDijTParameter(int i, int j) {
      return HVDijT[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setHValphaParameter(int i, int j, double value) {
      HValpha[i][j] = value;
      HValpha[j][i] = value;
      gePhase.setAlpha(HValpha);
    }

    /** {@inheritDoc} */
    @Override
    public double getHValphaParameter(int i, int j) {
      return HValpha[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getKijWongSandler(int i, int j) {
      return WSintparam[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setKijWongSandler(int i, int j, double value) {
      WSintparam[i][j] = value;
      WSintparam[j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      double aij = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      gePhase.setProperties(phase);

      for (int i = 0; i < numbcomp; i++) {
        aij = compArray[i].getaT() / compArray[i].getb();
        A += compArray[i].getNumberOfMolesInPhase() * aij;
      }
      A = calcB(phase, temperature, pressure, numbcomp) * (A - phase.getNumberOfMolesInPhase()
          * gePhase.getExcessGibbsEnergy(phase, phase.getNumberOfComponents(),
              phase.getTemperature(), phase.getPressure(), PhaseType.LIQUID)
          / gePhase.getNumberOfMolesInPhase() / hwfc);
      Atot = A;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      double aij = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      aij = compArray[compNumb].getaT() / compArray[compNumb].getb();
      A = getB() * (aij - R * temperature
          * Math.log(((ComponentGEInterface) gePhase.getComponent(compNumb)).getGamma()) / hwfc);

      A += getA() * calcBi(compNumb, phase, temperature, pressure, numbcomp) / getB();
      // System.out.println("Ai HV : " + A);
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0;

      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = getB()
          * (compArray[compNumb].getaDiffT() / compArray[compNumb].getb()
              - R * Math.log(((ComponentGEInterface) gePhase.getComponent(compNumb)).getGamma())
                  / hwfc
              - R * temperature
                  * ((ComponentGEInterface) gePhase.getComponent(compNumb)).getlnGammadt() / hwfc)
          + compArray[compNumb].getb() * calcAT(phase, temperature, pressure, numbcomp) / getB();
      // 0.5/Math.sqrt(compArray[compNumb].getaT()*compArray[j].getaT())*(compArray[compNumb].getaT()
      // * compArray[j].getaDiffT() +compArray[j].getaT() *
      // compArray[compNumb].getaDiffT())*(1-intparam[compNumb][j]);

      // System.out.println("Ait HV: " + A);

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      for (int i = 0; i < numbcomp; i++) {
        A += compArray[i].getNumberOfMolesInPhase()
            * (compArray[i].getaDiffT() / compArray[i].getb()
                - R * Math.log(((ComponentGEInterface) gePhase.getComponent(i)).getGamma()) / hwfc
                - R * temperature * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadt()
                    / Math.log(2.0));
        // ....);
        // 0.5/Math.sqrt(compArray[compNumb].getaT()*compArray[j].getaT())*(compArray[compNumb].getaT()
        // * compArray[j].getaDiffT() +compArray[j].getaT() *
        // compArray[compNumb].getaDiffT())*(1-intparam[compNumb][j]);
      }

      A *= getB();
      // System.out.println("AT HV: " + A);
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double aij = compArray[compNumbj].getb()
          * (compArray[compNumb].getaT() / compArray[compNumb].getb() - R * temperature
              * Math.log(((ComponentGEInterface) gePhase.getComponent(compNumb)).getGamma()) / hwfc)
          - getB() * R * temperature / hwfc
              * ((ComponentGEInterface) gePhase.getComponent(compNumb)).getlnGammadn(compNumbj)
          + compArray[compNumb].getb()
              * (compArray[compNumbj].getaT() / compArray[compNumbj].getb() - R * temperature
                  * Math.log(((ComponentGEInterface) gePhase.getComponents()[compNumbj]).getGamma())
                  / hwfc);
      return aij;
    }
  }

  public class SRKHuronVidal2 extends ClassicSRK implements HVMixingRulesInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    PhaseInterface orgPhase;
    PhaseGE gePhase;
    double Q = 0;

    double QT = 0;

    double DDE2;

    double alpha_mix = 0;

    double dadt = 0;

    double b_mix = 0;

    double dbdt = 0;

    double bdert = 0;

    double d2adt2 = 0;

    double d2bdt2 = 0;

    double[] ader;

    double[] adert;

    double[] qf1;

    double[] d2qp;

    double[] qPure;

    double[] qPuredT;

    double[] qPuredTdT;

    double[][] ad2;

    double[][] qf2;

    double[][] qft;

    double[][] bd2;

    double hwfc = 0;

    double gex = 0;

    double[] oneSubAlf;

    double[] abf;

    double[] bc;

    double[] abft2;

    double[] abft;

    double[] QFTD;

    double[] BDER;

    double[] BDERT;

    public SRKHuronVidal2(PhaseInterface phase, double[][] HValpha, double[][] HVDij,
        String[][] mixRule) {
      this.orgPhase = phase;

      if (mixingRuleGEModel.equals("NRTL")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("NRTL_HV")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIQUAQ")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC")) {
        gePhase = new PhaseGEUnifac(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC_PSRK")) {
        gePhase = new PhaseGEUnifacPSRK(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC_UMRPRU")) {
        gePhase = new PhaseGEUnifacUMRPRU(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else {
        gePhase = new PhaseGEUnifac(orgPhase, HValpha, HVDij, mixRule, intparam);
      }
      gePhase.init(phase.getNumberOfMolesInPhase(), phase.getNumberOfComponents(), 0,
          phase.getType(), phase.getBeta());
      gePhase.setProperties(phase);
    }

    public SRKHuronVidal2(PhaseInterface phase, double[][] HValpha, double[][] HVDij,
        double[][] HVDijT, String[][] mixRule) {
      this.orgPhase = phase;

      if (mixingRuleGEModel.equals("NRTL")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, HVDijT, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("NRTL_HV")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIQUAQ")) {
        gePhase = new PhaseGENRTLmodifiedHV(orgPhase, HValpha, HVDij, HVDijT, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC")) {
        gePhase = new PhaseGEUnifac(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC_PSRK")) {
        gePhase = new PhaseGEUnifacPSRK(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else if (mixingRuleGEModel.equals("UNIFAC_UMRPRU")) {
        gePhase = new PhaseGEUnifacUMRPRU(orgPhase, HValpha, HVDij, mixRule, intparam);
      } else {
        gePhase = new PhaseGEUnifac(orgPhase, HValpha, HVDij, mixRule, intparam);
      }
      gePhase.setProperties(phase);
      // gePhase.init(phase.getNumberOfMolesInPhase() ,
      // phase.getNumberOfComponents(),0,phase.getType(),phase.getBeta());
    }

    /**
     * init.
     *
     * @param phase Phase to initialize for.
     * @param temperature Temperature to initialize at.
     * @param pressure Pressure to initialize at.
     * @param numbcomp Number of components.
     */
    public void init(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      gePhase.setProperties(phase);
      gePhase.setParams(phase, HValpha, HVDij, HVDijT, classicOrHV, intparam);

      if (mixingRuleGEModel.equals("NRTL")) {
        gePhase.getExcessGibbsEnergy(phase, numbcomp, temperature, pressure, phase.getType());
      } else {
        gePhase.init((phase.getNumberOfMolesInPhase() / phase.getBeta()),
            phase.getNumberOfComponents(), phase.getInitType(), phase.getType(), phase.getBeta());
      }

      hwfc = -1.0 / (1.0
          / (compArray[0].getDeltaEosParameters()[1] - compArray[0].getDeltaEosParameters()[0])
          * Math.log((1.0 + compArray[0].getDeltaEosParameters()[1])
              / (1.0 + compArray[0].getDeltaEosParameters()[0])));
      if (mixingRuleGEModel.equals("UNIFAC_PSRK")) {
        hwfc = -1.0 / 0.64663;
      }
      if (mixingRuleGEModel.equals("UNIFAC_UMRPRU")) {
        // hwfc = -1.0 / 0.64663;
        hwfc = -1.0 / 0.53;
      }

      if (qPure == null) {
        qPure = new double[numbcomp];
        qPuredT = new double[numbcomp];
        qPuredTdT = new double[numbcomp];
        ader = new double[numbcomp];
        adert = new double[numbcomp];
        ad2 = new double[numbcomp][numbcomp];
        qf2 = new double[numbcomp][numbcomp];
        bd2 = new double[numbcomp][numbcomp];
        qft = new double[numbcomp][numbcomp];
        qf1 = new double[numbcomp];
        oneSubAlf = new double[numbcomp];
        abf = new double[numbcomp];
        bc = new double[numbcomp];
        QFTD = new double[numbcomp];
      }
      for (int i = 0; i < numbcomp; i++) {
        qPure[i] = compArray[i].getaT() / (compArray[i].getb() * R * temperature);
        if (phase.getInitType() > 1) {
          qPuredT[i] = -compArray[i].getaT() / (compArray[i].getb() * R * temperature * temperature)
              + compArray[i].diffaT(temperature) / (compArray[i].getb() * R * temperature);
          qPuredTdT[i] =
              2.0 * compArray[i].getaT() / (compArray[i].getb() * R * Math.pow(temperature, 3.0))
                  - compArray[i].getaDiffT() / (compArray[i].getb() * R * temperature * temperature)
                  + compArray[i].getaDiffDiffT() / (compArray[i].getb() * R * temperature)
                  - compArray[i].getaDiffT()
                      / (compArray[i].getb() * R * temperature * temperature);
        }
      }

      alpha_mix = 0.0;
      dadt = 0.0;

      double term = 0.0;
      double dubdert = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        term =
            qPure[i] + hwfc * Math.log(((ComponentGEInterface) gePhase.getComponent(i)).getGamma());
        alpha_mix += phase.getComponent(i).getNumberOfMolesInPhase()
            / phase.getNumberOfMolesInPhase() * term;
        ader[i] = term;
        compArray[i].setAder(ader[i]);

        if (phase.getInitType() > 1) {
          term =
              qPuredT[i] + hwfc * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadt();
          dubdert += (qPuredTdT[i]
              + hwfc * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadtdt())
              * phase.getComponent(i).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase();
          dadt += term * phase.getComponent(i).getNumberOfMolesInPhase()
              / phase.getNumberOfMolesInPhase();
          adert[i] = term;
          compArray[i].setdAdTdn(adert[i]);
        }
      }

      d2adt2 = dubdert;

      if (phase.getInitType() > 2) {
        for (int i = 0; i < numbcomp; i++) {
          for (int j = 0; j < numbcomp; j++) {
            ad2[i][j] = hwfc * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadn(j);
            compArray[i].setdAdndn(j, ad2[i][j]);
          }
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public PhaseInterface getGEPhase() {
      return gePhase;
    }

    /** {@inheritDoc} */
    @Override
    public void setHVDijParameter(int i, int j, double value) {
      HVDij[i][j] = value;
      gePhase.setDij(HVDij);
    }

    /** {@inheritDoc} */
    @Override
    public double getHVDijParameter(int i, int j) {
      return HVDij[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setHVDijTParameter(int i, int j, double value) {
      HVDijT[i][j] = value;
      gePhase.setDijT(HVDijT);
    }

    /** {@inheritDoc} */
    @Override
    public double getHVDijTParameter(int i, int j) {
      return HVDijT[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setHValphaParameter(int i, int j, double value) {
      HValpha[i][j] = value;
      HValpha[j][i] = value;
      gePhase.setAlpha(HValpha);
    }

    /** {@inheritDoc} */
    @Override
    public double getHValphaParameter(int i, int j) {
      return HValpha[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getKijWongSandler(int i, int j) {
      return WSintparam[i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setKijWongSandler(int i, int j, double value) {
      WSintparam[i][j] = value;
      WSintparam[j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      this.init(phase, temperature, pressure, numbcomp);
      // A = phase.getNumberOfMolesInPhase() * calcB(phase, temperature, pressure,
      // numbcomp) * R * temperature * alpha_mix;
      A = phase.getNumberOfMolesInPhase() * phase.getB() * R * temperature * alpha_mix;

      // System.out.println("A: " + A);
      Atot = A;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      // A = getB() * R * temperature * compArray[compNumb].getAder() +
      // phase.getNumberOfMolesInPhase() * calcBi(compNumb, phase, temperature,
      // pressure, numbcomp) * R * temperature * alpha_mix;
      A = getB() * R * temperature * compArray[compNumb].getAder() + phase.getNumberOfMolesInPhase()
          * compArray[compNumb].getBi() * R * temperature * alpha_mix;
      // calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature *
      // alpha_mix;

      // System.out.println("Ai: " + A);
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double A = 0.0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = calcBi(compNumbj, phase, temperature, pressure, numbcomp) * R * temperature
          * compArray[compNumb].getAder()
          + getB() * R * temperature * compArray[compNumb].getdAdndn(compNumbj)
          + calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature
              * compArray[compNumbj].getAder()
          + calcBij(compNumb, compNumbj, phase, temperature, pressure, numbcomp)
              * phase.getNumberOfMolesInPhase() * R * temperature * alpha_mix;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      // ComponentEosInterface[] compArray = (ComponentEosInterface[])
      // phase.getcomponentArray();

      A = phase.getNumberOfMolesInPhase() * getB() * R * temperature * dadt
          + phase.getNumberOfMolesInPhase() * getB() * R * alpha_mix;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0.0;
      // A = Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * temperature
      // * d2adt2 + Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * dadt
      // + Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * dadt;
      A = phase.getNumberOfMolesInPhase() * getB() * R * temperature * d2adt2
          + 2.0 * phase.getNumberOfMolesInPhase() * getB() * R * dadt;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      double A = getB() * R * compArray[compNumb].getAder()
          + getB() * R * temperature * compArray[compNumb].getdAdTdn()
          + phase.getNumberOfMolesInPhase()
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature * dadt
          + phase.getNumberOfMolesInPhase()
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * alpha_mix;

      return A;
    }
  }

  public class WongSandlerMixingRule extends SRKHuronVidal2 {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double Q = 0;

    double QT = 0;

    double DDE2;

    double alpha_mix = 0;

    double dadt = 0;

    double b_mix = 0;

    double dbdt = 0;

    double bdert = 0;

    double d2adt2 = 0;

    double d2bdt2 = 0;

    double[] ader;

    double[] adert;

    double[] qf1;

    double[] d2qp;

    double[] qPure;

    double[] qPuredT;

    double[] qPuredTdT;

    double[][] ad2;

    double[][] qf2;

    double[][] qft;

    double[][] bd2;

    double hwfc = 0;

    double gex = 0;

    double hex = 0;

    double cpex = 0;

    double[] oneSubAlf;

    double[] abf;

    double[] bc;

    double[] abft2;

    double[] abft;

    double[] QFTD;

    double[] BDER;

    double[] BDERT;

    public WongSandlerMixingRule(PhaseInterface phase, double[][] WSalpha, double[][] WSDij,
        String[][] mixRule) {
      super(phase, WSalpha, WSDij, mixRule);
    }

    public WongSandlerMixingRule(PhaseInterface phase, double[][] WSalpha, double[][] WSDij,
        double[][] WSDijT, String[][] mixRule) {
      super(phase, WSalpha, WSDij, WSDijT, mixRule);
    }

    /** {@inheritDoc} */
    @Override
    public void init(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      gePhase.setProperties(phase);

      if (mixingRuleGEModel.equals("NRTL")) {
        gePhase.getExcessGibbsEnergy(phase, numbcomp, temperature, pressure, phase.getType());
      } else {
        gePhase.init(phase.getNumberOfMolesInPhase(), phase.getNumberOfComponents(), 3,
            phase.getType(), phase.getBeta());
      }

      hwfc = -1.0 / (1.0
          / (compArray[0].getDeltaEosParameters()[1] - compArray[0].getDeltaEosParameters()[0])
          * Math.log((1.0 + compArray[0].getDeltaEosParameters()[1])
              / (1.0 + compArray[0].getDeltaEosParameters()[0])));

      double term = 0.0;
      qPure = new double[numbcomp];
      qPuredT = new double[numbcomp];
      qPuredTdT = new double[numbcomp];
      ader = new double[numbcomp];
      adert = new double[numbcomp];
      ad2 = new double[numbcomp][numbcomp];
      qf2 = new double[numbcomp][numbcomp];
      bd2 = new double[numbcomp][numbcomp];
      qft = new double[numbcomp][numbcomp];
      qf1 = new double[numbcomp];
      oneSubAlf = new double[numbcomp];
      abf = new double[numbcomp];
      bc = new double[numbcomp];
      abft2 = new double[numbcomp];
      abft = new double[numbcomp];
      QFTD = new double[numbcomp];
      BDER = new double[numbcomp];
      BDERT = new double[numbcomp];
      // first part
      hex = phase.getHresTP() / phase.getNumberOfMolesInPhase();
      cpex = phase.getCpres() / phase.getNumberOfMolesInPhase();

      for (int i = 0; i < numbcomp; i++) {
        qPure[i] = compArray[i].getaT() / (compArray[i].getb() * R * temperature);
        qPuredT[i] = -compArray[i].getaT() / (compArray[i].getb() * R * temperature * temperature)
            + compArray[i].diffaT(temperature) / (compArray[i].getb() * R * temperature);
        qPuredTdT[i] =
            2.0 * compArray[i].getaT() / (compArray[i].getb() * R * Math.pow(temperature, 3.0))
                - compArray[i].getaDiffT() / (compArray[i].getb() * R * Math.pow(temperature, 2.0))
                + compArray[i].getaDiffDiffT() / (compArray[i].getb() * R * temperature)
                - compArray[i].getaDiffT() / (compArray[i].getb() * R * Math.pow(temperature, 2.0));
      }

      double sd2 = (2 * hex - cpex * temperature) / Math.pow(temperature, 3.0);
      double cnt2 = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        cnt2 += phase.getComponent(i).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase()
            * qPuredTdT[i];
        // second part
      }
      alpha_mix = 0.0;
      dadt = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        term =
            qPure[i] + hwfc * Math.log(((ComponentGEInterface) gePhase.getComponent(i)).getGamma());
        alpha_mix += phase.getComponent(i).getNumberOfMolesInPhase()
            / phase.getNumberOfMolesInPhase() * term;
        ader[i] = term;
        compArray[i].setAder(ader[i]);

        term = qPuredT[i] + hwfc * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadt();
        dadt += term * phase.getComponent(i).getNumberOfMolesInPhase()
            / phase.getNumberOfMolesInPhase();
        adert[i] = term;
        compArray[i].setdAdTdn(adert[i]);
      }
      d2adt2 = cnt2 + hwfc * sd2;
      // TODO implment hex and Cpex and set dAdTdT
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          ad2[i][j] = hwfc * ((ComponentGEInterface) gePhase.getComponent(i)).getlnGammadn(j);
          compArray[i].setdAdndn(j, ad2[i][j]);
        }
      }

      // double rhs = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        oneSubAlf[i] = 1.0 - qPure[i];
        abf[i] = compArray[i].getb() * oneSubAlf[i];
        abft[i] = -compArray[i].getb() * qPuredT[i];
        abft2[i] = -compArray[i].getb() * qPuredTdT[i];
      }

      double dd2 = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        double ssi = 0.0;
        for (int j = 0; j < numbcomp; j++) {
          ssi += (1.0 - WSintparam[i][j]) * (abft2[i] + abft2[j])
              * phase.getComponent(j).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase();
        }
        dd2 +=
            phase.getComponent(i).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase() * ssi;
      }
      dd2 = 0.5 * dd2;

      for (int i = 0; i < numbcomp; i++) {
        for (int j = i; j < numbcomp; j++) {
          double ee = 1.0 - WSintparam[i][j];
          qf2[i][j] = ee * (abf[i] + abf[j]);
          qf2[j][i] = qf2[i][j];

          qft[i][j] = ee * (abft[i] + abft[j]);
          qft[j][i] = qft[i][j];
        }
      }
      Q = 0.0;
      QT = 0.0;

      for (int i = 0; i < numbcomp; i++) {
        double ss = 0.0;
        for (int j = 0; j < numbcomp; j++) {
          ss += qf2[j][i] * phase.getComponent(j).getNumberOfMolesInPhase()
              / phase.getNumberOfMolesInPhase();
        }
        qf1[i] = ss;
        Q += phase.getComponent(i).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase() * ss;
        double sst = 0.0;
        for (int j = 0; j < numbcomp; j++) {
          sst += qft[j][i] * phase.getComponent(j).getNumberOfMolesInPhase()
              / phase.getNumberOfMolesInPhase();
        }
        QFTD[i] = sst;
        QT +=
            phase.getComponent(i).getNumberOfMolesInPhase() / phase.getNumberOfMolesInPhase() * sst;
      }
      double d_mix = 0.5 * Q;
      double d_mixt = 0.5 * QT;

      double enum1 = 1.0 - alpha_mix;
      double enumr = 1.0 / enum1;
      b_mix = d_mix * enumr;
      dbdt = (d_mixt + b_mix * dadt) * enumr;

      for (int i = 0; i < numbcomp; i++) {
        BDER[i] = (qf1[i] - b_mix * (1.0 - ader[i])) * enumr;
        compArray[i].setBder(BDER[i]);
        double ss = QFTD[i] + b_mix * adert[i] + BDER[i] * dadt + dbdt * (ader[i] - 1.0);
        BDERT[i] = ss * enumr;
        compArray[i].setdBdndT(BDERT[i]);
      }

      double DD2E = dd2 + b_mix * d2adt2 + 2.0 * dbdt * dadt;
      d2bdt2 = DD2E / (1.0 - alpha_mix);

      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          bd2[i][j] =
              (qf2[i][j] + b_mix * ad2[i][j] + BDER[j] * ader[i] + BDER[i] * ader[j]) * enumr;
          compArray[i].setdBdndn(j, ad2[i][j]);
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public double calcA(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0;
      // super.calcA(phase, temperature, pressure, numbcomp);
      this.init(phase, temperature, pressure, numbcomp);
      A = Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
          * calcB(phase, temperature, pressure, numbcomp) * R * temperature * alpha_mix;
      Atot = A;
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      double A = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = getB() * R * temperature * compArray[compNumb].getAder()
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature
              * alpha_mix;

      // System.out.println("Ai: " + A);
      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0;
      // ComponentEosInterface[] compArray = (ComponentEosInterface[])
      // phase.getcomponentArray();

      A = Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * temperature * dadt
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * alpha_mix
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBT(phase, temperature, pressure, numbcomp) * R * temperature * alpha_mix;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcATT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double A = 0;

      A = Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * temperature * d2adt2
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * dadt
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0) * getB() * R * dadt
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBTT(phase, temperature, pressure, numbcomp) * R * temperature * alpha_mix
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBT(phase, temperature, pressure, numbcomp) * R * alpha_mix
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBT(phase, temperature, pressure, numbcomp) * R * temperature * dadt;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double A = getB() * R * compArray[compNumb].getAder()
          + getB() * R * temperature * compArray[compNumb].getdAdTdn()
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature * dadt
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * alpha_mix
          + calcBT(phase, temperature, pressure, numbcomp) * R * temperature
              * compArray[compNumb].getAder()
          + Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
              * calcBiT(compNumb, phase, temperature, pressure, numbcomp) * R * temperature
              * alpha_mix;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      double A = 0;
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      A = Math.pow(phase.getNumberOfMolesInPhase(), 0.0)
          * calcBi(compNumbj, phase, temperature, pressure, numbcomp) * R * temperature
          * compArray[compNumb].getAder()
          + Math.pow(phase.getNumberOfMolesInPhase(), 0.0)
              * calcB(phase, temperature, pressure, numbcomp) * R * temperature
              * compArray[compNumb].getdAdndn(compNumbj)
          + Math.pow(phase.getNumberOfMolesInPhase(), 0.0)
              * calcBi(compNumb, phase, temperature, pressure, numbcomp) * R * temperature
              * compArray[compNumbj].getAder()
          + Math.pow(phase.getNumberOfMolesInPhase(), 0.0)
              * calcBij(compNumb, compNumbj, phase, temperature, pressure, numbcomp) * R
              * temperature * alpha_mix;

      return A;
    }

    /** {@inheritDoc} */
    @Override
    public double calcB(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double B = b_mix * phase.getNumberOfMolesInPhase();
      Btot = B;
      return B;
    }

    /** {@inheritDoc} */
    @Override
    public double calcBi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      return compArray[compNumb].getBder();
    }

    public double calcBT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return dbdt * phase.getNumberOfMolesInPhase();
    }

    public double calcBTT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return d2bdt2 * phase.getNumberOfMolesInPhase();
    }

    public double calcBiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double bit = compArray[compNumb].getdBdndT();
      return bit;
    }

    /** {@inheritDoc} */
    @Override
    public double calcBij(int compNumb, int compNumbj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      return compArray[compNumb].getdBdndn(compNumbj);
    }
  }

  public class ElectrolyteMixRule implements ElectrolyteMixingRulesInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return mixingRuleName;
    }

    public ElectrolyteMixRule(PhaseInterface phase) {
      calcWij(phase);
    }

    /** {@inheritDoc} */
    @Override
    public void calcWij(PhaseInterface phase) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      int numbcomp = phase.getNumberOfComponents();

      // System.out.println("numb comp " + numbcomp);
      for (int i = 0; i < numbcomp; i++) {
        if (compArray[i].getIonicCharge() > 0) {
          for (int j = 0; j < numbcomp; j++) {
            if (wijCalcOrFitted[i][j] == 0) {
              if (compArray[j].getComponentName().equals("water")
                  || compArray[j].getComponentName().equals("MDEA")
                  || compArray[j].getComponentName().equals("Piperazine")) { // compArray[j].getIonicCharge()==0){
                wij[0][i][j] =
                    neqsim.thermo.util.constants.FurstElectrolyteConstants.getFurstParam(2)
                        * compArray[i].getStokesCationicDiameter()
                        + neqsim.thermo.util.constants.FurstElectrolyteConstants.getFurstParam(3);
              }
              // if(compArray[j].getComponentName().equals("MDEA")){
              // wij[0][i][j] =
              // (thermo.util.constants.FurstElectrolyteConstants.getFurstParamMDEA(2)
              // *
              // compArray[i].getStokesCationicDiameter() +
              // thermo.util.constants.FurstElectrolyteConstants.getFurstParamMDEA(3));
              // }
              if (compArray[j].getIonicCharge() < -0.01) {
                wij[0][i][j] =
                    neqsim.thermo.util.constants.FurstElectrolyteConstants.getFurstParam(4)
                        * Math.pow(compArray[i].getStokesCationicDiameter()
                            + compArray[j].getPaulingAnionicDiameter(), 4.0)
                        + neqsim.thermo.util.constants.FurstElectrolyteConstants.getFurstParam(5);
              }
              wij[0][j][i] = wij[0][i][j];
            }
          }
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public double calcWij(int compNumbi, int compNumj, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      return -2.0 * getWij(compNumbi, compNumj, temperature); // iwij[0][compNumbi][compNumj];
    }

    /** {@inheritDoc} */
    @Override
    public void setWijParameter(int i, int j, double value) {
      // System.out.println("intparam: " + value);
      wij[0][i][j] = value;
      wij[0][j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public double getWijParameter(int i, int j) {
      return wij[0][i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setWijT1Parameter(int i, int j, double value) {
      wij[1][i][j] = value;
      wij[1][j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public double gettWijT1Parameter(int i, int j) {
      return wij[1][i][j];
    }

    /** {@inheritDoc} */
    @Override
    public void setWijT2Parameter(int i, int j, double value) {
      wij[2][i][j] = value;
      wij[2][j][i] = value;
    }

    /** {@inheritDoc} */
    @Override
    public double gettWijT2Parameter(int i, int j) {
      return wij[2][i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getWij(int i, int j, double temperature) {
      return wij[0][i][j] + wij[1][i][j] * (1.0 / temperature - 1.0 / 298.15)
          + wij[2][i][j] * ((298.15 - temperature) / temperature + Math.log(temperature / 298.15));
    }

    /** {@inheritDoc} */
    @Override
    public double getWijT(int i, int j, double temperature) {
      return (-wij[1][i][j] / (temperature * temperature)
          - wij[2][i][j] * (298.15 - temperature) / (temperature * temperature));
    }

    /** {@inheritDoc} */
    @Override
    public double getWijTT(int i, int j, double temperature) {
      return (2.0 * wij[1][i][j] / (temperature * temperature * temperature)
          + wij[2][i][j] / (temperature * temperature) + 2.0 * wij[2][i][j] * (298.15 - temperature)
              / (temperature * temperature * temperature));
    }

    /** {@inheritDoc} */
    @Override
    public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double W = 0.0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          W += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * getWij(i, j, temperature); // wij[0][i][j];
        }
      }
      return -W;
    }

    /** {@inheritDoc} */
    @Override
    public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      double Wi = 0.0;
      for (int j = 0; j < numbcomp; j++) {
        Wi += compArray[j].getNumberOfMolesInPhase() * getWij(compNumb, j, temperature);
      }
      return -2.0 * Wi;
    }

    /** {@inheritDoc} */
    @Override
    public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure,
        int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();
      double WiT = 0;
      for (int j = 0; j < numbcomp; j++) {
        WiT += compArray[j].getNumberOfMolesInPhase() * getWijT(compNumb, j, temperature);
      }
      return -2.0 * WiT;
    }

    /** {@inheritDoc} */
    @Override
    public double calcWT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      double WT = 0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          WT += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * getWijT(i, j, temperature); // wij[0][i][j];
        }
      }
      return -WT;
    }

    /** {@inheritDoc} */
    @Override
    public double calcWTT(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      ComponentEosInterface[] compArray = (ComponentEosInterface[]) phase.getcomponentArray();

      double WTT = 0;
      for (int i = 0; i < numbcomp; i++) {
        for (int j = 0; j < numbcomp; j++) {
          WTT += compArray[i].getNumberOfMolesInPhase() * compArray[j].getNumberOfMolesInPhase()
              * getWijTT(i, j, temperature); // wij[0][i][j];
        }
      }
      return -WTT;
    }
  }

  /**
   * <p>
   * getElectrolyteMixingRule.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface} object
   */
  public ElectrolyteMixingRulesInterface getElectrolyteMixingRule(PhaseInterface phase) {
    return new ElectrolyteMixRule(phase);
  }

  /**
   * Getter for property CalcEOSInteractionParameters.
   *
   * @return Value of property CalcEOSInteractionParameters.
   */
  public boolean isCalcEOSInteractionParameters() {
    return calcEOSInteractionParameters;
  }

  /**
   * <p>
   * Setter for the field <code>calcEOSInteractionParameters</code>.
   * </p>
   *
   * @param CalcEOSInteractionParameters2 a boolean
   */
  public void setCalcEOSInteractionParameters(boolean CalcEOSInteractionParameters2) {
    calcEOSInteractionParameters = CalcEOSInteractionParameters2;
  }

  /**
   * Getter for property mixingRuleName.
   *
   * @return Value of property mixingRuleName.
   */
  public java.lang.String getMixingRuleName() {
    return mixingRuleName;
  }

  /**
   * Setter for property mixingRuleName.
   *
   * @param mixingRuleName New value of property mixingRuleName.
   */
  public void setMixingRuleName(java.lang.String mixingRuleName) {
    this.mixingRuleName = mixingRuleName;
  }

  /**
   * <p>
   * Setter for the field <code>mixingRuleGEModel</code>.
   * </p>
   *
   * @param GEmodel a {@link java.lang.String} object
   */
  public void setMixingRuleGEModel(java.lang.String GEmodel) {
    this.mixingRuleGEModel = GEmodel;
  }

  /**
   * <p>
   * getSRKbinaryInteractionParameters.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getSRKbinaryInteractionParameters() {
    return intparam;
  }

  /**
   * Getter for property HVDij.
   *
   * @return Value of property HVDij.
   */
  public double[][] getHVDij() {
    return this.HVDij;
  }

  /**
   * Getter for property HValpha.
   *
   * @return Value of property HValpha.
   */
  public double[][] getHValpha() {
    return this.HValpha;
  }

  /**
   * Getter for property HVDijT.
   *
   * @return Value of property HVDijT.
   */
  public double[][] getHVDijT() {
    return this.HVDijT;
  }

  /**
   * Getter for property NRTLDij.
   *
   * @return Value of property NRTLDij.
   */
  public double[][] getNRTLDij() {
    return this.NRTLDij;
  }

  /**
   * Getter for property NRTLalpha.
   *
   * @return Value of property NRTLalpha.
   */
  public double[][] getNRTLalpha() {
    return this.NRTLalpha;
  }

  /**
   * Getter for property NRTLDijT.
   *
   * @return Value of property NRTLDijT.
   */
  public double[][] getNRTLDijT() {
    return this.NRTLDijT;
  }

  /**
   * Getter for property WSintparam.
   *
   * @return Value of property WSintparam.
   */
  public double[][] getWSintparam() {
    return this.WSintparam;
  }

  /**
   * Getter for property classicOrHV.
   *
   * @return Value of property classicOrHV.
   */
  public java.lang.String[][] getClassicOrHV() {
    return this.classicOrHV;
  }

  /**
   * Getter for property classicOrWS.
   *
   * @return Value of property classicOrWS.
   */
  public java.lang.String[][] getClassicOrWS() {
    return this.classicOrWS;
  }

  /**
   * <p>
   * displayInteractionCoefficients.
   * </p>
   *
   * @param intType a {@link java.lang.String} object
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayInteractionCoefficients(String intType, PhaseInterface phase) {
    java.text.DecimalFormat nf = new java.text.DecimalFormat();

    java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
    symbols.setDecimalSeparator('.');
    nf.setDecimalFormatSymbols(symbols);

    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    // String[][] table = new String[getPhases()[0].getNumberOfComponents() +
    // 30][7];
    String[][] interactTable =
        new String[phase.getNumberOfComponents() + 1][phase.getNumberOfComponents() + 1];
    String[] names = new String[phase.getNumberOfComponents() + 1];
    names[0] = "";
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      interactTable[i + 1][0] = phase.getComponent(i).getComponentName();
      interactTable[0][i + 1] = phase.getComponent(i).getComponentName();
      names[i + 1] = "";
      for (int j = 0; j < phase.getNumberOfComponents(); j++) {
        interactTable[i + 1][j + 1] = Double.toString(intparam[i][j]);
      }
    }

    JFrame dialog = new JFrame("Interaction coefficients");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    JTable Jtab = new JTable(interactTable, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600); // pack();
    // \\dialog.pack();
    dialog.setVisible(true);
  }
}
