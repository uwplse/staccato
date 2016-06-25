import psycopg2

conn = psycopg2.connect("dbname=openfire user=openfire password=openfire123 host=127.0.0.1")
conn2 = psycopg2.connect("dbname=test_db user=ritis password=fizzbizz host=127.0.0.1")

cursor = conn.cursor()
cursor.execute("""DELETE FROM ofUser where username LIKE 'testuser%'""")

cursor2 = conn2.cursor()
cursor2.execute("""DELETE FROM website.users where username LIKE 'testuser%'""")

username = "testuser"
testpassword = "testpassword"

num_users = 10000

email_templ = "@example.com"
name = "Test User"

query = "INSERT INTO ofUser(username, plainpassword, \"name\", email, creationdate, modificationdate) VALUES (%(username)s, %(password)s, %(name)s, %(email)s, '0', '0')"

other_query = "INSERT INTO website.users(username, password, \"name\", email) VALUES (%(username)s, %(password)s, %(name)s, %(email)s)"

tuples = []

for i in range(0, num_users):
    str_i = str(i)
    params = {
        "username": username + str_i,
        "password": testpassword + str_i,
        "email": username + str_i + email_templ,
        "name": name
    }
    tuples.append(params)

cursor.executemany(query, tuples)
cursor2.executemany(other_query, tuples)

conn.commit()

conn.close()

conn2.commit()
conn2.close()
