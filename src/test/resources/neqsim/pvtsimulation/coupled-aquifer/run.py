"""Compile current repository APIs and run the complete optional coupled-aquifer matrix."""
from pathlib import Path
import subprocess
import sys

here = Path(__file__).resolve().parent
root = here.parents[5]
output = root / 'target' / 'coupled-aquifer-qualification'
output.mkdir(parents=True, exist_ok=True)
wrapper = root / ('mvnw.cmd' if sys.platform == 'win32' else 'mvnw')
classpath_file = output / 'classpath.txt'
subprocess.run([str(wrapper), '-B', '-ntp', 'compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(classpath_file)], cwd=root, check=True)
separator = ';' if sys.platform == 'win32' else ':'
classpath = str(root / 'target/classes') + separator + classpath_file.read_text().strip()
subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '8', '-cp', classpath,
                '-d', str(output), str(here / 'CoupledAquiferQualification.java')], check=True)
subprocess.run(['java', '-Xmx2g', '-cp', str(output) + separator + classpath,
                'CoupledAquiferQualification', str(here / 'reference.json'),
                str(output / 'native.json')], check=True)
subprocess.run([sys.executable, str(here / 'audit.py'), str(output / 'native.json')], check=True)
