/**
 * Data reconciliation and steady-state detection for online process optimization.
 *
 * <p>
 * This package provides two complementary capabilities for online process optimization:
 * </p>
 *
 * <p>
 * <b>1. Steady-State Detection (SSD)</b> — monitors process variables over a sliding window and
 * determines when the process has reached steady state using the R-statistic (ratio of filtered to
 * unfiltered variance), optional slope and std.dev tests. Only steady-state data should be fed into
 * reconciliation or model calibration.
 * </p>
 *
 * <p>
 * <b>2. Data Reconciliation</b> — weighted least squares (WLS) adjustment of plant measurements so
 * that mass (and energy) balance constraints are exactly satisfied, with gross error detection via
 * normalized residual tests and iterative elimination of faulty sensors.
 * </p>
 *
 * <p>
 * <b>Key classes:</b>
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.process.util.reconciliation.SteadyStateDetector} - Monitors variables and
 * evaluates steady-state status using R-statistic, slope, and std.dev criteria</li>
 * <li>{@link neqsim.process.util.reconciliation.SteadyStateVariable} - A monitored variable with
 * sliding window and computed statistics</li>
 * <li>{@link neqsim.process.util.reconciliation.SteadyStateResult} - Result of SSD evaluation with
 * per-variable diagnostics</li>
 * <li>{@link neqsim.process.util.reconciliation.DataReconciliationEngine} - WLS solver with gross
 * error detection and elimination</li>
 * <li>{@link neqsim.process.util.reconciliation.ReconciliationVariable} - A single measured
 * variable with value, uncertainty, and reconciled result</li>
 * <li>{@link neqsim.process.util.reconciliation.ReconciliationResult} - Complete reconciliation
 * result with statistics, JSON export, and text report</li>
 * </ul>
 *
 * <p>
 * <b>Typical online workflow:</b>
 * </p>
 *
 * <pre>
 * // 1. Monitor for steady state
 * SteadyStateDetector ssd = new SteadyStateDetector(30);
 * ssd.addVariable(new SteadyStateVariable("feed", 30).setUncertainty(20.0));
 * ssd.addVariable(new SteadyStateVariable("gas", 30).setUncertainty(15.0));
 * ssd.addVariable(new SteadyStateVariable("liquid", 30).setUncertainty(10.0));
 *
 * // 2. Push readings and check
 * ssd.updateVariable("feed", 1000.5);
 * ssd.updateVariable("gas", 600.2);
 * ssd.updateVariable("liquid", 399.1);
 * SteadyStateResult ssResult = ssd.evaluate();
 *
 * // 3. When steady, reconcile
 * if (ssResult.isAtSteadyState()) {
 *   DataReconciliationEngine engine = ssd.createReconciliationEngine();
 *   engine.addMassBalanceConstraint("Sep", new String[] {"feed"}, new String[] {"gas", "liquid"});
 *   ReconciliationResult recResult = engine.reconcile();
 *   System.out.println(recResult.toReport());
 * }
 * </pre>
 *
 * @author Process Optimization Team
 * @version 1.0
 * @see neqsim.process.util.reconciliation.SteadyStateDetector
 * @see neqsim.process.util.reconciliation.DataReconciliationEngine
 */
package neqsim.process.util.reconciliation;
