package com.xialulee.wavesyn.android.datatransfer

import java.net.Socket
import java.io.DataOutputStream
import java.io.OutputStream
import java.io.InputStream
import kotlin.concurrent.thread
import android.os.Build

fun sendHead(outp: DataOutputStream, password: Int) {
    val devModel = "${Build.MANUFACTURER} ${Build.MODEL}".toByteArray()
    val devName = ByteArray(32) {
        if (it < devModel.size)
            devModel[it]
        else
            0
    }

    outp.write(0)
    outp.writeInt(password)
    outp.write(devName)
}

fun sendBytes(scanResult: ServerInfo, data: ByteArray) {
    thread {
        Socket(scanResult.ip, scanResult.port).use {
            val outp = DataOutputStream(it.getOutputStream())
            sendHead(outp, scanResult.password)
            outp.writeInt(data.size)
            outp.write(data)
        }
    }
}

fun sendFile(scanResult: ServerInfo, fileName: String, stream: InputStream) {
    thread {
        Socket(scanResult.ip, scanResult.port).use {
            val outp = DataOutputStream(it.getOutputStream())
            sendHead(outp, scanResult.password)
            outp.writeInt(stream.available())
            outp.write(fileName.length)
            outp.write(fileName.toByteArray())
            val bufLen = 32768
            val buf = ByteArray(bufLen)
            while (true) {
                val readLen = stream.read(buf, 0, bufLen)
                if (readLen < 0) break
                outp.write(buf, 0, readLen)
            }
            outp.close()
        }
        stream.close()
    }
}

fun recvBytes(scanResult: ServerInfo, onFinished: ((ByteArray)->Unit)?) {
    thread {
        Socket(scanResult.ip, scanResult.port).use {
            val inp = it.getInputStream()
            val outp = DataOutputStream(it.getOutputStream())
            sendHead(outp, scanResult.password)
            outp.writeInt(0)
            val bytes = inp.readBytes()
            onFinished?.invoke(bytes)
        }
    }
}

fun recvStream(scanResult: ServerInfo, stream: OutputStream, onFinished: (()->Unit)? = null) {
    thread {
        Socket(scanResult.ip, scanResult.port).use {
            val inp = it.getInputStream()
            val outp = DataOutputStream(it.getOutputStream())
            sendHead(outp, scanResult.password)
            outp.writeInt(0)
            val bufSize = 32768
            val buf = ByteArray(bufSize)
            while (true) {
                val len = inp.read(buf, 0, bufSize)
                if (len < 0) break
                stream.write(buf, 0, len)
            }
        }
        onFinished?.invoke()
    }
}

