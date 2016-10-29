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

package moe.lymia.mppatch.ui

import java.awt.{Dimension, Font, GridBagConstraints, GridBagLayout}
import java.nio.file.{Files, Path}
import java.util.Locale
import javax.swing._

import moe.lymia.mppatch.core._
import moe.lymia.mppatch.util.{IOUtils, VersionInfo}

class MainFrame(val locale: Locale) extends FrameBase[JFrame] {
  var installButton  : ActionButton = _
  var uninstallButton: ActionButton = _
  var installPath    : JTextField   = _
  var currentVersion : JTextField   = _
  var targetVesrion  : JTextField   = _
  var currentStatus  : JTextField   = _

  val platform  = Platform.currentPlatform.getOrElse(error(i18n("error.unknownplatform")))
  def resolvePaths(paths: Seq[Path]) = paths.find(x => Files.exists(x) && Files.isDirectory(x))
  val installer = resolvePaths(platform.defaultSystemPaths) match {
    case Some(x) =>
      val patchData = PatchPackageLoader(s"patch_${VersionInfo.fromJar.versionString}.mppak")
      new PatchInstaller(x, new PatchLoader(patchData), platform)
    case None    =>
      // TODO: Allow user selection
      error("system path could not be found")
  }

  def actionUpdate(): Unit = {
    installer.safeUpdate()
  }
  def actionUninstall(): Unit = {
    installer.safeUninstall()
  }
  def actionCleanup(): Unit = {
    installer.cleanupPatch()
  }

  class ActionButton() extends JButton {
    var action: () => Unit = () => error("no action registered")
    var text  : String     = "<no action>"

    setAction(MainFrame.this.action { e =>
      try {
        action()
        JOptionPane.showMessageDialog(frame, i18n(text+".completed"))
      } catch {
        case e: Exception => dumpException("error.commandfailed", e, i18n(text+".continuous"))
      }
      MainFrame.this.update()
    })

    def setActionText(name: String): Unit = {
      text = name
      setText(i18n(name))
      setToolTipText(i18n(name+".tooltip"))
    }
    def setAction(name: String, action: () => Unit): Unit = {
      this.action = action
      setActionText(name)
    }
  }

  val symbolFont = Font.createFont(Font.TRUETYPE_FONT, IOUtils.getResource("text/Symbola_hint_subset.ttf"))
  def symbolButton(button: JButton) = {
    val size = button.getMinimumSize
    if(size.getWidth < size.getHeight) {
      button.setMinimumSize  (new Dimension(size.getHeight.toInt, size.getHeight.toInt))
      button.setPreferredSize(new Dimension(size.getHeight.toInt, size.getHeight.toInt))
    }

    val font = button.getFont
    button.setFont(symbolFont.deriveFont(Font.PLAIN, font.getSize))

    button
  }

  def buildForm() {
    frame = new JFrame()
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

    frame.setTitle(i18n("title"))
    frame.setLayout(new GridBagLayout())

    // Status seciton
    val statusPane = new JPanel()
    statusPane.setLayout(new GridBagLayout())

    def gridLabel(row: Int, labelStr: String) = {
      val label = new JLabel()
      label.setText(i18n(s"label.$labelStr"))
      statusPane.add(label, constraints(gridy = row, ipadx = 3, ipady = 3, anchor = GridBagConstraints.LINE_START))
    }
    def gridTextField(row: Int, width: Int = 2) = {
      val textField = new JTextField()
      textField.setEditable(false)
      textField.setPreferredSize(new Dimension(450, textField.getPreferredSize.getHeight.toInt))
      statusPane.add(textField, constraints(gridx = 1, gridy = row, gridwidth = width, weightx = 1,
                                            ipadx = 3, ipady = 3, fill = GridBagConstraints.BOTH))
      textField
    }

    gridLabel(0, "path")
    installPath = gridTextField(0, 1)

    val browseButton = new JButton()
    browseButton.setAction(action { e => update() })
    browseButton.setText(i18n("icon.browse"))
    browseButton.setToolTipText(i18n("tooltip.browse"))
    symbolButton(browseButton)
    statusPane.add(browseButton, constraints(gridx = 2, gridy = 0, fill = GridBagConstraints.BOTH))

    gridLabel(1, "installed")
    currentVersion = gridTextField(1)

    gridLabel(2, "target")
    targetVesrion = gridTextField(2)

    gridLabel(3, "status")
    currentStatus = gridTextField(3)

    frame.add(statusPane, constraints(gridwidth = 3, fill = GridBagConstraints.BOTH))

    // Button section
    installButton = new ActionButton()
    frame.add(installButton  , constraints(gridx = 0, gridy = 1, weightx = 0.5,
                                           fill = GridBagConstraints.BOTH))

    uninstallButton = new ActionButton()
    frame.add(uninstallButton, constraints(gridx = 1, gridy = 1, weightx = 0.5,
                                           fill = GridBagConstraints.BOTH))

    val settingsButton = new JButton()
    settingsButton.setAction(action { e => update() })
    settingsButton.setText(i18n("icon.settings"))
    settingsButton.setToolTipText(i18n("tooltip.settings"))
    symbolButton(settingsButton)
    frame.add(settingsButton, constraints(gridx = 2, gridy = 1,
                                          fill = GridBagConstraints.BOTH))
  }

  def setStatus(text: String) = currentStatus.setText(i18n(text))
  override def update() = {
    currentVersion.setText(installer.loadPatchState().fold(i18n("status.noversion"))(_.installedVersion))
    targetVesrion.setText(installer.loader.data.patchVersion)

    installButton.setEnabled(false)
    installButton.setAction("action.install", actionUpdate)

    uninstallButton.setEnabled(false)
    uninstallButton.setAction("action.uninstall", actionUninstall)

    installer.checkPatchStatus() match {
      case PatchStatus.Installed =>
        setStatus("status.ready")
        installButton.setActionText("action.reinstall")
        installButton.setEnabled(true)
        uninstallButton.setEnabled(true)
      case PatchStatus.NeedsUpdate =>
        setStatus("status.needsupdate")
        installButton.setActionText("action.update")
        installButton.setEnabled(true)
        uninstallButton.setEnabled(true)
      case PatchStatus.NotInstalled(true) =>
        setStatus("status.notinstalled")
        installButton.setEnabled(true)
      case PatchStatus.NotInstalled(false) =>
        setStatus("status.unknownversion")
      case x => setStatus("unknown state: "+x)
    }
  }

  // show loop
  def show(): Unit = try {
    val lockFile = installer.resolve(".mppatch_installer_gui_lock")
    var continueLoop = true
    def lockLoop() =
      IOUtils.withLock(lockFile, error = {
        val doOverride = JOptionPane.showConfirmDialog(frame, i18n("error.retrylock"),
                                                       i18n("title"), JOptionPane.YES_NO_OPTION)
        if(doOverride == JOptionPane.OK_OPTION) continueLoop = true
      }) {
        showForm()
        while(frame.isVisible) try {
          Thread.sleep(100)
        } catch {
          case _: InterruptedException => // ignored
        }
      }
    while(continueLoop) {
      continueLoop = false
      lockLoop()
    }
  } finally {
    if(frame.isDisplayable) frame.dispose()
  }
}