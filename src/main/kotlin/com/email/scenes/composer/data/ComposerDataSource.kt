package com.email.scenes.composer.data

import com.email.bgworker.BackgroundWorker
import com.email.bgworker.WorkHandler
import com.email.bgworker.WorkRunner
import com.email.db.dao.signal.RawSessionDao
import com.email.db.models.ActiveAccount
import com.email.scenes.mailbox.data.SendMailWorker
import com.email.signal.SignalClient

/**
 * Created by gabriel on 2/26/18.
 */

class ComposerDataSource(override val runner: WorkRunner) : WorkHandler<ComposerRequest, ComposerResult>() {

    override fun createWorkerFromParams(params: ComposerRequest, flushResults: (ComposerResult) -> Unit): BackgroundWorker<*> {
        return when(params) {
            is ComposerRequest.SuggestContacts -> LoadContactsWorker({ res ->
                flushResults(res)
            })
        }
    }

}