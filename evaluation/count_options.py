#!/usr/bin/python

import yaml
import sys
import os

def stats(checked, update_added=0, bugs=0, imm=0, other=0, no_update=0, control_flow=0, prim=0, prop=0):
    ratio = sum([checked]) / float(sum([checked, control_flow, prim, other]))
    ratio *= 100
    ratio = round(ratio)
    return {
        "total": sum([checked, bugs, imm, other, no_update, prim]),
        "checked": checked,
        "addupdate": update_added,
        "bugs": bugs,
        "imm": imm,
        "other": other,
        "noupdate": no_update,
        "controlflow": control_flow,
        "primitive": prim,
        "ratio": ratio,
        "prop": prop
    }

def dump_stats(project, stats):
    for (name,stat) in stats.iteritems():
        print r'\def\%s%s{%d}' % (project, name, stat)

def count_openfire(yaml_doc):
    tech = len(yaml_doc["impossible (tech)"])
    other =  len(yaml_doc["impossible (update-object-in-place)"])
    imm = len(yaml_doc["uninteresting (re-read)"])
    no_update = len(yaml_doc["impossible (update-breaks)"])
    prim = 0
    def count_header(hdr):
        to_ret = set()
        for item in yaml_doc[hdr]:
            if type(item) is str:
                to_ret.add(item)
            elif type(item) is dict:
                to_ret.add(list(item.iterkeys())[0])
            else:
                raise RuntimeError("unexpected type")
        return to_ret
    def count_props(hdrs):
        ref_props = set()
        for hdr in hdrs:
            ref_props |= count_header(hdr)
        return len(ref_props)
        
    prim = count_props([
        "primitives",
        "impossible (integer)",
        "impossible (enum)",
        "impossible (boolean)"
    ])

    enum = len(yaml_doc["impossible (enum)"])
    buggy = len(yaml_doc["impossible (buggy)"])
    no_update += len(yaml_doc["uninstrumented"])
    no_update += len(yaml_doc["impossible (update-breaks)"])
    checked = len(yaml_doc["instrumented"])
    cf = len(yaml_doc["impossible (control flow)"])
    update_added = 0
    for i in yaml_doc["instrumented"]:
        (prop, tags) = list(i.iteritems())[0]
        if "update-added" in tags:
            update_added += 1
    other = tech
    s = stats(checked, update_added, buggy, imm, other, no_update, cf, prim, tech)
    dump_stats("openfire", s)

def count_jforum(yaml_doc):
    imm = 0
    checked = 0
    updated = 0
    prim = 0
    control_flow = 0
    for (prop, tags) in yaml_doc.iteritems():
        tags = set(tags)
        if "rrpr" in tags:
            imm += 1
            continue
        if "added-update" in tags:
            updated += 1
        if "checked" in tags:
            checked += 1
            continue
        if "control-flow" in tags:
            control_flow += 1
            continue
        if "bool" in tags or "primitive" in tags:
            prim += 1
            continue
        print "%% no tag found for", prop
    s = stats(checked=checked, update_added=updated, imm=imm, prim=prim, control_flow = control_flow)
    dump_stats("jforum", s)

def count_subsonic(yaml_doc):
    imm = 0
    update_added = 0
    checked = 0
    no_update = 0
    prop = 0
    prim = 0
    control_flow = 0
    for (key, data) in yaml_doc.iteritems():
        if not key.startswith("KEY_"):
            continue
        tags = data["tags"]
        if "integer" in tags or \
           "boolean" in tags or \
           "long" in tags:
            prim += 1
            continue
        if "license" in tags:
            continue
        if "state" in tags:
            continue
        if "control-flow" in tags:
            control_flow += 1
        if "redirect" in tags:
            prop += 1
            continue
        if "unint" in tags or \
           "rrpr" in tags or \
           "ifti" in tags:
            imm += 1
            continue
        if "lin" in tags:
            #print "apparently we checked", key
            checked += 1
            continue
        print "% No matching category found for", key
    s = stats(checked=checked, update_added=update_added, imm=imm, other=0, no_update=no_update,
              prim = prim, control_flow = control_flow, prop = prop)
    dump_stats("subsonic", s)

handler_map = {
    "subsonic": count_subsonic,
    "jforum": count_jforum,
    "openfire": count_openfire
}

f_name = sys.argv[2]
project = sys.argv[1]

if project not in handler_map:
    sys.exit(-1)

yaml_doc = yaml.load(open(f_name))

handler_map[project](yaml_doc)
