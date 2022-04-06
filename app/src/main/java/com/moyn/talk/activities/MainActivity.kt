/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   @author Andy Scherzinger
 *   Copyright (C) 2021 Andy Scherzinger (infoi@andy-scherzinger.de)
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moyn.talk.activities

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.moyn.talk.R
import com.moyn.talk.api.NcApi
import com.moyn.talk.application.NextcloudTalkApplication
import com.moyn.talk.controllers.ConversationsListController
import com.moyn.talk.controllers.LockedController
import com.moyn.talk.controllers.ServerSelectionController
import com.moyn.talk.controllers.SettingsController
import com.moyn.talk.controllers.WebViewLoginController
import com.moyn.talk.controllers.base.providers.ActionBarProvider
import com.moyn.talk.databinding.ActivityMainBinding
import com.moyn.talk.models.database.UserEntity
import com.moyn.talk.models.json.conversations.RoomOverall
import com.moyn.talk.utils.ApiUtils
import com.moyn.talk.utils.ConductorRemapping.remapChatController
import com.moyn.talk.utils.SecurityUtils
import com.moyn.talk.utils.bundle.BundleKeys
import com.moyn.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.moyn.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.moyn.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.moyn.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.moyn.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.requery.Persistable
import io.requery.android.sqlcipher.SqlCipherDatabaseSource
import io.requery.reactivex.ReactiveEntityStore
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity : BaseActivity(), ActionBarProvider {
    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    lateinit var dataStore: ReactiveEntityStore<Persistable>

    @Inject
    lateinit var sqlCipherDatabaseSource: SqlCipherDatabaseSource

    @Inject
    lateinit var ncApi: NcApi

    private var router: Router? = null

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity: " + System.identityHashCode(this).toString())

        super.onCreate(savedInstanceState)
        // Set the default theme to replace the launch screen theme.
        setTheme(R.style.AppTheme)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        setSupportActionBar(binding.toolbar)

        router = Conductor.attachRouter(this, binding.controllerContainer, savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
            val channelId = "fcm_default_channel"
            val channelName = "Weather"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_LOW)
            )
        }

        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras. In this sample the launcher
        // intent is fired when the notification is tapped, so any accompanying data would
        // be handled here. If you want a different intent fired, set the click_action
        // field of the notification message to the desired intent. The launcher intent
        // is used when no click_action is specified.
        //
        // Handle possible data accompanying notification message.
        // [START handle_data_extras]
        intent.extras?.let {
            for (key in it.keySet()) {
                val value = intent.extras?.get(key)
                Log.d(TAG, "Key: $key Value: $value")
            }
        }
        // [END handle_data_extras]

        var hasDb = true

        try {
            sqlCipherDatabaseSource.writableDatabase
        } catch (exception: Exception) {
            hasDb = false
        }

        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (!router!!.hasRootController()) {
                router!!.setRoot(
                    RouterTransaction.with(ConversationsListController(Bundle()))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
                )
            }
            onNewIntent(intent)
        } else if (!router!!.hasRootController()) {
            if (hasDb) {
                if (userUtils.anyUserExists()) {
                    router!!.setRoot(
                        RouterTransaction.with(ConversationsListController(Bundle()))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                } else {
                    if (!TextUtils.isEmpty(resources.getString(R.string.weblogin_url))) {
                        router!!.pushController(
                            RouterTransaction.with(
                                WebViewLoginController(resources.getString(R.string.weblogin_url), false)
                            )
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler())
                        )
                    } else {
                        router!!.setRoot(
                            RouterTransaction.with(ServerSelectionController())
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler())
                        )
                    }
                }
            } else {
                if (!TextUtils.isEmpty(resources.getString(R.string.weblogin_url))) {
                    router!!.pushController(
                        RouterTransaction.with(
                            WebViewLoginController(resources.getString(R.string.weblogin_url), false)
                        )
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                } else {
                    router!!.setRoot(
                        RouterTransaction.with(ServerSelectionController())
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
                    )
                }
            }
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart: Activity: " + System.identityHashCode(this).toString())

        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkIfWeAreSecure()
        }

        handleActionFromContact(intent)
    }

    override fun onResume() {
        Log.d(TAG, "onResume: Activity: " + System.identityHashCode(this).toString())
        super.onResume()
    }

    override fun onPause() {
        Log.d(TAG, "onPause: Activity: " + System.identityHashCode(this).toString())
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Activity: " + System.identityHashCode(this).toString())
        super.onStop()
    }

    fun resetConversationsList() {
        if (userUtils.anyUserExists()) {
            router!!.setRoot(
                RouterTransaction.with(ConversationsListController(Bundle()))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }
    }

    fun openSettings() {
        router!!.pushController(
            RouterTransaction.with(SettingsController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    fun addAccount() {
        router!!.pushController(
            RouterTransaction.with(ServerSelectionController())
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
        )
    }

    private fun handleActionFromContact(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {

            val cursor = contentResolver.query(intent.data!!, null, null, null, null)

            var userId = ""
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    // userId @ server
                    userId = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1))
                }

                cursor.close()
            }

            when (intent.type) {
                "vnd.android.cursor.item/vnd.com.moyn.talk.chat" -> {
                    val user = userId.substringBeforeLast("@")
                    val baseUrl = userId.substringAfterLast("@")
                    if (userUtils.currentUser?.baseUrl?.endsWith(baseUrl) == true) {
                        startConversation(user)
                    } else {
                        Snackbar.make(
                            binding.controllerContainer,
                            R.string.nc_phone_book_integration_account_not_found,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun startConversation(userId: String) {
        val roomType = "1"
        val currentUser = userUtils.currentUser ?: return

        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion, currentUser.baseUrl, roomType,
            null, userId, null
        )
        ncApi.createRoom(
            credentials,
            retrofitBucket.url, retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }
                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putParcelable(KEY_USER_ENTITY, currentUser)
                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs.data.token)
                    bundle.putString(KEY_ROOM_ID, roomOverall.ocs.data.roomId)

                    // FIXME once APIv2 or later is used only, the createRoom already returns all the data
                    ncApi.getRoom(
                        credentials,
                        ApiUtils.getUrlForRoom(
                            apiVersion,
                            currentUser.baseUrl,
                            roomOverall.ocs.data.token
                        )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }
                            override fun onNext(roomOverall: RoomOverall) {
                                bundle.putParcelable(
                                    KEY_ACTIVE_CONVERSATION,
                                    Parcels.wrap(roomOverall.ocs.data)
                                )
                                remapChatController(
                                    router!!, currentUser.id,
                                    roomOverall.ocs.data.token, bundle, true
                                )
                            }

                            override fun onError(e: Throwable) {
                                // unused atm
                            }
                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }
                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun checkIfWeAreSecure() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                if (router != null && router!!.getControllerWithTag(LockedController.TAG) == null) {
                    router!!.pushController(
                        RouterTransaction.with(LockedController())
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
                            .tag(LockedController.TAG)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        Log.d(TAG, "onNewIntent Activity: " + System.identityHashCode(this).toString())
        super.onNewIntent(intent)
        handleActionFromContact(intent)
        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                val callNotificationIntent = Intent(this, CallNotificationActivity::class.java)
                intent.extras?.let { callNotificationIntent.putExtras(it) }
                startActivity(callNotificationIntent)
            } else {
                remapChatController(
                    router!!, intent.getParcelableExtra<UserEntity>(KEY_USER_ENTITY)!!.id,
                    intent.getStringExtra(KEY_ROOM_TOKEN)!!, intent.extras!!, false, true
                )
            }
        }
    }

    override fun onBackPressed() {
        if (router!!.getControllerWithTag(LockedController.TAG) != null) {
            return
        }

        if (!router!!.handleBack()) {
            super.onBackPressed()
        }
    }

    companion object {
        private val TAG = "MainActivity"
    }
}
