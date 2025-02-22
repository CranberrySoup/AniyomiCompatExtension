package recloudstream

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.AniyomiExtension
import com.example.BottomFragment
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.Coroutines.ioWorkSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.txt
import dalvik.system.BaseDexClassLoader
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Guarantee that the APK file is installed without interruptions.
 */
const val ANIYOMI_PLUGIN_SUCCESS_KEY = "Aniyomi_Plugin_Successful_Install"

enum class EpisodeSortMethods(val num: Int) {
    None(0),
    Ascending(1),
    Reverse(2),
}

@CloudstreamPlugin
class AniyomiPlugin : Plugin() {
    companion object {
        var currentLoadedFile: File? = null
        private var cachedApkMetadata: OutputMetadata? = null
        private const val packageName = "com.lagradost.aniyomicompat"
        private const val pluginClassName = "$packageName.AniyomiPlugin"
        private const val apkUrl =
            "https://github.com/CranberrySoup/AniyomiCompat/raw/builds/app-debug.apk"
        private const val apkMetadataUrl =
            "https://raw.githubusercontent.com/CranberrySoup/AniyomiCompat/builds/output-metadata.json"
        private const val apkDir = "AniyomiCompat"
        private const val apkName = "AniyomiCompat.apk"

        private const val sortingMethodKey = "ANIYOMI_SORTING_METHOD"
        var aniyomiSortingMethod: Int
            get() = getKey(sortingMethodKey) ?: EpisodeSortMethods.Ascending.num
            set(value) {
                setKey(sortingMethodKey, value)
            }

        data class Element(
            val versionCode: Long?,
            val versionName: String?,
            val outputFile: String?
        )

        data class OutputMetadata(
            val elements: List<Element>?
        )

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
                pathList.javaClass.getDeclaredMethod(
                    "addDexPath",
                    String::class.java,
                    File::class.java
                )
                    .apply { isAccessible = true }
            addDexPath.invoke(pathList, dex.absolutePath, null)
        }

        suspend fun getApkMetadata(): OutputMetadata? {
            return cachedApkMetadata ?: app.get(apkMetadataUrl)
                .parsedSafe<OutputMetadata>()?.also {
                    cachedApkMetadata = it
                }
        }

        fun listExtensions(context: Context): List<AniyomiExtension> {
            val extensionFeature = "tachiyomi.animeextension"
            val pkgManager = context.packageManager

            val flags = PackageManager.GET_CONFIGURATIONS

            @Suppress("DEPRECATION")
            val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pkgManager.getInstalledPackages(flags)
            }

            return installedPkgs.filter { pkg ->
                pkg.reqFeatures.orEmpty().any { it.name == extensionFeature }
            }.mapNotNull {
                val appInfo = it.applicationInfo ?: return@mapNotNull null
                val name =
                    pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Aniyomi: ")
                val icon = pkgManager.getApplicationIcon(appInfo)
                AniyomiExtension(it.packageName, name, icon)
            }
        }

        fun getInstalledApplication(context: Context): ApplicationInfo? {
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

        fun getLocalFile(context: Context): File {
            return File(context.filesDir, "$apkDir/$apkName")
        }

        fun getIsLocallyInstalled(context: Context): Boolean {
            return getLocalFile(context).exists() && getKey<Boolean>(ANIYOMI_PLUGIN_SUCCESS_KEY) == true
        }

        fun loadAniyomi(context: Context, file: File) {
            normalSafeApiCall {
                println("Loading Aniyomi Compat at: ${file.absolutePath}")
                file.setReadOnly()
                val classLoader = context.classLoader
                addDexToClasspath(file, classLoader)
                val aniyomiPlugin = classLoader.loadClass(pluginClassName).newInstance() as Plugin
                aniyomiPlugin.load(context)
                println("Successful load of Aniyomi Compat")
                currentLoadedFile = file
            }
        }

        suspend fun downloadApk(context: Context): Boolean {
            return ioWorkSafe {
                val finalFile = getLocalFile(context)
                normalSafeApiCall {
                    finalFile.setWritable(true)
                }
                val tmpFile = File.createTempFile("AniyomiCompat", null)

                val request = app.get(apkUrl)
                if (!request.isSuccessful) return@ioWorkSafe false

                request.body.byteStream().use {
                    tmpFile.writeBytes(it.readBytes())
                }
                setKey(ANIYOMI_PLUGIN_SUCCESS_KEY, false)
                tmpFile.copyTo(finalFile, true)
                setKey(ANIYOMI_PLUGIN_SUCCESS_KEY, true)
                tmpFile.delete()
                true
            } == true
        }

        fun installApk(context: Context): Boolean {
            if (!getIsLocallyInstalled(context)) return false
            val file = getLocalFile(context)
            openApk(context, Uri.fromFile(file))
            return true
        }

        private fun openApk(context: Context, uri: Uri) {
            try {
                uri.path?.let {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider",
                        File(it)
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        data = contentUri
                    }
                    context.startActivity(installIntent)
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    override fun load(context: Context) {
        this.openSettings = openSettings@{
            val manager = (context.getActivity() as? AppCompatActivity)?.supportFragmentManager
                ?: return@openSettings
            BottomFragment(this).show(manager, "AniyomiCompat")
        }
        runBlocking {
            // Prefer app to make debugging easier
            val file = getInstalledApplication(context)?.let { File(it.sourceDir) }
                ?: getIsLocallyInstalled(context).takeIf { it }?.let {
                    getLocalFile(context)
                }

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