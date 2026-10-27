import test from'node:test';import assert from'node:assert/strict';
import{detectWater,waterForText,hydrateWater,dailyWater,waterGoal}from'../src/water.mjs';
import{weeklyInsights}from'../src/insights.mjs';import{dailyTotals,manualEstimate}from'../src/domain.mjs';
test('water quantities match the Android defaults and explicit volume rules',()=>{
 for(const [text,ml]of[['water',250],['three glasses of water',750],['1 bottle of water',500],['half a glass of water',125],['1.5 litres of water',1500],['water 330ml',330],['one glass of water and one bottle of water',750],['💧 three glasses of water',750]]){assert.equal(detectWater(text)?.milliliters,ml,text);assert.equal(detectWater(text).onlyWater,true,text);}
 for(const text of['watermelon','coconut water','water chestnuts','boil rice in water','no water','coffee'])assert.equal(detectWater(text),null,text);
 assert.equal(detectWater('2 idlis and one glass of water').onlyWater,false);
});
test('manual water corrections survive unrelated text edits and reset when the water quantity changes',()=>{
 const before=detectWater('rice and one glass of water'),corrected={...before,milliliters:330,assumed:false,portionLabel:'Custom amount'};
 assert.equal(waterForText('dal and one glass of water',corrected).milliliters,330);
 assert.equal(waterForText('dal and two glasses of water',corrected).milliliters,500);
 assert.equal(waterForText('dal',corrected),null);
 assert.equal(waterGoal(null),2000);assert.equal(waterGoal({water_goal_ml:2500}),2500);
});
test('pure water is ready without AI, contributes to water totals, and never makes nutrition pending',()=>{
 const date='2026-10-27';const water=hydrateWater({body:'three glasses of water',journal_date:date,estimate:manualEstimate({caloriesKcal:0},'old water')});
 assert.equal(water.status,'READY');assert.equal(water.estimate,null);
 assert.deepEqual(dailyTotals([water]).caloriesKcal,{value:0,incomplete:false});
 assert.equal(dailyWater([water,{...water,deleted_at:'removed'}],date),750);
});
test('weekly insights use complete food days and include water-only days in consistency',()=>{
 const meal=(journal_date,caloriesKcal,proteinG=10)=>({body:'lunch',journal_date,estimate:manualEstimate({caloriesKcal,proteinG,carbsG:50,fatG:10},'lunch')});
 const entries=[meal('2026-10-27',600),meal('2026-10-26',400),{body:'water 500ml',journal_date:'2026-10-25'},meal('2026-10-24',100),{body:'pending rice',journal_date:'2026-10-24'}, {...meal('2026-10-23',999),deleted_at:'removed'},meal('2026-10-19',2000),{body:'water',journal_date:'2026-10-22',_local:true}];
 const report=weeklyInsights(entries,'2026-10-27');assert.equal(report.days.length,7);assert.equal(report.days[0].date,'2026-10-21');
 assert.deepEqual(report.averages.caloriesKcal,{value:500,days:2});assert.equal(report.loggedDays,4);assert.equal(report.totalWaterMl,500);assert.equal(report.averageWaterMl,71);
 assert.equal(report.days[3].totals.caloriesKcal.incomplete,true);
 const empty=weeklyInsights([],'2026-10-27');assert.equal(empty.averages.proteinG.value,null);assert.equal(empty.loggedDays,0);assert.equal(empty.averageWaterMl,0);
});
