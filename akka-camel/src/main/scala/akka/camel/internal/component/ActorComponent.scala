package akka.camel.internal.component

/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

import java.util.{Map => JMap}

import org.apache.camel._
import org.apache.camel.impl.{DefaultProducer, DefaultEndpoint, DefaultComponent}

import akka.actor._

import scala.reflect.BeanProperty
import akka.dispatch.Await
import akka.util.{Duration, Timeout}
import akka.util.duration._
import akka.camel.{Camel, CamelExchangeAdapter, Ack, Failure, Message, BlockingOrNot, Blocking, NonBlocking}

//TODO: replace with ActorPath class. When I tried I could not find a way of constructing ActorPath from a string. Any ideas?
case class Path(value: String) {
  //TODO: Like this
  //TODO: but I'm first completing the ProducerFeatureTest ;-)
  def fromString(path: String) = {
    path match {
      case LocalActorPath(root, children) => {
        val rootPath = RootActorPath(root)
        children.foldLeft[ActorPath](rootPath)((b, a) => b / a)
      }
    }
  }
}

/**
 * Camel component for sending messages to and receiving replies from (untyped) actors.
 *
 * @see akka.camel.component.ActorEndpoint
 * @see akka.camel.component.ActorProducer
 *
 * @author Martin Krasser
 */
class ActorComponent(camel : Camel) extends DefaultComponent {
  def createEndpoint(uri: String, remaining: String, parameters: JMap[String, Object]): ActorEndpoint = {
    val path = parsePath(remaining)
    new ActorEndpoint(uri, this, path, camel)
  }


  private def parsePath(remaining: String): Path = remaining match {
    case null | "" => throw invalidPath(remaining)
    case   id if id   startsWith "path:"   => Path(parseIdentifier(id substring 5).getOrElse(throw invalidPath(remaining)))
  }

  private def parseIdentifier(identifier: String): Option[String] = if (identifier.length > 0) Some(identifier) else None

  private[this] def invalidPath(id: String): scala.IllegalArgumentException = {
    new IllegalArgumentException("invalid path: [%s] - should be <actorid> or id:<actorid> or uuid:<actoruuid>" format id)
  }
}


/**
 * TODO fix the doc to be consistent with implementation
 * Camel endpoint for sending messages to and receiving replies from (untyped) actors. Actors
 * are referenced using <code>actor</code> endpoint URIs of the following format:
 * <code>actor:<actor-id></code>,
 * <code>actor:id:[<actor-id>]</code> and
 * <code>actor:uuid:[<actor-uuid>]</code>,
 * where <code><actor-id></code> refers to <code>ActorRef.id</code> and <code><actor-uuid></code>
 * refers to the String-representation od <code>ActorRef.uuid</code>. In URIs that contain
 * <code>id:</code> or <code>uuid:</code>, an actor identifier (id or uuid) is optional. In this
 * case, the in-message of an exchange produced to this endpoint must contain a message header
 * with name <code>CamelActorIdentifier</code> and a value that is the target actor's identifier.
 * If the URI contains an actor identifier, a message with a <code>CamelActorIdentifier</code>
 * header overrides the identifier in the endpoint URI.
 *
 * @see akka.camel.component.ActorComponent
 * @see akka.camel.component.ActorProducer

 * @author Martin Krasser
 */
class ActorEndpoint(uri: String,
                    comp: ActorComponent,
                    val path: Path,
                    camel : Camel) extends DefaultEndpoint(uri, comp)  with ActorEndpointConfig{



  /**
   * @throws UnsupportedOperationException
   */
  def createConsumer(processor: Processor): org.apache.camel.Consumer =
    throw new UnsupportedOperationException("actor consumer not supported yet")

  /**
   * Creates a new ActorProducer instance initialized with this endpoint.
   */
  def createProducer: ActorProducer = new ActorProducer(this, camel)

  /**
   * Returns true.
   */
  def isSingleton: Boolean = true
}

trait ActorEndpointConfig{
  def getEndpointUri : String
  def path : Path
  /**
   * When endpoint is outCapable (can produce responses) outTimeout is the maximum time
   * the endpoint can take to send the response back. It defaults to Int.MaxValue seconds.
   * It can be overwritten by setting @see blocking property
   */
  @BeanProperty var outTimeout: Duration = Int.MaxValue seconds


