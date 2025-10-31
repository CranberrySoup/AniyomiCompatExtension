package com.example

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.plugins.Plugin
import recloudstream.AniyomiPlugin

data class AniyomiExtension(val pkgName: String, val name: String, val icon: Drawable)

class ExtensionFragment(private val plugin: Plugin) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = plugin.resources!!.getIdentifier("fragment_extension", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findView<RecyclerView>("extension_recycler_view")
        recyclerView.layoutManager = LinearLayoutManager(view.context)

        val extensions = AniyomiPlugin.listExtensions(view.context)

        val aniyomiApis = apis.filter {
            // Good enough
            it.name.endsWith("⦁")
        }.map { api ->
            val canShow = runCatching {
                val method = api.javaClass.getDeclaredMethod("canShowPreferenceScreen")
                (method.invoke(api) as? Boolean) ?: false
            }.getOrDefault(false)

            val pkgName = runCatching {
                val method = api.javaClass.getDeclaredMethod("getPkgName")
                (method.invoke(api) as? String)
            }.getOrNull()

            Triple(api, canShow, pkgName)
        }

        val combined = extensions.associateWith { extension ->
            aniyomiApis.firstOrNull { (_, _, pkgName) ->
                extension.pkgName == pkgName
            }
        }


        recyclerView.adapter = ExtensionAdapter(plugin, extensions, combined)
    }
}