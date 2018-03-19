package com.email.db.seeders

import com.email.db.dao.FileDao
import com.email.db.models.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by sebas on 2/7/18.
 */

class FileSeeder {

    companion object {
        var files : List<File> = mutableListOf()
        var sdf : SimpleDateFormat = SimpleDateFormat( "yyyy-MM-dd HH:mm:dd")

        fun seed(fileDao: FileDao){
            files = fileDao.getAll()
            fileDao.deleteAll(files)
            files = mutableListOf()
            for (a in 1..2){
                files += fillFile(a)
            }
            fileDao.insertAll(files)
        }

        private fun fillFile(iteration: Int): File {
            lateinit var file: File
            when (iteration) {
                1 -> file = File( token = "XXXXXXXXXX2312XXXXX1",
                        name = "attachment 1" ,
                        size = 10,
                        status = 1,
                        date = sdf.parse("1992-05-23 20:12:58"),
                        emailId = 1,
                        readOnly = false
                )

                2 -> file = File( token = "XXXXXXXXXX2312XXXXX2",
                        name = "attachment 2" ,
                        size = 10,
                        status = 1,
                        date = sdf.parse("1993-05-23 20:12:58"),
                        emailId = 1,
                        readOnly = true
                )
            }
            return file
        }
    }

    init {
        sdf.timeZone = TimeZone.getDefault()
    }
}