import sys
import yaml

def parse_db(db):
    to_ret = {}
    if db is None:
        return to_ret
    for i in db:
        st = tuple(i["stacktrace"])
        tags = i["tags"]
        props = set(i["props"])
        prop_s = list(i["props"])
        prop_s.sort()
        prop_s = tuple(prop_s)
        k = (prop_s,st)
        eid = i["id"]
        to_ret[k] = { "tags": tags, "props": props, "id": eid, "prop_ver": i["ver"] }
    return to_ret

def write_db(s, db):
    doc = []
    for (k,v) in db.iteritems():
        doc.append({"stacktrace": list(k[1]), "tags": v["tags"], "id": v["id"], "props": list(v["props"]), "ver": v["prop_ver"] })
    yaml.dump(doc, s)

db = None
with open(sys.argv[2], 'r') as dbf:
    db_raw = yaml.load(dbf)
    db = parse_db(db_raw)

import re
msg_patt = re.compile('Epoch violation (repaired|detected): (\{\{[^}]+\}\}), bad props: \[([^]]+)\]')

def parse_message(msg):
    m = msg_patt.search(msg)
    if m is None:
        print "Unable to parse", msg
        sys.exit(-1)
    return (m.group(1), m.group(2), m.group(3).split(", "))
    
def compute_id(key):
    import hashlib
    m = hashlib.md5()
    for s in key[0]:
        m.update(s)
    for s in key[1]:
        m.update(s)
    return m.hexdigest()[:8]

def parse_log(db, btf):
    l = btf.readline()
    found_bugs = set()
    while l != '':
        l = l.rstrip('\n')
        st = []
        message = l
        l = btf.readline()
        while l != '':
            if l.startswith('\tat'):
                l = l.rstrip('\n')
                st.append(l)
                l = btf.readline()
            else:
                break
        st_t = tuple(st)
        (action, ver, props) = parse_message(message)
        p_key = list(props)
        p_key.sort()
        p_key = tuple(p_key)
        key = (p_key, st_t)
        if key not in db:
            eid = compute_id(key)
            found_bugs.add(eid)
            tag = None
            if action == "repaired":
                tag = { "repair": True }
            db[key] = { "tags": tag, "props": props, "id": eid, "prop_ver": ver }
            print "Found new entry", eid
        else:
            found_bugs.add(db[key]["id"])
    return found_bugs

seen_bugs = None

with open(sys.argv[1], 'r') as btf:
    seen_bugs = parse_log(db, btf)

with open(sys.argv[2], 'w') as dbf:
    write_db(dbf, db)

with open(sys.argv[3], 'w') as bug_file:
    for b in seen_bugs:
        print >> bug_file, b

print "Done"
