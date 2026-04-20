/**
 * Built-in workflow implementations for the decision support engine.
 *
 * <p>
 * Each workflow handles a specific type of operator question:
 * </p>
 * <ul>
 * <li>{@link neqsim.process.decisionsupport.workflow.RateChangeFeasibilityWorkflow} — "Can we run
 * at X rate?"</li>
 * <li>{@link neqsim.process.decisionsupport.workflow.GasQualityImpactWorkflow} — "What happens with
 * this new gas?"</li>
 * <li>{@link neqsim.process.decisionsupport.workflow.DerateOptionsWorkflow} — "What is the safest
 * derate option?"</li>
 * <li>{@link neqsim.process.decisionsupport.workflow.EquipmentStatusWorkflow} — "What is the
 * current equipment status?"</li>
 * <li>{@link neqsim.process.decisionsupport.workflow.ProductSpecCheckWorkflow} — "Are we meeting
 * product spec?"</li>
 * <li>{@link neqsim.process.decisionsupport.workflow.WhatIfWorkflow} — "What if we change parameter
 * X to Y?"</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
package neqsim.process.decisionsupport.workflow;
