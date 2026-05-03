# Diagnostics Playbook

Use `AutomationDiagnostics` to monitor automation quality over time.

## Recommended workflow
1. Collect safe-access payloads during runs.
2. Read `getDiagnostics().getLearningReport()` periodically.
3. Promote frequent corrections into canonical address dictionaries.
4. Alert when value-bound violations increase.
