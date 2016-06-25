for i in ["int", "char", "boolean", "short", "float", "long", "double", "byte"]:
    wrapper_name = "Tainted" + i[0:1].upper() + i[1:] + "WithObjTag"
    print """public static %(wrapper_name)s checkFieldTaint(Object source, Taint t, %(prim_type)s v, String fieldName, ReentrantReadWriteLock rwl) {
		if(t == null || t.lbl == null) {
			%(wrapper_name)s.valueOf(null, v);
		}
		%(wrapper_name)s ret = new %(wrapper_name)s();
		doPrimitiveCheck(source, t, v, fieldName, rwl, %(prim_type)sUpdate, ret);
		return ret;
    }""" % { "prim_type" : i, "wrapper_name": wrapper_name }

