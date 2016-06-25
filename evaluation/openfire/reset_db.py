import psycopg2
import socket

hostname = socket.gethostname()

auth_jid = ",".join([ usr + "@" +hostname for usr in ["jtoman", "admin"]])

# snapshot of the properties table
props = [
    ("admin.authorizedGroups","admins"),
    ("admin.authorizedJIDs",auth_jid),
    ("cache.KrakenRegistrationCache.maxLifetime","-1"),
    ("cache.KrakenRegistrationCache.min","-1"),
    ("cache.KrakenRegistrationCache.size","-1"),
    ("cache.KrakenRegistrationCache.type","optimistic"),
    ("cache.KrakenSessionLocationCache.maxLifetime","-1"),
    ("cache.KrakenSessionLocationCache.min","-1"),
    ("cache.KrakenSessionLocationCache.size","-1"),
    ("cache.KrakenSessionLocationCache.type","optimistic"),
    ("conversation.idleTime","10"),
    ("conversation.maxAge","0"),
    ("conversation.maxRetrievable","0"),
    ("conversation.maxTime","60"),
    ("conversation.messageArchiving","true"),
    ("conversation.metadataArchiving","true"),
    ("conversation.roomArchiving","false"),
    ("conversation.roomsArchived","''"),
    ("demo.workgroup","true"),
    ("fastpath.database.setup","true"),
    ("hybridAuthProvider.primaryProvider.className","org.jivesoftware.openfire.auth.DefaultAuthProvider"),
    ("hybridAuthProvider.primaryProvider.overrideList","testuser200,testuser700,testuser600"),
    ("hybridAuthProvider.secondaryProvider.className","org.jivesoftware.openfire.auth.JDBCAuthProvider"),
    ("hybridAuthProvider.secondaryProvider.overrideList","testuser1111,testuser3490"),
    ("hybridAuthProvider.tertiaryProvider.className","org.jivesoftware.openfire.auth.MyAuthProvider"),
    ("hybridAuthProvider.tertiaryProvider.overrideList","testuser2"),
    ("hybridUserProvider.primaryProvider.className","org.jivesoftware.openfire.user.MyUserProvider"),
    ("hybridUserProvider.secondaryProvider.className","org.jivesoftware.openfire.user.DefaultUserProvider"),
    ("hybridUserProvider.tertiaryProvider.className","org.jivesoftware.openfire.user.JDBCUserProvider"),
    ("jdbcAuthProvider.allowUpdate","false"),
    ("jdbcAuthProvider.useConnectionProvider", "false"),
    ("jdbcUserProvider.useConnectionProvider", "false"),
    ("jdbcAuthProvider.passwordSQL","select password from website.users where username = ? and 1 = 1"),
    ("jdbcAuthProvider.passwordType","plain"),
    ("jdbcProvider.connectionString","jdbc:postgresql://localhost:5432/test_db?user=ritis&password=fizzbizz"),
    ("jdbcProvider.driver","org.postgresql.Driver"),

    ("jdbcGroupProvider.useConnectionProvider", "false"),
    ("jdbcGroupProvider.groupCountSQL", "SELECT count(*) FROM ofGroup"),
    ("jdbcGroupProvider.allGroupsSQL", "SELECT groupName FROM ofGroup ORDER BY groupName"),
    ("jdbcGroupProvider.userGroupsSQL", "SELECT groupName FROM ofGroupUser WHERE username=?"),
    ("jdbcGroupProvider.descriptionSQL", "SELECT description FROM ofGroup WHERE groupName=?"),
    ("jdbcGroupProvider.loadMembersSQL", "SELECT username FROM ofGroupUser WHERE administrator=0 AND groupName=? ORDER BY username"),
    ("jdbcGroupProvider.loadAdminsSQL", "SELECT username FROM ofGroupUser WHERE administrator=1 AND groupName=? ORDER BY username"),

    ("jdbcUserProvider.allUsersSQL","select username from website.users"),
    ("jdbcUserProvider.loadUserSQL","select name, email from website.users where username = ? and 1 = 1"),
    ("jdbcUserProvider.searchSQL","select username from website.users WHERE"),
    ("jdbcUserProvider.userCountSQL","select count(*) From website.users"),
    ("mail.configured","true"),
    ("mail.debug","false"),
    ("mail.smtp.host","localhost2"),
    ("mail.smtp.password","foobar"),
    ("mail.smtp.port","25"),
    ("mail.smtp.ssl","false"),
    ("mail.smtp.username","jtoman"),
    ("mediaproxy.echoPort","10020"),
    ("mediaproxy.enabled","false"),
    ("mediaproxy.idleTimeout","60000"),
    ("mediaproxy.lifetime","9000"),
    ("mediaproxy.portMax","20000"),
    ("mediaproxy.portMin","10000"),
    ("mediaproxy.serviceName","ofbridge"),
    ("passwordKey","ibS43kNeJGBDx9W"),
    ("plugin.userservice.secret","0aV4mSlG"),
    ("provider.admin.className","org.jivesoftware.openfire.admin.DefaultAdminProvider"),
    ("provider.auth.className","org.jivesoftware.openfire.auth.DefaultAuthProvider"),
    ("provider.group.className","org.jivesoftware.openfire.group.DefaultGroupProvider"),
    ("provider.lockout.className","org.jivesoftware.openfire.lockout.DefaultLockOutProvider"),
    ("provider.roster.className","org.jivesoftware.openfire.roster.DefaultRosterItemProvider"),
    ("provider.securityAudit.className","org.jivesoftware.openfire.security.DefaultSecurityAuditProvider"),
    ("provider.user.className","org.jivesoftware.openfire.user.DefaultUserProvider"),
    ("provider.vcard.className","org.jivesoftware.openfire.vcard.DefaultVCardProvider"),
    ("register.inband","true"),
    ("register.password","true"),
    ("sasl.approvedRealms","localhost"),
    ("update.lastCheck","1431559969163"),
    ("user.usePlainPassword","true"),
    ("xmpp.audit.active","false"),
    ("xmpp.audit.days","-1"),
    ("xmpp.audit.filesize","10"),
    ("xmpp.audit.ignore","testuser3,testuser4,testuser5"),
    ("xmpp.audit.iq","true"),
    ("xmpp.audit.logdir","/tmp/audit"),
    ("xmpp.audit.logtimeout","120000"),
    ("xmpp.audit.message","true"),
    ("xmpp.audit.presence","true"),
    ("xmpp.audit.totalsize","1000"),
    ("xmpp.auth.anonymous","true"),
    ("xmpp.auth.sharedSecretEnabled","true"),
    ("xmpp.client.login.allowed",""),
    ("xmpp.client.tls.policy","disabled"),
    ("xmpp.domain",hostname),
    ("xmpp.filetransfer.enabled","true"),
    ("xmpp.proxy.enabled","true"),
    ("xmpp.proxy.externalip","10.0.0.7"),
    ("xmpp.proxy.port","7777"),
    ("xmpp.proxy.service","file-proxy"),
    ("xmpp.server.certificate.accept-selfsigned","false"),
    ("xmpp.server.dialback.enabled","true"),
    ("xmpp.server.tls.enabled","true"),
    ("xmpp.session.conflict-limit","0"),
    ("xmpp.socket.ssl.active","false")
]


conn = psycopg2.connect("dbname=openfire user=openfire password=openfire123 host=127.0.0.1")
cursor = conn.cursor()
cursor.execute("""DELETE FROM ofproperty""")

cursor.executemany("""INSERT INTO ofProperty(name, propvalue) VALUES (%s,%s)""", props)

cursor.execute("""DELETE FROM ofrostergroups WHERE rosterid IN (
  SELECT rosterid FROM ofroster WHERE username LIKE 'testuser%')""")
print cursor.rowcount
cursor.execute("""DELETE FROM ofroster WHERE username LIKE 'testuser%'""")
print cursor.rowcount

conn.commit()
