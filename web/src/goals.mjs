import { metrics, localDate } from "./domain.mjs";
export const dietOptions = [
  "Vegetarian",
  "Vegan",
  "Pescatarian",
  "Halal",
  "Gluten-free",
  "Dairy-free",
];
export const activityOptions = {
  sedentary: ["Mostly seated", 1.2],
  light: ["Lightly active", 1.375],
  moderate: ["Moderately active", 1.55],
  active: ["Very active", 1.725],
};
export const goalDefaults = {
  goal_type: "maintain",
  target_weight_kg: null,
  target_date: null,
  weight_unit: "kg",
  dietary_preferences: [],
  equation_sex: null,
  activity_level: "sedentary",
  track_sugar: false,
  track_fiber: false,
  track_sodium: false,
};
export function goalSettings(goals) {
  return {
    ...goalDefaults,
    ...goals,
    track_fiber: goals?.track_fiber ?? goals?.fiber_g != null,
  };
}
export function progress(amount, target) {
  return Number.isFinite(amount) && Number.isFinite(target) && target > 0
    ? Math.max(0, Math.min(1, amount / target))
    : 0;
}
export function estimatedDailyBurn(profile, settings, today = localDate()) {
  const born = profile?.date_of_birth;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(born ?? "")) return null;
  const birthday = new Date(born + "T12:00:00Z");
  if (
    !Number.isFinite(birthday.getTime()) ||
    birthday.toISOString().slice(0, 10) !== born
  )
    return null;
  const [y, m, d] = born.split("-").map(Number),
    [ty, tm, td] = today.split("-").map(Number);
  const age = ty - y - (tm < m || (tm === m && td < d) ? 1 : 0);
  const weight = Number(profile.weight_kg),
    height = Number(profile.height_cm);
  if (
    age < 19 ||
    age > 78 ||
    weight < 20 ||
    weight > 500 ||
    height < 50 ||
    height > 300 ||
    !["female", "male"].includes(settings.equation_sex) ||
    !activityOptions[settings.activity_level]
  )
    return null;
  const value =
    (10 * weight +
      6.25 * height -
      5 * age +
      (settings.equation_sex === "male" ? 5 : -161)) *
    activityOptions[settings.activity_level][1];
  return Number.isFinite(value) && value > 0 ? Math.round(value) : null;
}
export function validateGoals(values, today = localDate()) {
  for (const m of metrics) {
    const v = values[m.column];
    if (
      v != null &&
      (!Number.isFinite(v) ||
        v <= 0 ||
        v >
          (m.key === "caloriesKcal"
            ? 20000
            : m.key === "sodiumMg"
              ? 100000
              : 2000))
    )
      return "Use positive targets or leave them blank.";
  }
  if (
    !["maintain", "lose", "gain"].includes(values.goal_type) ||
    !["kg", "lb"].includes(values.weight_unit)
  )
    return "Choose a valid goal type and weight unit.";
  if (
    values.target_weight_kg != null &&
    (!Number.isFinite(values.target_weight_kg) ||
      values.target_weight_kg < 20 ||
      values.target_weight_kg > 500)
  )
    return "Enter a target weight between 20 and 500 kg (44.1–1,102.3 lb).";
  if (
    values.target_date &&
    (!/^\d{4}-\d{2}-\d{2}$/.test(values.target_date) ||
      Number.isNaN(Date.parse(values.target_date + "T12:00:00Z")) ||
      new Date(values.target_date + "T12:00:00Z").toISOString().slice(0, 10) !==
        values.target_date ||
      values.target_date < today)
  )
    return "Choose today or a future target date.";
  if (
    (values.equation_sex != null &&
      !["male", "female"].includes(values.equation_sex)) ||
    !Object.hasOwn(activityOptions, values.activity_level)
  )
    return "Choose valid daily burn calculation details.";
  if (
    !Array.isArray(values.dietary_preferences) ||
    values.dietary_preferences.some((v) => !dietOptions.includes(v))
  )
    return "Choose one of the listed dietary preferences.";
  return null;
}
