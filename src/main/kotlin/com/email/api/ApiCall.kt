package com.email.api

import com.email.signal.PreKeyBundleShareData
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * Created by sebas on 2/26/18.
 */

class ApiCall {

    companion object {
        var baseUrl = "http://172.30.1.151:8000"
        private val JSON = MediaType.parse("application/json; charset=utf-8")
        fun executeRequest(client: OkHttpClient, req: Request): String {
            val response = client.newCall(req).execute()
            if(!response.isSuccessful) throw(ServerErrorException(response.code()))
            return response.body()!!.string()
        }

        fun createUser(
                name: String,
                password: String,
                recoveryEmail: String?,
                recipientId: String,
                keyBundle: PreKeyBundleShareData.UploadBundle
        ): Request {
            val jsonObject = JSONObject()
            jsonObject.put("name", name)
            jsonObject.put("password", password)
            jsonObject.put("recipientId", recipientId)
            jsonObject.put("keybundle", keyBundle.toJSON())
            if(recoveryEmail != null) jsonObject.put("recoveryEmail", recoveryEmail)
            return postJSON(url = "$baseUrl/user", json = jsonObject)
        }

        fun authenticateUser(
                username: String,
                password: String,
                deviceId: Int
        ): Request {
            val jsonObject = JSONObject()
            jsonObject.put("username", username)
            jsonObject.put("password", password)
            jsonObject.put("deviceId", deviceId)
            return postJSON(url = "$baseUrl/user/auth", json = jsonObject)
        }

        private fun postJSON(url: String, json: JSONObject): Request {
            val body = RequestBody.create(JSON, json.toString())
            val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

            return request
        }
    }

}