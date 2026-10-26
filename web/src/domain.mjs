export const metrics = [
  {
    key: "caloriesKcal",
    column: "calories_kcal",
    label: "Calories",
    unit: "kcal",
    color: "#ffb800",
  },
  {
    key: "proteinG",
    column: "protein_g",
    label: "Protein",
    unit: "g",
    color: "#e5b100",
  },
  {
    key: "carbsG",
    column: "carbs_g",
    label: "Carbs",
    unit: "g",
    color: "#ff3868",
  },
  { key: "fatG", column: "fat_g", label: "Fat", unit: "g", color: "#d92ce6" },
  {
    key: "fiberG",
    column: "fiber_g",
    label: "Fiber",
    unit: "g",
    color: "#40ac82",
  },
  {
    key: "sugarG",
    column: "sugar_g",
    label: "Sugar",
    unit: "g",
    color: "#45a8df",
  },
  {
    key: "sodiumMg",
    column: "sodium_mg",
    label: "Sodium",
    unit: "mg",
    color: "#ed9963",
  },
];
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function shiftDate(date, amount) {
  const next = new Date(`${date}T12:00:00`);
  next.setDate(next.getDate() + amount);
  return localDate(next);
}
export function nutrientTotal(items, key) {
  if (!items?.length || items.some((item) => item.nutrients?.[key] == null))
    return null;
  return (
    Math.round(
      items.reduce((total, item) => total + item.nutrients[key], 0) * 10,
    ) / 10
  );
}
export function dailyTotals(entries) {
  const estimated = entries.filter(
    (entry) => !entry.deleted_at && entry.estimate?.items?.length,
  );
  return Object.fromEntries(
    metrics.map(({ key }) => {
      const values = estimated.flatMap((entry) =>
        entry.estimate.items.map((item) => item.nutrients?.[key] ?? null),
      );
      return [
        key,
        {
          value: Math.round(values.reduce((a, b) => a + (b ?? 0), 0) * 10) / 10,
          incomplete:
            entries.some(
              (e) =>
                !e.deleted_at &&
                !e.estimate &&
                (typeof e.body !== "string" || e.body.trim()),
            ) || values.some((v) => v === null),
        },
      ];
    }),
  );
}
export const formatNumber = (value) =>
  value == null
    ? "—"
    : new Intl.NumberFormat("en", { maximumFractionDigits: 1 }).format(value);
export function manualEstimate(values, body) {
  return {
    summary: body.slice(0, 60),
    items: [
      {
        name: body,
        portion: {
          quantity: 1,
          unitKey: "logged portion",
          kind: "SERVING",
          grams: null,
          milliliters: null,
          assumed: false,
          assumption: null,
        },
        nutrients: Object.fromEntries(
          metrics.map(({ key }) => [
            key,
            values[key] === "" || values[key] == null
              ? null
              : Number(values[key]),
          ]),
        ),
        assumptions: [],
        sources: [],
      },
    ],
    explanation:
      "Nutrition entered manually. Values are for the entire logged portion.",
    needsReview: false,
    calculatedAtEpochMillis: Date.now(),
    manual: true,
  };
}
export function validateNutrition(values) {
  return metrics.every(
    ({ key }) =>
      values[key] === "" ||
      values[key] == null ||
      (Number.isFinite(Number(values[key])) &&
        Number(values[key]) >= 0 &&
        Number(values[key]) <=
          (key === "caloriesKcal"
            ? 20000
            : key === "sodiumMg"
              ? 100000
              : 2000)),
  );
}
