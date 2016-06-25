import sys

name_map = {
    "int": "Integer"
}

spec = {
    "Integer": [
        "short",
        "byte",
        "int"
    ],
    "Short": [
        "short",
        "byte",
        "int"
    ],
    "Byte": [
        "short",
        "byte",
        "int"
    ]
}

for (k,v) in spec.iteritems():
    for t in v:
        print "public static Integer %(wrapper)s_%(primitive)sValue(%(wrapper)s s) {\n return (int)s.%(primitive)sValue();\n }" % { "wrapper": k, "primitive": t }
