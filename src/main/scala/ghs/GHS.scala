package ghs

import akka.actor._

case class InitMsg(neighbourProcs: List[(ActorRef, Double)])

class GHS extends Actor {

  var neighbourProcs = List.empty[(ActorRef, Double)]

  def receive = {
    case InitMsg(procs) =>
      neighbourProcs = procs
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

  // init graph
  a ! InitMsg(List((b, 3.0), (c, 6.0), (e, 9.0)))
  b ! InitMsg(List((a, 3.0), (c, 4.0), (d, 2.0), (f, 9.0), (e, 9.0)))
  c ! InitMsg(List((a, 6.0), (b, 4.0), (d, 2.0), (g, 9.0)))
  d ! InitMsg(List((b, 2.0), (c, 2.0), (f, 8.0), (g, 9.0)))
  e ! InitMsg(List((a, 9.0), (b, 9.0), (f, 8.0), (j, 18.0)))
  f ! InitMsg(List((b, 9.0), (d, 8.0), (e, 8.0), (g, 7.0), (i, 9.0), (j, 10.0)))
  g ! InitMsg(List((b, 3.0), (c, 6.0), (e, 9.0), (c, 6.0), (e, 9.0)))
  h ! InitMsg(List((f, 4.0), (i, 1.0), (j, 4.0)))
  i ! InitMsg(List((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)))
  j ! InitMsg(List((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)))

  system.shutdown()
}