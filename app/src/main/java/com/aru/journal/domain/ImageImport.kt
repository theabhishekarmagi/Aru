package com.aru.journal.domain

import kotlinx.serialization.Serializable

@Serializable data class ImageFoodOption(val id:String,val name:String,val description:String="",val category:String="",val portionHint:String="")
@Serializable data class ImageScanAnalysis(val kind:String,val description:String,val options:List<ImageFoodOption> = emptyList(),val estimate:NutritionEstimate?=null) {
 init {
  require(kind in listOf("dish","menu","ingredients","other")&&description.isNotBlank()&&description.length<=600&&options.size<=60)
  require(if(kind=="dish")estimate!=null&&options.isEmpty() else estimate==null)
  require(if(kind in listOf("menu","ingredients"))options.isNotEmpty() else options.isEmpty())
  require(options.map{it.id}.distinct().size==options.size)
  require(options.all{it.name.isNotBlank()&&it.name.length<=200&&it.description.length<=600&&it.category.length<=100&&it.portionHint.length<=100})
 }
}
fun interface ImageImportAnalyzer { suspend fun scan(requestId:String,jpeg:ByteArray):ImageScanAnalysis }
