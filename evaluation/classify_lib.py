import yaml
import sys

expand = {
    "$$StaccatoGroup-1": ["mail.smtp.username","mail.smtp.password"],
    "$$StaccatoGroup-0": ["LocaleLanguage","LocaleCountry","LocaleVariant"]
}

def compute_stats(untested, checked, option_spec):
    checked = set(checked) & set(option_spec["all_props"])
    nums = {
        "checked": len(checked),
        "updated": len(option_spec["updated"]),
        "immutable": len(option_spec["immutable"]),
        "internal": len(option_spec["internal"]),
        "untested": len(untested),
        "other": len(option_spec["other"]),
        "total": len(option_spec["all_props"])
    }
    num = len(checked) + len(option_spec["updated"])
    coverage =  (num * 100) / float(num + len(untested) + len(option_spec['internal']))
    nums["coverage"] = coverage
    return nums

def parse_checked(prop_file):
    check = None
    with open(prop_file, 'r') as f:
        check = yaml.load(f)
    for k1 in list(check.iterkeys()):
        values = set(check[k1])
        for (k,v) in expand.iteritems():
            if k in values:
                values.remove(k)
                values |= set(v)
        check[k1] = list(values)
    checked_props = set(check["con"]) | set(check["strict"])
    tested_props = set(check["write"])
    return tested_props & checked_props

def parse(option_file, prop_file):
    option_spec = None
    with open(option_file, 'r') as f:
        option_spec = yaml.load(f)
    checked_props = parse_checked(prop_file)
    candidate_props = set(option_spec["all_props"]) - (set(option_spec["immutable"]) | set(option_spec["updated"]) | set(option_spec["internal"]) | set(option_spec["other"]))
    checked_props = checked_props & candidate_props
    untested = candidate_props - checked_props
    return compute_stats(untested, checked_props, option_spec)
