"""Test-session settings shared by the devtools test suite."""
import os

# Report tests run the generator many times; keep Microsoft Word automation out of them.
os.environ.setdefault("NEQSIM_REPORT_FIELD_UPDATE", "0")
