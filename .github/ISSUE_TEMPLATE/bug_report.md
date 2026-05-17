---
name: Bug report
about: Report a bug in NeqSim's Java library or Python package
title: ''
labels: 'bug'
assignees: ''

---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps or code to reproduce the behavior:
```java
// Java example
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
// ...
```
or
```python
# Python example
from neqsim import jneqsim
# ...
```

**Expected behavior**
A clear and concise description of what you expected to happen.

**Actual behavior**
What happened instead (wrong values, exception, zero output, etc.).

**Environment:**
 - OS: [e.g., Windows 11, Ubuntu 24.04, macOS 15]
 - Java version: [e.g., JDK 8, JDK 21]
 - NeqSim version: [e.g., 3.7.0, master branch]
 - Python neqsim version (if applicable): [e.g., pip show neqsim]

**Additional context**
Add any other context — fluid composition, operating conditions, EOS used, etc.
