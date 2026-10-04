"""Test-session settings shared by the devtools test suite."""
import os

# Report tests run the generator many times; keep Microsoft Word automation out of them.
os.environ.setdefault("NEQSIM_REPORT_FIELD_UPDATE", "0")

# Never reach a real document backend (STID etc.) from the test suite; tests
# that exercise doc_retriever clear this and inject fakes.
os.environ.setdefault("NEQSIM_DISABLE_DOC_RETRIEVAL", "1")
