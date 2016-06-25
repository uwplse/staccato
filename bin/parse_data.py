from scipy.stats.mstats import gmean
from numpy import mean
from scipy.stats import describe
import numpy
from data_parsers import handlers
import sys
import yaml

curr_state = 0

name_map = {
    "post_reply": "Post 1",
    "post_new_topic": "Post 2",
    "pm_reply": "Msg. 1",
    "pm_send": "Msg. 2",
    "jukebox-add": "Juke. 1",
    "get now playing": "Juke. 2",
    "jukebox-control-clear": "Juke. 3",
    "jukebox-control-get": "Juke. 4",
    "jukebox-control-status": "Juke. 5",
    "get genres": "Query 1",
    "get indexes": "Query 2",
    "index page": "Home",
    "get starred": "Query 3",
    "get artists": "Query 4",
    "get random songs": "Query 5",
    "get music directory": "Query 7",
    "get music folders": "Query 8",
    "search": "Query 6",
    "login/logout": "Login",
    "tr_chat1": "Chat 1",
    "tr_chat2": "Chat 2",
    "tr_chat3": "Chat 3",
    "tr_rosteraction": "Roster"
}

project_map = {
    "openfire": "OF",
    "subsonic": "SS",
    "jforum": "JF"
}

def add_table_entry(k, project, orig_time, inst_time):
    k = name_map[k]
    unit = 'ms'
    if orig_time > 2000 or inst_time > 2000:
        orig_time /= 1000
        inst_time /= 1000
        unit = 's'
    slowdown = 100 * (inst_time / float(orig_time))
    p_key = project_map[project]
    print "(%s) %s | %0.1f%s | %0.1f%s | %0.1f%%" % (p_key, k, orig_time, unit, inst_time, unit, slowdown)


index = None
with open(sys.argv[1], "r") as f:
    index = yaml.load(f)

def extract_times(project, data):
    handler = handlers[project]
    a = {}
    for i in data:
        (t, _) = handler(i)
        for (k,v) in t.iteritems():
            if k not in a:
                a[k] = []
            a[k] += v
    to_ret = {}
    for (k,v) in a.iteritems():
        if project != "openfire":
            percentile = numpy.percentile(v, 95)
            v = filter(lambda d:  d <= percentile, v)
        to_ret[k] = mean(v)
    return to_ret

def handle_project(data_type):
    data = index["runtime"][data_type]
    inst_time = extract_times(data_type, data["inst"])
    base_time = extract_times(data_type, data["base"])
    tables_entry = []
    for (i,t) in inst_time.iteritems():
        if i not in base_time:
            print "not found: ", i, "for project", data_type
            continue
        tables_entry.append((i, data_type, base_time[i], t))
    tables_entry.sort(key= lambda d: name_map[d[0]])
    for t in tables_entry:
        add_table_entry(*t)

print r'Action | Originial | Instrumented | Slowdown'

for p in ["jforum", "subsonic", "openfire"]:
    handle_project(p)

