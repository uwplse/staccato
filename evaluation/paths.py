import sys
import os, os.path, subprocess

this_module = sys.modules[__name__]
this_dir = os.path.dirname(__file__)

paths = subprocess.check_output(["bash", os.path.join(this_dir, "../bin/_paths.sh")]).strip()
for a in paths.split("\n"):
    (k,v) = a.split('=', 1)
    setattr(this_module, k, v)


