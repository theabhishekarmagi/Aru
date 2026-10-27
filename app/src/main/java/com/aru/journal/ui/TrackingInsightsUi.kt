package com.aru.journal.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sin
import kotlin.math.roundToInt

private val trackingBlue=Color(0xFF288BE8)
private val trackingMuted=Color(0xFF8E898F)
private fun trackingNumber(n:Double?)=n?.let {"%.0f".format(java.util.Locale.US,it)}?:"—"
@Composable fun WaterSummaryButton(total:Int,goal:Int,onClick:()->Unit) {
 Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.Center) {
  TextButton(onClick=onClick,modifier=Modifier.semantics {contentDescription="Daily water tracking"}) {
   Icon(Icons.Rounded.WaterDrop,null,tint=trackingBlue,modifier=Modifier.size(19.dp));Spacer(Modifier.width(5.dp));Text("$total / $goal ml",color=trackingBlue,fontSize=13.sp)
  }
 }
}
@Composable fun WaterTrackerContent(entries:List<JournalEntry>,date:String,goals:NutritionGoals,error:String?,saveGoal:(Int)->Unit,addWater:(Int)->Unit) {
 val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 val total=dailyWater(entries,date);val goal=goals.preferences.waterGoalMl
 var target by remember(goal){mutableStateOf(goal.toString())}
 var submitted by remember{mutableStateOf<Int?>(null)}
 LaunchedEffect(goal,error,submitted){if(submitted==goal||error!=null)submitted=null}
 var filled by remember{mutableStateOf(false)};LaunchedEffect(Unit){filled=true}
 val fill by animateFloatAsState(if(filled)(total.toFloat()/goal).coerceIn(0f,1f) else 0f,tween(1000),label="Daily water fill")
 val motion=ValueAnimator.areAnimatorsEnabled()
 val phase=if(motion){val loop=rememberInfiniteTransition(label="Water wave");val value by loop.animateFloat(0f,6.2832f,infiniteRepeatable(tween(2600,easing=LinearEasing)),label="Wave phase");value}else 0f
 val percentage=(total.toDouble()/goal*100).roundToInt()
 Text(date,color=trackingMuted,fontSize=13.sp)
 Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
  Box(Modifier.size(210.dp,250.dp).clip(RoundedCornerShape(35.dp)).background(Color(0xFFEAF4FF)).semantics {contentDescription="$total of $goal ml, $percentage percent"},contentAlignment=Alignment.Center) {
   Canvas(Modifier.fillMaxSize()) {
    if(fill>0f){val surface=size.height*(1-fill);val path=Path().apply{moveTo(0f,size.height);lineTo(0f,surface);for(x in 0..100){val px=size.width*x/100;lineTo(px,surface+(if(motion)sin(x/100.0*6.2832+phase).toFloat()*6.dp.toPx() else 0f))};lineTo(size.width,size.height);close()};drawPath(path,trackingBlue.copy(alpha=.52f))}
   }
   Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha=.88f)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    Icon(Icons.Rounded.WaterDrop,null,tint=trackingBlue,modifier=Modifier.size(27.dp))
    Text("$total ml",fontWeight=FontWeight.Bold,fontSize=27.sp,color=trackingBlue)
    Text("of $goal ml",color=trackingMuted,fontSize=13.sp);Text("$percentage%",fontWeight=FontWeight.SemiBold,color=trackingBlue,modifier=Modifier.padding(top=8.dp))
   }
  }
 }
 Text("Your water logged for this day",color=trackingMuted,fontSize=13.sp)
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  Button(onClick={addWater(250)},modifier=Modifier.weight(1f)){Text("+ 250 ml")}
  OutlinedButton(onClick={addWater(500)},modifier=Modifier.weight(1f)){Text("+ 500 ml")}
 }
 Text("Your daily water goal",fontWeight=FontWeight.SemiBold,fontSize=17.sp)
 Text("Starts at 2 litres. Adjust it to the target you want to follow.",color=trackingMuted,fontSize=13.sp)
 val value=target.toIntOrNull();val valid=value!=null&&value in 1..20000
 OutlinedTextField(target,{target=it.take(6)},label={Text("Daily water goal (ml)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,isError=target.isNotBlank()&&!valid,modifier=Modifier.fillMaxWidth())
 if(!valid)Text("Use a whole number from 1 to 20000 ml.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
 Button(onClick={value?.let{focus.clearFocus();keyboard?.hide();submitted=it;saveGoal(it)}},enabled=valid&&submitted==null){Text(if(submitted!=null)"Saving…" else "Save water goal")}
 error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
}
@Composable private fun InsightCard(content:@Composable ColumnScope.()->Unit){Surface(shape=RoundedCornerShape(22.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)}}
@Composable fun WeeklyInsightsContent(entries:List<JournalEntry>,initialEnd:LocalDate) {
 var end by remember{mutableStateOf(minOf(initialEnd,LocalDate.now()))}
 val insights=remember(entries,end){weeklyInsights(entries,end)}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  TextButton(onClick={end=end.minusDays(7)}){Text("‹ Previous")}
  Text("${end.minusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM"))}",fontSize=13.sp,modifier=Modifier.weight(1f))
  TextButton(onClick={end=minOf(end.plusDays(7),LocalDate.now())},enabled=end<LocalDate.now()){Text("Next ›")}
 }
 InsightCard {
  Text("${insights.loggedDays} / 7 days logged",fontWeight=FontWeight.Bold,fontSize=19.sp)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){insights.days.forEach{day->Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(if(day.logged)Color(0xFF8354EE) else Color(0xFFECE7EE)));Text(LocalDate.parse(day.date).dayOfWeek.name.take(1),fontSize=11.sp,color=trackingMuted)}}}
  Text("Counts days with a saved meal or water entry.",fontSize=12.sp,color=trackingMuted)
 }
 InsightCard {
  Text("Daily calorie totals",fontWeight=FontWeight.SemiBold)
  InsightBars(insights.days.map{if(it.hasEstimates)it.totals.values[0].knownAmount else null},insights.days.map{it.date},Color(0xFFF5B547),insights.days.map{it.totals.pendingEntryCount>0||it.totals.values[0].missingItemCount>0})
 }
 InsightCard {
  Text("Daily averages",fontWeight=FontWeight.SemiBold)
  listOf("Calories","Protein","Carbs","Fat").forEachIndexed{i,name->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(i>0){MacroIcon(i,Modifier.size(16.dp));Spacer(Modifier.width(5.dp))};Text(name,modifier=Modifier.weight(1f));Text("${trackingNumber(insights.averages[i].value)} ${if(i==0)"kcal" else "g"}",fontWeight=FontWeight.SemiBold)}}
  Text("Averages use days with complete values for each nutrient. + marks incomplete totals; — means no calculated value.",color=trackingMuted,fontSize=12.sp,lineHeight=18.sp)
 }
 InsightCard {
  Text("Daily water intake",fontWeight=FontWeight.SemiBold)
  InsightBars(insights.days.map{it.waterMl.toDouble()},insights.days.map{it.date},trackingBlue,List(7){false})
  Text("${insights.averageWaterMl} ml / day · ${insights.totalWaterMl} ml this week",color=trackingBlue,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
  Text("Water average uses all 7 days.",color=trackingMuted,fontSize=12.sp)
 }
}
@Composable private fun InsightBars(values:List<Double?>,dates:List<String>,color:Color,incomplete:List<Boolean>) {
 val maximum=(values.filterNotNull().maxOrNull()?:1.0).coerceAtLeast(1.0)
 Row(Modifier.fillMaxWidth().height(130.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.Bottom){values.forEachIndexed{i,value->Column(Modifier.weight(1f).semantics {contentDescription="${dates[i]}: ${trackingNumber(value)}${if(incomplete[i]&&value!=null)"+" else ""}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){Text(trackingNumber(value)+(if(incomplete[i]&&value!=null)"+" else ""),fontSize=9.sp,color=trackingMuted);Spacer(Modifier.height(5.dp));Box(Modifier.fillMaxWidth(.7f).height((if(value==null)3f else (value/maximum*80).toFloat().coerceAtLeast(3f)).dp).clip(RoundedCornerShape(5.dp)).background(if(value==null)Color(0xFFECE7EE) else color));Text(LocalDate.parse(dates[i]).dayOfWeek.name.take(2),color=trackingMuted,fontSize=10.sp,modifier=Modifier.padding(top=5.dp))}}}
}
