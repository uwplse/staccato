module locks

sig Lock {
	
}

one sig Lock1 extends Lock {}
one sig Lock2 extends Lock {}
one sig Lock3 extends Lock {}

sig Thread {
	
}

abstract sig LockState {
	held_locks: set Lock,
	acquiring_lock: one Lock
}
sig L1 extends LockState {
}
sig L2 extends LockState {
}
sig L3 extends LockState {
}
sig L4 extends LockState {
}
fact {
	all l : L1 | l.held_locks = Lock1 && l.acquiring_lock = Lock2
}
fact {
	all l : L2 | l.held_locks = Lock1 + Lock2 && l.acquiring_lock = Lock3
}
fact {
	all l : L3 | l.held_locks = Lock2 + Lock3 + Lock1 && l.acquiring_lock = none
}
fact {
	all l : L4 | l.held_locks = none && l.acquiring_lock = Lock1
}

fact {
	(all l : Lock | lone l.~held_locks)
}

pred deadlock[l_state : Thread -> LockState] {
	(all t : Thread | t.l_state.acquiring_lock in ((Thread - t).l_state.held_locks))
}

pred one_state[l_state : Thread -> LockState] {
	Thread = LockState.~l_state && Thread.l_state = LockState
}

check {
	no l : Thread -> LockState |
		(some Thread &&
		one_state[l] &&
		deadlock[l])
} for 4 Thread, 4 LockState, 3 Lock expect 0
