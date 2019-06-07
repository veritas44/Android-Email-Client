package com.criptext.mail.scenes.signin.data

import com.criptext.mail.api.HttpClient
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.SignInLocalDB
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.dao.SignUpDao
import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.Contact
import com.criptext.mail.scenes.signin.workers.AuthenticateUserWorker
import com.criptext.mail.scenes.signup.data.RegisterUserTestUtils
import com.criptext.mail.services.MessagingInstance
import com.criptext.mail.signal.SignalKeyGenerator
import com.criptext.mail.utils.EmailAddressUtils
import com.karumi.kotlinsnapshot.matchWithSnapshot
import io.mockk.*
import org.amshove.kluent.`should be instance of`
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Created by gabriel on 5/18/18.
 */

class AuthenticateUserWorkerTest {
    private lateinit var keyGenerator: SignalKeyGenerator
    private lateinit var httpClient: HttpClient
    private lateinit var signUpDao: SignUpDao
    private lateinit var storage: KeyValueStorage
    private lateinit var accountDao: AccountDao
    private lateinit var messagingInstance: MessagingInstance
    private lateinit var db: SignInLocalDB
    private lateinit var account: Account


    @Before
    fun setup() {

        keyGenerator = mockk()
        httpClient = mockk()
        signUpDao = mockk()
        storage = mockk(relaxed = true)
        accountDao = mockk()
        account = mockk()
        messagingInstance = mockk()
        db = mockk()


        every { messagingInstance.token } returns ""
    }

    private fun newWorker(username: String, password: String): AuthenticateUserWorker =
            AuthenticateUserWorker(signUpDao = signUpDao, keyValueStorage = storage, httpClient = httpClient,
                    keyGenerator = keyGenerator, userData = UserData(username, Contact.mainDomain, password),
                    publishFn = {}, accountDao = accountDao, messagingInstance = messagingInstance, db = db,
                    isMultiple = false)


    @Test
    fun `should send login request and store new account data without errors`() {
        val worker = newWorker("tester", "securePassword123")

        // has no previous sign in session stored
        every { storage.getString(KeyValueStorage.StringKey.SignInSession, "") } returns ""

        val authRequestSlot = CapturingSlot<JSONObject>()
        val postKeyBundleRequestSlot = CapturingSlot<JSONObject>()
        val mockedAuthResponse = SignInSession(token = "__JWTOKEN__", deviceId = 2, name = "A Tester")
                    .toJSON().toString()
        val jsonObject = JSONObject()
        jsonObject.put("token", "__JWTOKEN__")
        jsonObject.put("refreshToken", "__REFRESH__")
        every {
            httpClient.post("/user/auth", null, capture(authRequestSlot)).body
        } returns mockedAuthResponse
        every {
            httpClient.post("/keybundle", "__JWTOKEN__", capture(postKeyBundleRequestSlot)).body
        } returns jsonObject.toString()

        every {
            httpClient.put("/keybundle/pushtoken", "__JWTOKEN__", any<JSONObject>()).body
        } returns mockedAuthResponse


        every {
            keyGenerator.register("tester", 2)
        } returns RegisterUserTestUtils.createRegistrationBundles("tester", 2)

        val extraStepsSlot = CapturingSlot<Runnable>()
        every {
            signUpDao.insertNewAccountData(account = any(), preKeyList = any(),
                    signedPreKey = any(), extraRegistrationSteps = capture(extraStepsSlot),
                    defaultLabels = any(), accountDao = any())
        } answers { extraStepsSlot.captured.run() }

        every { accountDao.updateJwt("tester", "__JWTOKEN__") } just Runs
        every { accountDao.updateRefreshToken("tester", "__REFRESH__") } just Runs
        every { accountDao.updateActiveInAccount() } just Runs
        every { accountDao.getLoggedInAccount() } returns Account(id = 1, recipientId = "tester", deviceId = 2,
                name = "A Tester", registrationId = 1,
                identityKeyPairB64 = "_IDENTITY_", jwt = "__JWTOKEN__",
                signature = "", refreshToken = "__REFRESH__", isActive = true, domain = "criptext.com", isLoggedIn = true,
                lastTimeBackup = null, wifiOnly = true, hasCloudBackup = false, autoBackupFrequency = 0, backupPassword = null)

        val result = worker.work(mockk())

        result `should be instance of` SignInResult.AuthenticateUser.Success::class.java

        // verify data got stored
        verify {
            storage.putString(KeyValueStorage.StringKey.SignInSession, mockedAuthResponse)
        }
        verify {
            storage.putString(KeyValueStorage.StringKey.ActiveAccount,
                    """{"signature":"","jwt":"__JWTOKEN__","domain":"criptext.com","name":"A Tester","recipientId":"tester","id":1,"deviceId":2,"refreshToken":"__REFRESH__"}""")
        }

        // request snapshots
        authRequestSlot.captured.toString(4)
                .matchWithSnapshot("should send login request with the right shape")
        postKeyBundleRequestSlot.captured.toString(4)
                .matchWithSnapshot("should upload keybundle with the right shape")
    }

     @Test
    fun `if post keybundle fails, should not store anything locally except for the sign in session`() {
        val worker = newWorker("tester", "securePassword123")

        // has no previous sign in session stored
        every { storage.getString(KeyValueStorage.StringKey.SignInSession, "") } returns ""

        val mockedAuthResponse = SignInSession(token = "__JWTOKEN__", deviceId = 2, name = "A Tester")
                    .toJSON().toString()
        every {
            httpClient.post("/user/auth", null, any<JSONObject>()).body
        } returns mockedAuthResponse
        every {
            httpClient.post("/keybundle", "__JWTOKEN__", any<JSONObject>())
        } throws SocketTimeoutException()

         every {
             httpClient.put("/keybundle/pushtoken", "__JWTOKEN__", any<JSONObject>()).body
         } returns mockedAuthResponse


        every {
            keyGenerator.register("tester", 2)
        } returns RegisterUserTestUtils.createRegistrationBundles("tester", 2)

        val extraStepsSlot = CapturingSlot<Runnable>()
        every {
            signUpDao.insertNewAccountData(account = any(), preKeyList = any(),
                    signedPreKey = any(), extraRegistrationSteps = capture(extraStepsSlot),
                    defaultLabels = any(), accountDao = any())
        } answers { extraStepsSlot.captured.run() }

        val result = worker.work(mockk())

        result `should be instance of` SignInResult.AuthenticateUser.Failure::class.java

        // verify data got stored
        verify {
            storage.putString(KeyValueStorage.StringKey.SignInSession, mockedAuthResponse)
        }
        verify(inverse = true) {
            storage.putString(KeyValueStorage.StringKey.ActiveAccount, any())
        }

    }

}