  /**
   * Whether to block caller thread during two-way message exchanges with (untyped) actors. This is
   * set via the <code>blocking=true|false</code> endpoint URI parameter. Default value is
   * <code>false</code>.
   */
  @BeanProperty var blocking: BlockingOrNot = NonBlocking

  /** TODO fix it
   * Whether to auto-acknowledge one-way message exchanges with (untyped) actors. This is
   * set via the <code>blocking=true|false</code> endpoint URI parameter. Default value is
   * <code>true</code>. When set to <code>true</code> consumer actors need to additionally
   * call <code>Consumer.ack</code> within <code>Actor.receive</code>.
   */
  @BeanProperty var autoack: Boolean = true
}

/**
 * Sends the in-message of an exchange to an (untyped) actor, identified by an
 * actor endpoint URI or by a <code>CamelActorIdentifier</code> message header.
 * <ul>
 * <li>If the exchange pattern is out-capable and <code>blocking</code> is set to
 * <code>true</code> then the producer waits for a reply, using the !! operator.</li>
 * <li>If the exchange pattern is out-capable and <code>blocking</code> is set to
 * <code>false</code> then the producer sends the message using the ! operator, together
 * with a callback handler. The callback handler is an <code>ActorRef</code> that can be
 * used by the receiving actor to asynchronously reply to the route that is sending the
 * message.</li>
 * <li>If the exchange pattern is in-only then the producer sends the message using the
 * ! operator.</li>
 * </ul>
 *
 * @see akka.camel.component.ActorComponent
 * @see akka.camel.component.ActorEndpoint
 *
 * @author Martin Krasser
 */
class ActorProducer(val ep: ActorEndpoint, camel: Camel) extends DefaultProducer(ep) with AsyncProcessor {
  def process(exchange: Exchange) {new TestableProducer(ep, camel).process(new CamelExchangeAdapter(exchange))}
  def process(exchange: Exchange, callback: AsyncCallback) = new TestableProducer(ep, camel).process(new CamelExchangeAdapter(exchange), callback)
}

//TODO needs to know about ActorSystem instead of ConsumerRegistry. why is it called TestableProducer?
// re: I'd rather keep the abstraction layer for now and let the Camel class delegate
class TestableProducer(ep : ActorEndpointConfig, camel : Camel) {

  private lazy val path = ep.path

  def process(exchange: CamelExchangeAdapter) {
    if (exchange.isOutCapable)
      sendSync(exchange, ep.outTimeout, forwardResponseTo(exchange))
    else
      fireAndForget(exchange)
  }

  def process(exchange: CamelExchangeAdapter, callback: AsyncCallback): Boolean = {
    def notifyDoneSynchronously() = callback.done(true)
    def notifyDoneAsynchronously() = callback.done(false)
    val DoneSync = true
    val DoneAsync = false

    def processAck : PartialFunction[Either[Throwable,Any], Unit] = {
      case Right(Ack) => { /* no response message to set */}
      case Right(failure : Failure) => exchange.fromFailureMessage(failure)
      case Right(msg) => exchange.fromFailureMessage(Failure(new IllegalArgumentException("Expected Ack or Failure message, but got: "+msg)))
      case Left(throwable) =>  exchange.fromFailureMessage(Failure(throwable))
    }

    def outCapable: Boolean = {
      ep.blocking match {
        case Blocking(timeout) => {
          sendSync(exchange, timeout, onComplete = forwardResponseTo(exchange))
          notifyDoneSynchronously
          DoneSync
        }
        case NonBlocking => {
          sendAsync(exchange, ep.outTimeout, onComplete = forwardResponseTo(exchange) andThen { _ => notifyDoneAsynchronously})
          DoneAsync
        }
      }
    }

    def inOnlyAutoAck: Boolean = {
      ep.blocking match {
        case NonBlocking => {
          fireAndForget(exchange)
          notifyDoneAsynchronously
          DoneAsync
        }
        case Blocking(_) => throw new IllegalStateException("cannot have blocking=true and autoack=true for in-only message exchanges")
      }
    }

    def inOnlyManualAck: Boolean = {
      ep.blocking match {
        case NonBlocking => {
          sendAsync(exchange, ep.outTimeout, onComplete = processAck andThen { _ => notifyDoneAsynchronously})
          DoneAsync
        }
        case Blocking(timeout) => {
          sendSync(exchange, timeout, onComplete = processAck)
          notifyDoneSynchronously
          DoneSync
        }
      }
    }

    if (exchange.isOutCapable){
      outCapable
    } else {
      if (ep.autoack) inOnlyAutoAck else inOnlyManualAck
    }
  }

