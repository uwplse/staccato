Require Import String.
Require Import List.

Inductive Object :=
| Obj : nat -> Object.

Inductive Action :=
| Lock : nat -> Action
| SuccessObj : nat -> Action
| Mutate : nat -> Action
| FailObj : nat -> Action.

Definition ObjectJudgment := nat -> bool.

Inductive ValidTrace : ObjectJudgment -> list Action -> Prop :=
| NilValid :
    forall o,
      ValidTrace o nil
| LockValid :
    forall o t' n,
      ValidTrace o t' ->
      ValidTrace o (cons (Lock n) t')
| MutateValid :
    forall n o t',
      ValidTrace o t' ->
      ~ In (Lock n) t' ->
      ValidTrace o (cons (Mutate n) t')
| SuccessValid :
    forall n o t',
      ValidTrace o t' ->
      In (Lock n) t' ->
      ~ In (Mutate n) t' ->
      o n = true ->
      ValidTrace o (cons (SuccessObj n) t')
| FailValid :
    forall n o t',
      ValidTrace o t' ->
      In (Lock n) t' ->
      (In (Mutate n) t' \/ o n = false) ->
      ValidTrace o (cons (FailObj n) t')
.

Inductive LockAction :=
| Lock' : nat -> LockAction
| Mutate' : nat -> LockAction.

Inductive ValidLockTrace : list LockAction -> Prop :=
| LockTValid : 
    forall t' n,
      ValidLockTrace t' ->
      ValidLockTrace (cons (Lock' n) t')
| MutateTValid :
    forall t' n,
      ValidLockTrace t' ->
      ~ In (Lock' n) t' ->
      ValidLockTrace (cons (Mutate' n) t')
| TNil :
    ValidLockTrace nil.

Inductive CheckAction := 
| CSucc : nat -> CheckAction
| CFail : nat -> CheckAction.


Inductive ValidCheckTrace : ObjectJudgment -> list LockAction -> list CheckAction -> Prop :=
| CNilValid :
    forall l o,
      ValidLockTrace l ->
      ValidCheckTrace o l nil
| CSuccValid :
    forall o l t n,
      ValidLockTrace l ->
      In (Lock' n) l ->
      ~ (In (Mutate' n) l) ->
      o n = true ->
      ValidCheckTrace o l t ->
      ValidCheckTrace o l (cons (CSucc n) t)
| CFailValid : 
    forall o l t n,
      ValidLockTrace l ->
      In (Lock' n) l ->
      (In (Mutate' n) l) \/ o n = false ->
      ValidCheckTrace o l t ->
      ValidCheckTrace o l (cons (CFail n) t).

Inductive Trace_Equiv : list LockAction -> list CheckAction -> list Action -> Prop :=
| TENil : Trace_Equiv nil nil nil
| TESuccess :
    forall l c t n,
      Trace_Equiv l c t ->
      Trace_Equiv l (cons (CSucc n) c) (cons (SuccessObj n) t)
| TEFail :
    forall l c t n,
      Trace_Equiv l c t ->
      Trace_Equiv l (cons (CFail n) c) (cons (FailObj n) t)
| TELock :
    forall l c t n,
      Trace_Equiv l c t ->
      Trace_Equiv (cons (Lock' n) l) c (cons (Lock n) t)
| TEMutate :
    forall l c t n,
      Trace_Equiv l c t ->
      Trace_Equiv (cons (Mutate' n) l) c (cons (Mutate n) t).

Lemma In_Lock'_In_Lock : forall n l t x,
                           In (Lock' n) l -> 
                           Trace_Equiv l t x ->
                           In (Lock n) x.
Proof.
  induction 2; intros.
  inversion H.
  apply in_cons.
  apply IHTrace_Equiv.
  assumption.
  apply in_cons.
  apply IHTrace_Equiv.
  assumption.
  firstorder.
  inversion H; subst.
  firstorder.
  apply in_inv in H.
  inversion H; [ inversion H1 | ].
  apply in_cons.
  apply IHTrace_Equiv.
  assumption.
Qed.

Lemma In_Mutate'_In_Mutate : forall n l t x,
                               In (Mutate' n) l -> 
                               Trace_Equiv l t x ->
                               In (Mutate n) x.
Proof.
  induction 2; intros.
  inversion H.
  apply in_cons.
  apply IHTrace_Equiv.
  assumption.
  apply in_cons.
  apply IHTrace_Equiv.
  assumption.
  apply in_cons.
  assert (Hin: In (Mutate' n) l).
  firstorder.
  inversion H.
  firstorder.

  apply in_inv in H.
  inversion H.
  inversion H1.
  subst.
  firstorder.
  apply IHTrace_Equiv in H1.
  apply in_cons.
  assumption.
Qed.

Lemma Not_Mutate'_Not_Mutate : forall n l t x,
                                 ~ (In (Mutate' n) l) ->
                                 Trace_Equiv l t x ->
                                 ~ (In (Mutate n) x).
Proof.
  induction 2; intros; unfold not; intros.
  inversion H0.
  apply IHTrace_Equiv in H.
  apply in_inv in H1.
  inversion H1; [ inversion H2 | ].
  firstorder.
  apply IHTrace_Equiv in H.
  apply in_inv in H1.
  inversion H1; [ inversion H2 | ].
  firstorder.
  assert (Hnot : ~ In (Mutate' n) l).
  firstorder.
  apply IHTrace_Equiv in Hnot.
  apply in_inv in H1; inversion H1; [ inversion H2 | ].
  firstorder.
  assert (Hneq: ~ In (Mutate' n) l).
  firstorder.
  apply IHTrace_Equiv in Hneq.
  apply in_inv in H1.
  inversion H1.
  inversion H2.
  firstorder.
  firstorder.
Qed.  
Lemma Not_Lock'_Not_Lock : forall n l t x,
                             ~ In (Lock' n) l ->
                             Trace_Equiv l t x ->
                             ~ In (Lock n) x.
Proof.
  induction 2; intros; unfold not; intros.
  assumption.
  firstorder.
  inversion H1.
  firstorder.
  inversion H1.
  apply in_inv in H1.
  inversion H1.
  inversion H2; subst.
  firstorder.
  assert (Hnot: ~ In (Lock' n) l).
  firstorder.
  apply IHTrace_Equiv in Hnot.
  firstorder.
  assert (Hnot: ~ In (Lock' n) l).
  firstorder.
  apply IHTrace_Equiv in Hnot.
  assert (Hin: In (Lock n) t).
  apply in_inv in H1.
  inversion H1.
  inversion H2.
  firstorder.
  firstorder.
Qed.

Lemma check_has_action : forall o l c,
                           ValidCheckTrace o l c ->
                           exists t,
                             ValidTrace o t /\ Trace_Equiv l c t.
Proof.
  induction 1. 
  Lemma ValidLock_ValidTrace : forall o l,
                                 ValidLockTrace l ->
                                 exists t, ValidTrace o t /\ Trace_Equiv l nil t.
  Proof.
    induction 1; firstorder.
    exists (Lock n :: x).
    split.
    constructor.
    assumption.
    constructor.
    assumption.
    exists (Mutate n :: x).
    split.
    constructor.
    assumption.
    eapply Not_Lock'_Not_Lock.
    eassumption.
    eassumption.
    constructor; assumption.
    exists nil.
    split; [ constructor | constructor ].
  Qed.
  apply ValidLock_ValidTrace.
  assumption.
  firstorder.
  exists (SuccessObj n :: x).
  split.
  constructor.
  assumption.
  eapply In_Lock'_In_Lock.
  eassumption.
  eassumption.

  eapply Not_Mutate'_Not_Mutate.
  eassumption.
  eassumption.
  assumption.
  constructor.
  assumption.
  
  firstorder.
  exists ((FailObj n)::x).
  split.
  constructor.
  assumption.
  eapply In_Lock'_In_Lock.
  eassumption.
  eassumption.
  left.
  eapply In_Mutate'_In_Mutate.
  eassumption.
  eassumption.
  constructor; assumption.
  exists (FailObj n::x).
  split.
  constructor.
  assumption.
  eapply In_Lock'_In_Lock; [ eassumption| eassumption ].
  right.
  assumption.
  constructor; assumption.
Qed.

Lemma not_in_list_cons : forall (A: Type) (x c: A) (l: list A),
                           x <> c ->
                           ~ In x l ->
                           ~ In x (c :: l).
Proof.
  intros.
  unfold not in *; intros.
  apply in_inv in H1.
  inversion H1.
  firstorder.
  firstorder.
Qed.

Lemma not_in_l : forall (A: Type) (c x: A) (l: list A),
                   ~ In x (c::l) ->
                   ~ In x l.
Proof.
  intros.
  firstorder.
Qed.

Lemma add_mutate_ok : forall o l c n,
                        ~ (In (Lock' n) l) ->
                        ValidCheckTrace o l c ->
                        ValidCheckTrace o (Mutate' n :: l) c.
Proof.
  induction 2; intros.
  constructor.
  constructor; assumption.
  constructor.
  constructor; assumption.
  firstorder.
  assert (Hneq: n <> n0).
  destruct (Peano_dec.eq_nat_dec n n0).
  subst.
  firstorder.
  assumption.
  apply not_in_list_cons.
  congruence.
  assumption.
  assumption.
  apply IHValidCheckTrace.
  assumption.
  constructor.

  constructor; assumption.
  firstorder.
  inversion H2.
  left.
  firstorder.
  right; firstorder.
  apply IHValidCheckTrace; assumption.
Qed.
  

Lemma add_lock_ok : forall o l c n,
                        ValidLockTrace l ->
                        ValidCheckTrace o l c ->
                        ValidCheckTrace o (Lock' n :: l) c.
Proof.
  induction 2; intros.
  constructor.
  constructor.
  assumption.
  constructor.
  constructor.
  assumption.
  firstorder.
  firstorder.
  apply not_in_list_cons.
  congruence.
  assumption.
  assumption.
  apply IHValidCheckTrace.
  assumption.
  constructor.
  constructor; assumption.
  firstorder.
  inversion H2.
  left.
  apply in_cons; assumption.
  right; assumption.
  apply IHValidCheckTrace; assumption.
Qed.

Lemma Not_Lock_Not_Lock' : forall n l t c,
                             ~ In (Lock n) t ->
                             Trace_Equiv l c t ->
                             ~ In (Lock' n) l.
Proof.
  induction 2; intros.
  assumption.
  apply IHTrace_Equiv.
  firstorder.
  firstorder.
  unfold not; intros.
  apply in_inv in H1.
  inversion H1.
  inversion H2.
  subst.
  firstorder.
  apply not_in_l in H.
  apply IHTrace_Equiv in H.
  firstorder.
  apply not_in_l in H.
  apply IHTrace_Equiv in H.
  apply not_in_list_cons.
  congruence.
  assumption.
Qed.

Lemma has_valid_lock : forall o l c,
                         ValidCheckTrace o l c ->
                         ValidLockTrace l.
Proof.
  intros.
  inversion H; assumption.
Qed.

Lemma In_Lock_In_Lock' : forall n l t c,
                           In (Lock n) t ->
                           Trace_Equiv l c t ->
                           In (Lock' n) l.
Proof.
  induction 2; intros.
  assumption.
  firstorder.
  inversion H.
  firstorder.
  inversion H.
  apply in_inv in H.
  inversion H.
  inversion H1.
  subst.
  firstorder.
  firstorder.
  firstorder.
  inversion H.
Qed.

Lemma Not_Mutate_Not_Mutate' : forall n l t c,
                                 ~ In (Mutate n) t ->
                                 Trace_Equiv l c t ->
                                 ~ In (Mutate' n) l.
Proof.
  induction 2; intros.
  assumption.
  firstorder.
  firstorder.
  apply not_in_list_cons.
  congruence.
  apply IHTrace_Equiv.
  apply not_in_l in H.
  assumption.
  unfold not; intros.
  apply in_inv in H1.
  inversion H1.
  inversion H2; subst.
  firstorder.
  apply not_in_l in H.
  firstorder.
Qed.

Lemma In_Mutate_In_Mutate': forall n l t c,
                              In (Mutate n) t ->
                              Trace_Equiv l c t ->
                              In (Mutate' n) l.
Proof.
  induction 2; intros.
  assumption.
  firstorder.
  inversion H.
  firstorder.
  inversion H.
  firstorder.
  inversion H.
  apply in_inv in H.
  inversion H.
  inversion H1; subst.
  firstorder.
  firstorder.
Qed.


Lemma action_has_check : forall o t,
                           ValidTrace o t ->
                           exists c l,
                             ValidCheckTrace o l c /\ Trace_Equiv l c t.
Proof.
  induction 1; intros; firstorder.
  exists nil. exists nil.
  split; constructor. constructor.
  exists x.
  exists (Lock' n::x0).
  split.
  assert (Hvalid: ValidLockTrace (Lock' n :: x0)).
  constructor.
  apply has_valid_lock in H0; assumption.
  inversion H0; subst.
  constructor.
  assumption.

  constructor.
  assumption.
  firstorder.
  apply not_in_list_cons; [ congruence | assumption ].
  assumption.
  apply add_lock_ok.  
  assumption.
  assumption.

  constructor.
  assumption.
  firstorder.
  inversion H4; [ left; firstorder | right; firstorder ].
  apply add_lock_ok; assumption.
  constructor; assumption.
  exists x.
  exists (Mutate' n :: x0).
  split.
  assert (Hvalid: ValidLockTrace (Mutate' n :: x0)).
  constructor.

  apply has_valid_lock in H1.
  assumption.
  eapply Not_Lock_Not_Lock' in H0; eassumption.
  assert (Hnolock: ~ In (Lock' n) x0).
  eapply Not_Lock_Not_Lock' in H0; eassumption.
  
  inversion H1; subst.
  constructor; assumption.
  assert (Hneq: n <> n0).
  destruct (Peano_dec.eq_nat_dec n n0).
  subst.
  apply In_Lock'_In_Lock with (t:=(CSucc n0 :: t)) (x:=t') in H4.
  firstorder.
  assumption.
  assumption.
  constructor.
  assumption.
  firstorder.
  apply not_in_list_cons.
  congruence.
  assumption.
  assumption.
  apply add_mutate_ok.
  eapply Not_Lock_Not_Lock' in H0; eassumption.
  assumption.
  apply add_mutate_ok.
  eapply Not_Lock_Not_Lock' in H0; eassumption.
  assumption.
  constructor; assumption.
  exists (CSucc n::x); exists x0; split.
  constructor.
  apply has_valid_lock in H3.
  assumption.
  eapply In_Lock_In_Lock' in H0; eassumption.
  eapply Not_Mutate_Not_Mutate' in H1; eassumption.
  assumption.
  assumption.
  constructor; assumption.
  exists (CFail n :: x); exists x0; split.
  constructor.
  apply has_valid_lock in H2; assumption.
  eapply In_Lock_In_Lock' in H0; eassumption.
  left.
  
  eapply In_Mutate_In_Mutate' in H1; eassumption.
  assumption.
  constructor; assumption.
  exists (CFail n :: x); exists x0; split.
  constructor.
  apply has_valid_lock in H2; assumption.   
  eapply In_Lock_In_Lock' in H0; eassumption.
  right; assumption.
  assumption.
  constructor; assumption.
Qed.
  