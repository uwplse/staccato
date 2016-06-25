wrappers = [ "Integer", "Long", "Byte", "Short", "Character", "Boolean", "Double", "Float" ]

for i in wrappers:
    fqn = "java.lang." + i
    print "<%(n)s,%(n)s>" % {"n": fqn}
    print "<%s,java.lang.String>" % fqn
    print "<%s:valueOf>" % fqn
    if i == "Character":
        continue
    if i == "Integer":
        parseName = "parseInt"
    else:
        parseName = "parse" + i
    print "<%s:%s>" % (fqn, parseName)
