package com.github.asyncftpclient.example

import akka.actor.{PoisonPill, Actor, Props, ActorSystem}
import com.github.asyncftpclient.{TransferBytes, Ftp, FtpClient}

/**
 * Created by shutty on 3/10/14.
 */
class DownloadActor (host:String, user:String, password:String, path:String) extends Actor {
  val client = context.actorOf(Props[FtpClient], name = "client")
  def receive = {
    case "start" => client ! {
      System.out.println(s"Connecting to $host")
      Ftp.Connect(host)
    }
    case Ftp.Connected => client ! {
      System.out.println(s"Connected, authenticating via $user:$password")
      Ftp.Auth(user, password)
    }
    case Ftp.AuthSuccess => {
      System.out.println(s"Authenticated, downloading $path")
      client ! Ftp.Download(path)
    }
    case TransferBytes(bytes) => {
      System.out.println(s"Received data, printing result and disconnecting")
      System.out.println(s"${new String(bytes)}")
      client ! Ftp.Disconnect
    }
    case Ftp.Disconnected => {
      System.out.println(s"Disconnected, quitting")
      self ! PoisonPill
    }
  }

}

object Download {
  def main(args:Array[String]) = {
    val system = ActorSystem()
    val client = system.actorOf(Props(classOf[DownloadActor], "ftp.ncdc.noaa.gov", "anonymous", "test@evil.com", "/welcome.msg"))
    client ! "start"
    system.awaitTermination()
  }
}