/*
 * Copyright (c) 2015-2016 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package moe.lymia.mppatch.util

import java.io.{IOException, InputStream}
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import scala.annotation.tailrec
import scala.io.Codec
import scala.xml.{Node, PrettyPrinter, XML}

class FileLock(lockFile: Path) {
  private val channel  = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
  private val lock     = Option(channel.tryLock)
  private var released = false

  val acquired = lock.isDefined
  def release() = if(!released) {
    lock.foreach(_.release)
    channel.close()
    released = true
  }
  if(lock.isEmpty) release()
}

object IOUtils {
  private val resPath = "/moe/lymia/mppatch/"

  def getResource(s: String) = getClass.getResourceAsStream(resPath + s)
  def resourceExists(s: String) = getResource(s) != null
  def loadFromStream(s: InputStream) = io.Source.fromInputStream(s)(Codec.UTF8).mkString
  def loadBinaryFromStream(s: InputStream) = Stream.continually(s.read).takeWhile(_ != -1).map(_.toByte).toArray
  def loadResource(s: String) = loadFromStream(getResource(s))
  def loadBinaryResource(s: String) = loadBinaryFromStream(getResource(s))

  def writeFile(path: Path, data: Array[Byte]): Unit = {
    if(path.getParent != null) Files.createDirectories(path.getParent)
    Files.write(path, data)
  }
  def writeFile(path: Path, data: String): Unit = writeFile(path, data.getBytes(StandardCharsets.UTF_8))

  def readFileAsBytes(path: Path) = Files.readAllBytes(path)
  def readFileAsString(path: Path) = new String(readFileAsBytes(path), StandardCharsets.UTF_8)

  def listFiles(path: Path) = Files.list(path).toArray.map(_.asInstanceOf[Path])
  def listFileNames(path: Path) = listFiles(path).map(_.getFileName.toString)

  val xmlWriter = new PrettyPrinter(Int.MaxValue, 4)
  def writeXML(path: Path, xml: Node, prettyPrint: Boolean = true) = {
    val xmlString = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
      "<!-- Generated by MPPatch Installer -->\n"+
      (if(prettyPrint) xmlWriter.format(xml) else xml.toString)+"\n"
    writeFile(path, xmlString)
  }
  def readXML(path: Path) = XML.load(Files.newInputStream(path))

  @tailrec def isSubdirectory(parent: Path, child: Path): Boolean =
    if(parent == null) false
    else if(Files.isSameFile(parent, child)) true
    else isSubdirectory(parent.getParent, child)

  def deleteDirectory(path: Path) =
    if(Files.exists(path))
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def visitFileFailed(file: Path, exc: IOException) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException) = if(exc == null) {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        } else throw exc
      })

  def lock(lockFile: Path) = {
    val lock = new FileLock(lockFile)
    if(!lock.acquired) None else Some(lock)
  }
  def withLock[T](lockFile: Path, error: => T = sys.error("Could not acquire lock."))(f: => T) =
    lock(lockFile) match {
      case None => error
      case Some(lock) => try {
        f
      } finally {
        lock.release()
      }
    }
}