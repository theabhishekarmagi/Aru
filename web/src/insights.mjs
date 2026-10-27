import { dailyTotals, metrics, shiftDate } from "./domain.mjs";
import { dailyWater, entryWater } from "./water.mjs";
export function weeklyInsights(entries, end) {
  const saved = entries.filter(
    (e) => !e.deleted_at && !e._local && e.body?.trim(),
  );
  const days = Array.from({ length: 7 }, (_, i) => {
    const date = shiftDate(end, i - 6),
      rows = saved.filter((e) => e.journal_date === date),
      food = rows.filter((e) => !entryWater(e)?.onlyWater);
    return {
      date,
      logged: rows.length > 0,
      food: food.length > 0,
      totals: dailyTotals(food),
      waterMl: dailyWater(rows),
      hasEstimates: food.some((e) => e.estimate?.items?.length),
    };
  });
  const averages = Object.fromEntries(
    metrics.slice(0, 4).map((m) => {
      const complete = days.filter(
        (d) => d.hasEstimates && !d.totals[m.key].incomplete,
      );
      return [
        m.key,
        {
          value: complete.length
            ? Math.round(
                (complete.reduce((n, d) => n + d.totals[m.key].value, 0) /
                  complete.length) *
                  10,
              ) / 10
            : null,
          days: complete.length,
        },
      ];
    }),
  );
  return {
    days,
    averages,
    loggedDays: days.filter((d) => d.logged).length,
    totalWaterMl: days.reduce((n, d) => n + d.waterMl, 0),
    averageWaterMl: Math.round(days.reduce((n, d) => n + d.waterMl, 0) / 7),
  };
}
