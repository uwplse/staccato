import sys
import subprocess
import os, os.path
import yaml

is_paper_test = False
if len(sys.argv) > 1 and sys.argv[1] == '--full':
    is_paper_test = True


sub_projects = [ "openfire", "subsonic", "jforum" ]

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

output = {"runtime": {}, "memory": {}}

def get_project_results(p):
    a = ["python", os.path.join(this_dir, "project_test.py")]
    if is_paper_test:
        a.append("--full")]
    a.append(p)
    out = subprocess.check_output(a, stderr = sys.stdout)
    d = yaml.load(out)
    output["runtime"].update(d["runtime"])
    output["memory"].update(d["memory"])

for project in sub_projects:
    get_project_results(project)

with open(os.path.join(this_dir, "data.yml"), 'w') as f:
    yaml.dump(output, f)

print "Done"
