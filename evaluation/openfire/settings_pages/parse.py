from option_parser import *
import sys

import socket

CONSTRAINTS = {
    "file-transfer-proxy.jsp": {
        "port": IntRange(8045, 8050)
    },
    "audit-policy.jsp": {
        "maxTotalSize": IntRange(0, 1000),
        "maxDays": IntRange(-1, 3),
        "maxFileSize": IntRange(0, 15),
        "logDir": StringGroup("/tmp/audit", "/tmp/audit2"),
        "logTimeout": IntRange(1, 360),
        "ignore": StringGroup("testuser0,testuser1,testuser2", "testuser3,testuser4,testuser5")
    },
    "client-connections-settings.jsp": {
        "port": IntGroup(5222, 5224),
        "sslPort": IntGroup(5223, 5225),
        "clientIdle": IntRange(360, 720)
    },
    "connection-managers-settings.jsp": {
        "port": IntRange(8000, 8015),
        "defaultSecret": RandomStrings(3)
    },
    "system-email.jsp": {
        "host": StringGroup("localhost", socket.gethostname(), "127.0.0.1"),
        "port": IntGroup(25, 2500),
        "server_username": RandomStrings(2),
        "server_password": RandomStrings(2)
    },
    "external-components-settings.jsp": {
        "port": IntRange(8016, 8030),
        "defaultSecret": RandomStrings(2)
    },
    "http-bind.jsp": {
        "port": IntGroup(7070, 7071),
        "securePort": IntGroup(7443, 7444),
        "CORSDomains": StringGroup("*", "localhost", "www.google.com,localhost"),
        "XFFHeader": RandomStrings(2),
        "XFFServerHeader": RandomStrings(3),
        "XFFHostHeader": RandomStrings(3),
        "XFFHostName": RandomStrings(4)
    },
    "offline-messages.jsp": {
        "quota": IntRange(100, 200)
    },
    "reg-settings.jsp": {
        "allowedIPs": StringGroup("*", "127.0.0.1,127.*.*.1", "127.*.*.1"),
        "allowedAnonymIPs": StringGroup("*", "127.0.0.1,127.0.*.*", "127.0.0.*")
    },
    "session-conflict.jsp": {
        "kickValue": IntRange(1,5)
    },
    # 'server2server-settings.jsp': {

    # }
    "manage-updates.jsp": {
        "proxyHost": StringGroup("localhost", "127.0.0.1"),
        "proxyPort": IntGroup(8055, 8056)
    }
}

PROPERTY_GROUPS = {
 
    "provider.auth.className": [
        "org.jivesoftware.openfire.auth.DefaultAuthProvider", 
        "org.jivesoftware.openfire.auth.JDBCAuthProvider", "org.jivesoftware.openfire.auth.HybridAuthProvider"],
    "provider.user.className": [
        "org.jivesoftware.openfire.user.DefaultUserProvider", 
        "org.jivesoftware.openfire.user.JDBCUserProvider", "org.jivesoftware.openfire.user.HybridUserProvider"],
    "provider.roster.className":[ "org.jivesoftware.openfire.roster.DefaultRosterItemProvider", "org.jivesoftware.openfire.roster.MyRosterItemProvider"],
    "provider.admin.className": ["org.jivesoftware.openfire.admin.DefaultAdminProvider", "org.jivesoftware.openfire.admin.MyAdminProvider"]
}

forms = []

for (k,v) in PROPERTY_GROUPS.iteritems():
    url = "server-properties.jsp"
    attr = {
        "propName": Attribute("propName", "text", ConstValue(k)),
        "propValue": Attribute("propValue", "text", StringGroup(*v)),
        "save": Attribute("save", "submit", "Save Property")
    }
    forms.append(FormResult(url, attr))

for i in sys.argv[1:]:
    v = None
    with open(i) as f:
        v = f.read()
    f_extract = FormExtractor()
    f_extract.feed(v)
    forms.append(f_extract.extract(CONSTRAINTS))

dump_config(forms)
