package com.example

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import recloudstream.AniyomiPlugin

class BlankFragment(val plugin: Plugin) : BottomSheetDialogFragment() {
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
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentlyUsing = view.findView<TextView>("currently_using")
        val internallyInstalled = view.findView<TextView>("internally_installed")
        val numberOfExtensions = view.findView<TextView>("number_of_extensions")
        val forceInstallButton = view.findView<ImageView>("force_install_button")
        val deleteLocalRoot = view.findView<View>("delete_local_root")
        val deleteLocalButton = view.findView<ImageView>("delete_local_button")
        val externalApkButton = view.findView<ImageView>("external_apk_button")
        val externalApkRoot = view.findView<View>("external_apk_root")

        currentlyUsing.text =
            (AniyomiPlugin.currentLoadedFile?.absolutePath ?: "None").toString()
        internallyInstalled.text =
            AniyomiPlugin.getIsLocallyInstalled(view.context).toString()
        numberOfExtensions.text =
            AniyomiPlugin.listExtensions(view.context).size.toString()

        val textColor = currentlyUsing.currentTextColor

        forceInstallButton.imageTintList = ColorStateList.valueOf(textColor)
        forceInstallButton.setImageDrawable(getDrawable("baseline_get_app_24"))
        forceInstallButton.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {
                showToast(view.context.getActivity(), "Downloading APK", Toast.LENGTH_LONG)
                ioSafe {
                    AniyomiPlugin.downloadApk(view.context)
                    this@BlankFragment.dismiss()
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
                this@BlankFragment.dismiss()
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
                this@BlankFragment.dismiss()
            }
        })
    }
}