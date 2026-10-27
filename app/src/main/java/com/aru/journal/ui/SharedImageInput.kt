package com.aru.journal.ui

import android.content.Context
import android.net.Uri

/** Read only a sender-granted content URI; never resolve a filesystem path or URL. */
internal fun readSharedImage(context:Context,uri:Uri):ByteArray? = runCatching {
 require(uri.scheme=="content")
 val type=context.contentResolver.getType(uri)
 require(type==null||type.startsWith("image/"))
 val bytes=context.contentResolver.openInputStream(uri)?.use{input->
  val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
  while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=25_000_000);out.write(buffer,0,count)}
  out.toByteArray()
 }?:return@runCatching null
 require(bytes.size<=25_000_000)
 compressMealPhoto(bytes)
}.getOrNull()
