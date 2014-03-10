package com.github.asyncftpclient.example

import akka.actor.{PoisonPill, Actor, Props, ActorSystem}
import com.github.asyncftpclient.{Ftp, FtpClient}

/**
 * Created by shutty on 3/1/14.
 */
case object Start
class ListerActor(host:String, user:String, password:String, dir:String) extends Actor {
  val client = context.actorOf(Props[FtpClient], name = "client")
  def receive = {
    case Start => client ! {
      System.out.println(s"Connecting to $host")
      Ftp.Connect(host)
    }
    case Ftp.Connected => client ! {
      System.out.println(s"Connected, authenticating via $user:$password")
      Ftp.Auth(user, password)
    }
    case Ftp.AuthSuccess => {
      System.out.println(s"Authenticated, listing $dir")
      client ! Ftp.Dir(dir)
    }
    case Ftp.DirListing(files) => {
      System.out.println(s"Received listing, printing result and disconnecting")
      files.foreach(f => System.out.println(s"file: ${f.name}"))
      client ! Ftp.Disconnect
    }
    case Ftp.Disconnected => {
      System.out.println(s"Disconnected, quitting")
      self ! PoisonPill
    }
  }
}


object Lister {
  def main(args:Array[String]) = {
    val system = ActorSystem()
    val client = system.actorOf(Props(classOf[ListerActor], "ftp.ncdc.noaa.gov", "anonymous", "test@evil.com", "/"))
    client ! Start
    system.awaitTermination()
  }
}
