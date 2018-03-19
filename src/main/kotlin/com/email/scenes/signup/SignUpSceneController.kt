package com.email.scenes.signup

import com.email.IHostActivity
import com.email.api.ServerErrorException
import com.email.scenes.SceneController
import com.email.scenes.params.MailboxParams
import com.email.scenes.signup.data.SignUpRequest
import com.email.scenes.signup.data.SignUpResult
import com.email.scenes.signup.data.SignUpDataSource
import com.email.utils.Utility

/**
 * Created by sebas on 2/15/18.
 */

class SignUpSceneController(
        private val model: SignUpSceneModel,
        private val scene: SignUpScene,
        private val host : IHostActivity,
        private val dataSource: SignUpDataSource): SceneController() {

    override val menuResourceId: Int?
        get() = null

    val arePasswordsMatching: Boolean
        get() = model.password == model.confirmPassword

    private val isPasswordErrorShown: Boolean
        get() = model.errors["password"] == true

    private val isUsernameErrorShown: Boolean
        get() = model.errors["username"] == true

    private val isCheckedTermsAndConditions: Boolean
        get() = model.checkTermsAndConditions

    private val fieldsAreEmpty: Boolean
        get() = areFieldsEmpty()

    val isSetRecoveryEmail: Boolean
        get() = model.recoveryEmail.isNotEmpty()

    private val isValidRecoveryEmail: Boolean
        get() {
            return if(isSetRecoveryEmail){
                val isRecoveryEmailValid = Utility.isEmailValid(model.recoveryEmail)
                isRecoveryEmailValid
            } else {
                true
            }
        }


    private fun shouldCreateButtonBeEnabled(): Boolean {
        return !isUsernameErrorShown
                && !isPasswordErrorShown
                && isCheckedTermsAndConditions
                && !fieldsAreEmpty
                && isValidRecoveryEmail
    }

    private val signUpListener : SignUpListener = object : SignUpListener {
        override fun onUsernameChangedListener(text: String) {
            if(model.errors["username"] == true) {
                model.errors["username"] = false
                scene.toggleUsernameErrors(show = false)
            }
            model.username = text
            if(shouldCreateButtonBeEnabled()) {
                scene.enableCreateAccountButton()
            } else {
                scene.disableCreateAccountButton()
            }
        }

        override fun onCheckedOptionChanged(state: Boolean) {
            model.checkTermsAndConditions = state
            if (model.checkTermsAndConditions) {
                if (shouldCreateButtonBeEnabled()) {
                    scene.enableCreateAccountButton()
                }
            } else {
                scene.disableCreateAccountButton()
            }
        }

        override fun onFullNameTextChangeListener(text: String){
            model.fullName = text
            if(shouldCreateButtonBeEnabled()) {
                scene.enableCreateAccountButton()
            } else {
                scene.disableCreateAccountButton()
            }
        }

        override fun onTermsAndConditionsClick(){
            TODO("READ TERMS AND CONDITIONS.")
        }

        override fun onRecoveryEmailTextChangeListener(text: String) {
            model.recoveryEmail = text
            if(shouldCreateButtonBeEnabled()) {
                scene.enableCreateAccountButton()
            } else {
                scene.disableCreateAccountButton()
            }
        }


        override fun onConfirmPasswordChangedListener(text: String) {
            model.confirmPassword = text
            if (arePasswordsMatching && model.confirmPassword.length > 0) {
                scene.togglePasswordErrors(show = false)
                scene.togglePasswordSuccess(show = true)
                model.errors["password"] = false
                if (shouldCreateButtonBeEnabled()) {
                    scene.enableCreateAccountButton()
                }
            } else if (arePasswordsMatching &&
                    model.confirmPassword.isEmpty()) {
                scene.togglePasswordSuccess(show = false)
                scene.togglePasswordErrors(show = false)
                model.errors["password"] = false
                scene.disableCreateAccountButton()
            } else {
                scene.togglePasswordErrors(show = true)
                scene.togglePasswordSuccess(show = false)
                model.errors["password"] = true
                scene.disableCreateAccountButton()
            }
        }

        override fun onPasswordChangedListener(text: String) {
            model.password = text
            if(arePasswordsMatching && model.password.length > 0) {
                scene.togglePasswordErrors(show = false)
                scene.togglePasswordSuccess(show = true)
                model.errors["password"] = false
                if(shouldCreateButtonBeEnabled()) {
                    scene.enableCreateAccountButton()
                }
            } else if(arePasswordsMatching && model.password.length == 0){
                scene.togglePasswordSuccess(show = false)
                scene.togglePasswordErrors(show = false)
                model.errors["password"] = false
                scene.disableCreateAccountButton()
            }
            else {
                scene.togglePasswordErrors(show = true)
                scene.togglePasswordSuccess(show = false)
                model.errors["password"] = true
                scene.disableCreateAccountButton()
            }
        }

        override fun onCreateAccountClick() {
            if(shouldCreateButtonBeEnabled()) {
                if (!isSetRecoveryEmail) {
                    scene.showRecoveryEmailWarningDialog(
                            onRecoveryEmailWarningListener
                    )
                } else {
                    this@SignUpSceneController.submitCreateUser()
                }
            }
        }

        override fun onRegisterUserSuccess(){
            host.goToScene(MailboxParams())
        }

        override fun onBackPressed() {
            host.finishScene()
        }
    }

    private val dataSourceListener = { result: SignUpResult ->
        when (result) {
            is SignUpResult.RegisterUser -> onUserRegistered(result)
        }
    }

    private fun onUserRegistered(result: SignUpResult.RegisterUser) {
        when (result) {
            is SignUpResult.RegisterUser.Success -> {
                scene.showSuccess()
            }
            is SignUpResult.RegisterUser.Failure -> {
                       scene.showError(result.message)
                       resetWidgetsFromModel()
                       if(result.exception is ServerErrorException &&
                               result.exception.errorCode == 400) {
                           scene.isUserAvailable(userAvailable = false)
                           model.errors["username"] = true
                           scene.disableCreateAccountButton()
                       }
            }
        }
    }

    private fun submitCreateUser() {
        scene.showKeyGenerationHolder()
        val newAccount = IncompleteAccount(
                username = model.username,
                name = model.fullName,
                password = model.password,
                recoveryEmail = if (isSetRecoveryEmail) model.recoveryEmail else null
        )

        val req = SignUpRequest.RegisterUser(
                account = newAccount,
                recipientId = model.username
        )
        dataSource.submitRequest(req)
    }
    private fun resetWidgetsFromModel() {
        scene.resetSceneWidgetsFromModel(
                username = model.username,
                recoveryEmail = model.recoveryEmail,
                password = model.password,
                fullName = model.fullName
        )
    }

    val onRecoveryEmailWarningListener = object : OnRecoveryEmailWarningListener {
        override fun willAssignRecoverEmail() {
        }

        override fun denyWillAssignRecoverEmail() {
            this@SignUpSceneController.submitCreateUser()
        }
    }

    fun isUserAvailable(): Boolean {
        return model.username == "sebas"
    }

    override fun onStart() {
        dataSource.listener = dataSourceListener
        scene.showFormHolder()
        scene.initListeners(
                signUpListener = signUpListener
        )
        scene.disableCreateAccountButton()
    }

    override fun onStop() {
        dataSource.listener = null
        scene.signUpListener = null
    }

    override fun onBackPressed(): Boolean {
        return true
    }

    override fun onOptionsItemSelected(itemId: Int) {
    }

    private fun areFieldsEmpty() : Boolean {
        return model.username.isEmpty() ||
                model.fullName.isEmpty() ||
                model.password.isEmpty() ||
                model.confirmPassword.isEmpty() ||
                !model.checkTermsAndConditions
    }

    interface SignUpListener {
        fun onCreateAccountClick()
        fun onPasswordChangedListener(text: String)
        fun onConfirmPasswordChangedListener(text: String)
        fun onUsernameChangedListener(text: String)
        fun onCheckedOptionChanged(state: Boolean)
        fun onTermsAndConditionsClick()
        fun onFullNameTextChangeListener(text: String)
        fun onRecoveryEmailTextChangeListener(text: String)
        fun onBackPressed()
        fun onRegisterUserSuccess()
    }
}