package com.criptext.mail

import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.Label
import com.criptext.mail.email_preview.EmailPreview
import com.criptext.mail.push.data.IntentExtrasData
import com.criptext.mail.push.services.LinkDeviceActionService
import com.criptext.mail.push.services.NewMailActionService
import com.criptext.mail.push.services.SyncDeviceActionService
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.SceneModel
import com.criptext.mail.scenes.composer.ComposerModel
import com.criptext.mail.scenes.composer.data.ComposerType
import com.criptext.mail.scenes.emaildetail.EmailDetailSceneModel
import com.criptext.mail.scenes.linking.LinkingModel
import com.criptext.mail.scenes.mailbox.MailboxActivity
import com.criptext.mail.scenes.mailbox.MailboxSceneModel
import com.criptext.mail.scenes.mailbox.emailsource.EmailSourceModel
import com.criptext.mail.scenes.params.*
import com.criptext.mail.scenes.search.SearchSceneModel
import com.criptext.mail.scenes.settings.SettingsActivity
import com.criptext.mail.scenes.settings.SettingsModel
import com.criptext.mail.scenes.settings.changepassword.ChangePasswordActivity
import com.criptext.mail.scenes.settings.changepassword.ChangePasswordModel
import com.criptext.mail.scenes.settings.privacyandsecurity.PrivacyAndSecurityModel
import com.criptext.mail.scenes.settings.privacyandsecurity.pinscreen.LockScreenActivity
import com.criptext.mail.scenes.settings.profile.ProfileModel
import com.criptext.mail.scenes.settings.recovery_email.RecoveryEmailModel
import com.criptext.mail.scenes.settings.replyto.ReplyToModel
import com.criptext.mail.scenes.settings.signature.SignatureModel
import com.criptext.mail.scenes.settings.syncing.SyncingModel
import com.criptext.mail.scenes.signin.SignInActivity
import com.criptext.mail.scenes.signin.SignInSceneModel
import com.criptext.mail.scenes.signup.SignUpActivity
import com.criptext.mail.scenes.signup.SignUpSceneModel
import com.criptext.mail.services.MessagingInstance
import com.criptext.mail.splash.SplashActivity
import com.criptext.mail.utils.*
import com.criptext.mail.utils.compat.PermissionUtilsCompat
import com.criptext.mail.utils.dialog.SingletonProgressDialog
import com.criptext.mail.utils.file.FileUtils
import com.criptext.mail.utils.file.IntentUtils
import com.criptext.mail.utils.mailtemplates.CriptextMailTemplate
import com.criptext.mail.utils.mailtemplates.FWMailTemplate
import com.criptext.mail.utils.mailtemplates.REMailTemplate
import com.criptext.mail.utils.mailtemplates.SupportMailTemplate
import com.criptext.mail.utils.ui.ActivityMenu
import com.criptext.mail.utils.ui.StartGuideTapped
import com.github.omadahealth.lollipin.lib.PinCompatActivity
import com.github.omadahealth.lollipin.lib.managers.AppLock
import com.google.firebase.analytics.FirebaseAnalytics
import droidninja.filepicker.FilePickerBuilder
import droidninja.filepicker.FilePickerConst
import kotlinx.android.synthetic.main.contact_item.*
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper
import java.io.File
import java.lang.Exception
import java.util.*


/**
 * Base class for all of our activities. If you extend this class you don't need to implement
 * `onCreate`, `onStart` or `onStop`. This class will create your controller with `initController`
 * and then forward `onStart` and `onStop` events to it.
 * Created by gabriel on 2/14/18.
 */

abstract class BaseActivity: PinCompatActivity(), IHostActivity {

    /**
     * Resource Id of the layout to be used by this activity. This value will be used on `onCreate`
     * to inflate the activity's views. Your layout must contain a toolbar somewhere.
     */
    abstract val layoutId: Int

    private val progressDialog: SingletonProgressDialog by lazy { SingletonProgressDialog(this) }
    private val storage: KeyValueStorage by lazy { KeyValueStorage.SharedPrefs(this) }

    /**
     * Resource Id of your activity's toolbar. After the layout is inflated, BaseActivity will call
     * `findViewById` with this value to get the toolbar and set it as action bar. If no toolbar
     * is found your activity will crash.
     */
    abstract val toolbarId: Int?

    lateinit var controller: SceneController
    lateinit var model: Any
    var mFirebaseAnalytics: FirebaseAnalytics? = null

    private val handler = Handler()

