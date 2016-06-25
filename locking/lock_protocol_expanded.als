open util/ordering[PropLock]
open util/ordering[ObjLock]

sig ReadWriteLock {}
sig PropLock extends ReadWriteLock {}
sig ObjLock extends ReadWriteLock {}


private sig Prop {
	prop_lock: one PropLock
}
private sig Obj {
	obj_lock: one ObjLock,
	props: some Prop
}
one sig LockLock extends ReadWriteLock { }
one sig MapLock extends ReadWriteLock { }
one sig TXLock extends ReadWriteLock { }
one sig UpdateLock extends ReadWriteLock { }

fact {
	(all p : Prop | p.prop_lock.~prop_lock = p)
}
abstract sig Thread { }

abstract sig ThreadState {
   acquiring_rlocks : set ReadWriteLock,
   acquiring_wlocks : set ReadWriteLock,
   held_rlocks : set ReadWriteLock,
   held_wlocks : set ReadWriteLock,

   recursive_rlocks : set ReadWriteLock,
   recursive_wlocks : set ReadWriteLock,
   t_held_rlocks : set ReadWriteLock,
   t_held_wlocks : set ReadWriteLock
} {
   t_held_rlocks = recursive_rlocks + held_rlocks &&
   t_held_wlocks = recursive_wlocks + held_wlocks
}

fact {
	all rwl : ReadWriteLock | lone rwl.~t_held_wlocks
}
/*
fact {
	no (ThreadState.t_held_rlocks & ThreadState.t_held_wlocks)
}
*/

fact {
    all rwl : ReadWriteLock | 
      (one rwl.~t_held_wlocks && rwl.~t_held_rlocks in rwl.~t_held_wlocks) ||
      (no rwl.~t_held_wlocks && some rwl.~t_held_rlocks) ||
      (no rwl.~t_held_wlocks && no rwl.~t_held_rlocks)
}

pred simple_recursion[t: ThreadState] {
   (t.recursive_rlocks = none || 
   (t.recursive_rlocks in PropLock && no (t.recursive_rlocks - PropLock))
	) &&
   	(t.recursive_wlocks = none || t.recursive_wlocks = UpdateLock)
}

abstract sig FieldUpdateState extends ThreadState {
    owner : one Obj,
    field: one Obj,
    new_field : one Obj,
} {
    owner != field && simple_recursion[this]

}
sig FieldUpdateThread extends Thread {
    u_state : one FieldUpdateState
}
fact {
   all u : FieldUpdateThread | u.u_state.~u_state = u
}

-- Field Updates (hairy!)
sig FObjLock extends FieldUpdateState { } {
    acquiring_rlocks = field.obj_lock && held_rlocks = none && held_wlocks = none && acquiring_wlocks = none
}
sig FObjLockHeld extends FieldUpdateState {}{ held_rlocks = field.obj_lock
    && held_wlocks = none && acquiring_wlocks = none && acquiring_rlocks = none }
sig FLLock extends FieldUpdateState { } { held_rlocks = none && held_wlocks = none
    && acquiring_wlocks = LockLock && acquiring_rlocks = none }
sig FLLockHeld extends FieldUpdateState { } { held_rlocks = none && held_wlocks = LockLock
   && acquiring_rlocks = none && acquiring_wlocks = none }

sig FPropLock extends FieldUpdateState { acquired: set PropLock, acquiring: one PropLock }
{ acquired in field.props.prop_lock && held_rlocks = acquired && held_wlocks = none && acquiring_wlocks = none &&
    acquiring in field.props.prop_lock && acquired in prevs[acquiring] && acquiring_rlocks = acquiring }
sig FMapLock extends FieldUpdateState {} { held_rlocks = field.props.prop_lock && held_wlocks = none
   && acquiring_rlocks = MapLock && acquiring_wlocks = none }
sig FMapLockHeld extends FieldUpdateState {} { held_rlocks = field.props.prop_lock + MapLock
   && held_wlocks = none && acquiring_wlocks = none && acquiring_rlocks = none }