  private def sendSync(exchange: CamelExchangeAdapter, timeout : Duration, onComplete: PartialFunction[Either[Throwable, Any], Unit]) {
    val future = send(exchange, timeout)
    val response = either(Await.result(future, timeout))
    onComplete(response)
  }

  private def sendAsync(exchange: CamelExchangeAdapter, timeout : Duration, onComplete: PartialFunction[Either[Throwable, Any], Unit]) {
    val future = send(exchange, timeout)
    future.onComplete(onComplete)
  }

  private def fireAndForget(exchange: CamelExchangeAdapter) { actorFor(path) ! messageFor(exchange) }

  private[this] def send(exchange: CamelExchangeAdapter, timeout : Duration) = {
    val actor = actorFor(path)
    val message = messageFor(exchange)
    actor ? (message, new Timeout(timeout))
  }

  private[this] def forwardResponseTo(exchange:CamelExchangeAdapter) : PartialFunction[Either[Throwable,Any], Unit] = {
    case Right(failure:Failure) => exchange.fromFailureMessage(failure);
    case Right(msg) => exchange.setResponse(Message.canonicalize(msg, camel))
    case Left(throwable) =>  exchange.fromFailureMessage(Failure(throwable))
  }

  private[this] def either[T](block: => T) : Either[Throwable,T] = try {Right(block)} catch {case e => Left(e)}
//TODO should this not just use actorSystem.actorFor?
  private[this] def actorFor(path:Path) : ActorRef =
    camel.findConsumer(path) getOrElse (throw new ActorNotRegisteredException(ep.getEndpointUri))

  private[this] def messageFor(exchange: CamelExchangeAdapter)  =
     exchange.toRequestMessage(Map(Message.MessageExchangeId -> exchange.getExchangeId))

}

/**
 * Thrown to indicate that an actor referenced by an endpoint URI cannot be
 * found in the Actor.registry.
 *
 * @author Martin Krasser
 */
class ActorNotRegisteredException(uri: String) extends RuntimeException{
  override def getMessage = "%s not registered" format uri
}

/**
 * Thrown to indicate that no actor identifier has been set.
 *
 * @author Martin Krasser
 */
class ActorIdentifierNotSetException extends RuntimeException {
  override def getMessage = "actor identifier not set"
}


object DurationTypeConverter extends CamelTypeConverter {
  def convertTo[T](`type`: Class[T], value: AnyRef) = Duration.fromNanos(value.toString.toLong).asInstanceOf[T]
}

object BlockingOrNotTypeConverter extends CamelTypeConverter{
  import akka.util.duration._
  val blocking = """Blocking\((\d+) nanos\)""".r
  def convertTo[T](`type`: Class[T], value: AnyRef) = `type` match{
    case c: Class[BlockingOrNot] => value.toString match  {
      case blocking(timeout) => Blocking(timeout.toLong nanos).asInstanceOf[T]
      case "NonBlocking" => NonBlocking.asInstanceOf[T]
    }
  }


  def toString(b : BlockingOrNot)  = b match{
    case NonBlocking => NonBlocking.toString
    case Blocking(timeout) => "Blocking(%d nanos)".format(timeout.toNanos)
  }

}

abstract class CamelTypeConverter extends TypeConverter{
  def convertTo[T](`type`: Class[T], exchange: Exchange, value: AnyRef) = convertTo(`type`, value)
  def mandatoryConvertTo[T](`type`: Class[T], value: AnyRef) = convertTo(`type`, value)
  def mandatoryConvertTo[T](`type`: Class[T], exchange: Exchange, value: AnyRef) = convertTo(`type`, value)
}

