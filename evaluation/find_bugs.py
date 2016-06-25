import subprocess
import os, os.path
import sys
import yaml
import base64
import cPickle as pickle

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

run_staccato = os.path.join(this_dir, "run_staccato.py")
classify_py = os.path.join(this_dir, "classify_bugs.py")

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

def run_test(p):
    project_dir = os.path.join(this_dir, p)
    bug_db = os.path.join(project_dir, "bug_db.yml")
    with open(bug_db, "w") as dbf:
        yaml.dump([], dbf)
    test_config = os.path.join(project_dir, "test_config.yml")
    import tempfile
    test_output = tempfile.NamedTemporaryFile(delete = False)
    try:
        subprocess.check_call(["python", run_staccato, test_config], stdout = test_output, stderr = subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        print "Test failed, output in ", test_output
        raise e
    else:
        os.remove(test_output.name)
    property_file = get_prop_file(project_dir)
    import re, shutil
    run_ts = re.sub(r'^props_(\d+).yml$', r'\1', os.path.basename(property_file))
    staccato_ldir = os.path.join(project_dir, "staccato_logs")
    assert run_ts != os.path.basename(property_file)
    output_db = os.path.join(staccato_ldir, "bug_db_%s.yml" % run_ts)
    shutil.copy2(bug_db, output_db)
    return {
        "property_file": property_file,
        "bug_db": output_db
    }

output_document = {}
p = sys.argv[2]
output_document[p] = run_test(p)

ofile = sys.argv[1]

with open(ofile, "w") as f:
    yaml.dump(output_document, f)
