import sys
import yaml
import os

eval_dir = sys.argv[1]
file_name = sys.argv[2]
merge_depth = int(sys.argv[3])
output_file = sys.argv[4]

def merge(out, in_doc, d):
    if d == 0:
        out.update(in_doc)
        return
    for (k,v) in in_doc.iteritems():
        if k not in out:
            out[k] = v
        else:
            merge(out[k], v, d - 1)

output_doc = {}
for p in ["openfire", "jforum", "subsonic"]:
    input_file = os.path.join(eval_dir, p, file_name)
    input_doc = None
    with open(input_file, "r") as f:
        input_doc = yaml.load(f)
    merge(output_doc, input_doc, merge_depth)

with open(output_file, "w") as f:
    yaml.dump(output_doc, f)
