package com.github.asyncftpclient

import java.nio.ByteBuffer

/**
 * Created by shutty on 3/1/14.
 */
object Ftp {
  trait ConnectionMode
  case object ActiveMode extends ConnectionMode
  case object PassiveMode extends ConnectionMode

  case class Connect(host:String, port:Int = 21)
  case object Connected
  case object ConnectionFailed

  case class Auth(username:String, password:String)
  case object AuthSuccess
  case object AuthFail

  case class Download(path:String, mode:ConnectionMode = PassiveMode)
  case class Downloaded(buffer:ByteBuffer)
  case class DownloadFail(reason:Throwable)

  case object Disconnect
  case object Disconnected

  case class Dir(path:String,mode:ConnectionMode = PassiveMode)
  case class DirListing(files:List[String])
  case object DirFail

  val ResponsePattern = "(\\d+) (.*)\r\n".r
}
