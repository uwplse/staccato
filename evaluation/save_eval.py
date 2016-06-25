import subprocess
import yaml
import sys
import os, os.path

import tarfile

this_dir = os.path.realpath(os.path.dirname(sys.argv[0]))


data_index = os.path.realpath(sys.argv[1])
update_index = os.path.realpath(sys.argv[2])
bug_index = os.path.realpath(sys.argv[3])

output = sys.argv[4]

os.chdir(os.path.join(this_dir, '..'))

relative_dir = os.path.realpath(os.path.join(this_dir, '..'))

t = tarfile.TarFile.bz2open(output, mode = 'w')

def do_add(t, f):
    assert f.startswith(this_dir)
    assert f.startswith(relative_dir)
    archive_name = f.replace(relative_dir + "/", '')
    assert archive_name.startswith('evaluation'), f + " | " + archive_name
    t.add(archive_name)

def add_data_to_file(f):
    d = yaml.load(f)
    for data_type in d.itervalues():
        for prj in data_type.itervalues():
            for mode in prj.itervalues():
                for f_name in mode:
                    do_add(t, f_name)

def add_bugs_to_file(f):
    d = yaml.load(f)
    for p_dict in d.itervalues():
        do_add(t, p_dict["bug_db"])
        do_add(t, p_dict["property_file"])

with open(data_index, 'r') as f:
    add_data_to_file(f)

do_add(t, update_index)
do_add(t, data_index)
do_add(t, bug_index)

do_add(t, os.path.join(this_dir, 'openfire', 'update_db.yml'))

with open(bug_index, 'r') as f:
    add_bugs_to_file(f)

for p in ["openfire", "jforum", "subsonic"]:
    patch_dir = os.path.join(this_dir, p, "patches")
    do_add(t, patch_dir)
    do_add(t, os.path.join(this_dir, p, p + "_options.yml"))

t.close()
