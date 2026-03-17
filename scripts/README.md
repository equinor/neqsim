# Scripts Directory

Utility scripts for NeqSim project maintenance and reporting.

---

## Available Scripts

### update_report.py

**Purpose**: Regenerate metrics badges for README.md

**Usage**:
```bash
python scripts/update_report.py
```

**When to run**:
- After security vulnerability updates
- After dependency updates
- After significant test coverage changes
- Before major releases

**What it updates**:
- Security metrics badge
- Dependency vulnerability metrics
- Test coverage metrics
- Build status indicators

**Requirements**:
- Python 3.8+
- Access to GitHub API (for metrics data)
- Repository write permissions

---

## Adding New Scripts

When adding new utility scripts to this directory:

1. **Create the script** with a descriptive name (e.g., `check_links.py`, `generate_reports.py`)
2. **Add documentation** to this README with:
   - Purpose (one sentence)
   - Usage example
   - When to run
   - Requirements
3. **Include a header** in your script:
   ```python
   """
   Script Name: <name>
   Purpose: <brief description>
   Usage: python scripts/<name>.py [args]
   """
   ```
4. **Make it executable** (optional on Linux/Mac):
   ```bash
   chmod +x scripts/<name>.py
   ```

---

## Best Practices

- ✅ Keep scripts focused on a single task
- ✅ Add error handling and meaningful error messages
- ✅ Document all command-line arguments
- ✅ Test scripts before committing
- ✅ Use relative paths (assume script runs from repo root)
- ❌ Don't hardcode credentials or tokens
- ❌ Don't modify source code (use `src/` for that)

---

## See Also

- [devtools/](../devtools/) - Development tools (Jupyter helpers, task creation)
- [docs/development/](../docs/development/) - Developer documentation
- [BUILD.md](../docs/development/BUILD.md) - Build and test commands
