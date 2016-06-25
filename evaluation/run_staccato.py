import subprocess
import sys
import os.path
import os
import time
import yaml

def reverse_readline(filename, buf_size=8192):
    """a generator that returns the lines of a file in reverse order"""
    with open(filename) as fh:
        segment = None
        offset = 0
        fh.seek(0, os.SEEK_END)
        file_size = fh.tell()
        total_size = remaining_size = fh.tell()
        while remaining_size > 0:
            offset = min(total_size, offset + buf_size)
            fh.seek(file_size - offset)
            buffer = fh.read(min(remaining_size, buf_size))
            remaining_size -= buf_size
            lines = buffer.split('\n')
            # the first line of the buffer is probably not a complete line so
            # we'll save it and append it to the last line of the next buffer
            # we read
            if segment is not None:
                # if the previous chunk starts right from the beginning of line
                # do not concact the segment to the last line of new chunk
                # instead, yield the segment first 
                if buffer[-1] is not '\n':
                    lines[-1] += segment
                else:
                    yield segment
            segment = lines[0]
            for index in range(len(lines) - 1, 0, -1):
                if len(lines[index]):
                    yield lines[index]
        yield segment

MAPPING = {
    "LINEAR PROPS": "con",
    "LINEAR CF": "conCF",
    "STRICT PROPS": "strict",
    "STRICT CF": "strict",
    "WRITE PROPS": "write",
    "ALL PROPS": "read"
}

import re

category_patt = re.compile("^--- (.+) ---$")

def read_prop_stats(log_file):
    log_gen = reverse_readline(log_file)
    to_ret = {}
    accum = set()
    while True:
        l = log_gen.next()
        l = l.strip()
        m = category_patt.match(l)
        if m is not None:
            cat_key = MAPPING[m.group(1)]
            if cat_key in to_ret:
                to_ret[cat_key] |= accum
            else:
                to_ret[cat_key] = accum
            if cat_key == "read":
                return to_ret
            else:
                accum = set()
        else:
            props = l.split(", ")
            accum |= set([ p[1:-1] for p in props ])

import paths

JMETER_DIR = paths.JMETER_DIR

def run_jmeter(test_file, stout, log_file, props, **kwargs):
    jmeter_exec = os.path.join(JMETER_DIR, "bin/jmeter.sh")
    cmd = [ "bash", jmeter_exec ]
    for (k,v) in props.iteritems():
        cmd.append("-J")
        cmd.append("%s=%s" % (k,str(v)))
    for (k,v) in kwargs:
        cmd.append("-J")
        cmd.append("%s=%s" % (k,str(v)))
    cmd += ["-n", "-t", test_file, "-l", log_file ]
    proc = subprocess.Popen(cmd, stdout = stout)
    return proc

this_dir = os.path.dirname(os.path.realpath(sys.argv[0]))
deploy_wp = os.path.join(this_dir, "deploy_wrapper")

class PathResolver:
    def __init__(self, base_dir):
        self.base_dir = base_dir
    def resolve_path(self, p):
        return p.replace("$thisdir", self.base_dir)

class JMeterTest:
    def __init__(self, settings, path_resolver):
        self.project_dir = path_resolver.resolve_path(settings["project_dir"])
        self.project = settings["project"]
        self.havoc_file = path_resolver.resolve_path(settings["havoc"])
        self.test_file = path_resolver.resolve_path(settings["test"])
        self.n_havoc_iterations = settings.get("havoc_iter", 250)
        self.n_test_iterations = settings.get("test_iter", 150)
        self.log_dir = os.path.join(self.project_dir, "staccato_logs")
        self.random_seed = settings.get("controller_seed", 1892057)
        self.log_file = "/var/log/tomcat7/catalina.out"
        self.staccato_log = "/tmp/staccato.log"
    def dump_test_files(self):
        print self.havoc_file
        print self.test_file
    def start_test(self, run_id):
        test_out = open(os.path.join(self.log_dir, "jmeter_test_%s.out" % run_id), "w")
        test_log = os.path.join(self.log_dir, "jmeter_test_%s.log" % run_id)
        return run_jmeter(self.test_file, test_out, test_log, {
            "CONTROLLER_RANDOM_SEED": self.random_seed,
            "ITERATIONS": self.n_test_iterations
        })
    def kill_test(self, test_proc):
        test_proc.kill()
    def stop_service(self):
        control_exec = os.path.join(this_dir, "tomcat_control")
        subprocess.check_call(["sudo", control_exec, "stop"])
    def start_havoc(self, run_id):
        havoc_out = open(os.path.join(self.log_dir, "jmeter_havoc_%s.out" % run_id), "w")
        havoc_log = os.path.join(self.log_dir, "jmeter_havoc_%s.log" % run_id)
        return run_jmeter(self.havoc_file, havoc_out, havoc_log, {
            "CONTROLLER_RANDOM_SEED": self.random_seed,
            "ITERATIONS": self.n_havoc_iterations
        })
    def signal_dump(self):
        subprocess.check_output(["sudo", os.path.join(this_dir, "tomcat_control"), "dump"])
        return True
        # tomcat_pid = subprocess.check_output("ps aux | grep tomcat7 | grep -v grep | awk '{print $2}'", shell=True).strip()
        # if tomcat_pid == '':
        #     print "No tomcat found!"
        #     return False
        # else:
        #     import signal
        #     os.kill(int(tomcat_pid), signal.SIGUSR2)
        #     return True
    def deploy(self, run_id):
        subprocess.call([
            "sudo", deploy_wp, self.project, "havoc"
        ])

