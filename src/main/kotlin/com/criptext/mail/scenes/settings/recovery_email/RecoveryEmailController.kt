package com.criptext.mail.scenes.settings.recovery_email

import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.api.models.SyncStatusData
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.params.*
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailDataSource
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailRequest
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailResult
import com.criptext.mail.scenes.signin.data.LinkStatusData
import com.criptext.mail.signal.SignalClient
import com.criptext.mail.signal.SignalStoreCriptext
import com.criptext.mail.utils.EmailAddressUtils
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.ServerCodes
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource
import com.criptext.mail.utils.generaldatasource.data.GeneralRequest
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.generaldatasource.data.UserDataWriter
import com.criptext.mail.utils.ui.data.DialogResult
import com.criptext.mail.utils.ui.data.DialogType
import com.criptext.mail.utils.ui.data.TransitionAnimationData
import com.criptext.mail.validation.AccountDataValidator
import com.criptext.mail.validation.FormData
import com.criptext.mail.validation.FormInputState
import com.criptext.mail.validation.TextInput
import com.criptext.mail.websocket.WebSocketEventListener
import com.criptext.mail.websocket.WebSocketEventPublisher
import com.criptext.mail.websocket.WebSocketSingleton

class RecoveryEmailController(
        private val model: RecoveryEmailModel,
        private val scene: RecoveryEmailScene,
        private val host: IHostActivity,
        private val keyboardManager: KeyboardManager,
        private var activeAccount: ActiveAccount,
        private val storage: KeyValueStorage,
        private var websocketEvents: WebSocketEventPublisher,
        private val generalDataSource: GeneralDataSource,
        private val dataSource: RecoveryEmailDataSource)
    : SceneController(host, activeAccount, storage){

    var lastTimeConfirmationLinkSent: Long
        get() = storage.getLong(KeyValueStorage.StringKey.LastTimeConfirmationLinkSent, 0L)
        set(value) {
            storage.putLong(KeyValueStorage.StringKey.LastTimeConfirmationLinkSent, value)
        }

    override val menuResourceId: Int? = null

    private val generalDataSourceListener: (GeneralResult) -> Unit = { result ->
        when(result) {
            is GeneralResult.ResetPassword -> onResetPassword(result)
            is GeneralResult.DeviceRemoved -> onDeviceRemovedRemotely(result)
            is GeneralResult.ConfirmPassword -> onPasswordChangedRemotely(result)
            is GeneralResult.LinkAccept -> onLinkAccept(result)
            is GeneralResult.SyncAccept -> onSyncAccept(result)
            is GeneralResult.ChangeToNextAccount -> onChangeToNextAccount(result)
            is GeneralResult.ResendConfirmationLink -> onResendConfirmationEmail(result)
        }
    }

    private val recoveryEmailUIObserver = object: RecoveryEmailUIObserver(generalDataSource, host){

        override fun onSnackbarClicked() {

        }

        override fun onGeneralCancelButtonPressed(result: DialogResult) {

        }

        override fun onGeneralOkButtonPressed(result: DialogResult) {
            when(result){
                is DialogResult.DialogConfirmation -> {
                    when(result.type){
                        is DialogType.SwitchAccount -> {
                            generalDataSource.submitRequest(GeneralRequest.ChangeToNextAccount())
                        }
                        is DialogType.SignIn ->
                            host.goToScene(SignInParams(true), true)
                    }
                }
            }
        }

        override fun onOkButtonPressed(password: String) {
            generalDataSource.submitRequest(GeneralRequest.ConfirmPassword(password))
        }

        override fun onCancelButtonPressed() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(true))
        }

        override fun onForgotPasswordPressed() {
            generalDataSource.submitRequest(GeneralRequest.ResetPassword(activeAccount.recipientId,
                    EmailAddressUtils.extractEmailAddressDomain(activeAccount.userEmail)))
        }

        override fun onRecoveryEmailTextChanged(text: String) {
            val newRecoveryEmail = if (text.isEmpty()) {
                TextInput(value = text, state = FormInputState.Unknown())
            } else {
                val userInput = AccountDataValidator.validateEmailAddress(text)
                when (userInput) {
                    is FormData.Valid -> {
                        TextInput(value = userInput.value,
                                state = FormInputState.Valid())
                    }

                    is FormData.Error -> {
                        TextInput(value = text,
                                state = FormInputState.Error(userInput.message))
                    }
                }
            }
            model.newRecoveryEmail = newRecoveryEmail
            scene.setRecoveryEmailState(newRecoveryEmail.state)
            toggleChangeEmailButton()
        }

        override fun onChangeButtonPressed(text: String) {
            if(model.userData.isEmailConfirmed)
                scene.showEnterPasswordDialog()
            else {
                scene.loadChangeEmailButton(true)
                dataSource.submitRequest(RecoveryEmailRequest.ChangeRecoveryEmail(null, model.newRecoveryEmail.value))
            }

        }

        override fun onEnterPasswordOkPressed(password: String) {
            scene.dialogToggleLoad(true)
            dataSource.submitRequest(RecoveryEmailRequest.ChangeRecoveryEmail(password, model.newRecoveryEmail.value))
        }

        override fun onBackButtonPressed() {
            keyboardManager.hideKeyboard()
            val message = if(model.comesFromMailbox) ActivityMessage.ComesFromMailbox() else null
            host.goToScene(
                    params = ProfileParams(false),
                    activityMessage = message,
                    keep = false
            )
        }

        override fun onResendRecoveryLinkPressed() {
            lastTimeConfirmationLinkSent = System.currentTimeMillis()
            scene.onResendLinkTimeSet(RESEND_TIME)
            generalDataSource.submitRequest(GeneralRequest.ResendConfirmationLink())
        }
    }

    private fun toggleChangeEmailButton() {
        if(model.newRecoveryEmail.state is FormInputState.Valid) {
            scene.enableChangeButton()
        } else {
            scene.disableChangeButton()
        }
    }

    private val dataSourceListener = { result: RecoveryEmailResult ->
        when (result) {
            is RecoveryEmailResult.ChangeRecoveryEmail -> onChangeRecoveryEmail(result)
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        if(activityMessage is ActivityMessage.ComesFromMailbox)
            model.comesFromMailbox = true
        websocketEvents.setListener(webSocketEventListener)
        model.lastTimeConfirmationLinkSent = lastTimeConfirmationLinkSent
        scene.attachView(recoveryEmailUIObserver, keyboardManager, model)
        dataSource.listener = dataSourceListener
        generalDataSource.listener = generalDataSourceListener
        return false
    }

    override fun onResume(activityMessage: ActivityMessage?): Boolean {
        websocketEvents.setListener(webSocketEventListener)
        return false
    }

    private fun onResendConfirmationEmail(result: GeneralResult.ResendConfirmationLink){
        when(result) {
            is GeneralResult.ResendConfirmationLink.Success -> {
                model.lastTimeConfirmationLinkSent = lastTimeConfirmationLinkSent
                scene.showConfirmationSentDialog()
            }
            is GeneralResult.ResendConfirmationLink.Failure -> {
                scene.onResendLinkFailed()
                scene.showMessage(UIMessage(R.string.recovery_confirmation_resend_failed))
            }
        }
    }

    private fun onChangeRecoveryEmail(result: RecoveryEmailResult.ChangeRecoveryEmail){
        scene.loadChangeEmailButton(false)
        when(result) {
            is RecoveryEmailResult.ChangeRecoveryEmail.Success -> {
                model.userData.recoveryEmail = model.newRecoveryEmail.value
                model.userData.isEmailConfirmed = false
                scene.updateCurrent(model)
                scene.enterPasswordDialogDismiss()
                scene.showMessage(UIMessage(R.string.recovery_email_has_changed))
            }
            is RecoveryEmailResult.ChangeRecoveryEmail.Failure -> {
                if(result.ex is ServerErrorException){
                    when(result.ex.errorCode){
                        ServerCodes.BadRequest -> {
                            scene.enterPasswordDialogError(result.message)
                            scene.dialogToggleLoad(false)
                        }
                        else -> {
                            scene.enterPasswordDialogDismiss()
                            scene.showMessage(result.message)
                        }
                    }
                }else {
                    scene.enterPasswordDialogDismiss()
                    scene.showMessage(result.message)
                }
            }
            is RecoveryEmailResult.ChangeRecoveryEmail.EnterpriseSuspended ->
                showSuspendedAccountDialog()
        }
    }

    private fun onResetPassword(result: GeneralResult.ResetPassword){
        when(result) {
            is GeneralResult.ResetPassword.Success -> {
                scene.showForgotPasswordDialog(result.email)
            }
            is GeneralResult.ResetPassword.Failure -> {
                scene.showMessage(result.message)
            }
        }
    }

    private fun onChangeToNextAccount(result: GeneralResult.ChangeToNextAccount){
        when(result) {
            is GeneralResult.ChangeToNextAccount.Success -> {
                activeAccount = result.activeAccount
                generalDataSource.activeAccount = activeAccount
                generalDataSource.signalClient = SignalClient.Default(SignalStoreCriptext(generalDataSource.db, activeAccount))
                dataSource.activeAccount = activeAccount
                val jwts = storage.getString(KeyValueStorage.StringKey.JWTS, "")
                websocketEvents = if(jwts.isNotEmpty())
                    WebSocketSingleton.getInstance(jwts)
                else
                    WebSocketSingleton.getInstance(activeAccount.jwt)

                websocketEvents.setListener(webSocketEventListener)

                scene.dismissAccountSuspendedDialog()

                scene.showMessage(UIMessage(R.string.snack_bar_active_account, arrayOf(activeAccount.userEmail)))

                host.goToScene(
                        params = MailboxParams(),
                        activityMessage = null,
                        keep = false, deletePastIntents = true
                )
            }
        }
    }

    private fun showSuspendedAccountDialog(){
        val jwtList = storage.getString(KeyValueStorage.StringKey.JWTS, "").split(",").map { it.trim() }
        val dialogType = if(jwtList.isNotEmpty() && jwtList.size > 1) DialogType.SwitchAccount()
        else DialogType.SignIn()
        scene.showAccountSuspendedDialog(recoveryEmailUIObserver, activeAccount.userEmail, dialogType)
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onLinkDeviceDismiss(accountEmail: String) {
            host.runOnUiThread(Runnable {
                scene.dismissLinkDeviceDialog()
            })
        }

        override fun onSyncDeviceDismiss(accountEmail: String) {
            host.runOnUiThread(Runnable {
                scene.dismissSyncDeviceDialog()
            })
        }

        override fun onAccountSuspended(accountEmail: String) {
            host.runOnUiThread(Runnable {
                if (accountEmail == activeAccount.userEmail)
                    showSuspendedAccountDialog()
            })
        }

        override fun onAccountUnsuspended(accountEmail: String) {
            host.runOnUiThread(Runnable {
                if (accountEmail == activeAccount.userEmail)
                    scene.dismissAccountSuspendedDialog()
            })
        }

        override fun onSyncBeginRequest(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            host.runOnUiThread(Runnable {
                scene.showSyncDeviceAuthConfirmation(trustedDeviceInfo)
            })
        }

        override fun onSyncRequestAccept(syncStatusData: SyncStatusData) {

        }

        override fun onSyncRequestDeny() {

        }

        override fun onDeviceDataUploaded(key: String, dataAddress: String, authorizerId: Int) {

        }

        override fun onDeviceLinkAuthDeny() {

        }

        override fun onDeviceLinkAuthRequest(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            host.runOnUiThread(Runnable {
                scene.showLinkDeviceAuthConfirmation(untrustedDeviceInfo)
            })
        }

        override fun onDeviceLinkAuthAccept(linkStatusData: LinkStatusData) {

        }

        override fun onKeyBundleUploaded(deviceId: Int) {

        }

        override fun onNewEvent(recipientId: String, domain: String) {

        }

        override fun onRecoveryEmailChanged(newEmail: String) {
            model.userData.recoveryEmail = model.newRecoveryEmail.value
            model.userData.isEmailConfirmed = false
            scene.updateCurrent(model)
        }

        override fun onRecoveryEmailConfirmed() {
            model.userData.isEmailConfirmed = true
            scene.updateCurrent(model)
        }

        override fun onDeviceLocked() {
            host.runOnUiThread(Runnable {
                host.showConfirmPasswordDialog(recoveryEmailUIObserver)
            })
        }

        override fun onDeviceRemoved() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
        }

        override fun onError(uiMessage: UIMessage) {
            scene.showMessage(uiMessage)
        }
    }

    override fun onPause() {
        cleanup()
    }

    override fun onStop() {
        cleanup()
    }

    override fun onNeedToSendEvent(event: Int) {
        generalDataSource.submitRequest(GeneralRequest.UserEvent(event))
    }

    private fun cleanup(){
        websocketEvents.clearListener(webSocketEventListener)
    }

    override fun onBackPressed(): Boolean {
        recoveryEmailUIObserver.onBackButtonPressed()
        return false
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    override fun onOptionsItemSelected(itemId: Int) {

    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

    }

    companion object {
        val RESEND_TIME = 300000L
    }
}