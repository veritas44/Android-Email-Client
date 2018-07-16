package com.email.scenes.mailbox.data

import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.email.androidtest.TestActivity
import com.email.androidtest.TestDatabase
import com.email.db.*
import com.email.db.models.*
import com.email.signal.*
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat

@RunWith(AndroidJUnit4::class)
class UserDataWriterTest {
    @get:Rule
    val mActivityRule = ActivityTestRule(TestActivity::class.java)

    private lateinit var db: TestDatabase

    private val keyGenerator = SignalKeyGenerator.Default()

    private val bobContact = Contact(email = "bob@jigl.com", name = "Bob", id = 1)
    private val joeContact = Contact(email = "joe@jigl.com", name = "Joe", id = 2)

    private val labelOne = Label(id = 1, color = "red", text = MailFolders.ALL_MAIL, type = LabelTypes.SYSTEM, visible = true)
    private val labelTwo = Label(id = 2, color = "blue", text = MailFolders.INBOX, type = LabelTypes.SYSTEM, visible = true)

    private val emailOne = Email(id = 1, content = "contents 1", date = SimpleDateFormat("dd/MM/yyyy").parse("21/12/2012"),
            delivered = DeliveryTypes.DELIVERED, isMuted = false, messageId = "id_1", metadataKey = 123,
            preview = "cont", secure = true, subject = "subject 1", threadId = "", unread = true)
    private val emailTwo = Email(id = 2, content = "contents 2", date = SimpleDateFormat("dd/MM/yyyy").parse("21/12/2012"),
            delivered = DeliveryTypes.DELIVERED, isMuted = false, messageId = "id_2", metadataKey = 456,
            preview = "cont", secure = true, subject = "subject 2", threadId = "", unread = true)

    private val fileOne = CRFile(id = 1, date = SimpleDateFormat("dd/MM/yyyy").parse("21/12/2012"), emailId = 1, name = "this.txt",
            readOnly = true, size = 12, status = 0, token = "txt")
    private val fileTwo = CRFile(id = 2, date = SimpleDateFormat("dd/MM/yyyy").parse("21/12/2012"), emailId = 2, name = "that.txt",
            readOnly = true, size = 14, status = 0, token = "txt")

    private val emailLabel1 = EmailLabel(emailId = 1, labelId = 1)
    private val emailLabel2 = EmailLabel(emailId = 2, labelId = 2)

    private val emailContact1 = EmailContact(id = 1, emailId = 1, contactId = 1, type = ContactTypes.TO)
    private val emailContact2 = EmailContact(id = 2, emailId = 2, contactId = 2, type = ContactTypes.FROM)

    private val deviceLinkFileExpectedContent = listOf("{\"table\":\"contact\",\"object\":{\"id\":1,\"email\":\"bob@jigl.com\",\"name\":\"Bob\"}}",
    "{\"table\":\"contact\",\"object\":{\"id\":2,\"email\":\"joe@jigl.com\",\"name\":\"Joe\"}}",
    "{\"table\":\"label\",\"object\":{\"id\":1,\"color\":\"red\",\"text\":\"SPAM\",\"type\":\"SYSTEM\",\"visible\":true}}",
    "{\"table\":\"label\",\"object\":{\"id\":2,\"color\":\"blue\",\"text\":\"INBOX\",\"type\":\"SYSTEM\",\"visible\":true}}",
    "{\"table\":\"file\",\"object\":{\"id\":1,\"token\":\"txt\",\"name\":\"this.txt\",\"size\":12,\"status\":0,\"date\":\"Fri Dec 21 00:00:00 GMT-05:00 2012\",\"readOnly\":true,\"emailId\":1}}",
    "{\"table\":\"file\",\"object\":{\"id\":2,\"token\":\"txt\",\"name\":\"that.txt\",\"size\":14,\"status\":0,\"date\":\"Fri Dec 21 00:00:00 GMT-05:00 2012\",\"readOnly\":true,\"emailId\":2}}",
    "{\"table\":\"email\",\"object\":{\"id\":1,\"messageId\":\"id_1\",\"threadId\":\"\",\"unread\":true,\"secure\":true,\"content\":\"contents 1\",\"preview\":\"cont\",\"subject\":\"subject 1\",\"delivered\":\"DELIVERED\",\"date\":\"Fri Dec 21 00:00:00 GMT-05:00 2012\",\"metadataKey\":123,\"isMuted\":false}}",
    "{\"table\":\"email\",\"object\":{\"id\":2,\"messageId\":\"id_2\",\"threadId\":\"\",\"unread\":true,\"secure\":true,\"content\":\"contents 2\",\"preview\":\"cont\",\"subject\":\"subject 2\",\"delivered\":\"DELIVERED\",\"date\":\"Fri Dec 21 00:00:00 GMT-05:00 2012\",\"metadataKey\":456,\"isMuted\":false}}",
    "{\"table\":\"email_label\",\"object\":{\"emailId\":1,\"labelId\":1}}",
    "{\"table\":\"email_label\",\"object\":{\"emailId\":2,\"labelId\":2}}",
    "{\"table\":\"email_contact\",\"object\":{\"id\":1,\"emailId\":1,\"contactId\":1,\"type\":\"TO\"}}",
    "{\"table\":\"email_contact\",\"object\":{\"id\":2,\"emailId\":2,\"contactId\":2,\"type\":\"FROM\"}}")

    @Before
    fun setup() {
        db = TestDatabase.getInstance(mActivityRule.activity)
        db.resetDao().deleteAllData(1)
        db.contactDao().insertIgnoringConflicts(bobContact)
        db.contactDao().insertIgnoringConflicts(joeContact)
        db.labelDao().insertAll(listOf(labelOne,labelTwo))
        db.emailDao().insert(emailOne)
        db.emailDao().insert(emailTwo)
        db.fileDao().insert(fileOne)
        db.fileDao().insert(fileTwo)
        db.emailLabelDao().insert(emailLabel1)
        db.emailLabelDao().insert(emailLabel2)
        db.emailContactDao().insert(emailContact1)
        db.emailContactDao().insert(emailContact2)
    }

    @Test
    fun should_correctly_save_all_data_from_database_into_link_device_file_with_correct_json_format() {
        val dataWriter = UserDataWriter(db.emailDao(), db.contactDao(), db.fileDao(),db.labelDao(),db.emailLabelDao(), db.emailContactDao())
        val result = dataWriter.createFile()

        val lines: List<String> = File(result.second).readLines()
        lines `shouldEqual` deviceLinkFileExpectedContent
    }
}