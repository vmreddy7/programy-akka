package mis

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
import scala.util.Random

case class InitNode(neighbourProcs: List[ActorRef])
case class InitNodeCompleted()
case class Initiate(round: Integer)
case class Proposal(proposalVal: Double)
case class Selected(selectedVal: Boolean)
case class Eliminated(eliminatedVal: Boolean)

class Luby extends Actor {

  val log = Logging(context.system, this)

  val rnd = new Random()

  var neighbours: List[ActorRef] = null

  var com_with: List[ActorRef] = null

  var round_no: Integer = null

  var proposed_val: Option[Double] = None
  
  var com_proposal: scala.collection.mutable.Map[ActorRef, Double] = null

  var com_selected: scala.collection.mutable.Map[ActorRef, Boolean] = null

  var com_selected_true: Option[Boolean] = None
  
  def receive = {

    case InitNode(procs) =>
      this.neighbours = procs
      this.com_with = procs
      sender ! InitNodeCompleted()

    case Initiate(round) =>
      this.round_no = round
      this.proposed_val = Some(rnd.nextDouble());
      this.com_proposal = scala.collection.mutable.Map[ActorRef, Double]()
      this.com_selected = scala.collection.mutable.Map[ActorRef, Boolean]()
      this.com_selected_true = Some(false)
      com_with.foreach { node =>
        node ! Proposal(proposed_val.get)
      }

    case Proposal(proposalVal) =>
      com_proposal(sender) = proposalVal
      if (com_proposal.size == com_with.size) {
        var selected = true
        com_proposal.values.foreach { v =>
          if (v >= proposed_val.get) {
            selected = false
          }
        }
        com_with.foreach { node =>
          node ! Selected(selected)
        }
      }

    case Selected(selectedVal) =>
      com_selected(sender) = selectedVal
      if (selectedVal) {
        com_selected_true = Some(true)
      }
      if (com_selected.size == com_with.size) {
        if (com_selected_true.get) {
          // TODO
        }
      }
  }

}

object LubyMain extends App {
  val system = ActorSystem("LubySystem")
  // default Actor constructor
  val a = system.actorOf(Props[Luby], name = "a")
  val b = system.actorOf(Props[Luby], name = "b")
  val c = system.actorOf(Props[Luby], name = "c")
  val d = system.actorOf(Props[Luby], name = "d")
  val e = system.actorOf(Props[Luby], name = "e")
  val f = system.actorOf(Props[Luby], name = "f")
  val g = system.actorOf(Props[Luby], name = "g")
  val h = system.actorOf(Props[Luby], name = "h")
  val i = system.actorOf(Props[Luby], name = "i")
  val j = system.actorOf(Props[Luby], name = "j")

  implicit val timeout = Timeout(5 seconds)

  val graph = Map(
    a -> List(b, c, e),
    b -> List(a, c, d, f, e),
    c -> List(a, b, d, g),
    d -> List(b, c, f, g),
    e -> List(a, b, f, j),
    f -> List(b, d, e, g, i, j),
    g -> List(c, d, f, i, h),
    h -> List(f, i, j),
    i -> List(f, g, h, j),
    j -> List(e, f, h, i))

  // init graph
  graph.foreach {
    case (node, nbs) =>
      val future = node ? InitNode(nbs)
      val result = Await.result(future, timeout.duration).asInstanceOf[InitNodeCompleted]
  }

  graph.keys.foreach { node =>
    node ! Initiate(1)
  }

  Thread.sleep(1000)

  system.shutdown()
}