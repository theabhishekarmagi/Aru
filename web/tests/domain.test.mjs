import test from "node:test";
import assert from "node:assert/strict";
import {
  dailyTotals,
  nutrientTotal,
  manualEstimate,
  validateNutrition,
  shiftDate,
} from "../src/domain.mjs";
test("unknown nutrition remains unknown and daily totals explicitly mark incompleteness", () => {
  const entry = {
    estimate: manualEstimate(
      { caloriesKcal: 100, proteinG: "", carbsG: 0, fatG: 2, fiberG: "" },
      "Meal",
    ),
  };
  assert.equal(nutrientTotal(entry.estimate.items, "proteinG"), null);
  assert.equal(nutrientTotal(entry.estimate.items, "carbsG"), 0);
  const totals = dailyTotals([entry, { body: "Pending meal" }]);
  assert.deepEqual(totals.caloriesKcal, { value: 100, incomplete: true });
  assert.deepEqual(totals.proteinG, { value: 0, incomplete: true });
});
test("deleted meals do not affect totals, while manual zero is preserved", () => {
  const estimate = manualEstimate(
    { caloriesKcal: 0, proteinG: 0, carbsG: 0, fatG: 0, fiberG: 0 },
    "Water",
  );
  assert.deepEqual(
    dailyTotals([
      { estimate },
      {
        deleted_at: "2026-01-01",
        estimate: manualEstimate({ caloriesKcal: 500 }, "Removed"),
      },
    ]).caloriesKcal,
    { value: 0, incomplete: false },
  );
});
test("calendar navigation handles month and leap-year boundaries", () => {
  assert.equal(shiftDate("2024-03-01", -1), "2024-02-29");
  assert.equal(shiftDate("2026-12-31", 1), "2027-01-01");
});
test("manual nutrition rejects negative, non-finite and excessive values", () => {
  assert.equal(validateNutrition({ caloriesKcal: 0, proteinG: "" }), true);
  for (const values of [
    { caloriesKcal: -1 },
    { proteinG: Infinity },
    { fatG: "x" },
    { caloriesKcal: 20001 },
  ])
    assert.equal(validateNutrition(values), false);
});

test("daily totals include known values from partially unknown multi-item meals", () => {
  const estimate = {
    items: [
      { nutrients: { caloriesKcal: 100, proteinG: 5 } },
      { nutrients: { caloriesKcal: null, proteinG: 2 } },
    ],
  };
  assert.deepEqual(dailyTotals([{ estimate }]).caloriesKcal, {
    value: 100,
    incomplete: true,
  });
  assert.deepEqual(dailyTotals([{ estimate }]).proteinG, {
    value: 7,
    incomplete: false,
  });
});
