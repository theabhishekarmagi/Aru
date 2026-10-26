@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aru.journal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.domain.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

private val goalMuted=Color(0xFF928C94)
private val goalGreen=Color(0xFF35BC68)
private val goalNames=listOf("Calories","Protein","Carbs","Fat","Fiber","Sugar","Sodium")
private val goalUnits=listOf("kcal","g","g","g","g","g","mg")
private val goalColors=listOf(goalGreen,Color(0xFFE5B100),Color(0xFFFF3868),Color(0xFFD92CE6))
private fun displayGoal(v:Double?):String= v?.let { if(it%1.0==0.0) it.roundToInt().toString() else "%.1f".format(java.util.Locale.US,it) }?:"—"
private fun totalGoal(t:DailyTotals,i:Int)=displayGoal(t.values[i].knownAmount)+if(t.values[i].missingItemCount>0||t.pendingEntryCount>0) "+" else ""

@Composable fun GoalsProgressCard(totals:DailyTotals, goals:NutritionGoals, onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(23.dp),color=Color(0xFFFFFCFF),tonalElevation=0.dp,shadowElevation=3.dp,
        modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).semantics { contentDescription="Open nutrition goals" }) {
        Column(Modifier.padding(horizontal=18.dp,vertical=13.dp)) {
            Text("Goals",fontWeight=FontWeight.SemiBold,fontSize=14.sp)
            Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=5.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("🔥 Calories",fontSize=12.sp)
                Text("${totalGoal(totals,0)} / ${displayGoal(goals.targets.caloriesKcal)}",fontWeight=FontWeight.SemiBold,fontSize=12.sp)
            }
            LinearProgressIndicator(progress={goalProgress(totals.values[0].knownAmount,goals.targets.caloriesKcal)},color=goalGreen,trackColor=Color(0xFFEDE8EC),gapSize=0.dp,drawStopIndicator={},modifier=Modifier.fillMaxWidth().height(6.dp))
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(2,1,3).forEach { i -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).semantics { contentDescription="${goalNames[i]}: ${totalGoal(totals,i)} of ${displayGoal(goals.targets.values()[i])} grams" },contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                            drawArc(Color(0xFFEDE8EC),-90f,360f,false,style=Stroke(4.dp.toPx()))
                            drawArc(goalColors[i],-90f,360f*goalProgress(totals.values[i].knownAmount,goals.targets.values()[i]),false,style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
                        }
                        Text(totalGoal(totals,i),fontSize=12.sp,fontWeight=FontWeight.Medium)
                    }
                    Text(goalNames[i],fontSize=10.sp,color=goalMuted)
                } }
            }
            if(goals.targets.caloriesKcal==null) Text("Set targets that work for you",fontSize=11.sp,color=goalMuted,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=5.dp))
            val micros=listOf(4 to goals.preferences.trackFiber,5 to goals.preferences.trackSugar,6 to goals.preferences.trackSodium).filter { it.second }
            if(micros.isNotEmpty()) Text(micros.joinToString(" · "){(i,_)->"${goalNames[i]} ${totalGoal(totals,i)} / ${displayGoal(goals.targets.values()[i])} ${goalUnits[i]}"},fontSize=10.sp,color=goalMuted,modifier=Modifier.padding(top=7.dp))
        }
    }
}

