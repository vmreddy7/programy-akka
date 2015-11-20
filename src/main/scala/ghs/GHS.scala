package ghs

import akka.actor._

case class InitActor(neighbourProcs: List[(ActorRef, Double)], fragmentId: Integer)
case class Test(fragementId: Integer, fragmentLevel: Integer)
case class Reject()
case class Accept()
case class Report(weight: Double)
case class Connect(fragmentLevel: Integer)
case class InitFragment(fragementId: Integer, fragmentLevel: Integer)

class GHS extends Actor {

  var neighbourBasic: List[(ActorRef, Double)] = null

  var neighbourBranch: List[(ActorRef, Double)] = null

  var neighbourRejected: List[(ActorRef, Double)] = null

  var fragmentId: Integer = null

  var fragmentLevel: Integer = null

  var fragmentCore: ActorRef = null

  var fragmentNodes: List[ActorRef] = null

  def receive = {

    case InitActor(procs, fragmentID) =>
      this.neighbourBasic = procs
      this.neighbourBranch = List.empty[(ActorRef, Double)]
      this.neighbourRejected = List.empty[(ActorRef, Double)]
      self ! InitFragment(fragmentID, 0)

    case InitFragment(fragmentId, fragmentLevel) =>
      this.fragmentId = fragmentId
      this.fragmentLevel = fragmentLevel
      this.fragmentCore = self
      this.fragmentNodes = List(fragmentCore)

      // send test to min basic edge
      if (!neighbourBasic.isEmpty) {
        var minWeight = Double.MaxValue
        var minEdge: ActorRef = null
        for (n <- neighbourBasic) {
          if (n._2 < minWeight) {
            minWeight = n._2
            minEdge = n._1
          }
        }
        //println("Sending Test to " + minEdge.path.name + " from " + self.path.name)
        minEdge ! Test(fragmentId, fragmentLevel)
      }

    case Test(fragmentId, fragmentLevel) =>
      //println("Received Test at " + self.path.name + " from " + sender.path.name)
      if (this.fragmentId == fragmentId) {
        sender ! Reject()
      } else {
        if (this.fragmentLevel >= fragmentLevel) {
          //println("Sending Accept to " + sender.path.name + " from " + self.path.name)
          sender ! Accept()
        } else {
          // response is delayed
        }
      }

    case Accept() =>
      println("Received Accept at " + self.path.name + " from " + sender.path.name)
      
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
  a ! InitActor(List((b, 3.0), (c, 6.0), (e, 9.0)), 1)
  b ! InitActor(List((a, 3.0), (c, 4.0), (d, 2.0), (f, 9.0), (e, 9.0)), 2)
  c ! InitActor(List((a, 6.0), (b, 4.0), (d, 2.0), (g, 9.0)), 3)
  d ! InitActor(List((b, 2.0), (c, 2.0), (f, 8.0), (g, 9.0)), 4)
  e ! InitActor(List((a, 9.0), (b, 9.0), (f, 8.0), (j, 18.0)), 5)
  f ! InitActor(List((b, 9.0), (d, 8.0), (e, 8.0), (g, 7.0), (i, 9.0), (j, 10.0)), 6)
  g ! InitActor(List((c, 9.0), (d, 9.0), (f, 7.0), (i, 5.0), (h, 4.0)), 7)
  h ! InitActor(List((f, 4.0), (i, 1.0), (j, 4.0)), 8)
  i ! InitActor(List((f, 9.0), (g, 5.0), (h, 1.0), (j, 3.0)), 9)
  j ! InitActor(List((e, 18.0), (f, 10.0), (h, 4.0), (i, 3.0)), 10)

  Thread.sleep(5000)
  
  system.shutdown()
}