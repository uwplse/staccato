module readwrite

sig ReadWriteLock {}

sig Thread {
	read_holds : some ReadWriteLock,
	write_holds : some ReadWriteLock
}

fact {
	all rwl : ReadWriteLock | lone rwl.~write_holds
}
fact {
	no (Thread.write_holds & Thread.read_holds)
}

pred write_lock_held[l : ReadWriteLock] {
	l in (Thread.write_holds)
}

pred read_lock_held[l : ReadWriteLock] {
	l in (Thread.read_holds)
}

run {
	some l : ReadWriteLock | #l.~read_holds > 1
} for 3 Thread, 4 ReadWriteLock expect 1
