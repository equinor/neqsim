package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import org.jfree.chart.JFreeChart;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.OperationInterface;

public class PTphaseEnvelopeNew3 implements OperationInterface {
  // Fields
  private final SystemInterface system;
  private double[][] betta;
  private boolean[][] bettaTransitionRegion;
  double[] pressures;
  double[] temperatures;

  private double[] dewPointTemperatures;
  private double[] dewPointPressures;

  // Lists to store phase envelope points
  private java.util.List<Double> pressurePhaseEnvelope = new java.util.ArrayList<>();
  private java.util.List<Double> temperaturePhaseEnvelope = new java.util.ArrayList<>();

  // Data structure to store refined transition points
  private java.util.List<double[]> refinedTransitionPoints = new java.util.ArrayList<>(); // [pressure,
                                                                                          // temperature,
                                                                                          // betta]

  private double minPressure, maxPressure, minTemp, maxTemp, pressureStep, tempStep;

  // Constructor
  public PTphaseEnvelopeNew3(SystemInterface system, double minPressure, double maxPressure,
      double minTemp, double maxTemp, double pressureStep, double tempStep) {
    this.system = system;
    this.minPressure = minPressure;
    this.maxPressure = maxPressure;
    this.minTemp = minTemp;
    this.maxTemp = maxTemp;
    this.pressureStep = pressureStep;
    this.tempStep = tempStep;
  }



  // Main calculation method
  public void run() {
    coarse();
    findBettaTransitionsAndRefine();
  }


  // Coarse grid calculation
  public void coarse() {
    neqsim.thermodynamicoperations.ThermodynamicOperations testOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
    int nP = (int) Math.round((maxPressure - minPressure) / pressureStep) + 1;
    int nT = (int) Math.round((maxTemp - minTemp) / tempStep) + 1;
    betta = new double[nT][nP];
    pressures = new double[nP];
    temperatures = new double[nT];
    for (int i = 0; i < nP; i++) {
      pressures[i] = minPressure + i * pressureStep;
    }
    for (int j = 0; j < nT; j++) {
      temperatures[j] = minTemp + j * tempStep;
    }

    for (int j = 0; j < nT; j++) {
      for (int i = 0; i < nP; i++) {
        // System.out.println("Flash calc at i = " + i + ", j = " + j);
        system.setPressure(pressures[i], "bara");
        system.setTemperature(temperatures[j], "C");
        try {
          testOps.TPflash();
        } catch (Exception e) {
          // System.err
          // .println("Flash failed at P=" + pressures[i] + " bara, T=" + temperatures[j] + " C");
          betta[j][i] = Double.NaN;
          continue;
        }
        betta[j][i] = system.getBeta();
        // System.out.println("Calculating phase at P=" + pressures[i] + " bara, T=" +
        // temperatures[j]
        // + " C" + " betta=" + betta[j][i]);
      }
    }
  }

  /**
   * Scan each pressure for betta transitions, refine with smaller step and bisection, and store all
   * transition points.
   */
  public void findBettaTransitionsAndRefine() {
    neqsim.thermodynamicoperations.ThermodynamicOperations testOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
    for (int i = 0; i < pressures.length; i++) {
      for (int j = 1; j < temperatures.length; j++) {
        double bettaPrev = betta[j - 1][i];
        double bettaCurr = betta[j][i];
        if (Double.isNaN(bettaPrev) || Double.isNaN(bettaCurr))
          continue;
        // Look for transition from >=1 to <1 or <1 to >=1
        if ((bettaPrev >= 1.0 && bettaCurr < 1.0) || (bettaPrev < 1.0 && bettaCurr >= 1.0)) {
          double tLow = temperatures[j - 1];
          double tHigh = temperatures[j];
          // Expand window ±10%

          double tStart = tLow;
          double tEnd = tHigh;
          // Bisection method to find transition temperature at this pressure
          double p = pressures[i];
          double tTransition =
              bisectionBettaTransition(testOps, p, tStart, tEnd, 0.9999999, 1e-8, 5);
          // System.out.println("Found transition at P=" + p + " bara, T=" + tTransition);

          if (!Double.isNaN(tTransition)) {
            system.setPressure(p, "bara");
            system.setTemperature(tTransition, "C");
            double bettaVal;
            try {
              testOps.TPflash();
              bettaVal = system.getBeta();
            } catch (Exception e) {
              bettaVal = Double.NaN;
            }
            refinedTransitionPoints.add(new double[] {p, tTransition, bettaVal});
            pressurePhaseEnvelope.add(p);
            temperaturePhaseEnvelope.add(tTransition);
          }
        }
      }
    }
  }

