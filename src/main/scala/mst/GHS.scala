package mst

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

object State extends Enumeration {
  type State = Value
  val Basic, Branch, Out = Value
}
import State._

case class Edge(state: State, weight: Double, node: ActorRef)

case class InitActor(neighbourProcs: Map[ActorRef, Double], fragmentId: Integer)
case class InitActorCompleted()
case class Wakeup()

class GHS extends Actor {

  val log = Logging(context.system, this)

  var edges: Map[ActorRef, Edge] = null
  var state : Integer = null
  var bestEdge : Integer = null
  var bestWeight : Double = Double.MaxValue
  var testEdge : Integer = null
  var parent : Integer = null
  var level : Integer = null
  var findCount : Integer = null
  var id : Integer = Integer.MAX_VALUE

  def receive = {

    case InitActor(procs, fragmentID) =>
      this.edges = Map()
      procs.keys.foreach { nb =>
        val weight = procs(nb)
        edges += (nb -> new Edge(Basic, weight, nb))
      }
      this.state = 0
      this.bestEdge = -1;
      this.bestWeight = Double.MaxValue;
      this.testEdge = -1;
      this.parent = -1;
      this.level = -1;
      this.findCount = -1;
      this.id = Integer.MAX_VALUE;

      sender ! InitActorCompleted()
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
    h -> Map((g, 4.0), (i, 1.0), (j, 4.0)),
    i -> Map((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)),
    j -> Map((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)))

  // init graph
  var fragmentId = 1
  graph.foreach {
    case (node, nbs) =>
      val future = node ? InitActor(nbs, fragmentId)
      val result = Await.result(future, timeout.duration).asInstanceOf[InitActorCompleted]
      fragmentId += 1
  }
  a ! Wakeup()

  Thread.sleep(1000)

  system.shutdown()
}