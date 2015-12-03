package ghs

import scala.annotation.migration
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

case class InitNode(neighbourProcs: Map[ActorRef, Double], fragmentId: Integer)
case class InitNodeCompleted()
case class InitTest()
case class Test(fragementId: Integer, fragmentLevel: Integer)
case class Reject()
case class Accept()
case class Report(mwoe: Option[(Double, ActorRef)])
case class InitConnect(mwoeNode: ActorRef)
case class Connect(fragmentLevel: Integer, fragmentId: Integer, fragmentCore: ActorRef, fragmentNodes: Set[ActorRef])
case class ChangeFragment(newFragmentId: Option[Integer] = None, newFragmentLevel: Option[Integer] = None, newFragmentCore: Option[ActorRef] = None, newFragmentNodes: Option[Set[ActorRef]] = None)
case class ChangeFragmentCompleted()

class GHS extends Actor {

  val log = Logging(context.system, this)

  var neighbourBasic: Map[ActorRef, Double] = null

  var neighbourBranch: Map[ActorRef, Double] = null

  var neighbourRejected: Map[ActorRef, Double] = null

  var fragmentId: Integer = null

  var fragmentLevel: Integer = null

  var fragmentCore: ActorRef = null

  var fragmentNodes: Set[ActorRef] = null

  var reportAcceptedCounter: Integer = 0

  var reportEmptyCounter: Integer = 0

  var reportMwoeWeight: Option[Double] = None

  var reportMwoeNode: ActorRef = null

  var reportMwoeSender: ActorRef = null

  var changeCoreCounter: Integer = 0

  def getMwoe: Option[(Double, ActorRef)] = {
    if (neighbourBasic.isEmpty) {
      None
    } else {
      var mwoeWeight = Double.MaxValue
      var mwoeNode: ActorRef = null
      for (n <- neighbourBasic) {
        if (n._2 < mwoeWeight) {
          mwoeWeight = n._2
          mwoeNode = n._1
        }
      }
      Some(mwoeWeight, mwoeNode)
    }
  }

  def receive = {

    case InitNode(procs, fragmentID) =>
      this.neighbourBasic = procs
      this.neighbourBranch = Map.empty[ActorRef, Double]
      this.neighbourRejected = Map.empty[ActorRef, Double]
      this.fragmentId = fragmentID
      this.fragmentLevel = 0
      this.fragmentCore = self
      this.fragmentNodes = Set(fragmentCore)
      sender ! InitNodeCompleted()

    case InitTest() =>
      changeCoreCounter = 0;
      // send test to min basic edge
      val mwoe = getMwoe
      mwoe match {
        case None =>
          fragmentCore ! Report(None)
        case Some((mwoeWeight, mwoeNode)) =>
          log.debug("Sending 'Test' to " + mwoeNode.path.name + " from " + self.path.name)
          mwoeNode ! Test(this.fragmentId, this.fragmentLevel)
      }

    case Test(fragmentId, fragmentLevel) =>
      log.debug("Received 'Test' at " + self.path.name + " from " + sender.path.name)
      if (this.fragmentId == fragmentId) {
        sender ! Reject()
      } else {
        if (this.fragmentLevel >= fragmentLevel) {
          log.debug("Sending 'Accept' to " + sender.path.name + " from " + self.path.name)
          sender ! Accept()
        } else {
          // response is delayed
        }
      }

    case Reject() =>
      log.debug("Received 'Reject' at " + self.path.name + " from " + sender.path.name)
      fragmentCore ! Report(None)

    case Accept() =>
      log.debug("Received 'Accept' at " + self.path.name + " from " + sender.path.name)
      val w: Double = neighbourBasic.get(sender).get
      fragmentCore ! Report(Some(w, sender))

    case Report(mwoe) =>
      log.debug("Received 'Report' at " + self.path.name + " from " + sender.path.name + ", mwoe -> " + mwoe)
      mwoe match {
        case Some((mwoeWeight, mwoeNode)) =>
          reportAcceptedCounter = reportAcceptedCounter + 1;
          reportMwoeWeight match {
            case None =>
              reportMwoeWeight = Some(mwoeWeight)
              reportMwoeNode = mwoeNode
              reportMwoeSender = sender
            case Some(w) =>
              if (mwoeWeight < w) {
                reportMwoeWeight = Some(mwoeWeight)
                reportMwoeNode = mwoeNode
                reportMwoeSender = sender
              }
          }
        case None =>
          reportEmptyCounter = reportEmptyCounter + 1;
      }
      if (reportAcceptedCounter + reportEmptyCounter == fragmentNodes.size) {
        log.info("Report completed at " + self.path.name)
        if (reportAcceptedCounter > 0) {
          reportMwoeSender ! InitConnect(reportMwoeNode)
        }
      }

    case InitConnect(mwoeNode) =>
      mwoeNode ! Connect(fragmentLevel, fragmentId, fragmentCore, fragmentNodes)

    case Connect(fragmentLevel, fragmentId, fragmentCore, fragmentNodes) =>
      if (fragmentLevel < this.fragmentLevel) {
        // connect accepted, low level fragment is merged immediately
        self ! ChangeFragment()
        sender ! ChangeFragment(Some(this.fragmentId), Some(this.fragmentLevel), Some(this.fragmentCore), Some(this.fragmentNodes));        
      } else if (fragmentLevel == this.fragmentLevel) {
        if (fragmentId > this.fragmentId) {
          // connect accepted, create new fragment at level+1
          self ! ChangeFragment(Some(fragmentId), Some(fragmentLevel+1), Some(fragmentCore), Some(fragmentNodes));
          sender ! ChangeFragment(None, Some(fragmentLevel+1), None, Some(this.fragmentNodes));
        }
        else {
          // wait
        }
      } else {
        // wait
      }

    case ChangeFragment(newFragmentId: Option[Integer], newFragmentLevel: Option[Integer], newFragmentCore: Option[ActorRef], newFragmentNodes: Option[Set[ActorRef]]) =>

      if (sender != fragmentCore) {
        // received from other than current core
        if (self == fragmentCore) {
          // at current fragment core
          this.fragmentNodes foreach {
            case (node) =>
              node ! ChangeFragment(newFragmentId, newFragmentLevel, newFragmentCore, newFragmentNodes)
          }
        } else {
          // or forward to current fragment core
          this.fragmentCore forward ChangeFragment(newFragmentId, newFragmentLevel, newFragmentCore, newFragmentNodes)
        }
      } else {
        // received from current fragment core
        if (newFragmentId.isDefined) {
          this.fragmentId = newFragmentId.get
        }
        if (newFragmentLevel.isDefined) {
          this.fragmentLevel = newFragmentLevel.get
        }
        if (newFragmentCore.isDefined) {
          this.fragmentCore = newFragmentCore.get
        }
        if (newFragmentNodes.isDefined) {
          this.fragmentNodes = newFragmentNodes.get ++ this.fragmentNodes
        }
        // confirm core
        this.fragmentCore ! ChangeFragmentCompleted()
      }

    case ChangeFragmentCompleted() =>
      changeCoreCounter = changeCoreCounter + 1;
      if (changeCoreCounter == this.fragmentNodes.size) {
        log.info("Received all 'ChangeFragmentCompleted' at " + self.path.name)
      }

  }

}

