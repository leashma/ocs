package edu.gemini.sp.vcs2

import edu.gemini.pot.sp.{SPNodeKey, ISPFactory, ISPProgram}
import edu.gemini.pot.sp.version._
import edu.gemini.shared.util.VersionComparison.{Conflicting, Same, Newer}
import edu.gemini.sp.vcs2.ProgramLocation.Remote
import edu.gemini.sp.vcs2.ProgramLocationSet.{LocalOnly, Neither, RemoteOnly}
import edu.gemini.sp.vcs2.VcsFailure.{Cancelled, IdClash, NeedsUpdate}
import edu.gemini.sp.vcs.log.VcsEventSet
import edu.gemini.spModel.core.{Peer, SPProgramID}
import edu.gemini.spModel.rich.pot.sp._
import edu.gemini.util.security.auth.keychain.KeyChain

import java.security.{Permission, Principal}
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._
import scalaz.concurrent.Task


/** Vcs provides the public API for vcs commands such as push, pull and sync. */
class Vcs(user: VcsAction[Set[Principal]], server: VcsServer, service: Peer => VcsService) {

  import Vcs.MergeEval

  def hasPermission(p: Permission): VcsAction[Boolean] =
    user >>= { u => server.hasPermission(p, u) }

  /** Provides access to the `VersionMap` associated with the given program in
    * the remote peer. */
  def version(id: SPProgramID, peer: Peer): VcsAction[VersionMap] =
    Client(peer).version(id)

  private def checkCancel(cancelled: AtomicBoolean): VcsAction[Unit] =
    if (cancelled.get()) VcsAction.fail(Cancelled) else VcsAction.unit

  /** Checks-out the indicated program from the remote peer, copying it into
    * the local database. */
  def checkout(id: SPProgramID, peer: Peer, cancelled: AtomicBoolean): VcsAction[ISPProgram] =
    for {
      p <- Client(peer).checkout(id)
      _ <- checkCancel(cancelled)
      u <- user
      _ <- server.add(p, u)
    } yield p

  /** Adds the given program to the remote peer, copying it into the remote
    * database. */
  def add(id: SPProgramID, peer: Peer): VcsAction[Unit] =
    for {
      p <- server.lookup(id)
      _ <- Client(peer).add(p)
    } yield ()

  // pull0 is shared by `pull` and `sync`, since the first half of a sync is
  // to merge in changes from the remote peer.  The local merge is only
  // performed if the remote peer has something new to offer.
  private def pull0(id: SPProgramID, client: Client, cancelled: AtomicBoolean): VcsAction[MergeEval] = {
    def validateProgKey(local: ISPProgram, remote: MergePlan): VcsAction[Unit] = {
      val lKey = local.getProgramKey
      val rKey = remote.update.rootLabel.key
      if (lKey === rKey) VcsAction.unit else VcsAction.fail(IdClash(id, lKey, rKey))
    }

    def evaluate(p: ISPProgram): VcsAction[MergeEval] =
      for {
        diffs  <- client.fetchDiffs(id, DiffState(p))
        _      <- checkCancel(cancelled)
        _      <- validateProgKey(p, diffs.plan)
        mc      = MergeContext(p, diffs)
        prelim <- PreliminaryMerge.merge(mc).liftVcs
        plan   <- MergeCorrection(mc)(prelim, hasPermission)
      } yield MergeEval(plan, p, mc.remote.vm)

    // Only do the merge if the merge plan has something new to offer.
    def filter(eval: MergeEval): Boolean = eval.localUpdate

    def update(f: ISPFactory, p: ISPProgram, eval: MergeEval): VcsAction[Unit] =
      checkCancel(cancelled) >> eval.plan.merge(f, p)

    user >>= { u => server.write[MergeEval](id, u, evaluate, filter, update) }
  }

  /** Provides an action that will pull changes from the indicated remote peer
    * and merge them with the local program if necessary.  The action returns
    * `true` if it results in an updated local program; `false` otherwise.
    */
  def pull(id: SPProgramID, peer: Peer, cancelled: AtomicBoolean): VcsAction[PullResult] =
    pull0(id, Client(peer), cancelled).map(_.localUpdate.fold(LocalOnly, Neither))

