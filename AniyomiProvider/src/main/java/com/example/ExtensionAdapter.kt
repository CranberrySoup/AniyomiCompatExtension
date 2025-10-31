package com.example

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral

class ExtensionAdapter(
    private val plugin: Plugin,
    private val values: List<AniyomiExtension>,
    private val extensionSettings: Map<AniyomiExtension, Triple<MainAPI, Boolean, String?>?>
) : RecyclerView.Adapter<ExtensionAdapter.ViewHolder>() {

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", "com.example")
        return this.findViewById(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", "com.example")
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val id = plugin.resources!!.getIdentifier("extension_item", "layout", "com.example")
        val layout = plugin.resources!!.getLayout(id)
        val inflater = LayoutInflater.from(parent.context)

        return ViewHolder(
            inflater.inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        val extensionSettings = extensionSettings[item]

        val nameView = holder.itemView.findView<TextView>("extension_name")
        val iconView = holder.itemView.findView<ImageView>("extension_icon")
        val settingsBtt = holder.itemView.findView<ImageView>("extension_settings_btt")

        nameView.text = item.name
        iconView.setImageDrawable(item.icon)

        settingsBtt.isVisible = extensionSettings?.second == true
        val settingsIcon = getDrawable("baseline_settings_24")
        settingsBtt.setImageDrawable(settingsIcon)

        settingsBtt.setOnClickListener(object : OnClickListener {
            override fun onClick(p0: View?) {
                openSettings(extensionSettings?.first, item, holder.itemView.context)
            }
        })
    }

    fun openSettings(api: MainAPI?, extension: AniyomiExtension, context: Context) {
        if (api == null) return
        val activity = (context.getActivity() as? AppCompatActivity) ?: return
        val manager = activity.supportFragmentManager

        // Easiest way to get preference fragment
        val fragment = SettingsGeneral()

        try {
            manager
                .beginTransaction()
                .add(fragment, "AniyomiExtensionPreferences")
                .commitNow()

            fragment.view?.setPadding(0, 0, 0, 0)
            fragment.setUpToolbar(extension.name)
            fragment.preferenceScreen.removeAll()
            val method = api.javaClass.getDeclaredMethod(
                "showPreferenceScreen",
                PreferenceScreen::class.java
            )
            method.invoke(api, fragment.preferenceScreen)

            Dialog(context, android.R.style.Theme_NoTitleBar_Fullscreen).apply {
                this.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
                this.setContentView(fragment.requireView())
                this.setOnDismissListener(object : DialogInterface.OnDismissListener {
                    override fun onDismiss(p0: DialogInterface?) {
                        manager.beginTransaction()
                            .remove(fragment)
                            .commit()
                    }
                })
            }.show()

        } catch (t: Throwable) {
            logError(t)
        }
    }

    override fun getItemCount(): Int = values.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}