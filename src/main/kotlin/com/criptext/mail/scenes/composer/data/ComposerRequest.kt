package com.criptext.mail.scenes.composer.data

import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.models.Label

/**
 * Created by gabriel on 2/26/18.
 */

sealed class ComposerRequest {
    class GetAllContacts : ComposerRequest()
    class GetAllFromAddresses : ComposerRequest()
    class SaveEmailAsDraft(val threadId: String?, val emailId: Long?,
                           val composerInputData: ComposerInputData,
                           val goToRecoveryEmail: Boolean,
                           val onlySave: Boolean, val attachments: List<ComposerAttachment>,
                           val fileKey: String?, val originalId: Long?, val senderEmail: String,
                           val currentLabel: Label): ComposerRequest()
    class UploadAttachment(val filepath: String, val fileKey: String?, val filesSize: Long, val uuid: String, val groupId: String?): ComposerRequest()
    class LoadInitialData(val composerType: ComposerType, val emailId: Long): ComposerRequest()
    data class CheckDomain(val emails: List<String>) : ComposerRequest()
    data class CheckCanSend(val composerInputData: ComposerInputData) : ComposerRequest()
    data class SwitchActiveAccount(val old: String, val new: String) : ComposerRequest()
}