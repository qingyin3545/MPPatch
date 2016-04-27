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

package moe.lymia.multiverse.ui

import java.io.File
import java.nio.file.{Files, Path}

import moe.lymia.multiverse.core.data._
import moe.lymia.multiverse.core.installer.Installer
import moe.lymia.multiverse.util.res.VersionInfo
import moe.lymia.multiverse.platform.Platform

import scala.collection.generic.GenericClassManifestCompanion

case class CLIArguments(command: (CLIArguments, Platform) => Unit,
                        systemPath: Option[Path], userPath: Option[Path],
                        // common options
                        force: Boolean = false,
                        // patch options
                        debug: Boolean = false)

object CLI {
  private val parser = new scopt.OptionParser[CLIArguments]("mvmm") {
    head("Multiverse Mod Manager", "v"+VersionInfo.versionString)

    help("help") text "Shows usage information."

    opt[File]("system-path").action((f, args) => args.copy(systemPath = Some(f.toPath)))
      .valueName("<directory>").text("Path for the Civilization V installation directory.")
    opt[File]("user-path"  ).action((f, args) => args.copy(userPath   = Some(f.toPath)))
      .valueName("<directory>").text("Path for the Civilization v user directory")

    note("")
    cmd("status").action((_, args) => args.copy(command = cmd_status))
      .text("Displays installation status for mods/the patch.")

    note("")
    cmd("list").action((_, args) => args.copy(command = cmd_list))
      .text("Lists installed DLC and mods available for installation.")

    note("")
    cmd("updatePatch").action((_, args) => args.copy(command = cmd_update))
      .text("Updates the Multiverse Mod Manager patch.")
      .children(
        opt[Unit]('f', "force").action((_, args) => args.copy(force = true)).text("Force update patch."),
        opt[Unit]('d', "debug").action((_, args) => args.copy(debug = true)).text("Install debug version of patch.")
      )
  }

  private def resolvePaths(paths: Seq[Path]) = paths.find(x => Files.exists(x) && Files.isDirectory(x))
  private def loadInstaller(args: CLIArguments, platform: Platform) =
    new Installer(args.systemPath.get, args.userPath.get, platform)

  private def printManifestList[T <: ManifestCommon](entries: ManifestList[T]): Unit = {
    if(entries.manifestList.isEmpty) println("  - <no entries found>")
    else for(entry <- entries.manifestList) {
      println(s"  - ${entry.manifest.uuid}: ${entry.manifest.name}")
    }
  }

  private def cmd_unknown(args: CLIArguments, platform: Platform) = sys.error("No command given!")
  private def cmd_status(args: CLIArguments, platform: Platform) = {
    val installer = loadInstaller(args, platform)
    println("Patch status: "+installer.patchInstaller.checkPatchStatus())
  }
  private def cmd_list(args: CLIArguments, platform: Platform) = {
    val installer = loadInstaller(args, platform)

    println("Available Mods:")
    printManifestList(installer.listMods())

    println("Installed DLC:")
    printManifestList(installer.listDLC())
  }
  private def cmd_update(args: CLIArguments, platform: Platform) = {
    val installer = loadInstaller(args, platform)
    if(!args.force) installer.patchInstaller.safeUpdate(args.debug)
    else sys.error("force update not yet implemented")
  }

  def executeCommand(args: Seq[String]) = {
    val platform = Platform.currentPlatform.getOrElse(sys.error("Unknown platform."))
    val systemPath = resolvePaths(platform.defaultSystemPaths)
    val userPath   = resolvePaths(platform.defaultUserPaths)
    parser.parse(args, CLIArguments(cmd_unknown, systemPath, userPath)) match {
      case Some(command) => command.command(command, platform)
      case None => System.exit(-1)
    }
  }
}