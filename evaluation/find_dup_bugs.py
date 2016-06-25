import yaml
import sys
import re

line_number_extract = re.compile("^\tat ([^(]+)\(([^:]+)(?::\d+)?\)$")

raw_bug_db = None
with open(sys.argv[1], 'r') as f:
    raw_bug_db = yaml.load(f)

def is_dup_tag(t):
    return type(t) == dict and "dup" in t

prefixes = {}
for v in raw_bug_db:
    curr_tag = v["tags"]
    st = v["stacktrace"]
    app_code_start = -1
    for i in range(0, len(st)):
        st_elem = st[i]
        if not st_elem.startswith("\tat edu.washington.cse.instrumentation.runtime.TaintHelper"):
            app_code_start = i
            break
    assert app_code_start != -1, v
    start_elem = st[app_code_start]
    m = line_number_extract.match(start_elem)
    assert m is not None, v
    stripped_elem = "\tat %s(%s)" % (m.group(1), m.group(2))
    props = list(v["props"])
    props.sort()
    p_tup = tuple(props)
    st_key = tuple([stripped_elem] + st[app_code_start + 1:])
    key = (p_tup, st_key)
    if key not in prefixes:
        prefixes[key] = { "tag": None, "parent": None, "elems": [] }
    dups = prefixes[key]
    if curr_tag is not None and is_dup_tag(curr_tag):
        if dups["tag"] is None:
            dups["tag"] = curr_tag
            dups["parent"] = v
        elif is_dup_tag(dups["tag"]):
            assert dups["tag"]["dup"] == curr_tag["dup"], (dups, v)
            dups["elems"].append(v)
        else:
            assert dups["parent"]["id"] == curr_tag["dup"], (dups, v)
            dups["elems"].append(v)
    elif curr_tag is not None:
        if dups["tag"] is not None:
            assert is_dup_tag(dups["tag"]) and dups["tag"]["dup"] == v["id"], (dups, v)
            dups["elems"].append(dups["parent"])
        dups["tag"] = curr_tag
        dups["parent"] = v
    else:
        dups["elems"].append(v)

output_db = []

for v in prefixes.itervalues():
    if v["tag"] is None:
        if len(v["elems"]) > 1:
            print "Possible dup group", [ x["id"] for x in v["elems"] ]
        output_db += v["elems"]
    elif is_dup_tag(v["tag"]):
        output_db.append(v["parent"])
        for e in v["elems"]:
            e["tags"] = v["tag"]
            output_db.append(e)
    else:
        output_db.append(v["parent"])
        for e in v["elems"]:
            e["tags"] = { "dup": v["parent"]["id"] }
            output_db.append(e)

assert len(output_db) == len(raw_bug_db)

assert set([ x["id"] for x in output_db ]) == set([ x["id"] for x in raw_bug_db ])

with open(sys.argv[2], 'w') as f:
    yaml.dump(output_db, f)
