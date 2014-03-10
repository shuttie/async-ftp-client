package com.github.asyncftpclient

import akka.actor._
import akka.io.{IO, Tcp}
import akka.util.{Timeout, ByteString}
import java.net.InetSocketAddress
import com.github.asyncftpclient.CommandConnection._
import java.io.ByteArrayOutputStream
import com.github.asyncftpclient.CommandConnection.MultiData
import com.github.asyncftpclient.CommandConnection.PlainData
import akka.io.Tcp.PeerClosed

/**
 * Created by shutty on 3/5/14.
 */
object CommandConnection {
  trait Data
  case object Uninitialized extends Data
  case class PlainData(connection:ActorRef) extends Data
  case class MultiData(connection:ActorRef, stream: ByteArrayOutputStream) extends Data
  trait State
  case object Connecting extends State
  case object ReceivePlain extends State
  case object ReceiveMulti extends State
}

case class Request(line:String)
case class Response(code:Int, line:String)
class CommandConnection(val addr:InetSocketAddress) extends FSM[State, Data] with ActorLogging {
  import context.system
  IO(Tcp) ! Tcp.Connect(addr)

  startWith(Connecting, Uninitialized)

  when(Connecting) {
    case Event(Tcp.Connected(remote, local), Uninitialized) => {
      sender ! Tcp.Register(self)
      goto(ReceivePlain) using PlainData(sender)
    }
  }

  when(ReceivePlain) {
    case Event(Tcp.Received(data),ctx:PlainData) => {
      data.utf8String match {
        case Ftp.ResponsePattern(rawCode, rawMessage) => {
          log.info(s"received $rawCode $rawMessage")
          context.parent ! Response(rawCode.toInt, rawMessage)
          stay()
        }
        case Ftp.MultilineResponsePattern(rawCode, rawMessage) => {
          log.info(s"received multiline $rawCode $rawMessage")
          val buffer = new ByteArrayOutputStream()
          val bytes = rawMessage.getBytes
          buffer.write(bytes, 0, bytes.size)
          goto(ReceiveMulti) using MultiData(ctx.connection, buffer)
        }
      }
    }
    case Event(Request(line), ctx:PlainData) => {
      log.info(s"sending $line")
      ctx.connection ! Tcp.Write(ByteString(s"$line\r\n"))
      stay()
    }
    case Event(PeerClosed, ctx:PlainData) => {
      context.parent ! Ftp.Disconnected
      self ! PoisonPill
      stay()
    }
  }

  when(ReceiveMulti) {
    case Event(Tcp.Received(data), ctx:MultiData) => {
      data.utf8String match {
        case Ftp.ResponsePattern(rawCode, rawMessage) => {
          log.info(s"received EOL $rawCode $rawMessage")
          val bytes = rawMessage.getBytes
          ctx.stream.write(bytes, 0, bytes.length)
          context.parent ! Response(rawCode.toInt, new String(ctx.stream.toByteArray))
          goto(ReceivePlain) using PlainData(ctx.connection)
        }
        case rawMessage:String => {
          val bytes = rawMessage.getBytes
          ctx.stream.write(bytes, 0, bytes.length)
          stay()
        }
      }
    }
  }
}