@Composable private fun GoalGroup(content:@Composable ColumnScope.()->Unit) {
    Surface(color=Color(0xFFFFFDFD),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(horizontal=14.dp),content=content)}
}
@Composable private fun GoalSection(title:String) { Text(title,color=goalMuted,fontSize=13.sp,modifier=Modifier.padding(start=10.dp,top=12.dp,bottom=1.dp)) }
@Composable private fun GoalSelect(label:String,value:String,options:Map<String,String>,change:(String)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min=50.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=14.sp,modifier=Modifier.weight(1f))
        Box { TextButton(onClick={menu=true}) {Text("${options[value]?:"Not set"} ⌄",color=goalMuted,fontSize=13.sp)}
            DropdownMenu(menu,{menu=false}) { options.forEach {(key,text)->DropdownMenuItem(text={Text(text)},onClick={change(key);menu=false})} }
        }
    }
}
@Composable private fun GoalSwitch(label:String,checked:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=50.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=14.sp,modifier=Modifier.weight(1f))
        Switch(checked,change,modifier=Modifier.semantics {contentDescription=label})
    }
}
@Composable private fun GoalNumber(label:String,value:String,unit:String,iconIndex:Int?=null,change:(String)->Unit) {
    OutlinedTextField(value,change,label={Text(label,fontSize=13.sp)},placeholder={Text("Not set")},suffix={Text(unit,color=goalMuted)},
        leadingIcon=if(iconIndex!=null) { { if(iconIndex==0)Text("🔥") else MacroIcon(iconIndex,Modifier.size(18.dp)) } } else null,
        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp))
}
@Composable fun NutritionGoalsEditor(goals:NutritionGoals,profile:UserProfile?,error:String?,save:(NutritionGoals)->Unit,close:()->Unit,editProfile:()->Unit) {
    var preferences by remember { mutableStateOf(goals.preferences) }
    var fields by remember { mutableStateOf(goals.targets.values().map {it?.let(::displayGoal)?:""}) }
    var weight by remember {mutableStateOf(goals.preferences.targetWeightKg?.let {displayGoal(it*if(preferences.weightUnit=="lb") 2.2046226218 else 1.0)}?:"")}
    var useDate by remember {mutableStateOf(preferences.targetDate!=null)}
    var datePicker by remember {mutableStateOf(false)}
    var burnDetails by remember {mutableStateOf(false)}
    var dietDetails by remember {mutableStateOf(false)}
    var help by remember {mutableStateOf(false)}
    var problem by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf<NutritionGoals?>(null)}
    LaunchedEffect(goals,submitted) {if(submitted!=null&&goals==submitted) close()}
    LaunchedEffect(error) {if(error!=null){submitted=null;problem=error}}
    val burn=estimatedDailyBurn(profile,preferences)
    val submit={
        val numbers=fields.map { if(it.isBlank()) null else it.toDoubleOrNull() }
        val weightKg=if(weight.isBlank())null else weight.toDoubleOrNull()?.div(if(preferences.weightUnit=="lb")2.2046226218 else 1.0)
        val nextDate=if(useDate)preferences.targetDate else null
        problem=when {
            fields.indices.any {i->fields[i].isNotBlank()&&(numbers[i]==null||!numbers[i]!!.isFinite()||numbers[i]!!<=0||numbers[i]!!>if(i==0)20000 else if(i==6)100000 else 2000)} ->"Use positive targets or leave them blank."
            weight.isNotBlank()&&(weightKg==null||!weightKg.isFinite()||weightKg !in 20.0..500.0)->"Target weight must be between 20 and 500 kg (44.1–1,102.3 lb)."
            useDate&&(nextDate==null||LocalDate.parse(nextDate)<LocalDate.now())->"Choose today or a future target date."
            else->null
        }
        if(problem==null) {
            val next=NutritionGoals(Nutrients(numbers[0],numbers[1],numbers[2],numbers[3],numbers[4],numbers[5],numbers[6]),preferences.copy(targetWeightKg=weightKg,targetDate=nextDate))
            submitted=next;save(next)
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick=close,enabled=submitted==null) {Text("Cancel")}
        Button(onClick=submit,enabled=submitted==null) {Text(if(submitted!=null)"Saving…" else "Save goals")}
    }
    AruProfileIllustration(3,Modifier.fillMaxWidth().height(125.dp))
    GoalSection("Your Daily Burn")
    GoalGroup {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("🔥",modifier=Modifier.padding(end=9.dp))
            Column(Modifier.weight(1f)){Text("TDEE · estimated daily burn",fontSize=11.sp,color=goalMuted);Text(burn?.let {"$it kcal / day"}?:"Not calculated",fontSize=14.sp)}
            TextButton(onClick=editProfile){Text("Edit Profile ›",fontSize=12.sp)}
        }
    }
    TextButton(onClick={burnDetails=!burnDetails}) {Text("Daily burn calculation details ⌄",fontSize=12.sp)}
    if(burnDetails) {
        Text("Uses your profile’s birth date, height and weight. Add calculation details below.",fontSize=12.sp,color=goalMuted)
        GoalGroup {
            GoalSelect("Sex used by equation",preferences.equationSex?:"",mapOf("" to "Not set","female" to "Female","male" to "Male")){preferences=preferences.copy(equationSex=it.takeIf(String::isNotBlank))}
            GoalSelect("Activity level",preferences.activityLevel,mapOf("sedentary" to "Mostly seated","light" to "Lightly active","moderate" to "Moderately active","active" to "Very active")){preferences=preferences.copy(activityLevel=it)}
        }
        Text("Mifflin–St Jeor resting energy × activity factor, ages 19–78. This is an estimate, not a measured burn or an intake recommendation.",fontSize=12.sp,color=goalMuted)
    }
    GoalSection("Weight Goals")
    GoalGroup {
        GoalSelect("Goal Type",preferences.goalType,mapOf("maintain" to "Maintain Weight","lose" to "Lose Weight","gain" to "Gain Weight")){preferences=preferences.copy(goalType=it)}
        HorizontalDivider(color=Color(0xFFF1EDF0))
        GoalSelect("Weight Unit",preferences.weightUnit,mapOf("kg" to "kg","lb" to "lb")) {unit->
            if(unit!=preferences.weightUnit) {weight=weight.toDoubleOrNull()?.let {displayGoal(it*if(unit=="lb")2.2046226218 else 1/2.2046226218)}?:weight;preferences=preferences.copy(weightUnit=unit)}
        }
        GoalNumber("Target Weight",weight,preferences.weightUnit){weight=it}
        GoalSwitch("Set Target Date",useDate){useDate=it}
        if(useDate)TextButton(onClick={datePicker=true}){Text(preferences.targetDate?:"Choose target date")}
    }
    GoalGroup {
        TextButton(onClick={dietDetails=!dietDetails},modifier=Modifier.fillMaxWidth()){Text("✦ Lifestyle & Dietary Preferences ⌄",fontSize=13.sp)}
        if(dietDetails) {
            GoalPreferences.dietOptions.forEach {diet->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(diet in preferences.dietaryPreferences,{selected->preferences=preferences.copy(dietaryPreferences=if(selected)preferences.dietaryPreferences+diet else preferences.dietaryPreferences-diet)});Text(diet,fontSize=14.sp)}}
            Text("Saved as preferences. Estimates still follow the food you log.",fontSize=12.sp,color=goalMuted,modifier=Modifier.padding(bottom=10.dp))
        }
    }
    GoalSection("Daily Nutrition Goals")
    GoalGroup { (0..3).forEach {i->GoalNumber(if(i==0)"Daily Calorie Goal" else "Target ${goalNames[i]}",fields[i],goalUnits[i],iconIndex=i){value->fields=fields.toMutableList().also {it[i]=value}}} }
    GoalSection("Additional Nutrients")
    GoalGroup {listOf(5,4,6).forEach {i->
        val checked=when(i){4->preferences.trackFiber;5->preferences.trackSugar;else->preferences.trackSodium}
        GoalSwitch("Track ${goalNames[i]}",checked){preferences=when(i){4->preferences.copy(trackFiber=it);5->preferences.copy(trackSugar=it);else->preferences.copy(trackSodium=it)}}
        if(checked)GoalNumber("${goalNames[i]} target",fields[i],goalUnits[i]){value->fields=fields.toMutableList().also {it[i]=value}}
    } }
    TextButton(onClick={help=!help}){Text("Not sure what values to put here? ⌄",fontSize=12.sp)}
    if(help)Text("Targets are optional. Use targets you already follow or agree them with a qualified professional. Weight goals and dates don’t change your nutrition targets automatically. A + means some nutrition is missing. Older meals may need recalculation or manual sugar and sodium values.",fontSize=12.sp,color=goalMuted)
    problem?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
    if(datePicker) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=(preferences.targetDate?.let(LocalDate::parse)?:LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let {preferences=preferences.copy(targetDate=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};datePicker=false}){Text("Use date")}},dismissButton={TextButton(onClick={datePicker=false}){Text("Cancel")}}){DatePicker(picker)}
    }
}
