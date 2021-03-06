package mis

import scala.annotation.migration
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import scala.collection.mutable.ListBuffer

import scala.collection.mutable.ArrayBuffer

object State extends Enumeration {
  type State = Value
  val Find, In, Out = Value
}
import State._

// messages
case class InitActor(neighbourProcs: List[ActorRef])
case class InitActorCompleted()
case class Initiate(round: Integer)
case class Proposal(proposalVal: Double)
case class Selected(selectedVal: Boolean)
case class Eliminated(eliminatedVal: Boolean)
case class ChangeState(state: State)

/**
  * Scala/Akka implementation of Luby distributed maximal independent set algorithm
  */
class Luby extends Actor {

  val log = Logging(context.system, this)
  val rnd = new Random()

  var neighbours: List[ActorRef] = null
  var com_with: ArrayBuffer[ActorRef] = null
  var round_no: Integer = 1
  var proposed_val: Option[Double] = None
  var com_proposal_messages: scala.collection.mutable.Map[ActorRef, Double] = scala.collection.mutable.Map[ActorRef, Double]()
  var com_selected_messages: scala.collection.mutable.Map[ActorRef, Boolean] = scala.collection.mutable.Map[ActorRef, Boolean]()
  var com_eliminated_messages: scala.collection.mutable.Map[ActorRef, Boolean] = scala.collection.mutable.Map[ActorRef, Boolean]()
  var com_selected: Boolean = false
  var state: State = Find

  def receive = {

    case InitActor(procs) =>
      this.neighbours = procs
      this.com_with = ArrayBuffer[ActorRef]()
      this.com_with ++= neighbours
      sender ! InitActorCompleted()

    case Initiate(round) =>
      log.info("Initiating round " + round + " at " + self.path.name)
      this.round_no = round
      this.com_proposal_messages.clear()
      this.com_selected_messages.clear()
      this.com_selected = false
      this.com_eliminated_messages.clear()

      // propose value
      this.proposed_val = Some(rnd.nextDouble())
      com_with.foreach { node =>
        node ! Proposal(proposed_val.get)
      }

    case Proposal(proposalVal) =>
      // update messages
      com_proposal_messages(sender) = proposalVal
      log.info("Received proposal " + proposalVal + " at " + self.path.name + " from " + sender.path.name + ", round " + this.round_no)

      if (com_proposal_messages.size == com_with.size) { // all messages received
        log.info("Completed proposal at " + self.path.name + ", round " + this.round_no)
        var selected = true
        com_proposal_messages.values.foreach { v =>
          if (v >= proposed_val.get) {
            selected = false
          }
        }
        if (selected) {
          log.info("Node is selected at " + self.path.name + ", round " + this.round_no)
          self ! ChangeState(In)
        }
        com_with.foreach { node =>
          node ! Selected(selected)
        }
      }

    case ChangeState(state) =>
      log.info("Returning into " + state + " at " + self.path.name)+ " from " + sender
      this.state = state
      context.stop(self)

    case Selected(selectedVal) =>
      // update messages
      com_selected_messages(sender) = selectedVal
      log.info("Received selected " + selectedVal + " at " + self.path.name + " from " + sender.path.name + ", round " + this.round_no)

      if (selectedVal) {
        com_selected = true
      }
      if (com_selected_messages.size == com_with.size) { // all messages received
        log.info("Completed selected at " + self.path.name + ", round " + this.round_no)
        if (com_selected) {
          // node is eliminated
          log.info("Node is eliminated at " + self.path.name + ", round " + this.round_no)
          com_selected_messages.foreach((e: (ActorRef, Boolean)) =>
            if (!e._2) {
              e._1 ! Eliminated(true)
            })
          self ! ChangeState(Out)
        } else {
          com_with.foreach { node =>
            node ! Eliminated(false)
          }
        }
      }

    case Eliminated(eliminatedVal) =>
      // update messages
      com_eliminated_messages(sender) = eliminatedVal
      log.info("Received eliminated " + eliminatedVal + " at " + self.path.name + " from " + sender.path.name + ", round " + this.round_no)

      if (com_eliminated_messages.size == com_with.size) { // all messages received
        log.info("Completed eliminated at " + self.path.name + ", round " + this.round_no)
        com_eliminated_messages.foreach((e: (ActorRef, Boolean)) =>
          if (e._2) {
            com_with -= e._1
          })
        if (com_with.isEmpty) {
          self ! ChangeState(In)
        } else {
          self ! Initiate(round_no + 1)
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
//    a -> List(b, d),
//    b -> List(a, c),
//    c -> List(b, d),
//    d -> List(a, c, e),
//    e -> List(d))

      a -> List(b, c, e),
      b -> List(a, c, d, f, e),
      c -> List(a, b, d, g),
      d -> List(b, c, f, g),
      e -> List(a, b, f, j),
      f -> List(b, d, e, g, i, j),
      g -> List(c, d, f, i, h),
      h -> List(g, i, j),
      i -> List(f, g, h, j),
      j -> List(e, f, h, i))

  // init graph
  graph.foreach {
    case (node, nbs) =>
      val future = node ? InitActor(nbs)
      val result = Await.result(future, timeout.duration).asInstanceOf[InitActorCompleted]
  }

  graph.keys.foreach { node =>
    node ! Initiate(1)
  }

  Thread.sleep(10000)

  system.shutdown()
}