  /** Provides an action that pushes local changes to the remote peer, merging
    * them with the remote version of the program if necessary.  When performed,
    * the action returns `true` if it results in an update remote program.
    *
    * Note that the push will only be possible if the local version is strictly
    * newer than the remote version. Otherwise it fails with a `NeedsUpdate`
    * `VcsFailure`. */
  def push(id: SPProgramID, peer: Peer, cancelled: AtomicBoolean): VcsAction[PushResult] = {
    def validateProgKey(lKey: SPNodeKey, remote: DiffState): VcsAction[Unit] = {
      val rKey = remote.progKey
      if (lKey === rKey) VcsAction.unit else VcsAction.fail(IdClash(id, lKey, rKey))
    }

    val client = Client(peer)
    for {
      diffState <- client.diffState(id)
      _         <- checkCancel(cancelled)
      u         <- user
      keyDiff   <- server.read(id, u) { p => (p.getProgramKey, ProgramDiff.compare(p, diffState)) }
      _         <- validateProgKey(keyDiff._1, diffState)
      _         <- checkCancel(cancelled)
      res       <- keyDiff._2.plan.compare(diffState.vm) match {
        case Newer => client.storeDiffs(id, keyDiff._2.plan).map(_.fold(RemoteOnly, Neither))
        case Same  => VcsAction(Neither)
        case _     => VcsAction.fail(NeedsUpdate)
      }
    } yield res
  }

  /**
   * Provides an action that synchronizes changes in the local program with the
   * remote peer, merging updates from the remote program with local changes
   * and then sending the resulting merged program to the peer.  When performed,
   * the action returns a `ProgramLocationSet` which describes whether either
   * or both of the local and remote versions of the program were updated. */
  def sync(id: SPProgramID, peer: Peer, cancelled: AtomicBoolean): VcsAction[ProgramLocationSet] = {
    val client = Client(peer)
    for {
      eval <- pull0(id, client, cancelled)
      s0    = eval.localUpdate.fold(LocalOnly, Neither)
      res  <- eval match {
        case MergeEval(_,     _, false) =>
          VcsAction(s0)

        case MergeEval(diffs, _, true)  =>
          client.storeDiffs(id, diffs).map { updated =>
            if (updated) s0 + Remote else s0
          }
      }
    } yield res
  }

  /** Returns a `VcsAction` that will sync the program with the remote peer,
    * retrying if it fails because the program was updated remotely while
    * performing the merge locally.  Retry up to `retryCount` times if
    * necessary. */
  def retrySync(id: SPProgramID, peer: Peer, cancelled: AtomicBoolean, retryCount: Int): VcsAction[ProgramLocationSet] = {
    def retryIfNeedsUpdate(f: VcsFailure): EitherT[Task, ProgramLocationSet, VcsFailure] = f match {
      case NeedsUpdate  => if (retryCount <= 0) EitherT.right(Task.delay(NeedsUpdate))
                           else retrySync(id, peer, cancelled, retryCount - 1).swap
      case otherFailure => EitherT.right(Task.delay(otherFailure))
    }

    (sync(id, peer, cancelled).swap >>= retryIfNeedsUpdate).swap
  }

  /** Provides access to (a chunk of) the VCS log. */
  def log(id: SPProgramID, peer: Peer, offset: Int, length: Int): VcsAction[(List[VcsEventSet], Boolean)] =
    Client(peer).log(id, offset, length)

  case class Client(peer: Peer) {
    val s = service(peer)

    def version(id: SPProgramID): VcsAction[VersionMap]  = s.version(id).liftVcs
    def add(p: ISPProgram): VcsAction[Unit]              = s.add(p).liftVcs
    def checkout(id: SPProgramID): VcsAction[ISPProgram] = s.checkout(id).liftVcs
    def diffState(id: SPProgramID): VcsAction[DiffState] = s.diffState(id).liftVcs

    def fetchDiffs(id: SPProgramID, vs: DiffState): VcsAction[ProgramDiff] =
      s.fetchDiffs(id, vs).map(_.decode).liftVcs

    def storeDiffs(id: SPProgramID, mp: MergePlan): VcsAction[Boolean] =
      s.storeDiffs(id, mp.encode).liftVcs

    def log(id: SPProgramID, offset: Int, length: Int): VcsAction[(List[VcsEventSet], Boolean)] =
      s.log(id, offset, length).liftVcs
  }
}

object Vcs {

  def apply(kc: KeyChain, server: VcsServer): Vcs =
    new Vcs(VcsAction(kc.subject.getPrincipals.asScala.toSet), server, VcsService.client(_, kc))

  /** Evaluation of the merge state, which includes whether local and/or remote
    * updates are needed.  We can skip merging locally or remotely if nothing
    * would be changed anyway. */
  private case class MergeEval(plan: MergePlan, localUpdate: Boolean, remoteUpdate: Boolean)

  private object MergeEval {
    def apply(plan: MergePlan, p: ISPProgram, remoteVm: VersionMap): MergeEval = {
      // ObsPermissionCorrection will reset inappropriately edited observations
      // which can cause Conflicting comparisons.
      val local = plan.compare(p.getVersions) match {
        case Newer | Conflicting => true
        case _                   => false
      }
      val remote = plan.compare(remoteVm) === Newer

      MergeEval(plan, local, remote)
    }
  }
}
