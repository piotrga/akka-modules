package akka.camel.internal
/**
 * Copyright (C) 2012 Scalable Solutions AB <http://scalablesolutions.se>
 */

import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.EventStream
import akka.actor.ActorSystem
import akka.event.Logging
import akka.event.EventBus

/**
 * Publishes actor events on the event stream
 */

object CamelEventsDispatcher {
  
  sealed trait CamelEvent {
	def publisher: AnyRef
  }

  case class ActorPublished(val publisher: AnyRef, actor: Actor, endpointUri: String) extends CamelEvent {
    override def toString() =  "published actor %s at endpoint %s" format(actor, endpointUri)
  }
  case class ActorUnpublished(val publisher: AnyRef, actor: ActorRef) extends CamelEvent {
    override def toString() =  "unpublished actor %s at endpoint %s" format(actor, actor.path)
  }

  case class ActorActivated(val publisher: AnyRef, actor: ActorRef) extends CamelEvent {
    override def toString() =  "activated actor %s at endpoint %s" format(actor, actor.path)
  }

  case class ActorFailedToActivate(val publisher: AnyRef, actor: ActorRef) extends CamelEvent {
    override def toString() =  "failed to activate actor %s at endpoint %s" format(actor, actor.path)
  }

  private[this] val stream = new EventStream(true)

  def info(event: CamelEvent) = {
    println(event)
  }
  def debug(event: CamelEvent) = {
    println(">>DEBUG" + event)
  }
}