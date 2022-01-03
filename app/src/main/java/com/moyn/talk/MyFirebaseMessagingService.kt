package com.moyn.talk

/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.moyn.talk.activities.CallNotificationActivity
import com.moyn.talk.api.NcApi
import com.moyn.talk.application.NextcloudTalkApplication
import com.moyn.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.moyn.talk.events.CallNotificationClick
import com.moyn.talk.jobs.NotificationWorker
import com.moyn.talk.jobs.PushRegistrationWorker
import com.moyn.talk.models.RingtoneSettings
import com.moyn.talk.models.SignatureVerification
import com.moyn.talk.models.json.participants.Participant
import com.moyn.talk.models.json.participants.ParticipantsOverall
import com.moyn.talk.models.json.push.DecryptedPushMessage
import com.moyn.talk.utils.ApiUtils
import com.moyn.talk.utils.NotificationUtils
import com.moyn.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.moyn.talk.utils.NotificationUtils.cancelExistingNotificationWithId
import com.moyn.talk.utils.NotificationUtils.createNotificationChannel
import com.moyn.talk.utils.PushUtils
import com.moyn.talk.utils.bundle.BundleKeys
import com.moyn.talk.utils.bundle.BundleKeys.KEY_FROM_NOTIFICATION_START_CALL
import com.moyn.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.moyn.talk.utils.preferences.AppPreferences
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.Retrofit
import java.io.IOException
import java.net.CookieManager
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.inject.Inject

