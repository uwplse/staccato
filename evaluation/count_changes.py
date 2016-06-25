import subprocess
import re
import os, os.path
import sys

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))

paper = len(sys.argv) > 1 and sys.argv[1] == "--paper"

diff_line = re.compile(r'^[- +]')
context_line = re.compile(r'^(---|\+\+\+)')
import_line = re.compile(r'^\s*import')

check = 0
propagation = 0

def count_lines_in_path(file_name):
    global check
    global propagation
    lines_changed = 0
    with open(file_name, 'r') as f:
        for l in f:
            if re.match(diff_line, l) is None:
                continue
            if re.match(context_line, l) is not None:
                continue
            # context line
            if l.startswith(" "):
                continue
            if l.find('StaccatoCheck') != -1:
                check += 1
            if l.find('StaccatoProp') != -1:
                propagation += 1
            l = l[1:]
            l = l.strip()
            if re.match(import_line, l) is not None:
                continue
            if len(l) == 0:
                continue
            lines_changed += 1
    return lines_changed

output_map = {}

relevant_cat = [
    "annotation",
    "flowchange", 
    "update",
    "bugfix",
    "confabs"
]


def count_lines_in_project(p):
    to_ret = {}
    project_dir = os.path.join(this_dir, p, "patches")
    patch_files = os.listdir(project_dir)
    for pf in patch_files:
        assert pf.endswith(".patch")
        cat = pf[:-len(".patch")]
        to_ret[cat] = count_lines_in_path(os.path.join(project_dir, pf))
    return to_ret

def compute_stats(p_map, sloc_total):
    p_counts = [ p_map[o] if o in p_map else 0 for o in relevant_cat ]
    total = sum(p_counts)
    p_map["all"] = total
    p_map["lineprop"] = (float(total) / sloc_total) * 100
    p_map["annotprop"] = (float(p_map["annotation"]) / sloc_total) * 100

sloc = {
    "openfire": 85416,
    "jforum": 29568,
    "subsonic": 29592
}    

for p in ["subsonic", "openfire", "jforum"]:
    s = count_lines_in_project(p)
    compute_stats(s, sloc[p])
    output_map[p] = s

if not paper:
    print "Project | Annot. | Flow | Repair CB | Bug-Fixes | Conf-Abs. | Total"

def format_number(n):
    if type(n) == float:
        return "%0.2f" % n
    else:
        return str(n)

output_order = relevant_cat + [ "all" ]

def dump_paper_stats(p_name):
    paper_stats = output_order + [ "annotprop", "lineprop" ]
    p_map = output_map[p_name]
    for p in paper_stats:
        s = format_number(p_map.get(p, 0))
        print "\\def\\%s%s{%s}" % (p_name, p, s)

def dump_project_stats(p_name):
    if paper:
        dump_paper_stats(p_name)
    else:
        p_map = output_map[p_name]
        print p_name + " | " + " | ".join([ format_number(p_map.get(o, 0)) for o in output_order ])

for p in ["subsonic", "openfire", "jforum"]:
    dump_project_stats(p)

def dump_aggregate_stats():
    if not paper:
        print "Total check: " + str(check)
        print "Total propagation: " + str(propagation)
        return
    print "\\def\\annotationtotal{%s}" % str(check + propagation)
    print '\\def\\checkannottotal{%d}' % check
    print '\\def\\propannottotal{%d}' % propagation
    for (k,v) in sloc.iteritems():
        print r'\def\%stotalsloc{%d}' % (k,v)
    all_sloc = sum(sloc.itervalues())
    check_per_1000sloc = (float(check) / all_sloc) * 1000
    print r'\def\checksloc1000{%0.1f}' % check_per_1000sloc
    
dump_aggregate_stats()

