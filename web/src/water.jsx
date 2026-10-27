import React, { useEffect, useRef, useState } from "react";
import { Droplet } from "lucide-react";
import { Sheet, Field, Button } from "./ui.jsx";
import { dailyWater, entryWater, waterGoal } from "./water.mjs";
import { formatNumber } from "./domain.mjs";
export function WaterLabel({ ml }) {
  return (
    <span className="water-amount">
      <Droplet size={17} fill="currentColor" />
      {formatNumber(ml)} ml
    </span>
  );
}
export function DescriptionEditor({ entry, app, working }) {
  const [text, setText] = useState(entry.body);
  useEffect(() => setText(entry.body), [entry.body]);
  return (
    <section className="white-card description-editor">
      <Field
        label="Description"
        as="textarea"
        value={text}
        maxLength={2000}
        disabled={working}
        onChange={(e) => setText(e.target.value)}
      />
      {text !== entry.body && (
        <Button
          disabled={working || !text.trim()}
          onClick={async () => {
            app.edit(entry.id, text);
            await app.commit(entry.id);
          }}
        >
          Save description
        </Button>
      )}
    </section>
  );
}
export function WaterEntryControls({ entry, app, working }) {
  const water = entryWater(entry),
    [custom, setCustom] = useState(water?.milliliters ?? 250);
  useEffect(
    () => setCustom(water?.milliliters ?? 250),
    [entry.id, water?.milliliters],
  );
  if (!water) return null;
  const n = Number(custom),
    valid = custom !== "" && Number.isInteger(n) && n > 0 && n <= 20000;
  return (
    <section className="water-entry-options">
      <h3>Water amount</h3>
      <WaterLabel ml={water.milliliters} />
      <div className="water-portions">
        {[
          ["1 glass · 250 ml", 250],
          ["2 glasses · 500 ml", 500],
          ["1 bottle · 500 ml", 500],
          ["1 litre · 1000 ml", 1000],
        ].map(([label, ml]) => (
          <button
            type="button"
            key={label}
            disabled={working}
            className={water.portionLabel === label ? "selected" : ""}
            onClick={() => app.water(entry, ml, label)}
          >
            {label}
          </button>
        ))}
      </div>
      <p className="small-muted">
        Glass preset: 250 ml. Bottle preset: 500 ml. Enter a measured amount
        below.
      </p>
      <Field
        label="Custom water amount"
        unit="ml"
        type="number"
        min="1"
        max="20000"
        step="1"
        value={custom}
        disabled={working}
        onChange={(e) => setCustom(e.target.value)}
      />
      <Button
        disabled={working || !valid}
        onClick={() => app.water(entry, n, "Custom amount")}
      >
        Save water amount
      </Button>
    </section>
  );
}
export function WaterTracker({ app, date, close }) {
  const goal = waterGoal(app.data.goals),
    total = dailyWater(app.data.entries, date),
    [target, setTarget] = useState(goal),
    [error, setError] = useState(""),
    [filled, setFilled] = useState(false),
    [quickIds, setQuickIds] = useState({}),
    [quickPending, setQuickPending] = useState(null);
  const quickLock = useRef(false);
  const quickEntry = (ml) =>
    app.data.entries.find(
      (entry) =>
        entry.id === quickIds[ml] &&
        !entry.deleted_at &&
        entry.journal_date === date &&
        entryWater(entry)?.onlyWater &&
        entryWater(entry)?.milliliters === ml,
    );
  async function toggleQuickWater(ml) {
    if (quickLock.current || app.busy) return;
    quickLock.current = true;
    setQuickPending(ml);
    try {
      const entry = quickEntry(ml);
      if (entry) {
        if (await app.mutate(entry, { deleted_at: new Date().toISOString() }))
          setQuickIds((ids) => ({ ...ids, [ml]: null }));
      } else {
        const created = await app.addWater(date, ml);
        if (created?.id) setQuickIds((ids) => ({ ...ids, [ml]: created.id }));
      }
    } catch {
      app.notify("Couldn’t update water. Please try again.");
    } finally {
      quickLock.current = false;
      setQuickPending(null);
    }
  }
  useEffect(() => {
    const id = requestAnimationFrame(() => setFilled(true));
    return () => cancelAnimationFrame(id);
  }, []);
  useEffect(() => setTarget(goal), [goal]);
  const percentage = Math.round((total / goal) * 100),
    fraction = Math.min(100, percentage),
    n = Number(target);
  return (
    <Sheet title="Daily Water" kind="water-tracker-sheet" close={close}>
      <p className="water-day">{date}</p>
      <div
        className="water-vessel"
        role="img"
        aria-label={`${formatNumber(total)} of ${formatNumber(goal)} ml, ${percentage} percent`}
      >
        <div
          className="water-fill"
          style={{ height: `${filled ? fraction : 0}%` }}
        />
        <Droplet className="water-vessel-drop" size={30} />
        <div className="water-vessel-label">
          <strong>{formatNumber(total)} ml</strong>
          <span>of {formatNumber(goal)} ml</span>
          <b>{percentage}%</b>
        </div>
      </div>
      <p className="water-caption">Your water logged for this day</p>
      <div className="water-quick">
        {[
          [250, "glass"],
          [500, "bottle"],
        ].map(([ml, name]) => (
          <Button
            key={ml}
            type="button"
            secondary
            aria-pressed={!!quickEntry(ml)}
            disabled={app.busy || quickPending !== null}
            onClick={() => toggleQuickWater(ml)}
          >
            {quickPending === ml
              ? "Saving…"
              : `${quickEntry(ml) ? "Undo" : "+"} 1 ${name} · ${ml} ml`}
          </Button>
        ))}
      </div>
      <p className="small-muted water-quick-hint">
        Tap again to undo the glass or bottle you just added.
      </p>
      <form
        className="water-goal-form"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!Number.isInteger(n) || n < 1 || n > 20000) {
            setError("Use a whole number from 1 to 20000 ml.");
            return;
          }
          if (await app.goals({ water_goal_ml: n })) {
            setError("");
            app.notify("Water goal saved", "success");
          }
        }}
      >
        <h3>Your daily water goal</h3>
        <p className="small-muted">
          Starts at 2 litres. Adjust it to the target you want to follow.
        </p>
        <Field
          label="Daily water goal"
          unit="ml"
          type="number"
          min="1"
          max="20000"
          step="1"
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          required
        />
        <Button disabled={app.busy}>Save water goal</Button>
        {error && (
          <p className="error-text" role="alert">
            {error}
          </p>
        )}
      </form>
    </Sheet>
  );
}
