package com.example.album.data

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

fun openMediaInputStream(context: Context, uri: Uri): InputStream? =
    if (uri.scheme == "file") uri.path?.let(::FileInputStream)
    else context.contentResolver.openInputStream(uri)

fun openMediaOutputStream(context: Context, uri: Uri, mode: String = "w"): OutputStream? =
    if (uri.scheme == "file") uri.path?.let(::FileOutputStream)
    else context.contentResolver.openOutputStream(uri, mode)
