import yaml

spec = None
with open('./havoc_properties.yml', 'r') as f:
    spec = yaml.load(f)

def dump_dlist(l):
    for s in l:
        for (k,v) in s.iteritems():
            print 'm.put("%s", "%s");' % (k,v)        

print "java.util.Map m = null;"
print "java.util.List l = null;"
i = 0
for s in spec:
    print "m = new java.util.HashMap();"
    if type(s) == dict:
        dump_dlist([s])
    elif type(s) == list:
        dump_dlist(s)
        print "l = new java.util.ArrayList();"
        for e in s:
            for k in e.iterkeys():
                print 'l.add("%s");' % k
        print 'ctx.getVariables().putObject("staccato.kv-group-%i-order", l);' % i
    print 'ctx.getVariables().putObject("staccato.kv-group-%i", m);' % i
    i += 1
print 'ctx.getVariables().putObject("staccato.num-kv", 0);'
print 'ctx.getVariables().putObject("staccato.next", 0);'

