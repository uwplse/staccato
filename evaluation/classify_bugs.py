import yaml
import sys

bug_db = None
with open(sys.argv[1], 'r') as f:
    bug_db = yaml.load(f)

bug_classify = None
with open(sys.argv[2], 'r') as f:
    bug_classify = yaml.load(f)

raw_output = len(sys.argv) > 3 and sys.argv[3] == "--raw"

bug_map = {}

for i in bug_classify:
    bug_type = i["type"]
    props = i["props"]
    if type(props) == str:
        props = [props]
    name = None
    if "name" not in i and len(props) == 1:
        name = props[0]
    else:
        name = i["name"]
    spec = {
        "name": name,
        "type": i["type"]
    }
    for i in props:
        bug_map[i] = spec

found_bugs = {}
for i in bug_db:
    is_repair = i["tags"] == {"repair": True}
    for p in i["props"]:
        if p not in bug_map:
            print "Unrecognized property: %s %s" % (p, i["id"])
            if raw_output:
                import sys
                sys.exit(1)
            continue
        bug_name = bug_map[p]["name"]
        result = {"repair": is_repair, "type": bug_map[p]["type"] }
        if bug_name not in found_bugs:
            found_bugs[bug_name] = result
        else:
            if found_bugs[bug_name] != result:
                print "Conflicting bug results: %s %s %s" % (i["id"], str(result), str(found_bugs[bug_name]))

if raw_output:
    import cPickle as pickle
    import base64
    print base64.b64encode(pickle.dumps(found_bugs))
    sys.exit(0)

by_type_count = {}
for (k,v) in found_bugs.iteritems():
    t = v["type"]
    if v["repair"]:
        t += " (repaired)"
    new_count = by_type_count.get(t, 0) + 1
    by_type_count[t] = new_count

print "Results by bug type:"

for (k,v) in by_type_count.iteritems():
    print "%s: %s" % (k, v)

print "All bugs:"
for (k,v) in found_bugs.iteritems():
    print "%s | type: %s, repaired?: %s" % (k, v["type"], v["repair"])