  // Bisection method to find temperature where betta crosses 1.0 at given pressure
  private double bisectionBettaTransition(
      neqsim.thermodynamicoperations.ThermodynamicOperations testOps, double pressure, double tLow,
      double tHigh, double target, double tol, int maxIter) {
    double fLow = getBettaAt(testOps, pressure, tLow) - target;
    double fHigh = getBettaAt(testOps, pressure, tHigh) - target;
    if (Double.isNaN(fLow) || Double.isNaN(fHigh) || fLow * fHigh > 0)
      return Double.NaN;
    for (int iter = 0; iter < maxIter; iter++) {
      double tMid = 0.5 * (tLow + tHigh);
      double fMid = getBettaAt(testOps, pressure, tMid) - target;
      if (Double.isNaN(fMid))
        return Double.NaN;
      if (Math.abs(fMid) < tol)
        return tMid;
      if (fLow * fMid < 0) {
        tHigh = tMid;
        fHigh = fMid;
      } else {
        tLow = tMid;
        fLow = fMid;
      }
    }
    return 0.5 * (tLow + tHigh);
  }

  // Helper to get betta at given P, T
  private double getBettaAt(neqsim.thermodynamicoperations.ThermodynamicOperations testOps,
      double pressure, double temperature) {
    system.setPressure(pressure, "bara");
    system.setTemperature(temperature, "C");
    try {
      testOps.TPflash();
      return system.getBeta();
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  // Accessor for refined transition points
  public java.util.List<double[]> getRefinedTransitionPoints() {
    return refinedTransitionPoints;
  }
  // removed extra closing brace



  // ...existing code...

  public double[] getPressures() {
    return pressures;
  }

  public double[] getTemperatures() {
    return temperatures;
  }

  // Interface methods (stubs)
  @Override
  public void displayResult() {
    // Not implemented
  }

  @Override
  public double[][] getPoints(int i) {
    // Not implemented
    return new double[0][0];
  }

  @Override
  public void addData(String name, double[][] data) {
    // Not implemented
  }

  @Override
  public String[][] getResultTable() {
    // Not implemented
    return new String[0][0];
  }

  @Override
  public void printToFile(String name) {
    // Not implemented
  }

  @Override
  public double[] get(String name) {
    // Not implemented
    return new double[0];
  }

  @Override
  public JFreeChart getJFreeChart(String name) {
    // Not implemented
    return null;
  }

  @Override
  public neqsim.thermo.system.SystemInterface getThermoSystem() {
    return system;
  }

  // Accessors

  public double[][] getPhaseMatrix() {
    return betta;
  }

  // Accessor for bettaTransitionRegion
  public boolean[][] getBettaTransitionRegion() {
    return bettaTransitionRegion;
  }

  // For clarity, also provide getBettaMatrix() as an alias
  public double[][] getBettaMatrix() {
    return betta;
  }

  public double[] getDewPointTemperatures() {
    return dewPointTemperatures;
  }

  public double[] getDewPointPressures() {
    return dewPointPressures;
  }

  // Accessors for phase envelope lists

  // Accessors for phase envelope lists
  public java.util.List<Double> getPressurePhaseEnvelope() {
    return pressurePhaseEnvelope;
  }

  public java.util.List<Double> getTemperaturePhaseEnvelope() {
    return temperaturePhaseEnvelope;
  }

  // Returns the maximum pressure in the phase envelope (cricondenbar)
  public double getCricondenbar() {
    if (pressurePhaseEnvelope.isEmpty())
      return Double.NaN;
    return java.util.Collections.max(pressurePhaseEnvelope);
  }

  // Returns the maximum temperature in the phase envelope (cricondentherm)
  public double getCricondentherm() {
    if (temperaturePhaseEnvelope.isEmpty())
      return Double.NaN;
    return java.util.Collections.max(temperaturePhaseEnvelope);
  }
}
