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
  trait State
  case object Idle extends State
  case object Connected extends State
  case object Active extends State
}

class FtpClient  extends FSM[FtpClient.State,FtpClient.Data] {
  import context.system
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(Ftp.Connect(host,port), Uninitialized) => {
      IO(Tcp) ! Tcp.Connect(new InetSocketAddress(host,port))
      stay()
    }
    case Event(Tcp.Connected(remote,local), Uninitialized) => {
      val connection = context.actorOf(Props(classOf[CommandConnection], sender))
      sender ! Tcp.Register(connection)
      context.parent ! Ftp.Connected
      goto(Connected) using ConnectData(connection)
    }
  }

  when(Connected) {
    case Event(Ftp.Auth(login, password), ctx: ConnectData) => {
      implicit val timeout = Timeout(10.seconds)
      implicit val executor = context.dispatcher
      val result = for {
        loginResponse <- ctx.connection.ask(Request(s"USER $login"))
        passwordResponse <- ctx.connection.ask(Request(s"PASS $password"))
      } yield (loginResponse, passwordResponse)
      stay()
    }
  }

  when(Active) {
    case Event(Ftp.Dir(dir, mode), ctx: ConnectData) => {
      stay()
    }
  }
}


/*object FtpClient {
  trait Data
  case object Uninitialized extends Data
  case class ConnectData(requester:ActorRef,
                         connection:ActorRef,
                         host:String,
                         port:Int) extends Data
  case class ActiveData(requester:ActorRef,
                        connection:ActorRef,
                        host:String,
                        port:Int,
                        username:String,
                        password:String) extends Data
  case class DirData(requester:ActorRef,
                     connection:ActorRef,
                     host:String,
                     port:Int,
                     username:String,
                     password:String,
                     dir: String) extends Data
  case class TransferData(requester:ActorRef,
                          connection:ActorRef,
                          host:String,
                          port:Int,
                          username:String,
                          password:String,
                          mode: Ftp.ConnectionMode,
                          transferHost:String,
                          transferPort:Int) extends Data
  trait State
  case object Idle extends State
  case object Connecting extends State
  case object SendingUsername extends State
  case object SendingPassword extends State
  case object Active extends State
  case object SettingMode extends State
  case object Transferring extends State

  class NotSupportedException(msg:String) extends Exception(msg)
}

class FtpClient extends FSM[FtpClient.State,FtpClient.Data] {
  import context.system
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(Ftp.Connect(host,port), FtpClient.Uninitialized) => {
      IO(Tcp) ! Tcp.Connect(new InetSocketAddress(host,port))
      stay() using ConnectData(sender, null, host, port)
    }
    case Event(Tcp.Connected(remote,local), ctx: ConnectData) => {
      sender ! Register(self)
      goto(Connecting) using ConnectData(ctx.requester, sender, ctx.host, ctx.port)
    }
    case Event(Tcp.CommandFailed(_:Connect), ctx: ConnectData) => {
      // connection failed
      ctx.requester ! Ftp.ConnectionFailed
      goto(Idle) using Uninitialized
    }
  }

  when(Connecting) {
    case Event(Tcp.Received(data), ctx: ConnectData) => {
      // received greeting, connection looks OK
      val line = data.utf8String
      data.utf8String match {
        case Ftp.ResponsePattern(code, message) => {
          ctx.requester ! Ftp.Connected
          stay()
        }
        case _ => {
          ctx.requester ! Ftp.ConnectionFailed
          goto(Idle) using Uninitialized
        }
      }
    }
    case Event(Tcp.PeerClosed, ctx:ConnectData) => {
      // server closed out socket, that makes us sad
      ctx.requester ! Ftp.ConnectionFailed
      goto(Idle) using Uninitialized
    }
    case Event(msg: Ftp.Auth, ctx: ConnectData) => {
      // got auth request
      self forward msg
      goto(SendingUsername) using ActiveData(sender, ctx.connection, ctx.host, ctx.port, msg.username, msg.password)
    }
  }

  when(SendingUsername) {
    case Event(Ftp.Auth(username, password), ctx: ActiveData) => {
      ctx.connection ! Tcp.Write(ByteString(s"USER $username\n"))
      stay()
    }
    case Event(Tcp.Received(data), ctx: ActiveData) => {
      data.utf8String match {
        case Ftp.ResponsePattern(code, message) if code == "331" => {
          ctx.connection ! Tcp.Write(ByteString(s"PASS ${ctx.password}\n"))
          goto(SendingPassword) using ctx
        }
        case _ => {
          ctx.requester ! Ftp.AuthFail
          stay()
        }
      }
    }
  }

  when(SendingPassword) {
    case Event(Tcp.Received(data), ctx: ActiveData) => {
      data.utf8String match {
        case Ftp.ResponsePattern(code, message) if code == "230" => {
          // login successful
          ctx.requester ! Ftp.AuthSuccess
          goto(Active) using ctx
        }
      }
    }
  }

  when (Active) {
    case Event(Ftp.Dir(dir:String, mode: Ftp.ConnectionMode), ctx: ActiveData) => {
      mode match {
        case Ftp.Active => throw new NotSupportedException("FTP Active mode not yet supported")
        case Ftp.Passive => ctx.connection ! Tcp.Write(ByteString("PASV\n"))
      }
      stay()
    }
  }

}
*/