    /**
     * Called during `onCreate` to create a controller for this activity given the current active
     * model which is passed as parameter. `BaseActivity` will call this once and keep a private
     * reference to the controller in order to forward events like `onStart` and `onStop`. There
     * is no need for your activity to keep another reference to the controller.
     * @param receivedModel The model that your controller should use. You should coerce this value
     * into the type that your controller expects.
     */
    abstract fun initController(receivedModel: Any): SceneController
    protected val photoUtil = PhotoUtil.Default()

    private fun getCachedModel(): Any? {
        return cachedModels[javaClass]
    }

    private fun getSavedInstanceModel(savedInstanceState: Bundle?): SceneModel? {
        if(savedInstanceState == null) return null
        return if(savedInstanceState.getString("type") != null){
            when(savedInstanceState.getString("type")){
                EMAIL_DETAIL_MODEL -> {
                    EmailDetailSceneModel(
                            threadId = savedInstanceState.getString("threadId")!!,
                            currentLabel = Label.fromJSON(savedInstanceState.getString("currentLabel")!!),
                            threadPreview = EmailPreview.emailPreviewFromJSON(savedInstanceState.getString("threadPreview")!!),
                            doReply = savedInstanceState.getBoolean("doReply")
                    )
                }
                COMPOSER_MODEL -> {
                    ComposerModel(
                            type = ComposerType.fromJSON(savedInstanceState.getString("composerType")!!, this)
                    )
                }
                PRIVACY_AND_SECURITY_MODEL -> {
                    PrivacyAndSecurityModel(
                            hasReadReceipts = savedInstanceState.getBoolean("hasReadReceipts")
                    )
                }
                PROFILE_MODEL -> {
                    ProfileModel(
                            name = savedInstanceState.getString("name")!!,
                            email = savedInstanceState.getString("email")!!,
                            exitToMailbox = savedInstanceState.getBoolean("exitToMailbox")
                    )
                }
                RECOVERY_EMAIL_MODEL -> {
                    RecoveryEmailModel(
                            isEmailConfirmed = savedInstanceState.getBoolean("isEmailConfirmed"),
                            recoveryEmail = savedInstanceState.getString("recoveryEmail")!!
                    )
                }
                REPLY_TO_MODEL -> {
                    ReplyToModel(
                            replyToEmail = savedInstanceState.getString("replyToEmail")!!
                    )
                }
                SIGNATURE_MODEL -> {
                    SignatureModel(
                            recipientId = savedInstanceState.getString("recipientId")!!
                    )
                }
                else -> null
            }
        }else
            null
    }

