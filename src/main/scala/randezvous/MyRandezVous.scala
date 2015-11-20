package randezvous

import akka.actor._

case class HelloMsg(response: Boolean)

class MyRandezVous extends CellularAlgorithm {

  def communicate(x: ActorRef) {
    println("Communnicate to " + x.path.name + " from " + self.path.name)
    x ! HelloMsg(true)
  }

  override def receive = super.receive orElse {
    case HelloMsg(response) =>
      println("Hello at " + self.path.name + " from " + sender.path.name)
      if (response) {
        sender ! HelloMsg(false)
      }
  }

}

object MyRandezVousMain extends App {
  val system = ActorSystem("MyRandezVousSystem")
  // default Actor constructor
  val a = system.actorOf(Props[MyRandezVous], name = "a")
  val b = system.actorOf(Props[MyRandezVous], name = "b")
  val c = system.actorOf(Props[MyRandezVous], name = "c")

  a ! InitMsg(List(b, c))
  b ! InitMsg(List(a, c))
  c ! InitMsg(List(a, b))

  system.shutdown()
}
