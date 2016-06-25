from option_parser import *
import sys

class JForumExtractor(FormExtractor):
    def handle_starttag(self, tag_name, attrs):
        attrs = dict(attrs)
        if tag_name == "input" and attrs["type"]  == "text" and attrs.get("id", None) == "address":
            return
        FormExtractor.handle_starttag(self,tag_name, attrs)
        if tag_name == "input" and self.handling_form and attrs["type"] == "hidden" and attrs.get("name", None) == "module":
            self.mod = attrs["value"]
    def constraint_key(self):
        return self.mod

import socket

hostname = socket.gethostname()

CONSTRAINTS = {
    "adminConfig": {
        "p_forum.link": StringGroup("http://localhost:8080/jforum-2.1.8/", "http://%s:8080/jforum-2.1.8/" % hostname),
        "p_homepage.link": StringGroup("http://localhost:8080/jforum-2.1.8/", "http://%s:8080/jforum-2.1.8/" % hostname),
        "p_forum.name": RandomStrings(4),
        "p_forum.page.title": RandomStrings(4),
        "p_forum.page.metatag.keywords": RandomStrings(3),
        "p_forum.page.metatag.description": RandomStrings(3),
        "p_posts.cache.size": IntRange(100, 150),
        "p_encoding": StringGroup("UTF-8", "US-ASCII"),
        "p_topicsPerPage": IntRange(15, 30),
        "p_postsPerPage": IntRange(15, 30),
        "p_usersPerPage": IntRange(30, 60),
        "p_posts.new.delay": IntGroup(0, 100, 1000),
        "p_html.tags.welcome": StringGroup("u, a, img, i, u, li, ul, font, br, p, b, hr", "u, a, img, i, u, li, ul, font, br"),
        "p_hot.topic.begin": IntRange(15, 30),
        "p_topic.recent": IntRange(20, 30),
        "p_avatarMaxKbSize": IntRange(5, 10),
        "p_avatar.maxWidth": IntRange(150, 200),
        "p_avatar.maxHeight": IntRange(150, 200),
#StringGroup("mail/de_DE/mailNewTopic.txt","mail/mailNewTopic.txt")
        "p_mail.charset": StringGroup("UTF-8","US-ASCII"),
        "p_mail.sender": StringGroup("admin@jforum.net", "server@jforum.net", "bar@localhost"),
        "p_mail.smtp.host": StringGroup("localhost", hostname),
        "p_mail.smtp.port": IntGroup(6050, 6051),
        "p_mail.smtp.username": StringGroup("username", "fizzbizz"),
        "p_mail.smtp.password": StringGroup("password", "password1"),
        "p_mail.newAnswer.messageFile": StringGroup("mail/de_DE/mailNewReply.txt","mail/mailNewReply.txt"),
        "p_mail.newAnswer.subject": StringGroup("NEW ANSWER!!11","NEW ANSWER"),
        "p_mail.newPm.messageFile": StringGroup("mail/de_DE/newPrivateMessage.txt","mail/newPrivateMessage.txt"),
        "p_mail.newPm.subject": StringGroup("NEW PM!1!","NEW PM"),
        "p_mail.activationKey.messageFile": StringGroup("mail/de_DE/activateAccount.txt", "mail/activateAccount.txt"),
        "p_mail.activationKey.subject": RandomStrings(2),
        "p_mail.lostPassword.messageFile": StringGroup("mail/de_DE/lostPassword.txt", "mail/lostPassword.txt"),
        "p_mail.lostPassword.subject": RandomStrings(2)
    },
    "adminAttachments": {
        "p_attachments.icon": StringGroup("images/icon_clip.gif", "images/icon_wink.gif"),
        "p_attachments.images.thumb.maxsize.w": IntRange(150, 200),
        "p_attachments.images.thumb.maxsize.h": IntRange(150, 200),
        "p_attachments.max.post": IntRange(1,3)
    }
}

forms = []

for i in sys.argv[1:]:
    v = None
    with open(i) as f:
        v = f.read()
    f_extract = JForumExtractor()
    f_extract.feed(v)
    forms.append(f_extract.extract(CONSTRAINTS))

dump_config(forms)
