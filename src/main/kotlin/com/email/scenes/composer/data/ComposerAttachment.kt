package com.email.scenes.composer.data

import com.email.db.AttachmentTypes
import com.email.utils.file.FileUtils

data class ComposerAttachment(val filepath: String, var uploadProgress: Int,
                              var filetoken: String, val type: AttachmentTypes, var size: Long) {
    constructor(filepath: String, size: Long): this (filepath, -1, filetoken = "",
            type = FileUtils.getAttachmentTypeFromPath(filepath), size = size)
}