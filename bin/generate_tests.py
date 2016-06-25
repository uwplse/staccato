from string import Template

wrapper_map = {
    "short": "Short",
    "int": ("Integer", "parseInt"),
    "long": "Long",
    "byte": "Byte",
    "float": "Float",
    "double": "Double"
}

def get_template(add_parse, prop, sentinel, sentinel2, expected, suff = ""):
    kv = { "prop": prop, "sent": str(sentinel), "sent2": str(sentinel2), "suff": suff, "expected": expected }
    ret = """
    @StaccatoPropagate(PropagationTarget.RETURN)
    public static $t propagate$t($t x) {
      return %(sent)s%(suff)s;
    }
    
    @StaccatoPropagate(PropagationTarget.RETURN)
    public static $wrapper propagate$wrapper($wrapper x) {
       return %(sent)s%(suff)s;
    }

    public static void test$wrapper() {
       Map<String, String> m = getMap();
       $t v1 = $wrapper.$parse(TaintHelper.getProp("%(prop)s", m));
    """ % kv
    if add_parse:
       ret += """    $t v2 = $wrapper.$parse(TaintHelper.getProp("%(prop)s", m), 10);\n""" % kv
    ret += """
       $wrapper w1 = $wrapper.valueOf(TaintHelper.getProp("%(prop)s", m));
       $wrapper w2 = $wrapper.valueOf(v1);
       $wrapper w3 = %(sent2)s%(suff)s;
       $t uw = w2.${t}Value();
       $wrapper w4 = v1;
       assert TaintPropagation.getTaint(w1).equals(%(expected)s);
       assert TaintPropagation.getTaint(w2).equals(%(expected)s);
       assert TaintPropagation.getTaint(w4).equals(%(expected)s);
       assert TaintPropagation.getTaint(w3) == null;

       assert %(expected)s.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
       assert %(expected)s.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
    """ % kv
    if add_parse:
       ret += """    assert %(expected)s.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));\n""" % kv
    ret += """
       $wrapper w5 = propagate$wrapper(w1);
       assert TaintPropagation.getTaint(w5).equals(%(expected)s);
       $wrapper ut = %(sent2)s%(suff)s;
       assert TaintPropagation.getTaint(ut) == null;

       $t wr = propagate$t(v1);
       assert %(expected)s.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
    }
""" % kv
    return Template(ret)
    
Template("""
    @StaccatoPropagate(PropagationTarget.RETURN)
    public static $t propagate$t($t x) {
      return 4;
    }
    
    @StaccatoPropagate(PropagationTarget.RETURN)
    public static $wrapper propagate$wrapper($wrapper x) {
       return 4;
    }

    public static void test$wrapper() {
       Map<String, String> m = getMap();
       $t v1 = $wrapper.$parse(TaintHelper.getProp("v", m));
       $t v2 = $wrapper.$parse(TaintHelper.getProp("v", m), 10);
       $wrapper w1 = $wrapper.valueOf(TaintHelper.getProp("v", m));
       $wrapper w2 = $wrapper.valueOf(v1);
       $wrapper w3 = 1;
       $t uw = w2.${t}Value();
       $wrapper w4 = v1;
       assert TaintPropagation.getTaint(w1).equals(${expected});
       assert TaintPropagation.getTaint(w2).equals(${expected});
       assert TaintPropagation.getTaint(w4).equals(${expected});
       assert TaintPropagation.getTaint(w3) == null;

       assert ${expected}.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
       assert ${expected}.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
       assert ${expected}.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));

       $wrapper w5 = propagate$wrapper(w1);
       assert TaintPropagation.getTaint(w5).equals(${expected});
       $wrapper ut = 4;
       assert TaintPropagation.getTaint(ut) == null;

       $t wr = propagate$t(v1);
       assert ${expected}.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
    }
    
""")

for i in ["short", "int", "long", "byte"]:
    wrapper_name = wrapper_map[i]
    if type(wrapper_name) == tuple:
        w = wrapper_name
        wrapper_name = w[0]
        parse_name = w[1]
    else:
        parse_name = "parse" + wrapper_name
    suff = ""
    if i == "long":
        suff = "l"
    templ = get_template(True, "v", 4, 1, "expectedVMap", suff)
    print templ.substitute({ "wrapper": wrapper_name, "t": i, "parse": parse_name })

for i in ["float", "double"]:
    wrapper_name = wrapper_map[i]
    parse_name = "parse" + wrapper_name
    suff = ""
    if i == "float":
        suff = "f"
    templ = get_template(False, "f", "0.5", "2.5", "expectedFMap", suff)
    print templ.substitute({ "wrapper": wrapper_name, "t": i, "parse": parse_name })