sig FUpdateLock extends FieldUpdateState {} {
   held_rlocks = field.props.prop_lock && acquiring_wlocks = UpdateLock &&
   acquiring_rlocks = none && held_wlocks = none }
sig FUpdateLockHeld extends FieldUpdateState {} {
   held_wlocks = UpdateLock && held_rlocks = field.props.prop_lock && acquiring_rlocks = none && acquiring_wlocks = none
}

/* argument checking */

abstract sig CheckState extends ThreadState {
    curr_check : set Obj
} {
    no recursive_rlocks & curr_check.props.prop_lock && simple_recursion[this]
}
sig CheckObj extends CheckState {
   curr_obj: one Obj
} {
   curr_obj in curr_check && held_rlocks = none && acquiring_wlocks = curr_check.obj_lock && held_wlocks = none && held_rlocks = none
}
sig CheckLL extends CheckState {} {
  held_rlocks = none && acquiring_wlocks = LockLock && acquiring_rlocks = none && held_wlocks = none
}
sig CheckLLHeld extends CheckState { } {
  held_rlocks = none && acquiring_wlocks = none && acquiring_rlocks = none && held_wlocks = LockLock
}
sig CheckPLocks extends CheckState { acquired: set PropLock, acquiring: one PropLock }
{
    acquired in curr_check.props.prop_lock && acquired in prevs[acquiring] && held_rlocks = acquired && 
    acquiring_rlocks = acquiring &&
    held_wlocks = none && acquiring_wlocks = none
}
sig CheckMapLock extends CheckState { } { held_rlocks = curr_check.props.prop_lock && acquiring_rlocks = MapLock && acquiring_wlocks =  none && held_wlocks = none }
sig CheckMLockHeld extends CheckState { } { held_rlocks = curr_check.props.prop_lock + MapLock && acquiring_rlocks = none && acquiring_wlocks = none && held_wlocks = none }

sig CheckThread extends Thread {
    check_state : one CheckState
}
fact {
   all c : CheckThread | c.check_state.~check_state = c
}


abstract sig PropagateState extends ThreadState {
    source: some Obj,
    target: one Obj,
} {
   not (target in source) && simple_recursion[this]
}
sig PStart extends PropagateState { } {
   acquiring_wlocks = none && acquiring_rlocks = UpdateLock && held_rlocks = none && held_wlocks = none
}
sig PFirst extends PropagateState { 
	private acquired: set ObjLock,
	private to_acquire: one ObjLock
} {
	acquired in source.obj_lock && to_acquire in source.obj_lock && acquired in prev[to_acquire] && (to_acquire + acquired) in prev[target.obj_lock] &&
      held_rlocks = UpdateLock + acquired && held_wlocks = none && acquiring_rlocks = to_acquire && acquiring_wlocks = none
}
sig PTarget extends PropagateState { 
	private acquired: set ObjLock
} {
	acquired in source.obj_lock && acquired = (prev[target.obj_lock] & source.obj_lock) &&
	held_rlocks  = UpdateLock + acquired && held_wlocks = none && acquiring_wlocks = target.obj_lock && acquiring_rlocks = none
}
sig PFinal extends PropagateState {
	private acquired: set ObjLock,
	private to_acquire : one ObjLock
} {
	to_acquire in source.obj_lock && acquired in source.obj_lock && acquired in prev[to_acquire] && target.obj_lock in prev[target.obj_lock] &&
      held_rlocks = UpdateLock + acquired && held_wlocks = target.obj_lock && acquiring_wlocks = none && acquiring_rlocks = to_acquire
}
sig PAll extends PropagateState { }
{
	held_rlocks = UpdateLock + source.obj_lock && held_wlocks = target.obj_lock && acquiring_wlocks = none && acquiring_rlocks = none
}

sig PropagateThread extends Thread {
    p_state : one PropagateState
}
fact {
   all p : PropagateThread | p.p_state.~p_state = p
}

