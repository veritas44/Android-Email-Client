package com.email.scenes.signup.data

import android.accounts.NetworkErrorException
import com.email.R
import com.email.api.HttpErrorHandlingHelper
import com.email.signal.PreKeyBundleShareData
import com.email.api.ServerErrorException
import com.email.signal.SignalKeyGenerator
import com.email.bgworker.BackgroundWorker
import com.email.db.KeyValueStorage
import com.email.db.SignUpLocalDB
import com.email.scenes.signup.IncompleteAccount
import com.email.utils.UIMessage
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.github.kittinunf.result.mapError
import org.json.JSONException

/**
 * Created by sebas on 2/26/18.
 */

class RegisterUserWorker(
        private val db: SignUpLocalDB,
        private val apiClient: SignUpAPIClient,
        private val signalKeyGenerator: SignalKeyGenerator,
        private val keyValueStorage: KeyValueStorage,
        private val incompleteAccount: IncompleteAccount,
        override val publishFn: (SignUpResult.RegisterUser) -> Unit)
    : BackgroundWorker<SignUpResult.RegisterUser> {

    override val canBeParallelized = false

    private val setNewUserAsActiveAccount: (String) -> Unit = { username ->
        keyValueStorage.putString(KeyValueStorage.StringKey.ActiveAccount, username)
    }

    override fun catchException(ex: Exception): SignUpResult.RegisterUser {

        val message = createErrorMessage(ex)
        return SignUpResult.RegisterUser.Failure(message, ex)
    }

    private fun postNewUserToServer(keyBundle: PreKeyBundleShareData.UploadBundle)
            : Result<String, Exception> =
            Result.of { apiClient.createUser(incompleteAccount, keyBundle) }
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)

    private fun persistNewUserData(keyBundle: SignalKeyGenerator.PrivateBundle)
            :(String) -> Result<String, Exception> {
        return { jwtoken: String ->
            Result.of {
                val user = incompleteAccount.complete(keyBundle, jwtoken)
                db.saveNewUserData(user, keyBundle)
                user.recipientId
            }

        }
    }

    override fun work(): SignUpResult.RegisterUser? {
        val registrationBundle = signalKeyGenerator.register(recipientId = incompleteAccount.username,
                deviceId = 1)
        val operation = postNewUserToServer(registrationBundle.uploadBundle)
                          .flatMap(persistNewUserData(registrationBundle.privateBundle))
                          .map(setNewUserAsActiveAccount)

        return when(operation) {
            is Result.Success -> {
                SignUpResult.RegisterUser.Success()
            }
            is Result.Failure -> {
                SignUpResult.RegisterUser.Failure(
                        exception = operation.error,
                        message = createErrorMessage(operation.error))
            }
        }
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val createErrorMessage: (ex: Exception) -> UIMessage = { ex ->
        when (ex) {
            is JSONException -> UIMessage(resId = R.string.json_error_exception)
            is ServerErrorException -> {
                if(ex.errorCode == 400) {
                    UIMessage(resId = R.string.duplicate_name_error_exception)
                } else {
                    UIMessage(resId = R.string.server_error_exception)
                }
            }
            is NetworkErrorException -> UIMessage(resId = R.string.network_error_exception)
            else -> UIMessage(resId = R.string.fail_register_try_again_error_exception)
        }
    }
}
