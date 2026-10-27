import React, { useState } from "react";
import { Sheet } from "./ui.jsx";
import { metrics, formatNumber, localDate, shiftDate } from "./domain.mjs";
import { weeklyInsights } from "./insights.mjs";
import { MacroIcon } from "./macro-icon.jsx";
function Bars({ days, water = false }) {
  const values = days.map((d) =>
      water ? d.waterMl : d.hasEstimates ? d.totals.caloriesKcal.value : null,
    ),
    max = Math.max(1, ...values.filter((v) => v !== null));
  return (
    <div className={`insight-bars ${water ? "water-bars" : ""}`}>
      {days.map((day, i) => {
        const value = values[i],
          partial = !water && day.totals.caloriesKcal.incomplete,
          label = `${day.date}: ${formatNumber(value)}${partial && value !== null ? "+" : ""} ${water ? "ml" : "kcal"}`;
        return (
          <div
            className="insight-bar-day"
            key={day.date}
            role="img"
            aria-label={label}
          >
            <span>
              {formatNumber(value)}
              {partial && value !== null ? "+" : ""}
            </span>
            <div className="insight-bar-space">
              <i
                style={{
                  height: `${value == null ? 2 : Math.max(2, (value / max) * 100)}%`,
                  background: value === null ? "#e8e2eb" : undefined,
                }}
              />
            </div>
            <small>
              {new Date(day.date + "T12:00:00").toLocaleDateString("en", {
                weekday: "short",
              })}
            </small>
          </div>
        );
      })}
    </div>
  );
}
export function WeeklyInsights({ entries, date, close }) {
  const today = localDate(),
    [end, setEnd] = useState(date > today ? today : date),
    report = weeklyInsights(entries, end);
  return (
    <Sheet title="Weekly Insights" close={close} kind="insights-sheet">
      <nav className="insight-week-nav" aria-label="Insight week">
        <button onClick={() => setEnd(shiftDate(end, -7))}>‹ Previous</button>
        <span>
          {shiftDate(end, -6)} – {end}
        </span>
        <button
          disabled={end >= today}
          onClick={() =>
            setEnd(shiftDate(end, 7) > today ? today : shiftDate(end, 7))
          }
        >
          Next ›
        </button>
      </nav>
      <section className="white-card consistency-card">
        <h3>{report.loggedDays} / 7 days logged</h3>
        <div className="consistency-days">
          {report.days.map((d) => (
            <span
              key={d.date}
              title={`${d.date}: ${d.logged ? "logged" : "no entries"}`}
            >
              <i className={d.logged ? "logged" : ""} />
              <small>
                {new Date(d.date + "T12:00:00").toLocaleDateString("en", {
                  weekday: "narrow",
                })}
              </small>
            </span>
          ))}
        </div>
        <p className="small-muted">
          Counts days with a saved meal or water entry.
        </p>
      </section>
      <section className="white-card">
        <h3>Daily calorie totals</h3>
        <Bars days={report.days} />
      </section>
      <section className="white-card">
        <h3>Daily averages</h3>
        <div className="insight-averages">
          {metrics.slice(0, 4).map((m) => (
            <div key={m.key}>
              <span>
                <MacroIcon kind={m.key} />
                {m.label}
              </span>
              <strong>
                {formatNumber(report.averages[m.key].value)} {m.unit}
              </strong>
            </div>
          ))}
        </div>
        <p className="small-muted">
          Averages use days with complete values for each nutrient. + marks
          incomplete totals; — means no calculated value.
        </p>
      </section>
      <section className="white-card">
        <h3>Daily water intake</h3>
        <Bars days={report.days} water />
        <strong className="water-amount">
          {formatNumber(report.averageWaterMl)} ml / day ·{" "}
          {formatNumber(report.totalWaterMl)} ml this week
        </strong>
        <p className="small-muted">Water average uses all 7 days.</p>
      </section>
    </Sheet>
  );
}
