module whatever

sig ReadWriteLock {}
sig Prop {
	prop_lock: one ReadWriteLock
}
sig Obj {
	props: some Prop
}
one sig LockLock in ReadWriteLock { }
one sig MapLock in ReadWriteLock { }
one sig TXLock in ReadWriteLock { }
-- locklock and maplock cannot be used as property or object locks
fun UnaryLocks[] : set ReadWriteLock {
	LockLock + MapLock + TXLock
}
fact {
	#UnaryLocks[] = 3
}
fact {
	no (Prop.prop_lock & UnaryLocks[])
}
fact {
	(all p : Prop | p.prop_lock.~prop_lock = p)
}
abstract sig Thread { }

abstract sig ThreadState {
	acquiring_rlocks : set ReadWriteLock,
	acquiring_wlocks : set ReadWriteLock,
	held_rlocks : set ReadWriteLock,
	held_wlocks : set ReadWriteLock
}

fact {
	all rwl : ReadWriteLock | lone rwl.~held_wlocks
}
fact {
	no (ThreadState.held_rlocks & ThreadState.held_wlocks)
}

abstract sig WriteState extends ThreadState {
	to_write : one Prop
}

pred no_rlocks[t : ThreadState] {
	no t.held_rlocks && no t.acquiring_rlocks
}

fact {
	all w : WriteState | no_rlocks[w]
}

sig WLockLock extends WriteState { }
sig WHeldLockLock extends WriteState { }
sig WWLock extends WriteState { }
sig WPutLock extends WriteState { }
sig WAllLock extends WriteState { }
sig WUnwrap extends WriteState { }
fact {
	all w : WLockLock | w.acquiring_wlocks = LockLock && w.held_wlocks = none
}
fact {
	all w : WHeldLockLock | w.acquiring_wlocks = none && w.held_wlocks = LockLock
}
fact {
	all w : WWLock | w.acquiring_wlocks = w.to_write.prop_lock && w.held_wlocks = none
}
fact {
	all w : WPutLock | w.acquiring_wlocks = MapLock && w.held_wlocks = w.to_write.prop_lock
}
fact {
	all w : WAllLock | w.acquiring_wlocks = none && w.held_wlocks = w.to_write.prop_lock + MapLock
}
fact {
	all w : WUnwrap | w.acquiring_wlocks = none && w.held_wlocks = w.to_write.prop_lock
}

sig Writer extends Thread {
	set_prop: one Prop,
	write_state : one WriteState
}

fact {
	all w : Writer | w.set_prop = w.write_state.to_write && w.write_state.~write_state = w
}


abstract sig PropagateState extends ThreadState {
	to_propagate_objs : some Obj
}

fact {
	all p : PropagateState | no_rlocks[p]
}

sig Propagator extends Thread {
	prop_objs: some Obj,
	p_state : one PropagateState
}

sig PHaveTXLock, PGetTXLock extends PropagateState {}
fact {
	all p : PGetTXLock | p.acquiring_wlocks = TXLock && p.held_wlocks = none
}
fact {
	all p : PHaveTXLock | p.held_wlocks = TXLock && p.acquiring_wlocks = none
}
fact {
	all p : Propagator | p.p_state.to_propagate_objs = p.prop_objs && p.p_state.~p_state = p
}


abstract sig CheckState extends ThreadState {
	to_check : some Obj
}

sig Check extends Thread {
	check_objs: some Obj,
	check_state : one CheckState
}

pred no_wlocks[c : CheckState] {
	c.held_wlocks = none && c.acquiring_wlocks = none
}
sig CGetLL, CHaveLL extends CheckState {}
sig CPLock extends CheckState {
	p_need : some Prop,
	p_have : some Prop
}
sig CGetML extends CheckState {}
sig CHaveML  extends CheckState {}
sig CGetTXLock extends CheckState {}

-- pred acquire_some_obj[held_locks: set ReadWriteLock, acquiring_locks : set ReadWriteLock, held_obj, need_obj, all_obj : set Obj] {
fact {
	all c : CGetTXLock | no_rlocks[c] && c.acquiring_wlocks = TXLock && c.held_wlocks = none
}
fact {
	all c : CGetLL | c.held_rlocks = none && c.acquiring_wlocks = LockLock && c.held_wlocks = TXLock && c.acquiring_rlocks = none
}
fact {
	all c : CHaveLL | c.held_rlocks = none && c.acquiring_wlocks = none && c.held_wlocks = LockLock + TXLock && c.acquiring_rlocks = none
}
fact {
	all c : CPLock | c.held_rlocks = c.p_have.prop_lock && c.acquiring_wlocks = none && c.held_wlocks = TXLock && c.acquiring_rlocks = c.p_need.prop_lock &&
				   no (c.p_need & c.p_have) && c.p_need + c.p_have = c.to_check.props
}
fact {
	all c : CGetML | c.held_rlocks = c.to_check.props.prop_lock && c.held_wlocks = TXLock && c.acquiring_wlocks = MapLock && c.acquiring_rlocks = none
}
fact {
	all c : CHaveML | c.held_rlocks = c.to_check.props.prop_lock && c.held_wlocks = MapLock + TXLock && c.acquiring_wlocks = none && c.acquiring_rlocks = none
}

fact {
	all c : Check | c.check_state.to_check = c.check_objs && c.check_state.~check_state = c
}

fact {
	Thread.(write_state + check_state + p_state) = ThreadState
}

pred progress_possible[t : set Thread] {
	(some t : Thread |
		let all_states = (Thread - t).(write_state + check_state + p_state) |
		let my_state = t.(write_state + check_state + p_state) |
		no my_state.acquiring_rlocks & (all_states.held_wlocks) &&
		no my_state.acquiring_wlocks & (all_states.(held_wlocks + held_rlocks))
	)
}

check {
	(some Thread) ==> progress_possible[Thread]
} for 8 Thread , 25 ReadWriteLock , 10 Obj, 12 Prop, 8 ThreadState expect 0
