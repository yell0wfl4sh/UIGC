package edu.illinois.osl.akka.gc.streams

import akka.actor.{Address, ExtendedActorSystem}
import akka.remote.artery.OutboundEnvelope
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import edu.illinois.osl.akka.gc.protocol

class Egress(system: ExtendedActorSystem, adjacentSystem: Address)
  extends GraphStage[FlowShape[OutboundEnvelope, OutboundEnvelope]] {

  val in: Inlet[OutboundEnvelope] = Inlet("Artery.Ingress.in")
  val out: Outlet[OutboundEnvelope] = Outlet("Artery.Ingress.out")
  val shape: FlowShape[OutboundEnvelope, OutboundEnvelope] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      val state: protocol.EgressState = protocol.spawnEgress(system, adjacentSystem)
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val msg = grab(in)
          protocol.onEgressEnvelope(state, msg, m => push(out, m))
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}

