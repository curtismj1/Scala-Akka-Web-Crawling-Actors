import akka.actor.{ActorSystem, Actor, Props, ActorRef}
import akka.actor.Actor.Receive


// Diagram of Actors and messages
//
//                                       |
//                                       |
//                                 StartIndexing
//                                       |
//                                       V

/////////////  <-- IndexRequest --   ////////////  <----- Query ----- //////////////
// Fetcher //                        // Master //                     // Prompter //
///////////// -- Option[RawPage] --> ////////////  -- QueryResult --> //////////////


//////////////////////////////////
// Case classes for messages

case class StartIndexing(urls: Seq[String])

case class IndexRequest(url: String)
case class RawPage(url: String, html: String) {
  // Override toString so printed messages do not
  //   dump the full html
  override def toString() = url
}

case class Query(terms: Seq[String])
case class QueryResult(fractionContaining: Double, numTotalPages: Int)

//////////////////////////////////
// Actors

// TODO: write the Fetcher class  
class Fetcher extends Actor {
  def receive = {
    case IndexRequest(url) => {
      try {
        val src = scala.io.Source.fromURL(url).getLines.mkString("\n")
        sender ! Some(RawPage(url, src))
      } catch {
        case ioe: java.io.IOException => sender ! None
      }
    }
  }

}

// Prompter asks the user to enter queries,
//   then sends them along to the Master,
//   receiving and displaying results
class Prompter extends Actor {
 
  // To add logging of messages received,
  //   1. edit the application.conf file
  //   2. use the following line instead of def receive = {
  //def receive = akka.event.LoggingReceive {
  
  def receive = {
    case QueryResult(fracContaining, numTotalPages) => {
      if(numTotalPages > 0){
        println((fracContaining*100.0) + "% of " + numTotalPages + " total pages matched.")
      }
      
      // Prompt for the next query
      val q = scala.io.StdIn.readLine("Enter a query: ")
      
      sender ! Query(if(q.length == 0) Nil else q.split("(_|\\W)+").map(_.toLowerCase))
    }
  }
}

class Master extends Actor {
  // TODO: Set maxPages lower for initial testing
  val maxPages = 100

  val urlsToIndex = scala.collection.mutable.HashSet[String]()
  
  // indexedUrls duplicates information with indexedPages,
  //   but indexedUrls is quick to check for duplicates
  val indexedPages = scala.collection.mutable.ListBuffer[Page]()
  val indexedUrls = scala.collection.mutable.HashSet[String]()
  
  def receive = {
   
    // TODO: handle StartIndexing message
    case x: StartIndexing => {
          //Make sure this is correct
          for(url <- x.urls){
            val myFetcher = context.actorOf(Props[Fetcher])
            myFetcher ! IndexRequest(url)
            val myPrompter = context.actorOf(Props[Prompter])
            myPrompter ! QueryResult(0,0)
          }
    }
    // TODO: handle Query message
    case x: Query => {
      if(x.terms.size == 0){
        context.stop(self)
      }
      else{
        val y = (for(page <- indexedPages if page.containsAll(x.terms))yield page).size
        sender ! QueryResult(y.toDouble / indexedPages.size.toDouble, indexedPages.size)
      }
    }
    case x: Option[_] => {
      // See if we got a RawPage back
      x match {
        case Some(RawPage(url, html)) => {
          // Add the page to the indexed collections
          val pg = new Page(url, html)
          indexedPages += pg
          indexedUrls += url
      
          // Add the links found in the page to the set of links to index
          urlsToIndex ++= pg.getLinks.filter( link =>
              !urlsToIndex.contains(link) && !indexedUrls.contains(link) )
        }
        case _ => Unit
      }  
      
      // Regardless of whether or not we got a RawPage,
      //   we should send another request to the Fetcher
      if (!urlsToIndex.isEmpty && indexedUrls.size < maxPages) {
        val nextUrl = urlsToIndex.head
        urlsToIndex.remove(nextUrl)
        sender ! IndexRequest(nextUrl)
      }
      // If urlsToIndex is empty, we should do something to prevent
      //   the sender Fetcher from remaining idle,
      //   but that is beyond our scope
    }
        
  }

}




    