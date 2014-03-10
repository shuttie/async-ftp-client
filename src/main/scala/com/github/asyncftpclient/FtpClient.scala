package com.github.asyncftpclient

import akka.actor.{Props, ActorRef, FSM, Actor}
import akka.io.{Tcp, IO}
import java.net.InetSocketAddress
import akka.io.Tcp.{Register, Connect}
import akka.util.{Timeout, ByteString}
import com.github.asyncftpclient.FtpClient._
import akka.pattern.ask
import scala.concurrent.duration._

/**
 * Created by shutty on 3/1/14.
 */
object FtpClient {
  trait Data
  case object Uninitialized extends Data
  case class ConnectData(connection:ActorRef) extends Data
  case class AuthData(connection:ActorRef, login:String, password:String) extends Data
  case class ListData(connection:ActorRef, dir:String, dataAddr: Option[InetSocketAddress] = None, dataConn: Option[ActorRef] = None) extends Data
  case class TransferData(prevState:State, prevData:Data, dataConn:ActorRef) extends Data
  trait State
  case object Idle extends State
  case object Connected extends State
  case object Active extends State
  case object Listing extends State
  case object Transferring extends State
  case object Disconnecting extends State
}

class FtpClient  extends FSM[FtpClient.State,FtpClient.Data] {
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(Ftp.Connect(host,port), Uninitialized) => {
      context.actorOf(Props(classOf[CommandConnection], new InetSocketAddress(host,port)), name = "command_connection")
      stay()
    }
    case Event(Response(code, message), Uninitialized) if code == 220 => {
      log.info(s"Greet: $code $message")
      context.parent ! Ftp.Connected
      goto(Connected) using ConnectData(sender)
    }
  }

  when(Connected) {
    case Event(Ftp.Auth(login, password), ctx: ConnectData) => {
      ctx.connection ! Request(s"USER $login")
      stay() using AuthData(ctx.connection, login, password)
    }
    case Event(Response(code, message), ctx:AuthData) if code == 331 => {
      ctx.connection ! Request(s"PASS ${ctx.password}")
      stay()
    }
    case Event(Response(code, message), ctx:AuthData) if code == 230 => {
      context.parent ! Ftp.AuthSuccess
      goto(Active) using ConnectData(ctx.connection)
    }
  }

  when(Active) {
    case Event(Ftp.Dir(dir, mode), ctx: ConnectData) => {
      self ! Ftp.Dir(dir, mode)
      goto(Listing) using ListData(ctx.connection, dir, None)
    }
    case Event(Ftp.Disconnect, ctx: ConnectData) => {
      self ! Ftp.Disconnect
      goto(Disconnecting) using ctx
    }
  }

  when(Disconnecting) {
    case Event(Ftp.Disconnect, ctx:ConnectData) => {
      ctx.connection ! Request("QUIT")
      stay()
    }
    case Event(Response(code, message), ctx:ConnectData) => {
      stay()
    }
    case Event(Ftp.Disconnected, ctx:ConnectData) => {
      context.parent ! Ftp.Disconnected
      goto(Idle) using Uninitialized
    }
  }

  when(Listing) {
    case Event(Ftp.Dir(dir, mode), ctx: ListData) => {
      ctx.connection ! Request("TYPE I")
      stay()
    }
    case Event(Response(code,message), ctx: ListData) if code == 200 => {
      ctx.connection ! Request(s"CWD ${ctx.dir}")
      stay()
    }
    case Event(Response(code,message), ctx: ListData) if code == 250 => {
      ctx.connection ! Request("PASV")
      stay()
    }
    case Event(Response(code,message), ctx: ListData) if code == 227 => {
      val addrPattern = ".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*".r
      message match {
        case addrPattern(host1, host2, host3, host4, port1, port2) => {
          val host = s"$host1.$host2.$host3.$host4"
          val port = (port1.toInt << 8) + port2.toInt
          val addr = new InetSocketAddress(host, port)
          ctx.connection ! Request("LIST")
          val dataConnection = context.actorOf(Props(classOf[TransferConnection], addr), name = "data_connection")
          stay() using ListData(ctx.connection, ctx.dir, Some(addr), Some(dataConnection))
        }
      }
    }
    case Event(Response(code,message), ctx:ListData) if code == 150 => {
      stay()
    }
    case Event(Response(code, message), ctx:ListData) if code == 226 => {
      ctx.dataConn.map(_ ! TransferCompleted)
      stay()
    }
    case Event(TransferBytes(data), ctx:ListData) => {
      val lines = new String(data).split("\n")
      val files = lines.map {
        case Ftp.ListPattern(mode, inodes, user, group, size, month, day, timeOrYear, name) =>
          Ftp.FileInfo(name, size.toLong, user, group, mode)
      }
      context.parent ! Ftp.DirListing(files.toList)
      goto(Active) using ConnectData(ctx.connection)
    }

  }

}
