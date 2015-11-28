package ghs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

case class InitNode(neighbourProcs: Map[ActorRef, Double], fragmentId: Integer)
case class InitNodeCompleted()
case class Initiate()
case class Test(fragementId: Integer, fragmentLevel: Integer)
case class Reject()
case class Accept()
case class Report(weight: Option[Double])
case class Connect(fragmentLevel: Integer)
case class ChangeCore()

class GHS extends Actor {

  var neighbourBasic: Map[ActorRef, Double] = null

  var neighbourBranch: Map[ActorRef, Double] = null

  var neighbourRejected: Map[ActorRef, Double] = null

  var fragmentId: Integer = null

  var fragmentLevel: Integer = null

  var fragmentCore: ActorRef = null

  var fragmentNodes: List[ActorRef] = null

  var reportAcceptedNum: Integer = 0

  var reportEmptyNum: Integer = 0

  var reportMwoe: Option[Double] = None

  def receive = {

    case InitNode(procs, fragmentID) =>
      this.neighbourBasic = procs
      this.neighbourBranch = Map.empty[ActorRef, Double]
      this.neighbourRejected = Map.empty[ActorRef, Double]
      this.fragmentId = fragmentID
      this.fragmentLevel = 0
      this.fragmentCore = self
      this.fragmentNodes = List(fragmentCore)
      sender ! InitNodeCompleted()

    case Initiate() =>

      // send test to min basic edge
      if (neighbourBasic.isEmpty) {
        fragmentCore ! Report(None)
      } else {
        var minWeight = Double.MaxValue
        var minEdge: ActorRef = null
        for (n <- neighbourBasic) {
          if (n._2 < minWeight) {
            minWeight = n._2
            minEdge = n._1
          }
        }
        //println("Sending Test to " + minEdge.path.name + " from " + self.path.name)
        minEdge ! Test(this.fragmentId, this.fragmentLevel)
      }

    case Test(fragmentId, fragmentLevel) =>
      //println("Received Test at " + self.path.name + " from " + sender.path.name)
      if (this.fragmentId == fragmentId) {
        sender ! Reject()
      } else {
        if (this.fragmentLevel >= fragmentLevel) {
          //println("Sending Accept to " + sender.path.name + " from " + self.path.name)
          sender ! Accept()
        } else {
          // response is delayed
        }
      }

    case Reject() =>
      println("Received Reject at " + self.path.name + " from " + sender.path.name)
      fragmentCore ! Report(None)

    case Accept() =>
      println("Received Accept at " + self.path.name + " from " + sender.path.name)
      val w: Double = neighbourBasic.get(sender).get
      fragmentCore ! Report(Some(w))

    case Report(weight) =>
      println("Received Report at " + self.path.name + " from " + sender.path.name + ", weight -> " + weight)
      reportMwoe = weight match {
        case Some(w) =>
          reportAcceptedNum = reportAcceptedNum + 1;
          reportMwoe match {
            case None =>
              Some(w)
            case Some(mwoe) =>
              Some(Math.min(mwoe, w))
          }
        case None =>
          reportEmptyNum = reportEmptyNum + 1;
          reportMwoe
      }
      if (reportAcceptedNum + reportEmptyNum == fragmentNodes.size) {
        println("Report completed at " + self.path.name)
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
    node ! Initiate()
  }

  Thread.sleep(1000)

  system.shutdown()
}