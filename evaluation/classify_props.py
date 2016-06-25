import yaml
import os, os.path
import sys
import classify_lib

projects = ["openfire", "jforum", "subsonic"]

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

headers = [
    "checked",
    "updated",
    "immutable",
    "internal",
    "untested",
    "other",
    "total",
    "coverage"
]

def get_prop_file(project_dir):
    staccato_dir = os.path.join(project_dir, 'staccato_logs')
    if not os.path.exists(staccato_dir):
        print "Staccato logs not found. Have you run run_staccato.py?"
        sys.exit(1)
    p = [ s for s in os.listdir(staccato_dir) if s.startswith("props_") and s.endswith(".yml") ]
    if len(p) == 0:
        print "Property logs not found. Have you run staccato.py?"
        sys.exit(1)
    p.sort()
    log = p[-1]
    return os.path.join(staccato_dir, log)
    
def parse_project_props(p):
    project_dir = os.path.join(this_dir, p)
    option_file = os.path.join(project_dir, p + "_options.yml")
    prop_file = get_prop_file(project_dir)
    prop_info = classify_lib.parse(option_file, prop_file)
    prop_info["coverage"] = "%0.2f" % prop_info["coverage"]
    to_print = [ p ] + [ str(prop_info[h]) for h in headers ]
    print " | ".join(to_print)

print "Project | " + " | ".join([ x[:1].upper() + x[1:] for x in headers ])

for p in projects:
    parse_project_props(p)
