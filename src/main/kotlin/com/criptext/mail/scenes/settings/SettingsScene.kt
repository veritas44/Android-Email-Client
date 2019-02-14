package com.criptext.mail.scenes.settings


import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewpager.widget.ViewPager
import com.criptext.mail.R
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.scenes.settings.devices.VirtualDeviceList
import com.criptext.mail.scenes.settings.labels.VirtualLabelWrapperList
import com.criptext.mail.scenes.settings.syncing.SyncBeginDialog
import com.criptext.mail.scenes.settings.views.DevicesSettingsView
import com.criptext.mail.scenes.settings.views.GeneralSettingsView
import com.criptext.mail.scenes.settings.views.LabelSettingsView
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.UIUtils
import com.criptext.mail.utils.getLocalizedUIMessage
import com.criptext.mail.utils.ui.*
import com.criptext.mail.utils.ui.data.DialogData
import com.criptext.mail.utils.uiobserver.UIObserver
import com.criptext.mail.utils.virtuallist.VirtualListView
import com.google.android.material.tabs.TabLayout

interface SettingsScene{

    fun attachView(name: String, model: SettingsModel, settingsUIObserver: SettingsUIObserver,
                   devicesListItemListener: DevicesListItemListener)
    fun showMessage(message : UIMessage)
    fun showLogoutDialog(isLastDeviceWith2FA: Boolean)
    fun showGeneralDialogWithInputPassword(dialogData: DialogData.DialogMessageData)
    fun showGeneralDialogWithInput(replyToEmail: String, dialogData: DialogData.DialogDataForReplyToEmail)
    fun showGeneralDialogConfirmation(dialogData: DialogData.DialogConfirmationData)
    fun showSyncBeginDialog()
    fun setGeneralDialogWithInputError(message: UIMessage)
    fun toggleGeneralDialogLoad(isLoading: Boolean)
    fun toggleGeneralDialogWithInputLoad(isLoading: Boolean)
    fun showMessageAndProgressDialog(message: UIMessage)
    fun showRemoveDeviceDialog(deviceId: Int, position: Int)
    fun dismissMessageAndProgressDialog()
    fun showCreateLabelDialog(keyboardManager: KeyboardManager)
    fun showConfirmPasswordDialog(observer: UIObserver)
    fun getLabelListView(): VirtualListView
    fun getDeviceListView(): VirtualListView
    fun updateUserSettings(model: SettingsModel)
    fun updateTwoFa(isChecked: Boolean)
    fun enableTwoFASwitch(isEnabled: Boolean)
    fun dismissConfirmPasswordDialog()
    fun setConfirmPasswordError(message: UIMessage)
    fun setRemoveDeviceError(message: UIMessage)
    fun removeDeviceDialogToggleLoad(loading: Boolean)
    fun removeDeviceDialogDismiss()
    fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo)
    fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo)
    fun showTwoFADialog(hasRecoveryEmailConfirmed: Boolean)
    fun setSyncContactsProgressVisisble(isVisible: Boolean)
    fun getLabelLocalizedName(name: String): String
    fun enableSyncBeginResendButton()
    fun dismissSyncBeginDialog()
    fun dismissReplyToEmailDialog()
    fun syncBeginDialogDenied()
    fun clearThemeSwitchListener()


    var settingsUIObserver: SettingsUIObserver?

    class Default(private val view: View): SettingsScene {

        private val context = view.context

        private val mViewPager: ViewPager by lazy {
            view.findViewById<ViewPager>(R.id.viewpager)
        }

        private val tabs: TabLayout by lazy {
            view.findViewById<TabLayout>(R.id.sliding_tabs)
        }

        private val backButton: ImageView by lazy {
            view.findViewById<ImageView>(R.id.mailbox_back_button)
        }

        private val labelView: LabelSettingsView by lazy {
            LabelSettingsView(view.findViewById(R.id.viewSettingsLabels),
                    context.getString(R.string.labels))
        }

        private val deviceView: DevicesSettingsView by lazy {
            DevicesSettingsView(view.findViewById(R.id.viewSettingsDevices), context.getString(R.string.devices))
        }
        private val generalView: GeneralSettingsView by lazy {
            GeneralSettingsView(view.findViewById(R.id.viewSettingsGeneral),
                    context.getString(R.string.general))
        }

        private var generalDialogWithInputPassword: GeneralDialogWithInputPassword? = null
        private var generalDialogWithInput: GeneralDialogWithInput? = null
        private var generalDialogConfirmation: GeneralDialogConfirmation? = null

        private val settingCustomLabelDialog = SettingsCustomLabelDialog(context)
        private val settingLogoutDialog = SettingsLogoutDialog(context)
        private var messageAndProgressDialog: MessageAndProgressDialog? = null
        private val settingRemoveDeviceDialog = SettingsRemoveDeviceDialog(context)
        private val confirmPassword = ConfirmPasswordDialog(context)
        private val linkAuthDialog = LinkNewDeviceAlertDialog(context)
        private val twoFADialog = Settings2FADialog(context)
        private val syncBeginDialog = SyncBeginDialog(context, UIMessage(R.string.title_sync))
        private val syncAuthDialog = SyncDeviceAlertDialog(context)

        override var settingsUIObserver: SettingsUIObserver? = null

        override fun attachView(name: String, model: SettingsModel,
                                settingsUIObserver: SettingsUIObserver,
                                devicesListItemListener: DevicesListItemListener) {

            this.settingsUIObserver = settingsUIObserver

            loadTabs(name, model, devicesListItemListener)

            backButton.setOnClickListener {
                settingsUIObserver.onBackButtonPressed()
            }
        }

        override fun showMessage(message: UIMessage) {
            val duration = Toast.LENGTH_LONG
            val toast = Toast.makeText(
                    context,
                    context.getLocalizedUIMessage(message),
                    duration)
            toast.show()
        }

        override fun showCreateLabelDialog(keyboardManager: KeyboardManager) {
            settingCustomLabelDialog.showCustomLabelDialog(settingsUIObserver, keyboardManager)
        }

        override fun showLogoutDialog(isLastDeviceWith2FA: Boolean) {
            settingLogoutDialog.showLogoutDialog(settingsUIObserver, isLastDeviceWith2FA)
        }

        override fun showMessageAndProgressDialog(message: UIMessage) {
            messageAndProgressDialog = MessageAndProgressDialog(context, message)
            messageAndProgressDialog?.showDialog()
        }

        override fun showGeneralDialogWithInputPassword(dialogData: DialogData.DialogMessageData) {
            generalDialogWithInputPassword = GeneralDialogWithInputPassword(context, dialogData)
            generalDialogWithInputPassword?.showDialog(settingsUIObserver)
        }

        override fun showGeneralDialogWithInput(replyToEmail: String, dialogData: DialogData.DialogDataForReplyToEmail) {
            generalDialogWithInput = GeneralDialogWithInput(context, dialogData)
            generalDialogWithInput?.showDialog(settingsUIObserver)
        }

        override fun showGeneralDialogConfirmation(dialogData: DialogData.DialogConfirmationData) {
            generalDialogConfirmation = GeneralDialogConfirmation(context, dialogData)
            generalDialogConfirmation?.showDialog(settingsUIObserver)
        }

        override fun setGeneralDialogWithInputError(message: UIMessage) {
            generalDialogWithInputPassword?.setPasswordError(message)
        }

        override fun toggleGeneralDialogLoad(isLoading: Boolean) {
            generalDialogWithInputPassword?.toggleLoad(isLoading)
        }

        override fun toggleGeneralDialogWithInputLoad(isLoading: Boolean) {
            generalDialogWithInput?.toggleLoad(isLoading)
        }

        override fun showRemoveDeviceDialog(deviceId: Int, position: Int) {
            settingRemoveDeviceDialog.showRemoveDeviceDialog(settingsUIObserver, deviceId, position)
        }

        override fun dismissMessageAndProgressDialog() {
            messageAndProgressDialog?.dismiss()
        }

        override fun dismissReplyToEmailDialog() {
            generalDialogWithInput?.dismiss()
        }

        override fun showConfirmPasswordDialog(observer: UIObserver) {
            confirmPassword.showDialog(observer)
        }

        override fun dismissConfirmPasswordDialog() {
            confirmPassword.dismissDialog()
        }

        override fun setConfirmPasswordError(message: UIMessage) {
            confirmPassword.setPasswordError(message)
        }

        override fun setRemoveDeviceError(message: UIMessage) {
            settingRemoveDeviceDialog.setPasswordError(message)
        }

        override fun removeDeviceDialogToggleLoad(loading: Boolean) {
            settingRemoveDeviceDialog.toggleLoad(loading)
        }

        override fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            if(linkAuthDialog.isShowing() != null && linkAuthDialog.isShowing() == false)
                linkAuthDialog.showLinkDeviceAuthDialog(settingsUIObserver, untrustedDeviceInfo)
            else if(linkAuthDialog.isShowing() == null)
                linkAuthDialog.showLinkDeviceAuthDialog(settingsUIObserver, untrustedDeviceInfo)
        }

        override fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            if(syncAuthDialog.isShowing() != null && syncAuthDialog.isShowing() == false)
                syncAuthDialog.showLinkDeviceAuthDialog(settingsUIObserver, trustedDeviceInfo)
            else if(syncAuthDialog.isShowing() == null)
                syncAuthDialog.showLinkDeviceAuthDialog(settingsUIObserver, trustedDeviceInfo)
        }

        override fun removeDeviceDialogDismiss() {

            settingRemoveDeviceDialog.dismissDialog()
        }

        override fun getLabelListView(): VirtualListView {
            return labelView.getListView()
        }

        override fun getDeviceListView(): VirtualListView {
            return deviceView.getListView()
        }

        override fun showTwoFADialog(hasRecoveryEmailConfirmed: Boolean) {
            twoFADialog.showLogoutDialog(hasRecoveryEmailConfirmed)
        }

        override fun showSyncBeginDialog() {
            syncBeginDialog.showDialog(settingsUIObserver)
        }

        override fun dismissSyncBeginDialog() {
            syncBeginDialog.dismiss()
        }

        override fun syncBeginDialogDenied() {
            syncBeginDialog.showFailedSync()
        }

        override fun enableSyncBeginResendButton() {
            syncBeginDialog.enableResendButton()
        }

        override fun setSyncContactsProgressVisisble(isVisible: Boolean) {
            generalView.setSyncContactsProgressVisisble(isVisible)
        }

        private fun loadTabs(name: String, model: SettingsModel, devicesListItemListener: DevicesListItemListener) {
            setupViewPager(mViewPager, name, model, devicesListItemListener)
            tabs.setupWithViewPager(mViewPager)
        }

        override fun updateUserSettings(model: SettingsModel) {
            generalView.setRecoveryEmailConfirmationText(model.isEmailConfirmed)
            generalView.enable2FASwitch(true)
            generalView.enableReplyTo(true)
            generalView.enablePrivacyOption(true)
            generalView.set2FA(model.hasTwoFA)
            generalView.setDarkTheme(
                    AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            )
            if(model.devices.size == 1)
                generalView.enable2FASwitch(true)
        }

        override fun clearThemeSwitchListener() {
            generalView.disableDarkThemeSwitch()
        }

        override fun enableTwoFASwitch(isEnabled: Boolean) {
            generalView.enable2FASwitch(isEnabled)
        }

        override fun updateTwoFa(isChecked: Boolean) {
            generalView.set2FA(isChecked)
        }

        override fun getLabelLocalizedName(name: String): String {
            return context.getLocalizedUIMessage(
                    UIUtils.getLocalizedSystemLabelName(name)
            )
        }

        private fun setupViewPager(viewPager: ViewPager, name: String, model: SettingsModel,
                                   devicesListItemListener: DevicesListItemListener) {

            val adapter = ViewPagerAdapter()
            generalView.setExternalListeners(settingsUIObserver)
            adapter.addView(generalView)

            labelView.initView(VirtualLabelWrapperList(model), settingsUIObserver)
            adapter.addView(labelView)

            deviceView.initView(VirtualDeviceList(model), devicesListItemListener)
            adapter.addView(deviceView)
            viewPager.offscreenPageLimit = 2
            viewPager.adapter = adapter
        }

    }
}