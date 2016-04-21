import akka.actor.{Props, ActorSystem}

/**
  * Created by Michael Curtis on 4/18/2016.
  */

object Main{
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("mySys")
    val worker = system.actorOf(Props[Master],name="lone_worker")
    worker ! StartIndexing(args)
  }
}