@SuppressLint("LongLogTag")
@AutoInjector(NextcloudTalkApplication::class)
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    var isServiceInForeground: Boolean = false
    private var decryptedPushMessage: DecryptedPushMessage? = null
    private var signatureVerification: SignatureVerification? = null
    private var handler: Handler = Handler()

    @JvmField
    @Inject
    var retrofit: Retrofit? = null

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null

    @JvmField
    @Inject
    var eventBus: EventBus? = null

    private val TAG = "MyFirebaseMessaging"

    override fun onCreate() {
        super.onCreate()
        sharedApplication!!.componentApplication.inject(this)
        eventBus?.register(this)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(event: CallNotificationClick) {
        Log.d(TAG, "CallNotification was clicked")
        isServiceInForeground = false
        stopForeground(true)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isServiceInForeground = false
        eventBus?.unregister(this)
        stopForeground(true)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sharedApplication!!.componentApplication.inject(this)
        appPreferences!!.pushToken = token
        Log.d(TAG, "onNewToken. token = $token")
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    override fun onMessageReceived(remoteMessage : RemoteMessage) {

        Log.d(TAG, "From moyn: ${remoteMessage.from}")
        Log.w(TAG,"moyn YUPI")

        if(remoteMessage.data.isNotEmpty()){
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        remoteMessage.notification?.let {
            Log.d(TAG,"Message Notificationn Body: ${it.body}")
        }

        if (!remoteMessage.data["subject"].isNullOrEmpty() && !remoteMessage.data["signature"].isNullOrEmpty()) {
            decryptMessage(remoteMessage.data["subject"]!!, remoteMessage.data["signature"]!!)
        }
    }

    private fun decryptMessage(subject: String, signature: String) {
        try {
            val base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT)
            val base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT)
            val pushUtils = PushUtils()
            val privateKey = pushUtils.readKeyFromFile(false) as PrivateKey
            try {
                signatureVerification = pushUtils.verifySignature(
                    base64DecodedSignature,
                    base64DecodedSubject
                )
                if (signatureVerification!!.signatureValid) {
                    val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privateKey)
                    val decryptedSubject = cipher.doFinal(base64DecodedSubject)
                    decryptedPushMessage = LoganSquare.parse(
                        String(decryptedSubject),
                        DecryptedPushMessage::class.java
                    )
                    decryptedPushMessage?.apply {
                        Log.d(TAG, this.toString())
                        timestamp = System.currentTimeMillis()
                        if (delete) {
                            cancelExistingNotificationWithId(
                                applicationContext,
                                signatureVerification!!.userEntity,
                                notificationId
                            )
                        } else if (deleteAll) {
                            cancelAllNotificationsForAccount(applicationContext, signatureVerification!!.userEntity)
                        } else if (deleteMultiple) {
                            notificationIds.forEach {
                                cancelExistingNotificationWithId(
                                    applicationContext,
                                    signatureVerification!!.userEntity,
                                    it
                                )
                            }
                        } else if (type == "call") {
                            val fullScreenIntent = Intent(applicationContext, CallNotificationActivity::class.java)
                            val bundle = Bundle()
                            bundle.putString(BundleKeys.KEY_ROOM_ID, decryptedPushMessage!!.id)
                            bundle.putParcelable(KEY_USER_ENTITY, signatureVerification!!.userEntity)
                            bundle.putBoolean(KEY_FROM_NOTIFICATION_START_CALL, true)
                            fullScreenIntent.putExtras(bundle)

                            fullScreenIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            val fullScreenPendingIntent = PendingIntent.getActivity(
                                this@MyFirebaseMessagingService,
                                0,
                                fullScreenIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )

                            val audioAttributesBuilder =
                                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)

                            val ringtonePreferencesString: String? = appPreferences!!.callRingtoneUri
                            val soundUri = if (TextUtils.isEmpty(ringtonePreferencesString)) {
                                Uri.parse(
                                    "android.resource://" + applicationContext.packageName +
                                        "/raw/librem_by_feandesign_call"
                                )
                            } else {
                                try {
                                    val ringtoneSettings =
                                        LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                                    ringtoneSettings.ringtoneUri
                                } catch (exception: IOException) {
                                    Uri.parse(
                                        "android.resource://" +
                                            applicationContext.packageName +
                                            "/raw/librem_by_feandesign_call"
                                    )
                                }
                            }

                            val notificationChannelId = NotificationUtils.getNotificationChannelId(
                                applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls),
                                applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls_description),
                                true,
                                NotificationManagerCompat.IMPORTANCE_HIGH,
                                soundUri!!,
                                audioAttributesBuilder.build(),
                                null,
                                false
                            )

                            createNotificationChannel(
                                applicationContext!!,
                                notificationChannelId,
                                applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls),
                                applicationContext.resources
                                    .getString(R.string.nc_notification_channel_calls_description),
                                true,
                                NotificationManagerCompat.IMPORTANCE_HIGH,
                                soundUri,
                                audioAttributesBuilder.build(),
                                null,
                                false
                            )

                            val uri = Uri.parse(signatureVerification!!.userEntity.baseUrl)
                            val baseUrl = uri.host

                            val notification =
                                NotificationCompat.Builder(this@MyFirebaseMessagingService, notificationChannelId)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setCategory(NotificationCompat.CATEGORY_CALL)
                                    .setSmallIcon(R.drawable.ic_call_black_24dp)
                                    .setSubText(baseUrl)
                                    .setShowWhen(true)
                                    .setWhen(decryptedPushMessage!!.timestamp)
                                    .setContentTitle(EmojiCompat.get().process(decryptedPushMessage!!.subject))
                                    .setAutoCancel(true)
                                    .setOngoing(true)
                                    // .setTimeoutAfter(45000L)
                                    .setContentIntent(fullScreenPendingIntent)
                                    .setFullScreenIntent(fullScreenPendingIntent, true)
                                    .setSound(soundUri)
                                    .build()
                            notification.flags = notification.flags or Notification.FLAG_INSISTENT
                            isServiceInForeground = true
                            checkIfCallIsActive(signatureVerification!!, decryptedPushMessage!!)
                            startForeground(decryptedPushMessage!!.timestamp.toInt(), notification)
                        } else {
                            val messageData = Data.Builder()
                                .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, subject)
                                .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, signature)
                                .build()
                            val pushNotificationWork =
                                OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                                    .build()
                            WorkManager.getInstance().enqueue(pushNotificationWork)
                        }
                    }
                }
            } catch (e1: NoSuchAlgorithmException) {
                Log.d(NotificationWorker.TAG, "No proper algorithm to decrypt the message " + e1.localizedMessage)
            } catch (e1: NoSuchPaddingException) {
                Log.d(NotificationWorker.TAG, "No proper padding to decrypt the message " + e1.localizedMessage)
            } catch (e1: InvalidKeyException) {
                Log.d(NotificationWorker.TAG, "Invalid private key " + e1.localizedMessage)
            }
        } catch (exception: Exception) {
            Log.d(NotificationWorker.TAG, "Something went very wrong " + exception.localizedMessage)
        }
    }

    private fun checkIfCallIsActive(
        signatureVerification: SignatureVerification,
        decryptedPushMessage: DecryptedPushMessage
    ) {
        Log.d(TAG, "checkIfCallIsActive")
        val ncApi = retrofit!!.newBuilder()
            .client(okHttpClient!!.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build()
            .create(NcApi::class.java)
        var hasParticipantsInCall = true
        var inCallOnDifferentDevice = false

        val apiVersion = ApiUtils.getConversationApiVersion(
            signatureVerification.userEntity,
            intArrayOf(ApiUtils.APIv4, 1)
        )

        ncApi.getPeersForCall(
            ApiUtils.getCredentials(signatureVerification.userEntity.username, signatureVerification.userEntity.token),
            ApiUtils.getUrlForCall(
                apiVersion,
                signatureVerification.userEntity.baseUrl,
                decryptedPushMessage.id
            )
        )
            .repeatWhen { completed ->
                completed.zipWith(Observable.range(1, 12), { _, i -> i })
                    .flatMap { Observable.timer(5, TimeUnit.SECONDS) }
                    .takeWhile { isServiceInForeground && hasParticipantsInCall && !inCallOnDifferentDevice }
            }
            .subscribeOn(Schedulers.io())
            .subscribe(object : Observer<ParticipantsOverall> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(participantsOverall: ParticipantsOverall) {
                    val participantList: List<Participant> = participantsOverall.ocs.data
                    hasParticipantsInCall = participantList.isNotEmpty()
                    if (hasParticipantsInCall) {
                        for (participant in participantList) {
                            if (participant.actorId == signatureVerification.userEntity.userId &&
                                participant.actorType == Participant.ActorType.USERS) {
                                inCallOnDifferentDevice = true
                                break
                            }
                        }
                    }

                    if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                        Log.d(TAG, "no participants in call OR inCallOnDifferentDevice")
                        stopForeground(true)
                        handler.removeCallbacksAndMessages(null)
                    } 
                }

                override fun onError(e: Throwable) {}
                override fun onComplete() {
                    stopForeground(true)
                    handler.removeCallbacksAndMessages(null)
                }
            })
    }
}
