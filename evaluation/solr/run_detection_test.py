import subprocess
import os, os.path
import sys

import paths

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

staccato_dir = os.path.join(this_dir, "../..")

broken_solr = paths.SOLR_BROKEN_PATH
fixed_solr = paths.SOLR_FIXED_PATH

print "Building and instrumenting SOLR"

with open("/dev/null", "w") as f:
    subprocess.check_call(["gradle", "solr:buildFixedSolr", "solr:buildBrokenSolr"], cwd = staccato_dir, stdout = f, stderr = subprocess.STDOUT)

print "---- STARTING TESTS ----"

def parse_output(sout):
    do_print = False
    for l in sout.split("\n"):
        if l.startswith("common.test:"):
            print ">>> BEGIN JUNIT OUTPUT <<<"
            do_print = True
        if do_print:
            print l
    print ">>> END JUNIT OUTPUT <<<"
        

def do_test(root_dir):
    print "running test..."
    test_dir = os.path.join(root_dir, "solr/core")
    p = subprocess.Popen(["ant", "test", "-Dtestcase=AnalysisAfterCoreReloadTest"], cwd = test_dir, stdout = subprocess.PIPE, stderr = subprocess.STDOUT)
    (sout, err) = p.communicate()
    print "test complete!"
    parse_output(sout)

print "Running solr with patch..."
do_test(fixed_solr)
print "Done."
print "Running solr without patch..."
do_test(broken_solr)
print "Done."
