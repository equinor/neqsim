/**
 * Living-task support for continuous improvement of NeqSim engineering tasks.
 *
 * <p>
 * {@link neqsim.process.operations.continuous.ModelDriftMonitor} detects drift in model KPIs,
 * {@link neqsim.process.operations.continuous.BaselineComparator} compares a cycle with the promoted baseline, and
 * {@link neqsim.process.operations.continuous.ImprovementCycle} runs one never-throwing cycle on a model through the
 * automation API. The Python runner ({@code devtools/neqsim_continuous}) orchestrates cycles, the ledger and
 * scheduling.
 * </p>
 */
package neqsim.process.operations.continuous;
