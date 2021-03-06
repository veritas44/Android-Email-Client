package com.criptext.mail.scenes.settings.changepassword.data

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.utils.ServerCodes
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.sha256
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError

class ChangePasswordWorker(
        private val oldPassword: String,
        private val password: String,
        private val httpClient: HttpClient,
        private val activeAccount: ActiveAccount,
        private val accountDao: AccountDao,
        private val storage: KeyValueStorage,
        override val publishFn: (
                ChangePasswordResult.ChangePassword) -> Unit)
    : BackgroundWorker<ChangePasswordResult.ChangePassword> {

    override val canBeParallelized = true
    private val apiClient = ChangePasswordAPIClient(httpClient, activeAccount.jwt)

    override fun catchException(ex: Exception): ChangePasswordResult.ChangePassword {
        if(ex is ServerErrorException){
            if(ex.errorCode == ServerCodes.EnterpriseAccountSuspended)
                return ChangePasswordResult.ChangePassword.EnterpriseSuspended()
            else
                return ChangePasswordResult.ChangePassword.Failure(UIMessage(R.string.server_bad_status, arrayOf(ex.errorCode)), ex)
        }

        return ChangePasswordResult.ChangePassword.Failure(UIMessage(R.string.password_enter_error), ex)
    }

    override fun work(reporter: ProgressReporter<ChangePasswordResult.ChangePassword>): ChangePasswordResult.ChangePassword? {
        val checkPasswordOperation = workOperation()

        val sessionExpired = HttpErrorHandlingHelper.didFailBecauseInvalidSession(checkPasswordOperation)

        val finalResult = if(sessionExpired)
            newRetryWithNewSessionOperation()
        else
            checkPasswordOperation

        return when (finalResult){
            is Result.Success -> {
                ChangePasswordResult.ChangePassword.Success()
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
        apiClient.putChangePassword(oldPassword.sha256(), password.sha256()).body
    }
    .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)

    private fun newRetryWithNewSessionOperation()
            : Result<String, Exception> {
        val refreshOperation =  HttpErrorHandlingHelper.newRefreshSessionOperation(apiClient,
                activeAccount, storage, accountDao)
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
