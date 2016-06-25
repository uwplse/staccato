import time
import subprocess
import os
import os.path

def start_memlistener(staccato_dir, mem_file):
    dnull = open("/dev/null", 'w')
    subprocess.Popen(["java", "-classpath", "%s/build/classes/main/" % staccato_dir, "edu.washington.cse.instrumentation.tests.RunJmx", mem_file], stdout = dnull, stderr = subprocess.STDOUT)
    time.sleep(5)

def kill_memlistener():
    with open("/dev/null", "w") as f:
        subprocess.call("kill $(pgrep -f edu.washington.cse.instrumentation.tests.RunJmx)", stdout = f, stderr = subprocess.STDOUT, shell = True)

import paths

JMETER_DIR = paths.JMETER_DIR

log_file = None

def _do_run_jmeter(test_file, random_seed, error_file, data_file, iterations, timeout = None):
    PREF = []
    if timeout is not None:
        PREF=["timeout", timeout]
    command = PREF + [
        "bash", os.path.join(JMETER_DIR, "bin/jmeter.sh"),  "-J",  "CONTROLLER_RANDOM_SEED=%d" % random_seed, "-J", "ERROR_FILE=%s" % error_file,  "-J", "RANDOM_USER_SEED=%d" % random_seed, "-J", "ITERATIONS=%d" % iterations, "-n", "-t", test_file, "-l", data_file
    ]
    proc = subprocess.Popen(command, stdout = log_file, stderr = subprocess.STDOUT)
    return proc

class RunSpec():
    def __init__(self, measure_mem, staccato_dir, this_dir):
        self.measure_mem = measure_mem
        self.staccato_dir = staccato_dir
        self.this_dir = this_dir

def run_jmeter(run_spec, test_file, dir_name, random_seed, iterations = 550):
    output_dir = os.path.join(run_spec.this_dir, "data", dir_name)
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    curr_time = int(time.time())
    data_file=""
    if run_spec.measure_mem:
        data_file = "/dev/null"
    else:
        data_file = os.path.join(output_dir, "respdata-%s.csv" % curr_time)
    mem_file = "/dev/null"
    error_dir = os.path.join(output_dir, "errors-%d" % curr_time)
    if not os.path.exists(error_dir):
        os.makedirs(error_dir)
    if run_spec.measure_mem:
        mem_file = os.path.join(output_dir, "memory-%d" % curr_time)
        if dir_name == "base":
            start_memlistener(run_spec.staccato_dir, mem_file)
        else:
            subprocess.check_call(["sudo", os.path.join(run_spec.this_dir, "../tomcat_control"),  "dump"])
    jmeter_proc = _do_run_jmeter(test_file, random_seed, os.path.join(output_dir, "errors-%d/error_" % curr_time), data_file, iterations)
    ret = jmeter_proc.wait()
    if run_spec.measure_mem:
        kill_memlistener()
    if run_spec.measure_mem:
        if dir_name != "base":
            subprocess.check_call("cp /tmp/staccato.mem %s" % mem_file, shell = True)
            #os.rename("/tmp/staccato.mem", mem_file)
        return mem_file
    else:
        return data_file

def warmup_jmeter(test_file):
    import tempfile
    temp_dir = tempfile.mkdtemp()
    jmeter_proc = _do_run_jmeter(test_file, 513955,  os.path.join(temp_dir, "error_"), "/dev/null", 25)
    ret = jmeter_proc.wait()
    if ret != 0:
        print "jmeter failed!"
        sys.exit(10)
    os.removedirs(temp_dir)
