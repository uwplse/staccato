import sys
import re
import tempfile
import subprocess
import os

project_name = sys.argv[1]
project_root = sys.argv[2]
project_dir = sys.argv[3]

if not os.path.exists(os.path.join(project_dir, "patches")):
    os.makedirs(os.path.join(project_dir, "patches"))

flow_change_re = re.compile("^flow-changes")
conf_abs_re = re.compile("^conf-abstraction")
annotation_re = re.compile("^annotations")
update_re = re.compile("^update(?:s)?")
bug_fix_re = re.compile("^bug-fixes")

message_map = {
    "confabs": conf_abs_re,
    "update": update_re,
    "annotation": annotation_re,
    "bugfix": bug_fix_re,
    "flowchange": flow_change_re
}

def dump_diff(project_dir, sha, ty):
    with open(os.path.join(project_dir, "patches", ty + ".patch"), 'w') as out_file:
        subprocess.call(["git", "diff", "-w", sha + "^.." + sha, "--", "."], cwd = project_root, stdout = out_file)

def parse_commit(l):
    space_index = l.index(" ")
    commit_sha = l[:space_index]
    commit_message = l[space_index+1:]
    found_ty = set()
    for (ty,re) in message_map.iteritems():
        if re.match(commit_message) is None:
            continue
        dump_diff(project_dir, commit_sha, ty)

for l in sys.stdin:
    ret = parse_commit(l)
