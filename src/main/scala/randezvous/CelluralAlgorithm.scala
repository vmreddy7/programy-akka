package randezvous

import akka.actor._
import java.util.Random

case class Init(neighbourProcs: List[ActorRef])
case class Probe(v: Integer)

abstract class CellularAlgorithm extends Actor {

  val rnd = new Random(System.currentTimeMillis())

  var neighbourProcs = List.empty[ActorRef]
  var neighborDoor = 0
  var i = 0
  var j = 0

  def communicate(x: ActorRef)

  def receive = {

    case Init(procs) =>
      neighbourProcs = procs
      neighborDoor = rnd.nextInt() % neighbourProcs.length

      neighbourProcs.foreach { proc =>
        if (i == neighborDoor) {
          proc ! Probe(1)
        } else {
          proc ! Probe(0)
        }
        i = i + 1
      }

    case Probe(v) =>
      if (v == 1 && j == neighborDoor) {
        communicate(sender)
      }
      j = j + 1
  }
}
