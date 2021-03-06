package com.criptext.mail.scenes.mailbox.data

import org.json.JSONObject

data class UpdateBannerData(val title: String, val message: String, val image: String, val version: String, val operator: Int){
    companion object {
        fun fromJSON(jsonString: String): UpdateBannerData{
            val json = JSONObject(jsonString)
            return UpdateBannerData(
                    title = json.getString("title"),
                    message = json.getString("body"),
                    image = json.getString("imageUrl"),
                    version = "",
                    operator = 0
            )
        }
    }
}