package com.xialulee.wavesyn.android.datatransfer

import android.content.*
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

import org.json.JSONObject

import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.Exception


class MainActivity : ActivityEx() {

    private fun makeClipboardJson(): ByteArray {
        val clipText = readClipboard()
        val content =
            JSONObject()
                .put("data", clipText)
                .toString()
                .toByteArray()
        return content
    }

    private fun sendClipboardText(serverInfo: ServerInfo) = sendBytes(serverInfo, makeClipboardJson())

    private fun sendGalleryPhoto(scanResult: ServerInfo) {
        val pickPhotoIntent = Intent().setAction("android.intent.action.PICK").setType("image/*")
        activityDo(pickPhotoIntent) {
            val uri = it.data?.data
            sendBytes(scanResult, readUriBytes(uri))
        }
    }

    private fun sendIntentUri(scanResult: ServerInfo) {
        if (intent.action != "android.intent.action.SEND") return
        val uri = intent.extras?.get("android.intent.extra.STREAM") as Uri
        uri ?: return
        val stream = contentResolver.openInputStream(uri)
        stream ?: return
        val fullPath = uri.toString()
        val pathArr = fullPath.split("/")
        val fileName = pathArr[pathArr.size-1]
        sendFile(scanResult, fileName, stream)
    }

    private fun recvClipboardText(serverInfo: ServerInfo) {
        recvBytes(serverInfo) {
            val text = JSONObject(it.toString(Charsets.UTF_8)).getString("data")
            writeClipboard(text)
        }
    }

    private fun recvFile(scanResult: ServerInfo) {
        val path = "${getDownloadDirectory()}/${scanResult.fileName}"
        val fobj = FileOutputStream(path)
        recvStream(scanResult, fobj) {
            fobj.close()
        }
    }

    private fun recvImage(scanResult: ServerInfo) {
        permittedToDo("android.permission.WRITE_EXTERNAL_STORAGE") {

            val path = "${getDownloadDirectory()}/${scanResult.fileName}"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, scanResult.fileName)
                put(MediaStore.Images.Media.DISPLAY_NAME, scanResult.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/*")
            }

            val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            item ?: return@permittedToDo

            val fobj = contentResolver.openOutputStream(item)
            fobj ?: return@permittedToDo
            recvStream(scanResult, fobj) {
                startActivity(Intent(Intent.ACTION_VIEW, item))
                fobj.close()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView.text = """Ordinary files can be found in
            |${getDownloadDirectory()}.
            |Media files are in gallery.""".trimMargin()

//        val shareIntent = intent
//        var shareStream: InputStream? = null
//        var shareFileName = ""
//        if (intent.action == "android.intent.action.SEND") {
//            val shareUri = shareIntent.extras?.get("android.intent.extra.STREAM") as Uri
//            shareStream = contentResolver.openInputStream(shareUri)
//            if (shareStream != null && shareUri != null) {
//                val fullPath = shareUri.toString()
//                val pathArr = fullPath.split("/")
//                shareFileName = pathArr[pathArr.size-1]
//            }
//        }

        val scanIntent =
            Intent()
                .setAction("com.google.zxing.client.android.SCAN")
                .putExtra("SAVE_HISTORY", false)

        activityDo(scanIntent) {
            val scanResult = parseBarcode(it.data?.getStringExtra("SCAN_RESULT"))
            scanResult ?: return@activityDo
            val command = "${scanResult.action}&${scanResult.source}&${scanResult.target}"
            fun doNothing(serverInfo: ServerInfo) = null
            when (command) {
                "read&clipboard&"                  -> this::sendClipboardText
                "read&gallery&"                    -> this::sendGalleryPhoto
                "read&storage&"                    -> this::sendIntentUri
                "write&&clipboard"                 -> this::recvClipboardText
                "write&storage:image&dir:Download" -> this::recvImage
                "write&storage:file&dir:Download"  -> this::recvFile
                else -> ::doNothing
            }.invoke(scanResult!!)
        }
    }

}


