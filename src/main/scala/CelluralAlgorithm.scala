import akka.actor._

case class InitMsg(neighbourProcs: List[ActorRef])

case class ProbeMsg(i: Integer)

abstract class CellularAlgorithm extends Actor {

  var neighbourProcs = List.empty[ActorRef]

  def receive = {
    case InitMsg(procs) =>
      neighbourProcs = procs

    case ProbeMsg(i) => println("NYI")
  }
}
