from scipy.stats.mstats import gmean
from scipy.stats import describe
import numpy
from data_parsers import handlers
import sys
import yaml

index = None
with open(sys.argv[1], "r") as f:
    index = yaml.load(f)
data_type = sys.argv[2]
handler = handlers[data_type]

def calc_slowdown(base_time, inst_time):
    # return (sum(inst_time.itervalues()) / float(sum(base_time.itervalues())) - 1) * 100
    vals = []
    for i in base_time.iterkeys():
        if i not in inst_time:
            continue
#        print i, inst_time[i] / float(base_time[i])
        vals.append(inst_time[i] / float(base_time[i]))
    return gmean(vals)
#    return (gmean(vals) - 1)# * 100

def extract_times(data):
    a = {}
    for i in data:
        (t, _) = handler(i)
        for (k,v) in t.iteritems():
            if k not in a:
                a[k] = []
            a[k] += v
    to_ret = {}
    for (k,v) in a.iteritems():
        if data_type != "openfire":
            percentile = numpy.percentile(v, 95)
            v = filter(lambda d:  d <= percentile, v)
            #v2 = numpy.median(v)
            v2 = numpy.mean(v)
        else:
            v2 = numpy.mean(v)
        to_ret[k] = v2
    return to_ret

data = index["runtime"][data_type]
inst_time = extract_times(data["inst"])
#print '---'
base_time = extract_times(data["base"])

slowdown_percent = calc_slowdown(base_time, inst_time)
slowdown_factor = slowdown_percent

print "\def\%sslowdown{%.02f}" % (data_type, slowdown_factor)
