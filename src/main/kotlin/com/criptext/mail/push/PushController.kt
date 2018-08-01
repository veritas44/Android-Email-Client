package com.criptext.mail.push

import com.criptext.mail.db.AppDatabase
import com.criptext.mail.push.data.PushDataSource
import com.criptext.mail.push.data.PushResult
import android.content.Context
import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.models.Label
import com.criptext.mail.push.data.PushRequest

/**
 * Controller designed to be used by EmailFirebaseMessageService. Exposes a single function:
 * parsePushPayload() which takes in the Map object with the push data and returns a Notifier
 * object, which the service can use to display a notification by invoking its notifyPushEvent()
 * method.
 *
 * The constructor receives a lambda function that performs a partial update. This function is
 * expected to throw a few exceptions related to connectivity issues every now and then, which are
 * handled by the controller to configure the resulting Notifier object.
 * Created by gabriel on 8/18/17.
 */

class PushController(private val dataSource: PushDataSource, private val isPostNougat: Boolean) {


    private fun parseNewMailPush(pushData: Map<String, String>,
                                 shouldPostNotification: Boolean): PushData.NewMail {
        val body = pushData["body"] ?: ""
        val title = pushData["title"] ?: ""
        val threadId = pushData["threadId"] ?: ""

        return PushData.NewMail(title = title, body = body, threadId = threadId,
                shouldPostNotification = shouldPostNotification,
                isPostNougat = isPostNougat)
    }

    private fun parseNewOpenMailbox(pushData: Map<String, String>,
                                 shouldPostNotification: Boolean): PushData.OpenMailbox {
        val body = pushData["body"] ?: ""
        val title = pushData["title"] ?: ""

        return PushData.OpenMailbox(title = title, body = body,
                shouldPostNotification = shouldPostNotification,
                isPostNougat = isPostNougat)
    }

    fun parsePushPayload(pushData: Map<String, String>, shouldPostNotification: Boolean): Notifier? {
        if(shouldPostNotification)
            dataSource.submitRequest(PushRequest.UpdateMailbox(Label.defaultItems.inbox, null))
        val action = pushData["action"]
        if (action != null) {
            val type = PushTypes.fromActionString(action)
            return when (type) {
                PushTypes.newMail -> {
                    val data = parseNewMailPush(pushData, shouldPostNotification)
                    NewMailNotifier.Single(data)
                }
                PushTypes.openActivity -> {
                    val data = parseNewOpenMailbox(pushData, shouldPostNotification)
                    OpenMailboxNotifier.Open(data)
                }
            }
        }
        return null
    }


}