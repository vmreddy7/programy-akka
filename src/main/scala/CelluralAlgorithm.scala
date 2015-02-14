import akka.actor._
import java.util.Random

case class InitMsg(neighbourProcs: List[ActorRef])
case class ProbeMsg(v: Integer)

abstract class CellularAlgorithm extends Actor {

  val rnd = new Random(System.currentTimeMillis())

  var neighbourProcs = List.empty[ActorRef]
  var neighborDoor = 0
  var i = 0

  def communicate(x: ActorRef)

  def receive = {

    case InitMsg(procs) =>
      neighbourProcs = procs
      neighborDoor = rnd.nextInt() % neighbourProcs.length

      var i = 0
      neighbourProcs.foreach { proc =>
        if (i==neighborDoor) {
          proc ! ProbeMsg(1)                    
        }
        else {
          proc ! ProbeMsg(0)          
        }
        i=i+1
      }

    case ProbeMsg(v) =>
      if (v==1 && i==neighborDoor) {
        communicate(sender)
      }
      i=i+1
  }
}
