package com.criptext.mail.scenes.mailbox.data

import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.Label
import com.criptext.mail.email_preview.EmailPreview
import com.criptext.mail.utils.UIMessage

/**
 * Created by sebas on 3/20/18.
 */

sealed class MailboxResult {

    sealed class UpdateEmailThreadsLabelsRelations: MailboxResult() {
        class Success: UpdateEmailThreadsLabelsRelations()
        data class Failure(
                val message: UIMessage,
                val exception: Exception) : UpdateEmailThreadsLabelsRelations()
        data class Unauthorized(val message: UIMessage) : UpdateEmailThreadsLabelsRelations()
    }

    sealed class MoveEmailThread: MailboxResult() {
        class Success: MoveEmailThread()
        data class Failure(
                val message: UIMessage,
                val exception: Exception) : MoveEmailThread()
        data class Unauthorized(val message: UIMessage) : MoveEmailThread()
    }

    sealed class GetSelectedLabels : MailboxResult() {
        class Success(val allLabels: List<Label>,
                      val selectedLabels: List<Label>): GetSelectedLabels()
        data class Failure(
                val message: UIMessage,
                val exception: Exception) : GetSelectedLabels()
    }

    sealed class LoadEmailThreads : MailboxResult() {
        abstract fun getDestinationMailbox(): String
        class Success(
                val emailPreviews: List<EmailPreview>,
                val isReset: Boolean,
                val mailboxLabel: String): LoadEmailThreads() {

            override fun getDestinationMailbox(): String {
                return mailboxLabel
            }
        }

        data class Failure(
                val mailboxLabel: String,
                val message: UIMessage,
                val exception: Exception) : LoadEmailThreads() {

            override fun getDestinationMailbox(): String {
                return mailboxLabel
            }
        }
    }

    sealed class UpdateMailbox : MailboxResult() {
        abstract fun getDestinationMailbox(): Label
        data class Success(
                val mailboxLabel: Label,
                val mailboxThreads: List<EmailPreview>?,
                val isManual: Boolean): UpdateMailbox() {

            override fun getDestinationMailbox(): Label {
                return mailboxLabel
            }
        }

        data class Failure(
                val mailboxLabel: Label,
                val message: UIMessage,
                val exception: Exception?): UpdateMailbox() {
            override fun getDestinationMailbox(): Label {
                return mailboxLabel
            }
        }

        data class Unauthorized(
                val mailboxLabel: Label,
                val message: UIMessage,
                val exception: Exception?): UpdateMailbox() {
            override fun getDestinationMailbox(): Label {
                return mailboxLabel
            }
        }
    }

    sealed class SendMail: MailboxResult() {
        class Success(val emailId: Long): SendMail()
        data class Failure(val message: UIMessage): SendMail()
        data class Unauthorized(val message: UIMessage): SendMail()
    }

    sealed class GetMenuInformation : MailboxResult() {
        data class Success(val account: Account, val totalInbox: Int, val totalDraft: Int,
                           val totalSpam: Int, val labels: List<Label>): GetMenuInformation()
        class Failure: GetMenuInformation()
    }

    sealed class UpdateUnreadStatus: MailboxResult(){
        class Success: UpdateUnreadStatus()
        class Failure(val message: UIMessage): UpdateUnreadStatus()
        class Unauthorized(val message: UIMessage): UpdateUnreadStatus()
    }

    sealed class LinkDevice: MailboxResult() {
        class Success(filePath: String): LinkDevice()
        data class Failure(val message: UIMessage): LinkDevice()
    }

    sealed class GetEmailPreview: MailboxResult() {
        data class Success(val emailPreview: EmailPreview,
                           val isTrash: Boolean, val isSpam: Boolean): GetEmailPreview()
        data class Failure(val message: String): GetEmailPreview()
    }
}