class TsungTest:
    def kill_test(self, test_proc):
        subprocess.check_call(["tsung", "stop"])
    def __init__(self, settings, path_resolver):
        self.project_dir = path_resolver.resolve_path(settings["project_dir"])
        self.havoc_file = path_resolver.resolve_path(settings["havoc"])
        self.test_file = path_resolver.resolve_path(settings["test"])
        self.n_havoc_iterations = settings.get("havoc_iter", 250)
        self.log_dir = os.path.join(self.project_dir, "staccato_logs")
        self.random_seed = settings.get("controller_seed", 1892057)
        self.staccato_log = "/tmp/of-staccato.log"
    def dump_test_files(self):
        print self.havoc_file
        print self.test_file
    def deploy(self, run_id):
        self.log_file = os.path.join(self.log_dir, "openfire_%s.out" % run_id)
        deploy_wrapper = os.path.join(self.project_dir, "deploy_of.sh")
        self.of_proc = subprocess.Popen(["bash", deploy_wrapper, "havoc", self.log_file])
        self._wait_for_logfile()
        with open(self.log_file, "r") as f:
            self._wait_for_server(f)
    def _wait_for_logfile(self):
        import time
        while True:
            if self.of_proc.poll() is not None:
                print "Openfire failed to start"
                import sys
                sys.exit(-1)
            if not os.path.exists(self.log_file):
                time.sleep(0.5)
            else:
                break
    def _wait_for_server(self, f):
        assert f is not None
        while True:
            new = f.readline()
            if new and new.startswith("Admin console listening at"):
                break
            elif not new:
                import time
                time.sleep(0.5)
    def signal_dump(self):
        import signal
        of_pid = subprocess.check_output("ps aux | grep startup.jar | grep -v grep | awk '{print $2}'", shell=True).strip()
        if of_pid == "":
            return False
        print "Signaling", of_pid
        os.kill(int(of_pid), signal.SIGUSR2)
        return True
    def stop_service(self):
        import signal
        of_pid = subprocess.check_output("ps aux | grep startup.jar | grep -v grep | awk '{print $2}'", shell=True).strip()
        os.kill(int(of_pid), signal.SIGKILL)
        #pid = self.of_proc.pid
        #subprocess.check_call(["kill", "-9", "--", "-" + str(pid)])
    def start_test(self, run_id):
        test_folder = os.path.join(self.log_dir, "tsung_%s" % run_id)
        os.mkdir(test_folder)
        return subprocess.Popen(["tsung", "-f", self.test_file, "-l", test_folder, "start"])
    def start_havoc(self, run_id):
        havoc_out = open(os.path.join(self.log_dir, "jmeter_havoc_%s.out" % run_id), "w")
        havoc_log = os.path.join(self.log_dir, "jmeter_havoc_%s.log" % run_id)
        return run_jmeter(self.havoc_file, havoc_out, havoc_log, {
            "CONTROLLER_RANDOM_SEED": self.random_seed,
            "ITERATIONS": self.n_havoc_iterations
        })

def run_test(test_hook):
    run_id = time.strftime("%Y%m%d%H%M")
    log_dir = test_hook.log_dir
    try:
        os.mkdir(log_dir)
    except OSError, e:
        pass
    test_hook.deploy(run_id)
    project_dir = test_hook.project_dir
    test_proc = test_hook.start_test(run_id)
    havoc_proc = test_hook.start_havoc(run_id)
    print "Waiting"
    ret = havoc_proc.wait()
    print "killing"
    test_hook.kill_test(test_proc)
    print "Killed test"
    staccato_log_file = os.path.join(log_dir, "staccato_%s.log" % run_id)
    subprocess.check_call(["cp", test_hook.staccato_log, staccato_log_file])
    succ = test_hook.signal_dump()
    test_hook.stop_service()
    prop_stats = {}
    if succ:
        prop_stats = read_prop_stats(test_hook.log_file)
    bug_db_file = os.path.join(project_dir, "bug_db.yml")
    result_file = os.path.join(log_dir, "bugs_%s" % run_id)
    classifier_script = os.path.join(this_dir, "log_classifier.py")
    # python calling python!
    subprocess.check_call(["python", classifier_script, staccato_log_file, bug_db_file, result_file])
    prop_for_yml = {}
    for (k,v) in prop_stats.iteritems():
        prop_for_yml[k] = list(v)

    prop_file = os.path.join(log_dir, "props_%s.yml" % run_id)
    with open(prop_file, "w") as f:
        yaml.dump(prop_for_yml, f)

settings_file = sys.argv[1]
settings = None
with open(sys.argv[1], "r") as sf:
    settings = yaml.load(sf)
test_hook_cls = globals()[settings["test_impl"]]
pr = PathResolver(os.path.dirname(sys.argv[1]))
test_driver = test_hook_cls(settings, pr)

if len(sys.argv) > 2 and sys.argv[2] == "--test-files":
    test_driver.dump_test_files()
    sys.exit(0)

run_test(test_driver)
