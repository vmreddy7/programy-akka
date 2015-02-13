import akka.actor._

class MyRandezVous extends CellularAlgorithm {

}

object MyRandezVousMain extends App {
  val system = ActorSystem("MyRandezVousSystem")
  // default Actor constructor
  val a = system.actorOf(Props[MyRandezVous], name = "a")
  val b = system.actorOf(Props[MyRandezVous], name = "b")
  val c = system.actorOf(Props[MyRandezVous], name = "c")
  val d = system.actorOf(Props[MyRandezVous], name = "d")

  val procs = List(a, b, c, d)

  a ! InitMsg(procs)
  b ! InitMsg(procs)
  c ! InitMsg(procs)
  d ! InitMsg(procs)
}
