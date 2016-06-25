import sleekxmpp

class Client(sleekxmpp.ClientXMPP):
    def __init__(self):
        sleekxmpp.ClientXMPP.__init__(self, "jtoman@localhost", "foobar")
        self.add_event_handler("session_start", self.do_it)
    def do_it(self, event):
        iq = self.make_iq_get(queryxmlns='edu:uw:tuner:email')
        iq.send()
        self.disconnect()

if __name__ == '__main__':
    f = Client()
    f.connect()
    f.process(block = True)


