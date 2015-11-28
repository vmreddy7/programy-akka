package ghs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.postfixOps

case class InitNode(neighbourProcs: List[(ActorRef, Double)], fragmentId: Integer)
case class InitNodeCompleted()
case class Initiate()
case class Test(fragementId: Integer, fragmentLevel: Integer)
case class Reject()
case class Accept()
case class Report(weight: Option[Double])
case class Connect(fragmentLevel: Integer)
case class ChangeCore()

class GHS extends Actor {

  var neighbourBasic: List[(ActorRef, Double)] = null

  var neighbourBranch: List[(ActorRef, Double)] = null

  var neighbourRejected: List[(ActorRef, Double)] = null

  var fragmentId: Integer = null

  var fragmentLevel: Integer = null

  var fragmentCore: ActorRef = null

  var fragmentNodes: List[ActorRef] = null

  def receive = {

    case InitNode(procs, fragmentID) =>
      this.neighbourBasic = procs
      this.neighbourBranch = List.empty[(ActorRef, Double)]
      this.neighbourRejected = List.empty[(ActorRef, Double)]
      this.fragmentId = fragmentID
      this.fragmentLevel = 0
      this.fragmentCore = self
      this.fragmentNodes = List(fragmentCore)
      sender ! InitNodeCompleted()

    case Initiate() =>

      // send test to min basic edge
      if (neighbourBasic.isEmpty) {
        fragmentCore ! Report(None)
      }
      else {
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
      fragmentCore ! Report(None)
      
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
    a -> List((b, 3.0), (c, 6.0), (e, 9.0)),
    b -> List((a, 3.0), (c, 4.0), (d, 2.0), (f, 9.0), (e, 9.0)),
    c -> List((a, 6.0), (b, 4.0), (d, 2.0), (g, 9.0)),
    d -> List((b, 2.0), (c, 2.0), (f, 8.0), (g, 9.0)),
    e -> List((a, 9.0), (b, 9.0), (f, 8.0), (j, 18.0)),
    f -> List((b, 9.0), (d, 8.0), (e, 8.0), (g, 7.0), (i, 9.0), (j, 10.0)),
    g -> List((c, 9.0), (d, 9.0), (f, 7.0), (i, 5.0), (h, 4.0)),
    h -> List((f, 4.0), (i, 1.0), (j, 4.0)),
    i -> List((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)),
    j -> List((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)))

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