const counts = {
  a: 1,
  an: 1,
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  six: 6,
  seven: 7,
  eight: 8,
  nine: 9,
  ten: 10,
  half: 0.5,
};
const count =
  "(?:[0-9]+(?:\\.[0-9]+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|half(?:\\s+a)?)";
const units =
  "(?:ml|millilitres?|milliliters?|litres?|liters?|l|glasses|glass|bottles?|cups?)";
const adjective =
  "(?:(?:plain|drinking|warm|cold|hot|filtered|mineral|sparkling|still)\\s+)*";
const filler = new Set([
  "i",
  "we",
  "drank",
  "drink",
  "drinking",
  "had",
  "have",
  "having",
  "some",
  "and",
  "plus",
  "with",
  "of",
  "today",
  "just",
  "a",
  "an",
]);
export function detectWater(text) {
  if (typeof text !== "string" || text.length > 10000) return null;
  const lower = text.toLowerCase().trim();
  if (
    /\b(coconut|tonic|sugar|sugared|sweetened|honey|rose|lemon|lime|flavou?red|juice)\s+(?:\w+\s+)?water\b|\bwater\s+(?:chestnuts?|melon|ice)\b/.test(
      lower,
    ) ||
    /\b(?:cook(?:ed|ing)?|boil(?:ed|ing)?|recipe|ingredient|mix(?:ed|ing)?)\b/.test(
      lower,
    ) ||
    /\b(?:no|without|not)\s+(?:any\s+)?water\b/.test(lower)
  )
    return null;
  const matches = [];
  const add = (m, ml, assumed) => {
    if (
      !matches.some((v) => v.start < m.index + m[0].length && m.index < v.end)
    )
      matches.push({ start: m.index, end: m.index + m[0].length, ml, assumed });
  };
  const amount = (q, u) => {
    const word = q.replace(/ a$/, "");
    const n = Object.hasOwn(counts, word) ? counts[word] : Number(word);
    const factor = /^(glass|cup)/.test(u)
      ? 250
      : u.startsWith("bottle")
        ? 500
        : u === "l" || u.startsWith("lit")
          ? 1000
          : 1;
    const ml = n * factor;
    return Number.isFinite(n) && n > 0 && ml >= 1 && ml <= 20000
      ? { ml: Math.round(ml), assumed: /^(glass|cup|bottle)/.test(u) }
      : null;
  };
  for (const pattern of [
    new RegExp(
      `\\b(${count})\\s*(${units})\\s+(?:of\\s+)?${adjective}water\\b`,
      "g",
    ),
    new RegExp(
      `\\b${adjective}water\\s*[,:(-]?\\s*(${count})\\s*(${units})\\b`,
      "g",
    ),
  ])
    for (const m of lower.matchAll(pattern)) {
      const a = amount(m[1], m[2]);
      if (!a) return null;
      add(m, a.ml, a.assumed);
    }
  for (const m of lower.matchAll(
    new RegExp(
      `\\b(glasses|glass|bottles?|cups?)\\s+(?:of\\s+)?${adjective}water\\b`,
      "g",
    ),
  )) {
    const a = amount("one", m[1]);
    add(m, a.ml, true);
  }
  for (const m of lower.matchAll(new RegExp(`\\b${adjective}water\\b`, "g")))
    add(m, 250, true);
  const milliliters = matches.reduce((n, v) => n + v.ml, 0);
  if (!matches.length || milliliters > 20000) return null;
  const chars = lower.split("");
  for (const m of matches) for (let i = m.start; i < m.end; i++) chars[i] = " ";
  return {
    milliliters,
    detectionKey: matches
      .sort((a, b) => a.start - b.start)
      .map((m) => lower.slice(m.start, m.end))
      .join("|"),
    onlyWater: (chars.join("").match(/[a-z0-9]+/g) ?? []).every((w) =>
      filler.has(w),
    ),
    assumed: matches.some((m) => m.assumed),
    portionLabel: null,
  };
}
export function waterForText(text, prior) {
  const next = detectWater(text);
  if (!next) return null;
  return prior?.detectionKey === next.detectionKey &&
    Number.isInteger(prior.milliliters) &&
    prior.milliliters > 0 &&
    prior.milliliters <= 20000
    ? { ...prior, onlyWater: next.onlyWater }
    : next;
}
export function entryWater(entry) {
  return waterForText(entry.body ?? "", entry.water);
}
export function hydrateWater(entry) {
  const water = entryWater(entry);
  return water?.onlyWater
    ? { ...entry, water, estimate: null, status: "READY" }
    : { ...entry, water };
}
export function dailyWater(entries, date) {
  return entries
    .filter((e) => !e.deleted_at && (!date || e.journal_date === date))
    .reduce((n, e) => n + (entryWater(e)?.milliliters ?? 0), 0);
}
export const waterGoal = (goals) =>
  Number.isInteger(goals?.water_goal_ml) &&
  goals.water_goal_ml > 0 &&
  goals.water_goal_ml <= 20000
    ? goals.water_goal_ml
    : 2000;
