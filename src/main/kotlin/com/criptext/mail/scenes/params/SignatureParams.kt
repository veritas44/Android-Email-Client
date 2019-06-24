package com.criptext.mail.scenes.params

import com.criptext.mail.scenes.settings.signature.SignatureActivity

class SignatureParams(val recipientId: String, val domain: String): SceneParams(){
    override val activityClass = SignatureActivity::class.java
}