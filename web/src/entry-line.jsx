import React, { useState, useEffect, useRef } from "react";
import { WaterLabel } from "./water.jsx";
import { entryWater } from "./water.mjs";
import { nutrientTotal, formatNumber } from "./domain.mjs";
export function NoteLine({
  entry,
  phase,
  focused,
  onFocused,
  onFocus,
  onBlur,
  edit,
  details,
  onWater,
  next,
}) {
  const ref = useRef(),
    [editing, setEditing] = useState(false);
  const seenEstimate = useRef(entry.estimate?.calculatedAtEpochMillis);
  const [reveal, setReveal] = useState("settled"),
    [animatedCalories, setAnimatedCalories] = useState(0);
  const [summarizing, setSummarizing] = useState(false);
  useEffect(() => {
    const timestamp = entry.estimate?.calculatedAtEpochMillis;
    if (!timestamp) {
      setReveal("settled");
      return;
    }
    if (timestamp === seenEstimate.current) return;
    seenEstimate.current = timestamp;
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    setReveal("sources");
    let frame;
    const timer = setTimeout(() => {
      setReveal("calories");
      const start = performance.now(),
        calories = nutrientTotal(entry.estimate.items, "caloriesKcal") ?? 0;
      const tick = (now) => {
        const progress = Math.min(1, (now - start) / 720);
        setAnimatedCalories(Math.round(calories * progress));
        if (progress < 1) frame = requestAnimationFrame(tick);
      };
      frame = requestAnimationFrame(tick);
    }, 1200);
    const finish = setTimeout(() => setReveal("settled"), 2570);
    return () => {
      clearTimeout(timer);
      clearTimeout(finish);
      cancelAnimationFrame(frame);
    };
  }, [entry.estimate?.calculatedAtEpochMillis]);
  useEffect(() => {
    if (
      editing ||
      !entry.estimate?.summary ||
      entry.estimate.summary === entry.body ||
      matchMedia("(prefers-reduced-motion: reduce)").matches
    )
      return;
    setSummarizing(true);
    const timer = setTimeout(() => setSummarizing(false), 780);
    return () => clearTimeout(timer);
  }, [editing, entry.estimate?.calculatedAtEpochMillis]);
  useEffect(() => {
    if (focused) {
      ref.current?.focus();
      onFocused();
    }
  }, [focused]);
  useEffect(() => {
    if (ref.current) {
      ref.current.style.height = "29px";
      ref.current.style.height = `${ref.current.scrollHeight}px`;
    }
  }, [entry.body, editing]);
  const water = entryWater(entry);
  const summary = water ? entry.body : entry.estimate?.summary || entry.body;
  return (
    <div className="note-line">
      <div className="note-text">
        <textarea
          ref={ref}
          aria-label="Food entry"
          maxLength={2000}
          rows={1}
          placeholder="Write what you ate…"
          value={entry.body}
          onChange={(e) => edit(e.target.value)}
          onFocus={() => {
            setEditing(true);
            onFocus();
          }}
          onBlur={() => {
            setEditing(false);
            onBlur();
          }}
          onKeyDown={(e) => {
            if (
              e.key === "Enter" &&
              !e.shiftKey &&
              !e.nativeEvent.isComposing
            ) {
              e.preventDefault();
              next();
            }
          }}
          className={!editing && entry.body.trim() ? "summarized" : ""}
        />
        {!editing && entry.body.trim() && (
          <button
            className="summary-label"
            aria-label={`Meal details for ${entry.body}`}
            onClick={details}
          >
            {summarizing ? (
              <span className="summarizing-label">✦ Summarizing meal…</span>
            ) : (
              summary
            )}
          </button>
        )}
      </div>
      <div
        className={`calorie-label ${phase === "calculating" || phase === "queued" ? "thinking" : ""} ${reveal === "calories" ? "calorie-reveal" : ""}`}
      >
        {water && (
          <button
            type="button"
            className="water-entry-trigger"
            aria-label={`Water tracking for ${entry.body}`}
            onClick={onWater}
          >
            <WaterLabel ml={water.milliliters} />
          </button>
        )}
        {!water?.onlyWater &&
          (phase === "calculating" ||
          phase === "saving" ||
          phase === "queued" ? (
            <>
              Thinking<span className="thinking-dots">•••</span>
            </>
          ) : phase === "error" ? (
            "Retry"
          ) : entry.estimate ? (
            reveal === "sources" ? (
              <>
                <span className="source-dots">●●</span>
                {Math.max(
                  1,
                  new Set(
                    entry.estimate.items.flatMap((item) =>
                      (item.sources ?? []).map((source) =>
                        JSON.stringify(source),
                      ),
                    ),
                  ).size,
                )}{" "}
                sources
              </>
            ) : reveal === "calories" ? (
              `✦ ${animatedCalories} cal`
            ) : (
              `${formatNumber(nutrientTotal(entry.estimate.items, "caloriesKcal"))} cal`
            )
          ) : entry.body.trim() ? (
            "Thinking"
          ) : (
            ""
          ))}
      </div>
    </div>
  );
}
