package com.github.asyncftpclient

import java.net.InetSocketAddress
import akka.actor.{ActorRef, PoisonPill, ActorLogging, Actor}
import akka.io.{IO, Tcp}
import java.io.ByteArrayOutputStream

/**
 * Created by shutty on 3/10/14.
 */
case object TransferCompleted
case class TransferBytes(data:Array[Byte])

class TransferConnection(val addr:InetSocketAddress) extends Actor with ActorLogging {
  private val stream = new ByteArrayOutputStream()
  private var connection:ActorRef = null
  import context.system
  IO(Tcp) ! Tcp.Connect(addr)

  def receive = {
    case Tcp.Connected(remote,local) => {
      log.info(s"connected to $remote")
      connection = sender
      sender ! Tcp.Register(self)
    }
    case Tcp.Received(data) => {
      log.info(s"received ${data.length} bytes")
      val asArray = data.toArray
      stream.write(asArray, 0, asArray.length)
    }
    case TransferCompleted => {
      log.info(s"flushing stream")
      context.parent ! TransferBytes(stream.toByteArray)
      connection ! Tcp.Close
      self ! PoisonPill
    }
  }
}