    private fun dismissAllNotifications() {
        val notificationManager = this.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        storage.getInt(KeyValueStorage.StringKey.NewMailNotificationCount, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (storage.getBool(KeyValueStorage.StringKey.HasDarkTheme, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            setTheme(R.style.DarkAppTheme)
        }else{
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            setTheme(R.style.AppTheme)
        }
        super.onCreate(savedInstanceState)
        setContentView(layoutId)

        val toolbarId = this.toolbarId
        if(toolbarId != null) {
            val toolbar = findViewById<Toolbar>(toolbarId)
            setSupportActionBar(toolbar)
        }

        val savedInstanceModel = getSavedInstanceModel(savedInstanceState)
        val cacheModel = if(savedInstanceState == null || savedInstanceModel == null) getCachedModel()
        else savedInstanceModel

        if(cacheModel == null){
            restartApplication()
            return
        }else{
            model = cacheModel
        }
        try {
            controller = initController(model)
        } catch (ex: Exception) {
            restartApplication()
        }
    }

    override fun onStart() {
        super.onStart()
        dismissAllNotifications()
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)

        if (controller.onStart(activityMessage))
            activityMessage = null
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        mFirebaseAnalytics = null
        super.onStop()
        controller.onStop()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if(item.isCheckable){
            item.isChecked = true
        }
        controller.onOptionsItemSelected(itemId)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentModel = model
        when(currentModel) {
            is EmailDetailSceneModel -> {
                outState.putString("type", EMAIL_DETAIL_MODEL)
                outState.putString("threadId", currentModel.threadId)
                outState.putString("currentLabel", Label.toJSON(currentModel.currentLabel).toString())
                outState.putString("threadPreview", EmailPreview.emailPreviewToJSON(currentModel.threadPreview))
                outState.putBoolean("doReply", currentModel.doReply)
            }
            is ComposerModel -> {
                outState.putString("type", COMPOSER_MODEL)
                outState.putString("composerType", ComposerType.toJSON(currentModel.type))
            }
            is PrivacyAndSecurityModel -> {
                outState.putString("type", PRIVACY_AND_SECURITY_MODEL)
                outState.putBoolean("hasReadReceipts", currentModel.hasReadReceipts)
            }
            is ProfileModel -> {
                outState.putString("type", PROFILE_MODEL)
                outState.putString("name", currentModel.name)
                outState.putString("email", currentModel.email)
                outState.putBoolean("exitToMailbox", currentModel.exitToMailbox)
            }
            is RecoveryEmailModel -> {
                outState.putString("type", RECOVERY_EMAIL_MODEL)
                outState.putBoolean("isEmailConfirmed", currentModel.isEmailConfirmed)
                outState.putString("recoveryEmail", currentModel.recoveryEmail)
            }
            is ReplyToModel -> {
                outState.putString("type", REPLY_TO_MODEL)
                outState.putString("replyToEmail", currentModel.replyToEmail)
            }
            is SignatureModel -> {
                outState.putString("type", SIGNATURE_MODEL)
                outState.putString("recipientId", currentModel.recipientId)
            }
        }
    }

    override fun onBackPressed() {
        val shouldCallSuper = controller.onBackPressed()
        if (shouldCallSuper) super.onBackPressed()
    }

    private fun startActivity(activityClass: Class<*>, isExitCompletely: Boolean  = false) {
        val intent = Intent(this, activityClass)
        if(isExitCompletely)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if(!this::controller.isInitialized) return false
        val activeSceneMenu = controller.menuResourceId
        if(activeSceneMenu != null) menuInflater.inflate(activeSceneMenu, menu)
        controller.onMenuChanged(ActivityMenu(menu))
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase))
    }

    private fun createNewSceneFromParams(params: SceneParams): Any {
        return when(params) {
            is SearchParams -> SearchSceneModel()
            is SignUpParams -> SignUpSceneModel()
            is SignInParams -> SignInSceneModel()
            is MailboxParams -> MailboxSceneModel(params.showWelcome)
            is  EmailDetailParams -> EmailDetailSceneModel(params.threadId,
                    params.currentLabel, params.threadPreview, params.doReply)
            is ComposerParams -> ComposerModel(params.type)
            is SettingsParams -> SettingsModel(params.hasChangedTheme)
            is SignatureParams -> SignatureModel(params.recipientId)
            is RecoveryEmailParams -> RecoveryEmailModel(params.isConfirmed, params.recoveryEmail)
            is ChangePasswordParams -> ChangePasswordModel()
            is LinkingParams -> LinkingModel(params.email, params.deviceId, params.randomId, params.deviceType)
            is PrivacyAndSecurityParams -> PrivacyAndSecurityModel(params.hasReadReceipts)
            is SyncingParams -> SyncingModel(params.email, params.deviceId, params.randomId,
                    params.deviceType, params.authorizerName)
            is EmailSourceParams -> EmailSourceModel(params.emailSource)
            is ReplyToParams -> ReplyToModel(params.replyToEmail)
            is ProfileParams -> ProfileModel(params.name, params.email, params.exitToMailbox)
            else -> throw IllegalArgumentException("Don't know how to create a model from ${params.javaClass}")
        }
    }

    override fun refreshToolbarItems() {
        this.invalidateOptionsMenu()
    }

    override fun getLocalizedString(message: UIMessage): String {
        return getLocalizedUIMessage(message)
    }

    override fun goToScene(params: SceneParams, keep: Boolean, deletePastIntents: Boolean) {
        val newSceneModel = createNewSceneFromParams(params)
        cachedModels[params.activityClass] = newSceneModel
        startActivity(params.activityClass, deletePastIntents)

        if (! keep) finish()
    }

    override fun showStartGuideView(view: View, title: Int, dimension: Int) {
        val showStartGuideEmail = StartGuideTapped(this)
        showStartGuideEmail.showViewTapped(
                view,
                this,
                title,
                dimension)
    }

    override fun postDelay(runnable: Runnable, delayMilliseconds: Long) {
        handler.postDelayed(runnable, delayMilliseconds)
    }

    override fun exitToScene(params: SceneParams, activityMessage: ActivityMessage?,
                             forceAnimation: Boolean, deletePastIntents: Boolean) {
        BaseActivity.activityMessage = activityMessage
        finish()
        if(forceAnimation) {
            overridePendingTransition(0, R.anim.slide_out_right)
        }
        goToScene(params, false, deletePastIntents)
    }

    override fun getIntentExtras(): IntentExtrasData? {
        if(intent.extras != null && !intent.extras.isEmpty) {
            PinLockUtils.disablePinLock()
            when(intent.action){
                Intent.ACTION_MAIN ->    {
                    val threadId = intent.extras.get(MessagingInstance.THREAD_ID).toString()
                    if(intent.extras != null) {
                        for (key in intent.extras.keySet()){
                            intent.removeExtra(key)
                        }
                    }
                    return IntentExtrasData.IntentExtrasDataMail(intent.action, threadId)
                }
                LinkDeviceActionService.APPROVE ->    {
                    val uuid = intent.extras.get("randomId").toString()
                    val deviceType = DeviceUtils.getDeviceType(intent.extras.getInt("deviceType"))
                    val version = intent.extras.getInt("version")
                    if(intent.extras != null) {
                        for (key in intent.extras.keySet()){
                            intent.removeExtra(key)
                        }
                    }
                    return IntentExtrasData.IntentExtrasDataDevice(intent.action, uuid, deviceType, version)
                }
                SyncDeviceActionService.APPROVE ->    {
                    val uuid = intent.extras.get("randomId").toString()
                    val deviceType = DeviceUtils.getDeviceType(intent.extras.getInt("deviceType"))
                    val version = intent.extras.getInt("version")
                    val deviceId = intent.extras.getInt("deviceId")
                    val deviceName = intent.extras.getString("deviceName")
                    if(intent.extras != null) {
                        for (key in intent.extras.keySet()){
                            intent.removeExtra(key)
                        }
                    }
                    return IntentExtrasData.IntentExtrasSyncDevice(intent.action, uuid, deviceId, deviceName, deviceType, version)
                }
                NewMailActionService.REPLY -> {
                    val threadId = intent.extras.get(MessagingInstance.THREAD_ID).toString()
                    val metadataKey = intent.extras.getLong("metadataKey")
                    if(intent.extras != null) {
                        for (key in intent.extras.keySet()){
                            intent.removeExtra(key)
                        }
                    }
                    return IntentExtrasData.IntentExtrasReply(intent.action, threadId, metadataKey)
                }
                Intent.ACTION_VIEW -> {
                    val mailTo = intent.data
                    if(mailTo.toString().contains("mailto:"))
                        return IntentExtrasData.IntentExtrasMailTo(intent.action, mailTo.toString().removePrefix("mailto:"))
                }
                Intent.ACTION_SEND,
                Intent.ACTION_SEND_MULTIPLE -> {
                    val data = intent
                    if(data != null) {
                        val clipData = data.clipData
                        if(clipData == null) {
                            data.data?.also { uri ->
                                val attachment = FileUtils.getPathAndSizeFromUri(uri, contentResolver, this)
                                if (attachment != null)
                                    return IntentExtrasData.IntentExtrasSend(intent.action, listOf(attachment))
                            }
                        }else{
                            val attachmentList = mutableListOf<Pair<String, Long>>()
                            for (i in 0 until clipData.itemCount) {
                                clipData.getItemAt(i).also { item ->
                                    if (item.uri != null) {
                                        val attachment = FileUtils.getPathAndSizeFromUri(item.uri,
                                                contentResolver, this)
                                        if (attachment != null)
                                            attachmentList.add(attachment)
                                    }
                                }
                            }
                            if (attachmentList.isNotEmpty())
                                return IntentExtrasData.IntentExtrasSend(intent.action, attachmentList)
                        }
                    }
                }
            }


        }
        return null
    }

    override fun finishScene() {
        finish()
    }

    private fun restartApplication() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    override fun showDialog(message: UIMessage) {
        progressDialog.show(getLocalizedString(message))
    }

    override fun dismissDialog() {
        progressDialog.dismiss()
    }

    override fun launchExternalActivityForResult(params: ExternalActivityParams) {
        when(params){
            is ExternalActivityParams.ProfileImagePicker -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, FilePickerConst.REQUEST_CODE_PHOTO)
            }
            is ExternalActivityParams.FilePicker -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    type = "*/*"
                }
                startActivityForResult(intent, FilePickerConst.REQUEST_CODE_DOC)
            }
            is ExternalActivityParams.ImagePicker -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    val mimeTypes = arrayOf("image/*", "video/*")
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                }
                startActivityForResult(intent, FilePickerConst.REQUEST_CODE_PHOTO)
            }
            is ExternalActivityParams.Camera -> {
                val file = photoUtil.createImageFile()
                if(file != null) {
                    val photoIntent = IntentUtils.createIntentToOpenCamera(this, file)
                    if (photoIntent.resolveActivity(this.packageManager) != null)
                        startActivityForResult(photoIntent, PhotoUtil.REQUEST_CODE_CAMERA)
                }
            }
            is ExternalActivityParams.FilePresent -> {
                val file = File(params.filepath)
                val newIntent = IntentUtils.createIntentToOpenFileInExternalApp(this, file)
                startActivity(newIntent)
            }
            is ExternalActivityParams.PinScreen -> {
                if(params.isFirstTime) {
                    val intent = Intent(this, LockScreenActivity::class.java)
                    intent.putExtra(AppLock.EXTRA_TYPE, AppLock.ENABLE_PINLOCK)
                    startActivityForResult(intent, ExternalActivityParams.PIN_REQUEST_CODE)
                }else{
                    val intent = Intent(this, LockScreenActivity::class.java)
                    intent.putExtra(AppLock.EXTRA_TYPE, AppLock.CHANGE_PIN)
                    startActivityForResult(intent, ExternalActivityParams.PIN_REQUEST_CODE)
                }
            }
            is ExternalActivityParams.InviteFriend -> {
                val share = Intent(android.content.Intent.ACTION_SEND)
                share.type = "text/plain"
                share.putExtra(Intent.EXTRA_SUBJECT, "Invite a Friend")
                share.putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_text))
                startActivity(Intent.createChooser(share, getString(R.string.invite_title)))

                val bundle = Bundle()
                bundle.putString("app_source", "Unknown")
                mFirebaseAnalytics?.logEvent("invite_friend", bundle)
            }
            is ExternalActivityParams.OpenGooglePlay -> {
                // you can also use BuildConfig.APPLICATION_ID
                val appId = getPackageName()
                val rateIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$appId"))
                var marketFound = false

                // find all applications able to handle our rateIntent
                val otherApps = getPackageManager()
                        .queryIntentActivities(rateIntent, 0)
                for (otherApp in otherApps) {
                    // look for Google Play application
                    if (otherApp.activityInfo.applicationInfo.packageName == "com.android.vending") {

                        val otherAppActivity = otherApp.activityInfo
                        val componentName = ComponentName(
                                otherAppActivity.applicationInfo.packageName,
                                otherAppActivity.name
                        )
                        // make sure it does NOT open in the stack of your activity
                        rateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // task reparenting if needed
                        rateIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        // if the Google Play was already open in a search result
                        //  this make sure it still go to the app page you requested
                        rateIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // this make sure only the Google Play app is allowed to
                        // intercept the intent
                        rateIntent.component = componentName
                        startActivity(rateIntent)
                        marketFound = true
                        break

                    }
                }

                // if GP not present on device, open web browser
                if (!marketFound) {
                    val webIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=$appId"))
                    startActivity(webIntent)
                }
            }
        }
    }

    override fun getContentResolver(): ContentResolver? {
        return this.applicationContext.contentResolver
    }

    override fun getHandler(): Handler? {
        return handler
    }

    override fun setAppTheme(themeResource: Int) {
        setTheme(themeResource)
    }

    override fun contextMenuRegister(view: View) {
        registerForContextMenu(view)
    }

    override fun checkPermissions(requestCode: Int, permission: String): Boolean =
        if (PermissionUtilsCompat.checkPermission(applicationContext, permission)) {
            true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            false
        }

    override fun getMailTemplate(type: CriptextMailTemplate.TemplateType): CriptextMailTemplate {
        return when (type) {
            CriptextMailTemplate.TemplateType.SUPPORT -> SupportMailTemplate(this)
            CriptextMailTemplate.TemplateType.FW -> FWMailTemplate(this)
            CriptextMailTemplate.TemplateType.RE -> REMailTemplate(this)
        }
    }

    protected fun setActivityMessage(message: ActivityMessage?) {
        activityMessage = message
    }

    companion object {
        private val cachedModels = HashMap<Class<*>, Any>()
        private var activityMessage: ActivityMessage? = null

        init {
            // set initial state
            cachedModels[MailboxActivity::class.java] = MailboxSceneModel()
            cachedModels[SignInActivity::class.java] = SignInSceneModel()
            cachedModels[SignUpActivity::class.java] = SignUpSceneModel()
            cachedModels[SettingsActivity::class.java] = SettingsModel()
            cachedModels[ChangePasswordActivity::class.java] = ChangePasswordModel()
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun setCachedModel(clazz: Class<*>, model: Any) {
            cachedModels[clazz] = model
        }

        private const val EMAIL_DETAIL_MODEL = "EmailDetailModel"
        private const val COMPOSER_MODEL = "ComposerModel"
        private const val PRIVACY_AND_SECURITY_MODEL = "PrivacyAndSecurityModel"
        private const val PROFILE_MODEL = "ProfileModel"
        private const val RECOVERY_EMAIL_MODEL = "RecoveryEmailModel"
        private const val REPLY_TO_MODEL = "ReplyToModel"
        private const val SIGNATURE_MODEL = "SignatureModel"
    }

    enum class RequestCode {
        writeAccess, readAccess
    }
}
