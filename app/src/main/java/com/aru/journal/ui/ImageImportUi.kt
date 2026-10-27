@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aru.journal.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable fun ImageImportSheet(jpeg:ByteArray,date:LocalDate,controller:JournalController,close:()->Unit) {
 val scope=rememberCoroutineScope()
 var stage by remember{mutableStateOf("ready")};var analysis by remember{mutableStateOf<ImageScanAnalysis?>(null)}
 var selection by remember{mutableStateOf(mapOf<String,String>())};var search by remember{mutableStateOf("")}
 var review by remember{mutableStateOf<PhotoMealAnalysis?>(null)};var error by remember{mutableStateOf<String?>(null)}
 val bitmap=remember(jpeg){BitmapFactory.decodeByteArray(jpeg,0,jpeg.size)?.asImageBitmap()}
 val busy=stage in listOf("analyzing","estimating","saving")
 fun run(next:String,operation:suspend()->Unit){if(stage in listOf("analyzing","estimating","saving"))return;stage=next;error=null;scope.launch{try{operation()}catch(cancelled:CancellationException){throw cancelled}catch(e:Exception){error=if(e is NutritionServiceException)nutritionErrorMessage(e.code) else "Couldn’t complete this image. Please try again.";stage=if(next=="analyzing"){if(analysis?.kind=="other")"other" else "ready"} else if(review!=null)"review" else "choices"}}}
 val dismiss={if(stage!="saving")close()}
 ModalBottomSheet(onDismissRequest=dismiss,containerColor=Color(0xFFFCF8F5),sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).navigationBarsPadding().imePadding().padding(horizontal=22.dp)) {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=dismiss,enabled=stage!="saving"){Text("Cancel")};Text("Log with Aru",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Spacer(Modifier.width(45.dp))}
   Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    bitmap?.let{Image(it,"Shared image preview",Modifier.fillMaxWidth().height(if(stage=="choices")130.dp else 190.dp),contentScale=ContentScale.Fit)}
    Text("Journal date: $date",fontSize=12.sp,color=Color.Gray)
    if(stage=="ready"||stage=="other") {
     Text(if(stage=="other")"No readable food found" else "What would you like to log?",fontSize=22.sp,fontWeight=FontWeight.Bold)
     Text(if(stage=="other")analysis?.description.orEmpty() else "Share a dish, menu or ingredient list. Choose what you ate before saving.")
     Text("Analyze sends this image to Aru’s AI provider. Aru does not keep the image. Avoid faces and personal information.",fontSize=13.sp,color=Color.Gray)
     Button(onClick={run("analyzing"){val result=controller.scanSharedImage(jpeg);analysis=result;if(result.kind=="dish"){review=PhotoMealAnalysis(result.description,result.estimate!!);stage="review"}else stage=if(result.kind=="other")"other" else "choices"}},modifier=Modifier.fillMaxWidth()){Text(if(stage=="other")"Try again" else "Analyze image")}
    }
    if(stage=="analyzing"||stage=="estimating"){Row(Modifier.fillMaxWidth().padding(25.dp),horizontalArrangement=Arrangement.spacedBy(15.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(24.dp));Text(if(stage=="analyzing")"Reading your image…" else "Calculating selected items…")}}
    if(stage=="choices") {
     Text("What would you like to log?",fontSize=22.sp,fontWeight=FontWeight.Bold)
     Text(analysis?.description.orEmpty())
     Text("Found ${analysis?.options?.size?:0} readable items. Choose what you ate and adjust amounts. Select up to 12; defaults are estimated servings.",fontSize=13.sp,color=Color.Gray)
     OutlinedTextField(search,{search=it},label={Text("Search items")},modifier=Modifier.fillMaxWidth())
     val filtered=analysis?.options.orEmpty().filter{"${it.name} ${it.description} ${it.category}".contains(search,ignoreCase=true)}
     filtered.forEach {option->val checked=option.id in selection
      Surface(color=if(checked)Color(0xFFF4EDFF) else Color.White,shape=RoundedCornerShape(20.dp)) {
       Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){if(option.category.isNotBlank())Text(option.category,color=Color.Gray,fontSize=12.sp);Text(option.name,fontWeight=FontWeight.SemiBold);if(option.description.isNotBlank())Text(option.description,color=Color.Gray,fontSize=13.sp)};Checkbox(checked,{selected->selection=if(selected)selection+(option.id to option.portionHint.ifBlank{"1 serving"}) else selection-option.id},enabled=checked||selection.size<12,modifier=Modifier.semantics{contentDescription="Select ${option.name}"})}
        if(checked)OutlinedTextField(selection[option.id].orEmpty(),{selection=selection+(option.id to it.take(100))},label={Text("Amount for ${option.name}")},modifier=Modifier.fillMaxWidth())
       }
      }
     }
     if(filtered.isEmpty())Text("No matching items. Try another search.")
    }
    if((stage=="review"||stage=="saving")&&review!=null) {
     val result=review!!;Text(result.estimate.summary?:"Meal preview",fontSize=24.sp,fontWeight=FontWeight.Bold)
     val totals=(0..6).map{i->val values=result.estimate.items.map{it.nutrients.values()[i]};if(values.any{it==null})null else values.sumOf{it!!}}
     Surface(color=Color.White,shape=RoundedCornerShape(24.dp)) {Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
      Text("🔥 ${totals[0]?.toInt()?.toString()?:"—"} total calories",fontSize=25.sp,fontWeight=FontWeight.Bold)
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Protein","Carbs","Fat").forEachIndexed{i,label->Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${totals[i+1]?.let{"%.1f".format(java.util.Locale.US,it)}?:"—"} g",fontWeight=FontWeight.Bold);Row(verticalAlignment=Alignment.CenterVertically){MacroIcon(i+1,Modifier.size(16.dp));Text(" $label",fontSize=12.sp)}}}}
     }}
     Text("Food details",fontWeight=FontWeight.SemiBold);Text(result.description)
     Text("Items",fontWeight=FontWeight.SemiBold)
     result.estimate.items.forEach {item->Surface(color=Color.White,shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(15.dp)){Text(item.name,fontWeight=FontWeight.SemiBold);Text("${item.portion.quantity} ${item.portion.unitKey}${if(item.portion.assumed)" · estimated portion" else ""}",color=Color.Gray,fontSize=13.sp)}}}
     Text(result.estimate.explanation+" Review portions and estimates. You can edit nutrition after adding.",color=Color.Gray,fontSize=13.sp)
    }
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    Spacer(Modifier.height(8.dp))
   }
   if(stage=="choices")Button(enabled=selection.isNotEmpty()&&selection.values.none{it.isBlank()},onClick={run("estimating"){
    val description=analysis!!.options.filter{it.id in selection}.joinToString("; "){"${selection[it.id]} ${it.name}${if(it.description.isBlank())"" else " (${it.description})"}"}
    if(description.length>2000){error="Choose fewer items or shorten the amounts.";stage="choices"}else{review=PhotoMealAnalysis(description,controller.previewImageSelection(description,date));stage="review"}
   }},modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){Text("Continue · ${selection.size} selected")}
   if(stage=="review"||stage=="saving")Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    if(analysis?.kind!="dish")OutlinedButton(onClick={stage="choices";review=null},enabled=!busy,modifier=Modifier.weight(1f)){Text("Edit selection")}
    Button(onClick={run("saving"){controller.saveImageMeal(review!!.description,review!!.estimate,date);close()}},enabled=!busy,modifier=Modifier.weight(1f)){Text(if(stage=="saving")"Adding…" else "+ Add to Aru")}
   }
  }
 }
}
