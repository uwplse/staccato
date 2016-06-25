from option_parser import *
import sys

LDAP_CONSTRAINTS = GroupConstraint({
    "ldapManagerDn": ["cn=admin,dc=nodomain", "cn=root,dc=nodomain", "cn=admin,dc=nodomain"],
    "ldapManagerPassword": ["foobar", "fizzbizz", "foobar"],
    "ldapUrl": ["ldap://localhost:389/dc=nodomain", "ldap://localhost:389/ou=Users,dc=nodomain", "ldap://localhost:389/ou=Users,dc=nodomain"],
    "ldapSearchFilter": ["(uid={0})", "(&(objectClass=account)(uid={0}))", "(&(objectClass=account)(uid={0}))"]
})

CONSTRAINTS = {
    "advancedSettings.view": {
        "ldapManagerDn": LDAP_CONSTRAINTS.get_group("ldapManagerDn"),
        "ldapManagerPassword": LDAP_CONSTRAINTS.get_group("ldapManagerPassword"),
        "ldapUrl": LDAP_CONSTRAINTS.get_group("ldapUrl"),
        "ldapSearchFilter": LDAP_CONSTRAINTS.get_group("ldapSearchFilter"),
        "ldapAutoShadowing": None,
        "ldapEnabled": ConstValue("true"),
        "uploadLimit": IntGroup(0, 100, 1024),
        "downloadLimit": IntGroup(0, 100, 1024),
        "streamPort": IntGroup(10111, 10112),
        "coverArtLimit": IntGroup(0, 50, 100)
    },
    "generalSettings.view": {
        "index": StringGroup("A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ)", "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z"),
        "ignoredArticles": StringGroup("The El La Los Las Le Les", "The El La Los Las Le A"),
        "shortcuts": StringGroup("New Incoming Podcast", "New Incoming Podcast Whatever"),
        "playlistFolder": StringGroup("/var/playlists", "/var/playlists2"),
        "loginMessage": RandomStrings(3),
        "videoFileTypes": StringGroup("flv avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts", "flv avi mpg mpeg mp4 m4v mkv mov wmv"),
        "musicFileTypes": StringGroup("mp3 ogg oga aac m4a flac wav wma aif aiff ape mpc shn", "mp3 ogg oga aac m4a flac wav wma aif aiff"),
        "coverArtFileTypes": StringGroup("cover.jpg folder.jpg jpg jpeg gif png", "cover.jpg folder.jpg"),
        "welcomeTitle": RandomStrings(3),
        "welcomeMessage": RandomStrings(3),
        "welcomeSubtitle": RandomStrings(3)
    },
    "musicFolderSettings.view": {
        "newMusicFolder.path": None,
        "newMusicFolder.name": None,
        "newMusicFolder.enabled": None,
        "_newMusicFolder.enabled": None
    },
    "podcastSettings.view": {
        "folder": StringGroup("/var/music/Podcast", "/var/music/Podcasts")
    },
    "networkSettings.view": {
        "portForwardingEnabled": None,
        "urlRedirectFrom": RandomStrings(3)
    }
    # "transcodingSettings.view": {
    #     "hlsCommand": None,
        
    # }
}

forms = []

for i in sys.argv[1:]:
    v = None
    with open(i) as f:
        v = f.read()
    f_extract = FormExtractor(False)
    f_extract.feed(v)
    forms.append(f_extract.extract(CONSTRAINTS))

dump_config(forms, False)
