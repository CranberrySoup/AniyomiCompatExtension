package com.example

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import recloudstream.AniyomiPlugin
import recloudstream.EpisodeSortMethods

class BottomFragment(private val plugin: Plugin) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = plugin.resources!!.getIdentifier("bottom_sheet_layout", "layout", "com.example")
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", "com.example")
        return this.findViewById(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", "com.example")
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cloudStreamVersion = view.findView<TextView>("cloudstream_version")
        val apkVersion = view.findView<TextView>("apk_version")
        val apkVersionHolder = view.findView<View>("apk_version_holder")
        val apkOutdated = view.findView<View>("apk_outdated")
        val currentlyUsing = view.findView<TextView>("currently_using")
        val internallyInstalled = view.findView<TextView>("internally_installed")
        val numberOfExtensions = view.findView<TextView>("number_of_extensions")
        val forceInstallButton = view.findView<ImageView>("force_install_button")
        val deleteLocalRoot = view.findView<View>("delete_local_root")
        val deleteLocalButton = view.findView<ImageView>("delete_local_button")
        val externalApkButton = view.findView<ImageView>("external_apk_button")
        val externalApkRoot = view.findView<View>("external_apk_root")
//        val goToExtensionGithubButton = view.findView<ImageView>("go_to_apk_github")

        val sortingGroup = view.findView<RadioGroup>("sorting_group")
        val radioNone = view.findView<RadioButton>("radio_button_none")
        val radioReverse = view.findView<RadioButton>("radio_button_reverse")
        val radioAscending = view.findView<RadioButton>("radio_button_ascending")
        val episodeSortNotice = view.findView<TextView>("episode_sort_notice")

        val extensionSettingsButton = view.findView<ImageView>("extension_settings")

        runCatching {
            val context = view.context
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            cloudStreamVersion.text = packageInfo.versionName + " • " + versionCode
        }

        apkOutdated.isVisible = false
        try {
            val cls =
                Class.forName("com.lagradost.aniyomicompat.BuildConfig")
            val instance = cls.newInstance()
            val code = cls.getDeclaredField("VERSION_CODE").getInt(instance)
            val name = cls.getDeclaredField("VERSION_NAME").get(instance) as? String
            apkVersion.text = "$name • $code"
            apkVersionHolder.isVisible = true

            ioSafe {
                val element =
                    AniyomiPlugin.getApkMetadata()?.elements?.firstOrNull { it.versionCode != null }
                        ?: return@ioSafe
                val onlineVersionCode = element.versionCode ?: return@ioSafe
                main {
                    apkOutdated.isVisible = onlineVersionCode > code
                    episodeSortNotice.isVisible = code < 6
                }
            }

            extensionSettingsButton.setOnClickListener(object : OnClickListener {
                override fun onClick(p0: View?) {
                    if (code < 7) {
                        Toast.makeText(
                            context,
                            "Update Aniyomi Compat to access settings!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val manager = (context?.getActivity() as? AppCompatActivity)?.supportFragmentManager ?: return
                        ExtensionFragment(plugin).show(manager, "AniyomiExtensionFragment")
                    }
                }
            })

        } catch (_: Throwable) {
            apkVersionHolder.isVisible = false
        }

        currentlyUsing.text =
            (AniyomiPlugin.currentLoadedFile?.absolutePath ?: "None").toString()
        internallyInstalled.text =
            AniyomiPlugin.getIsLocallyInstalled(view.context).toString()
        numberOfExtensions.text =
            AniyomiPlugin.listExtensions(view.context).size.toString()

        val textColor = currentlyUsing.currentTextColor

        extensionSettingsButton.imageTintList = ColorStateList.valueOf(textColor)
        extensionSettingsButton.setImageDrawable(getDrawable("baseline_settings_24"))

        forceInstallButton.imageTintList = ColorStateList.valueOf(textColor)
        forceInstallButton.setImageDrawable(getDrawable("baseline_get_app_24"))
        forceInstallButton.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {
                showToast(view.context.getActivity(), "Downloading APK", Toast.LENGTH_LONG)
                ioSafe {
                    AniyomiPlugin.downloadApk(view.context)
                    this@BottomFragment.dismiss()
                }
            }
        })

        externalApkRoot.visibility =
            if (AniyomiPlugin.getIsLocallyInstalled(view.context)) VISIBLE else INVISIBLE
        externalApkButton.imageTintList = ColorStateList.valueOf(textColor)
        externalApkButton.setImageDrawable(getDrawable("baseline_install_mobile_24"))
        externalApkButton.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {
                showToast(view.context.getActivity(), "Installing APK", Toast.LENGTH_LONG)
                AniyomiPlugin.installApk(view.context)
                this@BottomFragment.dismiss()
            }
        })


        deleteLocalRoot.visibility =
            if (AniyomiPlugin.getLocalFile(view.context).exists()) VISIBLE else INVISIBLE

        deleteLocalButton.imageTintList = ColorStateList.valueOf(textColor)
        deleteLocalButton.setImageDrawable(getDrawable("baseline_delete_outline_24"))
        deleteLocalButton.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {
                showToast(view.context.getActivity(), "Deleting local file", Toast.LENGTH_LONG)
                normalSafeApiCall {
                    AniyomiPlugin.getLocalFile(view.context).delete()
                }
                this@BottomFragment.dismiss()
            }
        })

//        goToExtensionGithubButton.imageTintList = ColorStateList.valueOf(textColor)
//        goToExtensionGithubButton.setImageDrawable(getDrawable("ic_github_logo"))
//        goToExtensionGithubButton.setOnClickListener(object : OnClickListener {
//            override fun onClick(p0: View?) {
//                runCatching {
//                    val intent = Intent(Intent.ACTION_VIEW).apply {
//                        data = Uri.parse("https://github.com/CranberrySoup/AniyomiCompatExtension")
//                    }
//                    activity?.startActivity(intent)
//                }
//            }
//        })

        val sortingMap = mapOf(
            EpisodeSortMethods.None.num to radioNone,
            EpisodeSortMethods.Ascending.num to radioAscending,
            EpisodeSortMethods.Reverse.num to radioReverse
        )
        sortingMap.forEach { (i, radioButton) ->
            radioButton.setOnClickListener(object : OnClickListener {
                override fun onClick(p0: View?) {
                    AniyomiPlugin.aniyomiSortingMethod = i
                    sortingGroup.check(radioButton.id)
                }
            })
        }
        sortingMap[AniyomiPlugin.aniyomiSortingMethod]?.id?.let { selectedItem ->
            sortingGroup.check(selectedItem)
        }
    }
}