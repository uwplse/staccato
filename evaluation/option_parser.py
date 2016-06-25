import HTMLParser
import random, string
import itertools

def randomword(length):
   return ''.join(random.choice(string.lowercase) for i in range(length))

class ConfigConstraint:
    def generate_setup(self):
        pass
    def generate(self, rand_var):
        pass
    def generate_int_index(self, ref, random_var):
        pass

class IntRange(ConfigConstraint):
    def __init__(self, start, stop):
        self.start = start
        self.stop = stop
    def generate(self, rand_var):
        m = self.stop - (self.start - 1)
        return "(%s.nextInt(%d) + %d) + \"\"" % (rand_var, m, self.start)

class BooleanConstraint(ConfigConstraint):
    def __init__(self):
        pass
    def generate(self, rand_var):
        return "%s.nextBoolean() ? \"true\" : \"false\"" % rand_var

BOOL_CONST = BooleanConstraint()

class StringGroup(ConfigConstraint):
    COUNTER = 1
    def __init__(self, *group):
        self.group = group
        self.key = "staccato.group" + str(StringGroup.COUNTER)
        StringGroup.COUNTER += 1
    def generate(self, rand_var):
        return "((String[])ctx.getVariables().getObject(\"%s\"))[%s.nextInt(%d)]" % (self.key, rand_var, len(self.group))
    def generate_setup(self):
        string_lit = "new String[]{" + ", ".join([ '"' + x + '"' for x in self.group ]) + "}"
        print 'ctx.getVariables().putObject("%s", %s);' % (self.key, string_lit)

class GroupConstraint():
    class ConstraintStringGroup(StringGroup):
        def __init__(self, options, fl):
            StringGroup.__init__(self, *options)
            self.flag = fl
        def generate_int_index(self, ref, random_var):
            if self.flag[0] == False:
                ind = ref.next()
                index_string = "tmp_ind_%d" % ind
                self.flag[0] = index_string
                print "\tint %s = %s.nextInt(%d);" % (index_string, random_var, len(self.group))
            else:
                return
        def generate(self, rand_var):
            return "((String[])ctx.getVariables().getObject(\"%s\"))[%s]" % (self.key, self.flag[0])
    def __init__(self, opt):
        self.shared_flag = [False]
        self.opt = opt
    def get_group(self, name):
        return GroupConstraint.ConstraintStringGroup(self.opt[name], self.shared_flag)

class IntGroup(StringGroup):
    def __init__(self, *group):
        new_grp = [ str(x) for x in group ]
        StringGroup.__init__(self, *new_grp)

class RandomStrings(StringGroup):
    def __init__(self, num_string, length = 10):
        s = []
        for i in range(0, num_string):
            s.append(randomword(length))
        StringGroup.__init__(self, *s)

class ConstValue(ConfigConstraint):
    def __init__(self, val):
        self.val = val
    def generate(self, rand_var):
        return "\"%s\"" % self.val

class Attribute:
    def __init__(self, name, ty, value):
        self.name = name
        self.type = ty
        self.value = value

class FormResult:
    def __init__(self, url, attrs):
        self.url = url
        self.attrs = attrs


class FormExtractor(HTMLParser.HTMLParser):
    def __init__(self, check_as_bool = True):
        HTMLParser.HTMLParser.__init__(self)
        self.handling_form = False
        self.url = None
        self.attributes = {}
        self.handling_select = False
        self.select_values = []
        self.select_name = None
        self.check_as_bool = check_as_bool
    def handle_starttag(self, tag_name, attrs):
        attrs = dict(attrs)
        if "readonly" in attrs:
            return
        if tag_name == "form":
            self.handling_form = True
            self.url = attrs["action"]
        elif tag_name == "input" and self.handling_form:
            i_type = attrs["type"]
            name = attrs.get("name", None)
            if i_type == "submit":
                if name is None:
                    return
                self.attributes[name] = { "type": "submit", "value": attrs["value"] }
            elif i_type == "radio":
                if name in self.attributes:
                    self.attributes[name]["value"].append(attrs["value"])
                else:
                    self.attributes[name] = { "type": "radio", "value": [ attrs["value"] ] }
            elif i_type == "checkbox":
                if self.check_as_bool:
                    self.attributes[name] = { "type": "checkbox", "value": BOOL_CONST }
                else:
                    self.attributes[name] = { "type": "checkbox", "value": attrs["value"] }
            elif i_type == "password" or i_type == "text":
                self.attributes[name] = { "type": "text", "value": None }
            elif i_type == "hidden":
                if name is not None and "value" in attrs:
                    self.attributes[name] = { "type": "submit", "value": attrs["value"] }
        elif tag_name == "textarea" and self.handling_form:
            self.attributes[attrs["name"]] = { "type": "text", "value": None }
        elif tag_name == "select" and self.handling_form:
            self.handling_select = True
            self.select_name = attrs["name"]
        elif tag_name == "option" and self.handling_select:
            self.select_values.append(attrs["value"])

    def handle_endtag(self, tag_name):
        if tag_name == "form":
            self.handling_form = False
        elif tag_name == "select":
            self.handling_select = False
            self.attributes[self.select_name] = { "type": "radio", "value": self.select_values }
            self.select_values = []
            self.select_name = None

    def constraint_key(self):
        return self.url

    def extract(self, constraints):
        to_ret = {}
        url = self.url
        const_key = self.constraint_key()
        f_constraints = constraints.get(const_key, {})
        for (name,value) in self.attributes.iteritems():
            if name in f_constraints and f_constraints[name] is None:
                continue
            if value["type"] == "text":
                to_ret[name] = Attribute(name, value["type"], f_constraints[name])
            elif value["type"] == "radio":
                to_ret[name] = Attribute(name, "text", StringGroup(*value["value"]))
            else:
                if name in f_constraints:
                    to_ret[name] = Attribute(name, value["type"], f_constraints[name])
                else:
                    to_ret[name] = Attribute(name, value["type"], value["value"])
        return FormResult(self.url, to_ret)

def dump_config(forms, check_as_bool = True):
    print "Random r = ctx.getVariables().getObject(\"staccato.rand\");"
    num_configs = len(forms)
    generators = []
    print "int controller_id = r.nextInt(%d);" % num_configs
    it = itertools.count(0)
    for i in range(0, num_configs):
        p = forms[i]
        if i != 0:
            print "else",
        print "if(controller_id == %d) {" % i
        print "\tsampler.setPath(\"%s\");" % p.url
        for (name,attr) in p.attrs.iteritems():
            if isinstance(attr.value, ConfigConstraint):
                attr.value.generate_int_index(it, "r")
            if attr.type == "radio":
                print "\tsampler.addArgument(\"%s\", r.nextBoolean() ? \"%s\" : \"%s\");" % (name, attr.value[0], attr.value[1])
            elif attr.type == "submit":
                print "\tsampler.addArgument(\"%s\", \"%s\");" % (name, attr.value)
            elif attr.type == "checkbox":
                if check_as_bool or isinstance(attr.value, ConfigConstraint):
                    print "\tsampler.addArgument(\"%s\", %s);" % (name, attr.value.generate("r"))
                else:
                    print "\tif(r.nextBoolean()) {"
                    print "\t\tsampler.addArgument(\"%s\", \"%s\");" % (name, attr.value)
                    print "\t}"
            else:
                print "\tsampler.addArgument(\"%s\", %s);" % (name, attr.value.generate("r"))
                generators.append(attr.value)
        print "}"

    print "ctx.getVariables().putObject(\"staccato.rand\", new Random(ctx.getThread().getThreadNum()));"

    for gen in generators:
        gen.generate_setup()

