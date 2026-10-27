import React, { useState } from "react";
import { WaterLabel } from "./water.jsx";
import { GoalsProgress } from "./goals.jsx";
import { formatNumber } from "./domain.mjs";
export function GoalsFooter({
  goals,
  totals,
  onEdit,
  waterTotal,
  waterGoal,
  onWater,
}) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="goals-footer">
      {onWater && (
        <button
          type="button"
          className="water-summary-pill"
          aria-label="Daily water tracking"
          onClick={onWater}
        >
          <WaterLabel ml={waterTotal ?? 0} />
          <span>/ {waterGoal ?? 2000} ml</span>
        </button>
      )}
      {expanded && (
        <div id="journal-goals-progress">
          <GoalsProgress goals={goals} totals={totals} onClick={onEdit} />
        </div>
      )}
      <DailyBar
        totals={totals}
        expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      />
    </div>
  );
}
export function DailyBar({ totals, onClick, expanded }) {
  return (
    <button
      className="pill daily-bar"
      aria-label="Daily totals and goals"
      aria-expanded={expanded}
      aria-controls={
        expanded === undefined ? undefined : "journal-goals-progress"
      }
      onClick={onClick}
    >
      <span>
        <i>🔥</i>
        <b>
          {formatNumber(totals.caloriesKcal.value)}
          {totals.caloriesKcal.incomplete ? "+" : ""}
        </b>
      </span>
      {[
        ["carbsG", "C", "#ff3868"],
        ["proteinG", "P", "#e5b100"],
        ["fatG", "F", "#d92ce6"],
      ].map(([key, label, color]) => (
        <span key={key}>
          <i style={{ color }}>{label}</i>
          <b>{formatNumber(totals[key].value)}</b>
        </span>
      ))}
    </button>
  );
}
