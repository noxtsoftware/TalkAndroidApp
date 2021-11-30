/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.moyn.talk.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity
import autodagger.AutoInjector
import com.moyn.talk.R
import com.moyn.talk.application.NextcloudTalkApplication
import com.moyn.talk.events.CertificateEvent
import com.moyn.talk.utils.SecurityUtils
import com.moyn.talk.utils.preferences.AppPreferences
import com.moyn.talk.utils.ssl.MagicTrustManager
import com.yarolegovich.lovelydialog.LovelyStandardDialog
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.text.DateFormat
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
open class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var context: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        super.onCreate(savedInstanceState)
    }

    public override fun onResume() {
        super.onResume()
        if (appPreferences.isScreenSecured) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (appPreferences.isScreenLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecurityUtils.createKey(appPreferences.screenLockTimeout)
            }
        }
    }

    fun showCertificateDialog(
        cert: X509Certificate,
        magicTrustManager: MagicTrustManager,
        sslErrorHandler: SslErrorHandler?
    ) {
        try{
            magicTrustManager.addCertInTrustStore(cert)
            sslErrorHandler?.proceed()
        }catch (e: CertificateParsingException) {
            Log.d(TAG, "Failed to parse the certificate")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: CertificateEvent) {
        showCertificateDialog(event.x509Certificate, event.magicTrustManager, event.sslErrorHandler)
    }

    public override fun onStart() {
        super.onStart()
        eventBus.register(this)
    }

    public override fun onStop() {
        super.onStop()
        eventBus.unregister(this)
    }

    companion object {
        private val TAG = "BaseActivity"
    }
}
