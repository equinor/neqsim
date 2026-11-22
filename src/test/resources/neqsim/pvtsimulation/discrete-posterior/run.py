"""Compile the exact checkout and retain the optional complete posterior benchmark."""
from pathlib import Path
import subprocess
import sys

here = Path(__file__).resolve().parent
root = here.parents[5]
output = root / 'target' / 'discrete-posterior-qualification'
output.mkdir(parents=True, exist_ok=True)
wrapper = root / ('mvnw.cmd' if sys.platform == 'win32' else 'mvnw')
classpath_file = output / 'classpath.txt'
subprocess.run([str(wrapper), '-B', '-ntp', 'compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(classpath_file)], cwd=root, check=True)
separator = ';' if sys.platform == 'win32' else ':'
classpath = str(root / 'target/classes') + separator + classpath_file.read_text().strip()
subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '8', '-cp', classpath,
                '-d', str(output), str(here / 'DiscretePosteriorQualification.java'),
                str(here / 'QuantileEndpointQualification.java')], check=True)
subprocess.run(['java', '-Xmx2g', '-cp', str(output) + separator + classpath,
                'DiscretePosteriorQualification', str(here / 'reference.json'),
                str(output / 'native.json')], check=True)
subprocess.run([sys.executable, str(here / 'audit.py'), str(output / 'native.json')], check=True)
subprocess.run(['java', '-cp', str(output) + separator + classpath, 'QuantileEndpointQualification',
                str(output / 'endpoints.json')], check=True)
