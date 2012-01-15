package akka.camel.internal

/**
 * Copyright (C) 2009-2010 Scalable Solutions AB <http://scalablesolutions.se>
 */

import java.io.InputStream
import scala.collection.mutable
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.RouteDefinition
import akka.actor.actorRef2Scala
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.camel.internal.component.BlockingOrNotTypeConverter
import akka.camel.Camel
import akka.camel.Consumer
import akka.camel.ConsumerConfig
import akka.camel.internal.CamelEventsDispatcher.ActorPublished
import akka.camel.internal.CamelEventsDispatcher.ActorUnpublished

/**
 * Guarantees idempotent registration of camel consumer endpoints.
 *
 * Once registered the consumer is watched and unregistered upon termination.
 * It also publishes events to the eventStream so interested parties could subscribe to them.
 * The main consumer of these events is currently the ActivationTracker.
 */
private[camel] class IdempotentCamelConsumerRegistry(camel : Camel) extends Actor {

  case class UnregisterConsumer(actorRef: ActorRef)

  val activated  = new mutable.HashSet[ActorRef]

  val registrator = context.actorOf(Props(new Actor {

    override def postRestart(reason: Throwable) {
      println("Restarted registrator")
    }

    def receive ={
      case RegisterConsumer(endpointUri, consumer) => {
        camel.context.addRoutes(new ConsumerActorRouteBuilder(endpointUri, consumer.self, consumer))
        context.sender ! EndpointActivated(consumer.self)
        CamelEventsDispatcher info ActorPublished(this,consumer,endpointUri)
      }

      case UnregisterConsumer(consumer) => {
        camel.context.stopRoute(consumer.path.toString)
        context.sender ! EndpointDeActivated(consumer)
        CamelEventsDispatcher info ActorUnpublished(this,consumer)
      }
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
      message match{
        case Some(RegisterConsumer(_, consumer)) => sender ! EndpointFailedToActivate(consumer.self, reason)
        case Some(UnregisterConsumer(consumer)) => sender ! EndpointFailedToDeActivate(consumer, reason)
        case _ =>
      }
    }
  }))

  def receive = {
    case msg @ RegisterConsumer(_, consumer) => unless(isAlreadyActivated(consumer.self)) {
      activated.add(consumer.self)
      registrator ! msg
    }
    case msg @ EndpointActivated(consumer) => {
      context.watch(consumer)
      context.system.eventStream.publish(msg)
    }
    case msg @ EndpointFailedToActivate(consumer, _) => {
      activated.remove(consumer)
      context.system.eventStream.publish(msg)
    }
    case Terminated(ref) => {
      activated.remove(ref)
      registrator ! UnregisterConsumer(ref)
    }
    case msg @ EndpointDeActivated(ref) => { context.system.eventStream.publish(msg) }
    case msg @ EndpointFailedToDeActivate(ref, cause) => { context.system.eventStream.publish(msg) }
  }

  def unless[A](condition: Boolean)(block  : => A) = if (!condition) block
  def isAlreadyActivated(ref: ActorRef): Boolean = activated.contains(ref)

}

/**
 * Abstract builder of a route to a target which can be either an actor or an typed actor method.
 *
 * @param endpointUri endpoint URI of the consumer actor or typed actor method.
 *
 * @author Martin Krasser
 */
private[camel] class ConsumerActorRouteBuilder(endpointUri: String, consumer : ActorRef,  config: ConsumerConfig) extends RouteBuilder {

  //TODO: what if actorpath contains parameters? Should we use url encoding? But this will look ugly...
  protected def targetActorUri = "actor:path:%s?blocking=%s&autoack=%s&outTimeout=%s" format (consumer.path, BlockingOrNotTypeConverter.toString(config.blocking), config.autoack, config.outTimeout.toNanos)


  def configure = {

    val scheme = endpointUri take endpointUri.indexOf(":") // e.g. "http" from "http://whatever/..."


    val route = from(endpointUri).routeId(consumer.path.toString)
    val converted = Conversions(scheme, route)
    val userCustomized = applyUserRouteCustomization(converted)
    userCustomized.to(targetActorUri)
  }

  def applyUserRouteCustomization(rd: RouteDefinition) = config.onRouteDefinition(rd)

  object Conversions{
    // TODO: make conversions configurable
    private val bodyConversions = Map(
      "file" -> classOf[InputStream]
    )

    def apply(scheme: String, routeDefinition: RouteDefinition): RouteDefinition = bodyConversions.get(scheme) match {
      case Some(clazz) => routeDefinition.convertBodyTo(clazz)
      case None        => routeDefinition
    }
  }

}

private[camel] case class RegisterConsumer(endpointUri:String, actor: Consumer)

/**
 * Super class of all activation messages.
 */
//TODO investigate warnings about case class inheritance
private[camel] case class ActivationMessage(actor: ActorRef)

/**
 * Event message indicating that a single endpoint has been activated.
 */
private[camel] case class EndpointActivated(actorRef : ActorRef) extends ActivationMessage(actorRef)

private[camel] case class EndpointFailedToActivate(actorRef : ActorRef, cause : Throwable) extends ActivationMessage(actorRef)

private[camel] case class EndpointDeActivated(actorRef : ActorRef) extends ActivationMessage(actorRef)

private[camel] case class EndpointFailedToDeActivate(actorRef : ActorRef, cause : Throwable) extends ActivationMessage(actorRef)
