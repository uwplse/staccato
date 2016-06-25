import ldap
import sys

conn = ldap.initialize("ldap://localhost:389/")
conn.bind_s("cn=admin,dc=nodomain", "foobar")

curr_users = conn.search_s("ou=Users,dc=nodomain", ldap.SCOPE_ONELEVEL)
for i in curr_users:
    (dn,_) = i
    if dn == "uid=Joe,ou=Users,dc=nodomain":
        continue
    conn.delete_s(dn)

for i in xrange(0,100):
    username = "username%03d" % i
    dn = "uid=" + username + ",ou=Users,dc=nodomain"
    attributes = [('objectClass', ['account', 'top', 'simpleSecurityObject']), ('userPassword', 'password'), ('uid', username)]
    conn.add_s(dn, attributes)
