package com.criptext.mail.scenes.settings.recovery_email.workers

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailAPIClient
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailResult
import com.criptext.mail.utils.ServerCodes
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.sha256
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError
import org.json.JSONObject

class ChangeRecoveryEmailWorker(
        private val password: String?,
        private val newEmail: String,
        private val storage: KeyValueStorage,
        private val accountDao: AccountDao,
        private val httpClient: HttpClient,
        private val activeAccount: ActiveAccount,
        override val publishFn: (
                RecoveryEmailResult.ChangeRecoveryEmail) -> Unit)
    : BackgroundWorker<RecoveryEmailResult.ChangeRecoveryEmail> {

    override val canBeParallelized = true
    private val apiClient = RecoveryEmailAPIClient(httpClient, activeAccount.jwt)

    override fun catchException(ex: Exception): RecoveryEmailResult.ChangeRecoveryEmail {
        return if(ex is ServerErrorException) {
            when(ex.errorCode) {
                ServerCodes.MethodNotAllowed -> {
                    val body = ex.body
                    if(body == null) {
                        RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                UIMessage(resId = R.string.recovery_email_sign_up_error))
                    } else {
                        val json = JSONObject(body)
                        when (json.getInt("error")){
                            1 -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                    UIMessage(resId = R.string.recovery_email_sign_up_error_1))
                            2 -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                    UIMessage(resId = R.string.recovery_email_sign_up_error_2,
                                            args = arrayOf(json.getJSONObject("data").getInt("max"))))
                            3 -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                    UIMessage(resId = R.string.recovery_email_sign_up_error_3))
                            4 -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                    UIMessage(R.string.recovery_email_change_fail_same, arrayOf(newEmail)))
                            else -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex,
                                    UIMessage(resId = R.string.recovery_email_sign_up_error))
                        }
                    }
                }
                ServerCodes.BadRequest -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex, UIMessage(R.string.password_enter_error))
                ServerCodes.EnterpriseAccountSuspended -> RecoveryEmailResult.ChangeRecoveryEmail.EnterpriseSuspended()
                else -> RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex, UIMessage(R.string.server_bad_status, arrayOf(ex.errorCode)))
            }
        }else {
            RecoveryEmailResult.ChangeRecoveryEmail.Failure(ex, UIMessage(R.string.server_error_exception))
        }
    }

    override fun work(reporter: ProgressReporter<RecoveryEmailResult.ChangeRecoveryEmail>): RecoveryEmailResult.ChangeRecoveryEmail? {
        val changeEmailOperation = workOperation()

        val sessionExpired = HttpErrorHandlingHelper.didFailBecauseInvalidSession(changeEmailOperation)

        val finalResult = if(sessionExpired)
            newRetryWithNewSessionOperation()
        else
            changeEmailOperation

        return when (finalResult){
            is Result.Success -> {
                RecoveryEmailResult.ChangeRecoveryEmail.Success(newEmail)
            }
            is Result.Failure -> {
                catchException(finalResult.error)
            }
        }
    }

    override fun cancel() {
        TODO("CANCEL IS NOT IMPLEMENTED")
    }

    private fun workOperation() : Result<String, Exception> = Result.of {
        apiClient.putChangerecoveryEmail(newEmail, password?.sha256()).body
    }
    .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)

    private fun newRetryWithNewSessionOperation()
            : Result<String, Exception> {
        val refreshOperation =  HttpErrorHandlingHelper.newRefreshSessionOperation(apiClient, activeAccount, storage, accountDao)
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
        return when(refreshOperation){
            is Result.Success -> {
                apiClient.token = refreshOperation.value
                workOperation()
            }
            is Result.Failure -> {
                Result.of { throw refreshOperation.error }
            }
        }
    }

}
