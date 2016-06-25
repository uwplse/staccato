import sys
import subprocess
import yaml, os.path, os
import re

data_index = sys.argv[1]

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

bin_dir = os.path.realpath(os.path.join(this_dir, "../bin"))
slowdown_py = os.path.join(bin_dir, "calc_slowdown.py")
parse_py = os.path.join(bin_dir, "parse_data.py")

memover_py = os.path.join(bin_dir, "mem_overhead.py")

match = re.compile('\{(.+)\}$')

for p in ["openfire", "subsonic", "jforum"]:
    res = subprocess.check_output(["python", slowdown_py, data_index, p])
    res = res.strip()
    m = match.search(res)
    assert m is not None
    print "Total slowdown for %s is %sx" % (p, m.group(1))

    res = subprocess.check_output(["python", memover_py, data_index, p])
    res = res.strip()
    m = match.search(res)
    assert m is not None
    print "Total memory overhead for %s is %s%%" % (p, m.group(1))

print "Slowdown times:"
subprocess.call(["python", parse_py, data_index])
