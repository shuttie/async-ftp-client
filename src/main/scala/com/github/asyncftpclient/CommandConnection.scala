package com.github.asyncftpclient

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.io.Tcp
import akka.util.{Timeout, ByteString}
import akka.pattern.ask
import scala.concurrent.duration._
/**
 * Created by shutty on 3/5/14.
 */
case class Request(line:String)
case class Response(code:Int, line:String)
class CommandConnection(val connection: ActorRef) extends Actor with ActorLogging {
  def receive = {
    case Request(line) => {
      log.info(s"sending $line")
      implicit val executor = context.dispatcher
      implicit val timeout = Timeout(10.seconds)
      connection.ask(Tcp.Write(ByteString(s"$line\n"))).onSuccess {
        case Tcp.Received(data) => data.utf8String match {
          case Ftp.ResponsePattern(rawCode, rawMessage) => {
            log.info(s"received $rawCode $rawMessage")
            sender ! Response(rawCode.toInt, rawMessage)
          }
        }
      }
    }
    case Tcp.Received(data) => data.utf8String match {
      case Ftp.ResponsePattern(rawCode, rawMessage) => {
        log.info(s"received $rawCode $rawMessage")
        sender ! Response(rawCode.toInt, rawMessage)
      }
    }

  }
}
