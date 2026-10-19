/*
 * SPDX-FileCopyrightText: 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class DozeSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private var pocketPreference: MainSwitchPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.doze_settings, rootKey)

        pocketPreference = findPreference(Utils.GESTURE_POCKET_KEY)
        pocketPreference?.isChecked = Utils.isPocketEnabled(requireContext())
        pocketPreference?.onPreferenceChangeListener = this
    }

    override fun onResume() {
        super.onResume()
        pocketPreference?.isChecked = Utils.isPocketEnabled(requireContext())
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == Utils.GESTURE_POCKET_KEY) {
            val isChecked = newValue as Boolean
            Utils.setPocketEnabled(requireContext(), isChecked)
            Utils.checkDozeService(requireContext())
            DozeContentProvider.notifyPocketChanged(requireContext())
            return true
        }
        return false
    }
}
