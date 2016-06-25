import subprocess
import os.path
import time
import sys
import eval_tools

THIS_DIR = os.path.realpath(os.path.dirname(sys.argv[0]))

test_output = open(os.path.join(THIS_DIR, 'test.log'), 'w')

PROFILES= {"baseline": "", "mem": "-Dstaccato.mem-file=/tmp/of-mem -Dstaccato.record-mem=true"}

test_files = {
    "mini": [
        os.path.realpath(os.path.join(THIS_DIR, "tsung-chat-roster-warmup-mini.xml")),
        os.path.realpath(os.path.join(THIS_DIR, "tsung-chat-update-mini.xml"))
    ],
    "paper": [
        os.path.realpath(os.path.join(THIS_DIR, "tsung-chat-roster-warmup.xml")),
        os.path.realpath(os.path.join(THIS_DIR, "tsung-chat-update.xml"))
    ]
}

selected_profile ="baseline"
profile_flags = PROFILES[selected_profile]

test_selection = 'mini'

if len(sys.argv) > 1 and sys.argv[1] == "--test-files":
    for v in test_files.itervalues():
        for f in v:
            print f
    sys.exit(0)

if len(sys.argv) > 1 and sys.argv[1] == '--full':
    test_selection = 'paper'
    del sys.argv[1]

if len(sys.argv) > 1:
    selected_profile=sys.argv[1]
    profile_flags= PROFILES[selected_profile]

warmup_xml = test_files[test_selection][0]
scenario_xml = test_files[test_selection][1]

#. $THIS_DIR/../eval_tools.sh

measure_mem = False
if selected_profile == "mem":
    measure_mem = True

def kill_of():
    subprocess.call("kill -9 $(ps aux | grep startup.jar | grep -v grep | awk '{print $2}')", stdout = test_output, stderr = subprocess.STDOUT, shell=True)

STACCATO_DIR = os.path.join(THIS_DIR, '../../')

import paths

INST_OF = os.path.join(paths.OPENFIRE_ROOT, "target/openfire/bin/")
BASE_OF = os.path.join(paths.CLEAN_OPENFIRE_ROOT, "target/openfire/bin/")

def warmup_tsung(scenario):
    if not os.path.exists("/tmp/dummy_tsung"):
        os.makedirs("/tmp/dummy_tsung")
    sh = subprocess.Popen(["tsung", "-f", scenario, "-l", "/tmp/dummy_tsung", "start"], stdout = test_output)
    ret = sh.wait()
    if ret != 0:
        print "Warmup tsung failed with exit code: %d" % ret
        sys.exit(10)

def run_tsung(scenario, data_dir):
	output_dir = None
	if measure_mem:
            output_dir = "/tmp/dummy_tsung"
	else:
            output_dir = os.path.join(THIS_DIR, "data", data_dir)
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
	mem_file = None
        if measure_mem:
            mem_file = os.path.join(THIS_DIR, "data", data_dir, "memory-%d" % int(time.time()))
            if not os.path.exists(os.path.dirname(mem_file)):
                os.makedirs(os.path.dirname(mem_file))
            if data_dir == "base":
                eval_tools.start_memlistener(STACCATO_DIR, mem_file)
            else:
                subprocess.check_call("kill -12 $(ps aux | grep startup.jar | grep -v grep | awk '{print $2}')", shell = True, stdout = test_output)
        tsung_proc = subprocess.Popen(["tsung", '-f', scenario, '-l', output_dir, 'start'], stdout = test_output)
        ret = tsung_proc.wait()
        if ret != 0:
            print "Tsung failed with exit code: %d" % ret
            sys.exit(10)
        if measure_mem:
            eval_tools.kill_memlistener()
        to_ret = ""
        if measure_mem:
            if data_dir != "base":
                import shutil
                shutil.move("/tmp/of-mem", mem_file)
            return os.path.realpath(mem_file)
	else:
            l = [ d for d in os.listdir(output_dir) if os.path.isdir(os.path.join(output_dir, d)) ]
            l.sort()
            data_dir = l[-1]
            subprocess.check_call(["perl", "/usr/lib/tsung/bin/tsung_stats.pl",  "--stat", "./tsung.log"], cwd = os.path.join(output_dir,data_dir), stdout = test_output, stderr = subprocess.STDOUT)
            return os.path.realpath(os.path.join(output_dir,data_dir, "data"))

def start_of(directory):
    with open("/tmp/ofhosts.csv", "w") as f:
        import socket
        print >> f, socket.gethostname()
    new_flags = dict(os.environ)
    new_flags["OPENFIRE_DEF"] = profile_flags
    import tempfile
    out_file = tempfile.NamedTemporaryFile(delete = False)
    of_proc = subprocess.Popen(["bash", os.path.join(directory, "openfire.sh")], stdout = out_file, env = new_flags, stderr = subprocess.STDOUT)
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

subprocess.check_call(["gradle", "openfire:deploy"], cwd = STACCATO_DIR, stdout = test_output, stderr = subprocess.STDOUT)
subprocess.check_call(["gradle", "openfire:buildClean"], cwd = STACCATO_DIR, stdout = test_output, stderr = subprocess.STDOUT)

def copy_conf(dir_name):
    subprocess.call("cp %s/conf/* %s/../conf/" % (THIS_DIR, dir_name), shell=True)

copy_conf(INST_OF)
copy_conf(BASE_OF)

subprocess.call(["tsung", "stop"], stdout = test_output, stderr = subprocess.STDOUT)
kill_of()

subprocess.call("kill $(pgrep -f edu.washington.cse.instrumentation.tests.RunJmx) > /dev/null 2>&1", shell=True, stdout = test_output, stderr = subprocess.STDOUT)

subprocess.call(["bash", "reset_db.sh"], cwd = THIS_DIR, stdout = test_output, stderr = subprocess.STDOUT)

inst_name="inst-%s" % selected_profile
base_name="base"

start_of(INST_OF)
warmup_tsung(warmup_xml)
print >> test_output, "Starting tsung at $(date)"
ret = run_tsung(scenario_xml, inst_name)
print ret
kill_of()

time.sleep(1)

subprocess.call(["bash", "reset_db.sh"], cwd = THIS_DIR, stdout = test_output, stderr = subprocess.STDOUT)

start_of(BASE_OF)
warmup_tsung(warmup_xml)
print >> test_output, "Starting tsung at $(date)"
ret = run_tsung(scenario_xml, base_name)
kill_of()
print ret

