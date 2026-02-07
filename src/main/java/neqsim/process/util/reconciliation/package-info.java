/**
 * Data reconciliation engine for adjusting plant measurements to satisfy process balance
 * constraints.
 *
 * <p>
 * This package provides weighted least squares (WLS) data reconciliation with gross error
 * detection. The typical workflow is:
 * </p>
 *
 * <ol>
 * <li>Create a {@link neqsim.process.util.reconciliation.DataReconciliationEngine}</li>
 * <li>Add {@link neqsim.process.util.reconciliation.ReconciliationVariable} objects with measured
 * values and uncertainties (set externally, e.g., from Python)</li>
 * <li>Add linear constraints (mass/energy balances) via coefficient arrays</li>
 * <li>Call {@code reconcile()} to get a
 * {@link neqsim.process.util.reconciliation.ReconciliationResult}</li>
 * <li>Inspect adjusted values, gross errors, and chi-square statistics</li>
 * </ol>
 *
 * <p>
 * <b>Key classes:</b>
 * </p>
 *
 * <ul>
 * <li>{@link neqsim.process.util.reconciliation.DataReconciliationEngine} - Main solver with WLS
 * formula and gross error elimination</li>
 * <li>{@link neqsim.process.util.reconciliation.ReconciliationVariable} - A single measured
 * variable with value, uncertainty, and reconciled result</li>
 * <li>{@link neqsim.process.util.reconciliation.ReconciliationResult} - Complete result with
 * statistics, JSON export, and text report</li>
 * </ul>
 *
 * <p>
 * <b>Python usage example:</b>
 * </p>
 *
 * <pre>
 * from neqsim import jneqsim
 * Engine = jneqsim.process.util.reconciliation.DataReconciliationEngine
 * Variable = jneqsim.process.util.reconciliation.ReconciliationVariable
 *
 * engine = Engine()
 * engine.addVariable(Variable("feed", 1000.0, 20.0))
 * engine.addVariable(Variable("product", 600.0, 15.0))
 * engine.addVariable(Variable("waste", 380.0, 10.0))
 * engine.addConstraint([1.0, -1.0, -1.0])
 * result = engine.reconcile()
 * print(result.toReport())
 * </pre>
 *
 * @author Process Optimization Team
 * @version 1.0
 * @see neqsim.process.util.reconciliation.DataReconciliationEngine
 */
package neqsim.process.util.reconciliation;
