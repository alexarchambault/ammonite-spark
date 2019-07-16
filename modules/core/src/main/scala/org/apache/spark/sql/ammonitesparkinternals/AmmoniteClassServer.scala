package org.apache.spark.sql.ammonitesparkinternals

import java.io.{File, IOException}
import java.net.{InetSocketAddress, ServerSocket, URI}
import java.nio.file.Files

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import ammonite.repl.api.Frame
import org.eclipse.jetty.server.{Request, Server}
import org.eclipse.jetty.server.handler.AbstractHandler

final class AmmoniteClassServer(host: String, bindTo: String, port: Int, frames: => List[Frame]) {

  private val socketAddress = InetSocketAddress.createUnresolved(bindTo, port)

  private val handler = new AbstractHandler {
    def handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse): Unit = {

      val path = target.stripPrefix("/").stripSuffix(".class")
      val item = path.replace('/', '.')

      val fromClassMaps =
        frames
          .toStream
          .flatMap(_.classloader.inMemoryClasses.get(item))
          .headOption

      def fromDirs =
        frames
          .toStream
          .flatMap(_.classpath)
          .filter(_.getProtocol == "file")
          .map(url => new File(url.toURI))
          .filter(f => f.isDirectory)
          .map(path.split('/').foldLeft(_)(new File(_, _)))
          .collectFirst { case f if f.exists() => Files.readAllBytes(f.toPath) }

      fromClassMaps.orElse(fromDirs) match {
        case Some(bytes) =>
          response.setContentType("application/octet-stream")
          response.setStatus(HttpServletResponse.SC_OK)
          baseRequest.setHandled(true)
          response.getOutputStream.write(bytes)

        case None =>
          response.setContentType("text/plain")
          response.setStatus(HttpServletResponse.SC_NOT_FOUND)
          baseRequest.setHandled(true)
          response.getWriter.println("not found")
      }
    }
  }

  private val server = new Server(socketAddress)
  server.setHandler(handler)

  try server.start()
  catch {
    case e: Throwable =>
      throw new Exception(s"Error starting class server at $uri", e)
  }

  def stop(): Unit =
    server.stop()

  def uri = new URI(s"http://$host:$port")

}

object AmmoniteClassServer {

  def randomPort(): Int = {
    val s = new ServerSocket(0)
    val port = s.getLocalPort
    s.close()
    port
  }

  def availablePortFrom(from: Int): Int = {
    var socket: ServerSocket = null
    try {
      socket = new ServerSocket(from)
      from
    }
    catch {
      case _: IOException =>
        availablePortFrom(from + 1)
    }
    finally {
      if (socket != null) socket.close()
    }
  }

}