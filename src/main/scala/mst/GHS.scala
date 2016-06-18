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

object NodeState extends Enumeration {
  type NodeState = Value
  val Sleeping, Find, Found = Value
}
import NodeState._

object EdgeState extends Enumeration {
  type EdgeState = Value
  val Basic, Branch, Rejected = Value
}
import EdgeState._

case class Edge(state: EdgeState, weight: Double, node: ActorRef)

// messages
case class InitActor(neighbourProcs: Map[ActorRef, Double], id: Integer)
case class InitActorCompleted()
case class Wakeup()
case class Connect(level: Integer)
case class Initiate(level: Integer, id: Integer, state: NodeState)

/**
  * Scala/Akka implementation of GHS distributed minimum spanning tree algorithm
  */
class GHS extends Actor {

  val log = Logging(context.system, this)

  var edges: scala.collection.mutable.Map[ActorRef, Edge] = null
  var mst: scala.collection.mutable.ArrayBuffer[ActorRef] = null
  var state : NodeState = Sleeping
  var bestEdge : ActorRef = null
  var bestWeight : Double = Double.MaxValue
  var testEdge : ActorRef = null
  var parent : ActorRef = null
  var level : Integer = -1
  var findCount : Integer = -1
  var id : Integer = null

  def receive = {

    case InitActor(procs, id) =>
      this.edges = scala.collection.mutable.Map()
      this.mst = scala.collection.mutable.ArrayBuffer()
      procs.keys.foreach { nb =>
        val weight = procs(nb)
        edges += (nb -> new Edge(Basic, weight, nb))
      }
      this.id = id;
      sender ! InitActorCompleted()

    case Wakeup() =>
      log.info("Received 'Wakeup' at " + self.path.name + " from " + sender.path.name)
      val minEdgeOption = findMinEdge
      minEdgeOption match {
        case None =>
          log.warning("No neighbours found, finishing.")
        case Some((minNode, minWeight)) =>
          this.edges(minNode) = new Edge(Branch, edges(minNode).weight, edges(minNode).node)
          this.mst += minNode
          this.level = 0
          this.state = Found
          this.findCount = 0
          minNode ! Connect(0)
      }

    case Connect(level) =>
      log.info("Received 'Connect' at " + self.path.name + " from " + sender.path.name)
      if (level < this.level) {
        // absorb fragment
        this.edges(sender) = new Edge(Branch, edges(sender).weight, edges(sender).node)
        sender ! Initiate(this.level, this.id, this.state)
      }
      else if (edges(sender).state == Basic) {
        sender ! Connect(level)
      }
      else {
        // create new fragment
        sender ! Initiate(this.level + 1, this.id, Find)
      }

    case Initiate(level, id, state) =>
      this.level = level
      this.id = id
      this.state = state
      this.parent = sender
      this.bestEdge = null
      this.bestWeight = Double.MaxValue
      edges.keys.foreach { nb =>
        val edge = edges(nb)
        if (nb != sender && edge.state == Branch) {
          nb ! Initiate(level, id, state)
        }
      }
      if (state == Find) {
        this.findCount = 0
        test()
      }


  }

  def test() = {

  }

  def findMinEdge: Option[(ActorRef,Double)] = {
    if (edges.isEmpty) {
      None
    } else {
      var mwoeWeight = Double.MaxValue
      var mwoeNode: ActorRef = null
      edges.keys.foreach { nb =>
        val edge = edges(nb)
        if (edge.weight < mwoeWeight) {
          mwoeWeight = edge.weight
          mwoeNode = nb
        }
      }
      Some(mwoeNode,mwoeWeight)
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
    h -> Map((g, 4.0), (i, 1.0), (j, 4.0)),
    i -> Map((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)),
    j -> Map((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)))

  // init graph
  var id = 1
  graph.foreach {
    case (node, nbs) =>
      val future = node ? InitActor(nbs, id)
      val result = Await.result(future, timeout.duration).asInstanceOf[InitActorCompleted]
      id += 1
  }
  a ! Wakeup()

  Thread.sleep(1000)

  system.shutdown()
}