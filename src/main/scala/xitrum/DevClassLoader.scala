package xitrum

import java.nio.file.Paths
import scala.util.Properties
import xitrum.routing.SwaggerJson
import xitrum.util.{ClassFileLoader, FileMonitor}

object DevClassLoader {
  val CLASSES_DIR = {
    val withPatch    = Properties.versionNumberString              // Ex: "2.11.1"
    val withoutPatch = withPatch.split('.').take(2).mkString(".")  // Ex: "2.11"
    s"target/scala-$withoutPatch/classes"
  }

  /** Regex of names of the classes that shouldn't be reloaded. */
  var ignorePattern = "".r

  // "public" because this can be used by, for example, Scalate template engine
  // (xitrum-scalate) to pickup the latest class loader in development mode
  var classLoader = newClassFileLoader()

  def onReload(hook: (ClassLoader) => Unit) {
    CLASSES_DIR.synchronized {
      onReloads = onReloads :+ hook
    }
  }

  def removeOnReload(hook: (ClassLoader) => Unit) {
    CLASSES_DIR.synchronized {
      onReloads = onReloads.filterNot(_ == hook)
    }
  }

  def reloadIfNeeded() {
    CLASSES_DIR.synchronized {
      if (needNewClassLoader) {
        needNewClassLoader = false
        classLoader        = newClassFileLoader()

        // Also reload routes
        Config.routes    = Config.loadRoutes(classLoader)
        SwaggerJson.apis = SwaggerJson.loadApis()

        onReloads.foreach { hook => hook(classLoader) }
      }
    }
  }

  def load(className: String) = classLoader.loadClass(className)

  //----------------------------------------------------------------------------

  private var needNewClassLoader = false  // Only reload on new request
  private var lastLogAt          = 0L     // Avoid logging too frequently
  private var onReloads          = Seq.empty[(ClassLoader) => Unit]

  // In development mode, watch the directory "classes". If there's modification,
  // mark that at the next request, a new class loader should be created.
  if (!Config.productionMode && Config.autoreloadInDevMode) monitorClassesDir()

  private def newClassFileLoader() = new ClassFileLoader(CLASSES_DIR) {
    override def ignorePattern = DevClassLoader.ignorePattern
  }

  private def monitorClassesDir() {
    val classesDir = Paths.get(CLASSES_DIR).toAbsolutePath
    FileMonitor.monitorRecursive(FileMonitor.MODIFY, classesDir, { path =>
      CLASSES_DIR.synchronized {
        // Do this not only for .class files, because file change events may
        // sometimes be skipped!
        needNewClassLoader = true

        // Avoid logging too frequently
        val now = System.currentTimeMillis()
        val dt  = now - lastLogAt
        if (dt > 4000) {
          Log.info(s"$CLASSES_DIR changed; reload classes and routes on next request")
          lastLogAt = now
        }

        // https://github.com/lloydmeta/schwatcher
        // Callbacks that are registered with recursive=true are not
        // persistently-recursive. That is, they do not propagate to new files
        // or folders created/deleted after registration. Currently, the plan is
        // to have developers handle this themselves in the callback functions.
        FileMonitor.unmonitorRecursive(FileMonitor.MODIFY, classesDir)

        if (Config.autoreloadInDevMode) monitorClassesDir()
      }
    })
  }
}