object GHSMain extends App {
  val system = ActorSystem("GHSSystem")
  // default Actor constructor
  val a = system.actorOf(Props[GHS], name = "a")
  val b = system.actorOf(Props[GHS], name = "b")
  val c = system.actorOf(Props[GHS], name = "c")
  val d = system.actorOf(Props[GHS], name = "d")
  val e = system.actorOf(Props[GHS], name = "e")
  val f = system.actorOf(Props[GHS], name = "f")
  val g = system.actorOf(Props[GHS], name = "g")
  val h = system.actorOf(Props[GHS], name = "h")
  val i = system.actorOf(Props[GHS], name = "i")
  val j = system.actorOf(Props[GHS], name = "j")

  implicit val timeout = Timeout(5 seconds)

  val graph = Map(
    a -> Map((b, 3.0), (c, 6.0), (e, 9.0)),
    b -> Map((a, 3.0), (c, 4.0), (d, 2.0), (f, 9.0), (e, 9.0)),
    c -> Map((a, 6.0), (b, 4.0), (d, 2.0), (g, 9.0)),
    d -> Map((b, 2.0), (c, 2.0), (f, 8.0), (g, 9.0)),
    e -> Map((a, 9.0), (b, 9.0), (f, 8.0), (j, 18.0)),
    f -> Map((b, 9.0), (d, 8.0), (e, 8.0), (g, 7.0), (i, 9.0), (j, 10.0)),
    g -> Map((c, 9.0), (d, 9.0), (f, 7.0), (i, 5.0), (h, 4.0)),
    h -> Map((f, 4.0), (i, 1.0), (j, 4.0)),
    i -> Map((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)),
    j -> Map((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)))

  // init graph
  var fragmentId = 1
  graph.foreach {
    case (node, nbs) =>
      val future = node ? InitNode(nbs, fragmentId)
      val result = Await.result(future, timeout.duration).asInstanceOf[InitNodeCompleted]
      fragmentId += 1
  }

  graph.keys.foreach { node =>
    node ! InitTest()
  }

  Thread.sleep(1000)

  system.shutdown()
}