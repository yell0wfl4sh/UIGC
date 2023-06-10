package edu.illinois.osl.akka.gc

import akka.actor.typed
import akka.actor.typed.scaladsl
import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.{BehaviorInterceptor, TypedActorContext}
import edu.illinois.osl.akka.gc.interfaces.Message

import scala.reflect.ClassTag

object Behaviors {
  /** 
   * Sets up a garbage-collected actor. The [[ActorFactory]] instance
   * produced by this method can only be used by *GC-aware* actors.
   */
  def setup[T](factory: ActorContext[T] => Behavior[T]): ActorFactory[T] =
    (info: protocol.SpawnInfo) =>
      scaladsl.Behaviors.setup(context => factory(new ActorContext(context, info)))

  private class RootAdapter[T <: Message](
    implicit interceptMessageClassTag: ClassTag[T]
  ) extends BehaviorInterceptor[T, protocol.GCMessage[T]] {
    def aroundReceive(
      ctx: TypedActorContext[T], 
      msg: T, 
      target: ReceiveTarget[protocol.GCMessage[T]]
    ): Behavior[T] =
      target.apply(ctx, protocol.rootMessage(msg, msg.refs))
  }

  /**
   * Sets up a root actor. Root actors are GC-aware actors that act as "entry
   * points" to a garbage-collected subsystem. Although root actors themselves
   * must be terminated manually, their descendants will all be terminated
   * automatically---as long as those descendants only interact with other
   * GC-aware actors.  
   */
  def setupRoot[T <: Message](
    factory: ActorContext[T] => Behavior[T]
  )(implicit classTag: ClassTag[T]): typed.Behavior[T] = {

    val b: Behavior[T] = scaladsl.Behaviors.setup(context =>
      factory(new ActorContext(context, protocol.rootSpawnInfo()))
    )

    scaladsl.Behaviors.intercept(() => new RootAdapter[T]())(b)
  }

  /**
   * Returns a behavior that releases all its references, ignores all messages,
   * and waits to be terminated by the garbage collector.
   */
  def stopped[T](context: ActorContext[T]): Behavior[T] = scaladsl.Behaviors.stopped
}
