package recloudstream

import android.annotation.SuppressLint
import android.app.AlertDialog
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioWorkSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import dalvik.system.BaseDexClassLoader
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Guarantee that the APK file is installed without interruptions.
 */
const val ANIYOMI_PLUGIN_SUCCESS_KEY = "Aniyomi_Plugin_Successful_Install"

@CloudstreamPlugin
class AniyomiPlugin : Plugin() {
    private val packageName = "com.lagradost.aniyomicompat"
    private val pluginClassName = "$packageName.AniyomiPlugin"
    private val apkUrl = "https://github.com/CranberrySoup/AniyomiCompat/raw/builds/app-debug.apk"
    private val apkDir = "AniyomiCompat"
    private val apkName = "AniyomiCompat.apk"

    /**
     * From Aliucord: https://github.com/Aliucord/Aliucord/blob/2cf5ce8d74c9da6965f6c57454f9583545e9cd24/Injector/src/main/java/com/aliucord/injector/Injector.kt#L162-L175
     */
    @SuppressLint("DiscouragedPrivateApi") // this private api seems to be stable, thanks to facebook who use it in the facebook app
    @Throws(Throwable::class)
    private fun addDexToClasspath(dex: File, classLoader: ClassLoader) {
        // https://android.googlesource.com/platform/libcore/+/58b4e5dbb06579bec9a8fc892012093b6f4fbe20/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java#59
        val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList")
            .apply { isAccessible = true }
        val pathList = pathListField[classLoader]!!
        val addDexPath =
            pathList.javaClass.getDeclaredMethod("addDexPath", String::class.java, File::class.java)
                .apply { isAccessible = true }
        addDexPath.invoke(pathList, dex.absolutePath, null)
    }

    private fun getInstalledApplication(context: Context): ApplicationInfo? {
        return try {
            val pkgManager = context.packageManager

            pkgManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun getLocalFile(context: Context): File {
        return File(context.filesDir, "$apkDir/$apkName")
    }

    private fun getIsLocallyInstalled(context: Context): Boolean {
        return getLocalFile(context).exists() && getKey<Boolean>(ANIYOMI_PLUGIN_SUCCESS_KEY) == true
    }

    private fun loadAniyomi(context: Context, file: File) {
        normalSafeApiCall {
            println("Loading Aniyomi Compat at: ${file.absolutePath}")
            val classLoader = context.classLoader
            addDexToClasspath(file, classLoader)
            val aniyomiPlugin = classLoader.loadClass(pluginClassName).newInstance() as Plugin
            aniyomiPlugin.load(context)
            println("Successful load of Aniyomi Compat")
        }
    }

    private suspend fun downloadApk(context: Context): Boolean {
        return ioWorkSafe {
            val finalFile = getLocalFile(context)
            val tmpFile = File.createTempFile("AniyomiCompat", null)

            val request = app.get(apkUrl)
            if (!request.isSuccessful) return@ioWorkSafe false

            request.body.byteStream().use {
                tmpFile.writeBytes(it.readBytes())
            }
            setKey(ANIYOMI_PLUGIN_SUCCESS_KEY, false)
            tmpFile.copyTo(finalFile, true)
            setKey(ANIYOMI_PLUGIN_SUCCESS_KEY, true)
            tmpFile.deleteOnExit()
            true
        } == true
    }

    override fun load(context: Context) {
        this.openSettings = {
            AlertDialog.Builder(it)
                .setTitle("Remove internal AniyomiCompat file?")
                .setPositiveButton("Yes", object : OnClickListener {
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        normalSafeApiCall {
                            getLocalFile(context).delete()
                            showToast(
                                context.getActivity(),
                                txt("File deleted"),
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                })
                .setNegativeButton("No", null)
                .show()
        }
        runBlocking {
            val file = getIsLocallyInstalled(context).takeIf { it }?.let {
                getLocalFile(context)
            } ?: getInstalledApplication(context)?.let { File(it.sourceDir) }

            if (file == null) {
                if (downloadApk(context) && getIsLocallyInstalled(context)) {
                    loadAniyomi(context, getLocalFile(context))
                    main {
                        showToast(
                            context.getActivity(),
                            txt("Successfully installed Aniyomi Compat."),
                            Toast.LENGTH_LONG
                        )
                    }
                } else {
                    main {
                        showToast(
                            context.getActivity(),
                            txt("Unable to download Aniyomi Compat APK!"),
                            Toast.LENGTH_LONG
                        )
                    }
                }
            } else {
                loadAniyomi(context, file)
            }
        }
    }
}