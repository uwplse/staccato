import psycopg2

conn = psycopg2.connect("dbname=jforum user=jforum password=jforum host=localhost")

cursor = conn.cursor()
cursor.execute("CREATE TEMPORARY TABLE to_delete_users ON COMMIT DROP AS SELECT user_id FROM jforum_users WHERE username LIKE 'user%'")
cursor.execute("CREATE TEMPORARY TABLE to_delete_topics ON COMMIT DROP AS SELECT topic_id FROM jforum_topics WHERE user_id IN (SELECT user_id FROM to_delete_users) OR user_id = 1")
cursor.execute("CREATE TEMPORARY TABLE to_delete_posts ON COMMIT DROP AS SELECT post_id FROM jforum_posts WHERE user_id IN (SELECT user_id FROM to_delete_users) or user_id = 1")
cursor.execute("CREATE TEMPORARY TABLE to_delete_messages ON COMMIT DROP AS SELECT privmsgs_id FROM jforum_privmsgs pm INNER JOIN to_delete_users tdu ON pm.privmsgs_from_userid = tdu.user_id OR pm.privmsgs_to_userid = tdu.user_id")

# now the fun begins
delete_specs = { \
                 "post_id": ("to_delete_posts", [ "jforum_posts", "jforum_karma", "jforum_attach", "jforum_moderation_log", "jforum_posts_text" ]),
                 "topic_id": ("to_delete_topics", [ "jforum_moderation_log", "jforum_posts", "jforum_vote_desc", "jforum_topics_watch", "jforum_karma", "jforum_topics" ]),
                 "user_id": ("to_delete_users", ["jforum_topics_watch", "jforum_forums_watch", "jforum_user_groups", "jforum_posts", "jforum_topics", "jforum_banlist", "jforum_users", "jforum_bookmarks", "jforum_attach", "jforum_moderation_log"]),
                 "privmsgs_id": ("to_delete_messages", ["jforum_privmsgs_text", "jforum_attach", "jforum_privmsgs"])
}

def do_delete(column_name, table, ref_table):
    cursor.execute("DELETE FROM %(table)s WHERE %(column_name)s IN (SELECT %(column_name)s FROM %(ref_table)s)" % { "table": table,
                                                                                                                    "column_name": column_name,
                                                                                                                    "ref_table": ref_table })
    print "Affected %d rows for table %s" % (cursor.rowcount, table)

for (column,(ref_table,table_list)) in delete_specs.iteritems():
    for table_name in table_list:
        do_delete(column, table_name, ref_table)

# now generate the new users

for i in xrange(0,1000):
    username = "user%03d" % i
    cursor.execute("INSERT INTO jforum_users(username, user_password, user_email) VALUES (%(username)s, '5f4dcc3b5aa765d61d8327deb882cf99', 'example@example.com')", { "username": username })
    cursor.execute("INSERT INTO jforum_user_groups(group_id, user_id) SELECT 1, user_id FROM jforum_users WHERE username = %s", (username,))

# now fix up the number of topics
cursor.execute("UPDATE jforum_forums jf SET forum_topics = (SELECT count(*) FROM jforum_topics t WHERE t.forum_id = jf.forum_id)")

conn.commit()
