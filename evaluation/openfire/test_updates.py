import subprocess
import os, os.path, sys
import yaml
import paths

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

JMETER_DIR = paths.JMETER_DIR

def start_of():
    import tempfile
    out_file = tempfile.NamedTemporaryFile(delete = False)
    of_proc = subprocess.Popen(["bash", os.path.join(this_dir, "deploy_of.sh"), "havoc"], stdout = out_file, stderr = subprocess.STDOUT)
    f = None
    while True:
        if of_proc.poll() is not None:
            print "Openfire failed to start"
            import sys
            sys.exit(-1)
        if not os.path.exists(out_file.name):
            time.sleep(0.5)
        else:
            f = open(out_file.name, "r")
            break
    while True:
        new = f.readline()
        if new and new.startswith("Admin console listening at"):
            break
        elif not new:
            import time
            time.sleep(0.5)
    return of_proc

subprocess.check_call(["bash", os.path.join(this_dir, "reset_db.sh")], stdout = open("/dev/null", "w"), stderr = subprocess.STDOUT)
of_proc = start_of()
subprocess.call(["bash", os.path.join(JMETER_DIR, 'bin/jmeter.sh'), "-n", "-l", "/dev/null", "-t", os.path.join(this_dir, "havoc_test.jmx")])
of_proc.kill()

update_dbf = os.path.join(this_dir, "update_db.yml")

with open(update_dbf, "w") as f:
    yaml.dump([], f)

with open("/dev/null", "w") as f:
    subprocess.check_call(["python", os.path.join(this_dir, "../log_classifier.py"), "/tmp/of-staccato.log", update_dbf, "/dev/null"], stdout = f, stderr = subprocess.STDOUT)

update_db = None
with open(update_dbf, "r") as f:
    update_db = yaml.load(f)

updated_props = set()

for p in update_db:
    prop = p["props"]
    assert len(prop) == 1
    if p["tags"] != {"repair": True }:
        print "FOUND NON UPDATED PROP!!", prop
        sys.exit(1)
    updated_props.add(prop[0])

if len(sys.argv) > 1 and sys.argv[1].startswith("--dump-raw="):
    # arg parsing with substrings: brillant
    output_path = sys.argv[1][len("--dump-raw="):]
    with open(output_path, "w") as f:
        yaml.dump(list(updated_props), f)
    sys.exit(0)

print "Total properties updated: %d" % len(updated_props)
print "Updated props: %s" % str(updated_props)
