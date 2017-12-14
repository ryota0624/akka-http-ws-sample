import PublishFeedActor.SubFeed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.ExecutionContext
import scala.io.StdIn

case class Feed(id: Int, subject: String, text: String)

case class SendFeed(feed: Feed)

class PublishFeedActor extends Actor {

  import PublishFeedActor.SubFeed

  var feedIdSeed = 0

  import scala.concurrent.duration._

  var subscribers: Map[Int, ActorRef] = Map.empty

  override def preStart(): Unit = {
    super.preStart()
    implicit val ctx: ExecutionContext = context.dispatcher
    context.system.scheduler.schedule(0.millisecond, 10.milliseconds) {
      publishFeed(Feed(feedIdSeed, s"${feedIdSeed} subject!", "text"))
      feedIdSeed += 1
    }
  }

  private def publishFeed(feed: Feed): Unit = {
    subscribers.foreach {
      case (_, subscriber) => subscriber ! feed
    }
  }

  override def receive: Receive = {
    case SubFeed(subscriber) =>
      subscribers = subscribers + (subscriber.hashCode -> subscriber)
  }
}

class SubscribeFeedActor extends Actor {
  override def receive: Receive = {
    case SendFeed(feed) =>
      println(feed.subject)
  }
}

object WSSampleMain extends App {
  implicit val actorSystem = ActorSystem("server")
  implicit val flowMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  val webSocketRoute = path("feed") {
    handleWebSocketMessages(feed)
  }

  val bindingFuture = Http().bindAndHandle(webSocketRoute, "localhost", 9000)

  val feedPublishActor = actorSystem.actorOf(Props(classOf[PublishFeedActor]))

  val feedStream = Source.actorRef[Feed](5, OverflowStrategy.fail).mapMaterializedValue {
    actorRef: ActorRef => feedPublishActor ! SubFeed(actorRef)
  }

  println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")

  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => actorSystem.terminate()) // and shutdown when done

  def feed: Flow[Message, Message, Any] =
    Flow[Message]
      .merge(feedStream.map { feed => TextMessage(feed.toString) })
      .mapConcat {
        case tm: TextMessage =>
          tm :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
}

object PublishFeedActor {

  case class SubFeed(subscriber: ActorRef)

}

//  val feedSubscribeActor = actorSystem.actorOf(Props(classOf[SubscribeFeedActor]))
//
//  feedPublishActor ! SubFeed(feedSubscribeActor)
//  val stdInSource = Source.fromIterator(() => io.Source.stdin.getLines()).map(_.length.toString)
//   val stdoutSink = Sink.foreach(println)
