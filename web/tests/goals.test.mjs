import test from "node:test";
import assert from "node:assert/strict";
import {
  estimatedDailyBurn,
  goalDefaults,
  goalSettings,
  progress,
  validateGoals,
} from "../src/goals.mjs";
import { dailyTotals, manualEstimate } from "../src/domain.mjs";
const profile = { date_of_birth: "1996-10-26", height_cm: 175, weight_kg: 70 };
test("daily burn uses adult profile, birthday and explicit equation details without setting an intake target", () => {
  const settings = { ...goalDefaults, equation_sex: "male" };
  assert.equal(estimatedDailyBurn(profile, settings, "2026-10-26"), 1979);
  assert.equal(
    estimatedDailyBurn(
      profile,
      { ...settings, equation_sex: "female" },
      "2026-10-26",
    ),
    1779,
  );
  assert.equal(estimatedDailyBurn(profile, goalDefaults, "2026-10-26"), null);
  assert.equal(
    estimatedDailyBurn({ ...profile, weight_kg: null }, settings, "2026-10-26"),
    null,
  );
  assert.equal(
    estimatedDailyBurn(
      { ...profile, date_of_birth: "2010-01-01" },
      settings,
      "2026-10-26",
    ),
    null,
  );
  assert.equal(
    estimatedDailyBurn(
      { ...profile, date_of_birth: "1940-01-01" },
      settings,
      "2026-10-26",
    ),
    null,
  );
  assert.equal(goalSettings(null).calories_kcal, undefined);
});
test("goals validate dates, unit-normalized weights and finite optional nutrient targets", () => {
  const good = {
    ...goalDefaults,
    target_weight_kg: 75,
    target_date: "2026-12-01",
    calories_kcal: 2000,
    sodium_mg: 2300,
  };
  assert.equal(validateGoals(good, "2026-10-26"), null);
  for (const edit of [
    { target_date: "2026-10-25" },
    { target_date: "2027-02-30" },
    { target_weight_kg: NaN },
    { target_weight_kg: 501 },
    { sodium_mg: Infinity },
    { protein_g: 0 },
    { equation_sex: "x" },
    { dietary_preferences: ["unknown"] },
  ])
    assert.ok(validateGoals({ ...good, ...edit }, "2026-10-26"));
  assert.equal(goalSettings({ fiber_g: 30 }).track_fiber, true);
  assert.equal(
    goalSettings({ fiber_g: 30, track_fiber: false }).track_fiber,
    false,
  );
});
test("progress clamps rings but preserves over-target amounts; legacy micronutrients are incomplete", () => {
  assert.equal(progress(250, 100), 1);
  assert.equal(progress(20, null), 0);
  assert.equal(progress(20, 0), 0);
  assert.equal(progress(Infinity, 100), 0);
  const totals = dailyTotals([
    { estimate: { items: [{ nutrients: { caloriesKcal: 100 } }] } },
    { estimate: manualEstimate({ sugarG: 0, sodiumMg: 600 }, "Fixture") },
  ]);
  assert.deepEqual(totals.sodiumMg, { value: 600, incomplete: true });
  assert.deepEqual(totals.sugarG, { value: 0, incomplete: true });
});