/* transactional writes */
abstract sig TWriteState extends ThreadState {
    to_update : set Prop
} { recursive_rlocks = none && recursive_wlocks = none}
sig TWriteThread extends Thread {
    tw_state : one TWriteState
}
fact {
   all t : TWriteThread | t.tw_state.~tw_state = t
}

/* Extend this with lock lock, and all held */
sig TLockAcquire extends TWriteState { } {
   held_rlocks = none && held_wlocks = none && acquiring_rlocks = none && acquiring_wlocks = TXLock
}
sig TPAcquire extends TWriteState { acquired: set PropLock, to_acq : one PropLock }
{
   acquired + to_acq in to_update.prop_lock && acquired in prev[to_acq] &&
   held_wlocks = acquired + TXLock && acquiring_wlocks = to_acq && held_rlocks = none && acquiring_rlocks = none
}

abstract sig TObjectWrite extends ThreadState {
   to_set : one Prop,
   to_prop : set Obj
} {
   recursive_rlocks = none && recursive_wlocks = none
}

sig TObjectLLAcq extends TObjectWrite {} {
   acquiring_rlocks = none && acquiring_wlocks = LockLock && held_rlocks = none && held_wlocks = none
}

sig TObjectLLHave extends TObjectWrite { } {
   acquiring_rlocks = none && acquiring_wlocks = none && held_rlocks = none && held_wlocks = LockLock
}
sig TPAcq extends TObjectWrite {} {
   acquiring_rlocks = none && acquiring_wlocks = to_set.prop_lock && held_rlocks = none && held_wlocks = none
}

sig TObjectObjAcq extends TObjectWrite { to_acq: one Obj, acquired: set Obj } {
   acquired in to_prop && to_acq in to_prop && acquired.obj_lock in prev[to_acq.obj_lock] &&
   held_rlocks = none && held_wlocks = acquired.obj_lock + to_set.prop_lock
   && acquiring_rlocks = none && acquiring_wlocks = to_acq.obj_lock
}
sig TMapAcq extends TObjectWrite {} {
   acquiring_rlocks = none && acquiring_wlocks = MapLock && held_rlocks = none && held_wlocks = to_prop.obj_lock + to_set.prop_lock
}

sig TAllHeld extends TObjectWrite {} {
   acquiring_rlocks = none && acquiring_wlocks = none && held_rlocks = none && held_wlocks = to_prop.obj_lock + to_set.prop_lock + MapLock
}

sig ObjectWriteThread extends Thread {
   o_state : one TObjectWrite
}

fact {
    all t : ObjectWriteThread | t.o_state.~o_state = t
}

/*
abstract sig WriteState extends ThreadState {
   to_write : one Prop
}
sig WriteThread extends Thread {
   write_state : one WriteState
}
fact {
   all w : WriteThread | w.w_state.~w_state = w
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
*/
fact {
	Thread.(u_state + check_state + p_state + tw_state + o_state) = ThreadState
}

pred no_deadlock[all_t : set Thread] {
      let all_rel = u_state + check_state + p_state + tw_state + o_state |
	some t : all_t |
		let all_states = (all_t - t).(all_rel) |
		let my_state = t.(all_rel) |
		no my_state.acquiring_rlocks & (all_states.t_held_wlocks) &&
		no my_state.acquiring_wlocks & (all_states.(t_held_wlocks + t_held_rlocks))
	
}

pred progress_possible[all_t : set Thread] {
   let all_rel = u_state + check_state + p_state + tw_state + o_state |
	no_deadlock[all_t] || (
	   one (all_t.all_rel & TPAcquire) && no_deadlock[all_t - (TPAcquire.~all_rel)]
      )
}

check {
   some Thread => progress_possible[Thread]
} for 4 Thread, 21 ReadWriteLock, 8 Prop, 8 PropLock, 8 Obj, 8 ThreadState expect 0
