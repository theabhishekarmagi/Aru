import React, { useState } from "react";
import { MacroIcon } from "./macro-icon.jsx";
import { Flame, ChevronDown, ChevronRight } from "lucide-react";
import { Sheet, ProfileArt } from "./ui.jsx";
import { metrics, formatNumber, localDate } from "./domain.mjs";
import {
  activityOptions,
  dietOptions,
  estimatedDailyBurn,
  goalSettings,
  progress,
  validateGoals,
} from "./goals.mjs";
const macroMetrics = [metrics[2], metrics[1], metrics[3]];
const limit = (m) =>
  m.key === "caloriesKcal" ? 20000 : m.key === "sodiumMg" ? 100000 : 2000;
const totalText = (total) =>
  `${formatNumber(total?.value ?? 0)}${total?.incomplete ? "+" : ""}`;
export function GoalsProgress({ goals, totals, onClick }) {
  const settings = goalSettings(goals);
  return (
    <button
      type="button"
      className="goals-preview-card"
      onClick={onClick}
      aria-label="Open nutrition goals"
    >
      <span className="goals-preview-heading">
        Goals <ChevronRight size={16} />
      </span>
      <span className="goals-calorie-line">
        <span>
          <Flame size={15} /> Calories
        </span>
        <strong>
          {totalText(totals.caloriesKcal)} /{" "}
          {formatNumber(goals?.calories_kcal)}
        </strong>
      </span>
      <span className="goal-track">
        <span
          style={{
            width: `${progress(totals.caloriesKcal?.value, goals?.calories_kcal) * 100}%`,
          }}
        />
      </span>
      <span className="goals-rings">
        {macroMetrics.map((m) => (
          <span className="goal-ring-item" key={m.key}>
            <span
              className="goal-ring"
              role="img"
              aria-label={`${m.label}: ${totalText(totals[m.key])} of ${formatNumber(goals?.[m.column])} grams`}
              style={{
                "--progress": `${progress(totals[m.key]?.value, goals?.[m.column]) * 100}%`,
                "--ring": m.color,
              }}
            >
              <strong>{totalText(totals[m.key])}</strong>
            </span>
            <span>{m.label}</span>
          </span>
        ))}
      </span>
      {!goals?.calories_kcal && (
        <span className="goal-hint">Set targets that work for you</span>
      )}
      {(settings.track_sugar ||
        settings.track_fiber ||
        settings.track_sodium) && (
        <span className="micro-progress">
          {metrics
            .slice(4)
            .filter((m) => settings[`track_${m.label.toLowerCase()}`])
            .map((m) => (
              <span key={m.key}>
                {m.label} {totalText(totals[m.key])} /{" "}
                {formatNumber(goals?.[m.column])} {m.unit}
              </span>
            ))}
        </span>
      )}
    </button>
  );
}
function Toggle({ label, checked, onChange, disabled }) {
  return (
    <label className="goals-toggle">
      <span>{label}</span>
      <input
        type="checkbox"
        role="switch"
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
    </label>
  );
}
export function Goals({ app, totals, close, editProfile }) {
  const [settings, setSettings] = useState(() => goalSettings(app.data.goals));
  const [fields, setFields] = useState(() =>
    Object.fromEntries(
      metrics.map((m) => [m.column, app.data.goals?.[m.column] ?? ""]),
    ),
  );
  const [target, setTarget] = useState(() =>
    settings.target_weight_kg == null
      ? ""
      : Number(
          (
            settings.target_weight_kg *
            (settings.weight_unit === "lb" ? 2.2046226218 : 1)
          ).toFixed(1),
        ),
  );
  const [useDate, setUseDate] = useState(!!settings.target_date),
    [error, setError] = useState("");
  const update = (key, value) => setSettings((s) => ({ ...s, [key]: value }));
  const burn = estimatedDailyBurn(app.data.profile, settings);
  const save = async (e) => {
    e.preventDefault();
    const values = {
      ...Object.fromEntries(
        Object.keys(goalSettings(null)).map((key) => [key, settings[key]]),
      ),
      ...Object.fromEntries(
        metrics.map((m) => [
          m.column,
          fields[m.column] === "" ? null : Number(fields[m.column]),
        ]),
      ),
      target_weight_kg:
        target === ""
          ? null
          : Number(target) / (settings.weight_unit === "lb" ? 2.2046226218 : 1),
      target_date: useDate ? settings.target_date : null,
    };
    if (useDate && !values.target_date) {
      setError("Choose a target date or turn the date option off.");
      return;
    }
    const problem = validateGoals(values);
    if (problem) {
      setError(problem);
      return;
    }
    if (await app.goals(values)) close();
  };
  const numberField = (m, label = m.label) => (
    <label className="goal-setting-row" key={m.key}>
      <span className="goal-macro-label">
        {["proteinG", "carbsG", "fatG"].includes(m.key) && (
          <MacroIcon kind={m.key} />
        )}{" "}
        {label}
      </span>
      <span className="goal-inline-value">
        <input
          aria-label={`${m.label} target`}
          type="number"
          inputMode="decimal"
          min="0.1"
          max={limit(m)}
          step="any"
          value={fields[m.column]}
          placeholder="Not set"
          onChange={(e) =>
            setFields((f) => ({ ...f, [m.column]: e.target.value }))
          }
        />
        <small>{m.unit}</small>
      </span>
    </label>
  );
  return (
    <Sheet
      title="Nutrition Goals"
      kind="nutrition-goals-sheet"
      close={close}
      action={
        <button
          type="submit"
          form="nutrition-goals-form"
          className="goal-save"
          disabled={app.busy}
        >
          {app.busy ? "Saving…" : "Save"}
        </button>
      }
    >
      <form id="nutrition-goals-form" onSubmit={save}>
        <div className="goals-art">
          <ProfileArt step={3} />
        </div>
        <p className="goals-section-title">Your Daily Burn</p>
        <div className="goal-settings-group burn-row">
          <Flame size={21} />
          <div>
            <small>TDEE · estimated daily burn</small>
            <strong>
              {burn ? `${formatNumber(burn)} kcal / day` : "Not calculated"}
            </strong>
          </div>
          <button type="button" className="text-button" onClick={editProfile}>
            Edit Profile <ChevronRight size={15} />
          </button>
        </div>
        <details className="goal-disclosure">
          <summary>
            Daily burn calculation details <ChevronDown size={16} />
          </summary>
          <p>
            Uses your profile’s birth date, height and weight. Add the following
            details to calculate an estimate.
          </p>
          <label className="goal-setting-row">
            <span>Sex used by equation</span>
            <select
              aria-label="Sex used by equation"
              value={settings.equation_sex ?? ""}
              onChange={(e) => update("equation_sex", e.target.value || null)}
            >
              <option value="">Not set</option>
              <option value="female">Female</option>
              <option value="male">Male</option>
            </select>
          </label>
          <label className="goal-setting-row">
            <span>Activity level</span>
            <select
              aria-label="Activity level"
              value={settings.activity_level}
              onChange={(e) => update("activity_level", e.target.value)}
            >
              {Object.entries(activityOptions).map(([key, [label]]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <p>
            Mifflin–St Jeor resting energy × activity factor. Available for ages
            19–78; an estimate, not a measured burn or an intake recommendation.{" "}
            <a
              href="https://pubmed.ncbi.nlm.nih.gov/2305711/"
              target="_blank"
              rel="noreferrer"
            >
              Method
            </a>
          </p>
        </details>
        <p className="goals-section-title">Weight Goals</p>
        <section className="goal-settings-group">
          <label className="goal-setting-row">
            <span>Goal Type</span>
            <select
              aria-label="Goal Type"
              value={settings.goal_type}
              onChange={(e) => update("goal_type", e.target.value)}
            >
              <option value="maintain">Maintain Weight</option>
              <option value="lose">Lose Weight</option>
              <option value="gain">Gain Weight</option>
            </select>
          </label>
          <label className="goal-setting-row">
            <span>Weight Unit</span>
            <select
              aria-label="Weight Unit"
              value={settings.weight_unit}
              onChange={(e) => {
                const unit = e.target.value;
                if (target !== "")
                  setTarget(
                    Number(
                      (
                        Number(target) *
                        (unit === "lb" ? 2.2046226218 : 1 / 2.2046226218)
                      ).toFixed(1),
                    ),
                  );
                update("weight_unit", unit);
              }}
            >
              <option value="kg">kg</option>
              <option value="lb">lb</option>
            </select>
          </label>
          <label className="goal-setting-row">
            <span>Target Weight</span>
            <span className="goal-inline-value">
              <input
                aria-label="Target Weight"
                type="number"
                min={settings.weight_unit === "lb" ? 44.1 : 20}
                max={settings.weight_unit === "lb" ? 1102.3 : 500}
                step="any"
                value={target}
                placeholder="Not set"
                onChange={(e) => setTarget(e.target.value)}
              />
              <small>{settings.weight_unit}</small>
            </span>
          </label>
          <Toggle
            label="Set Target Date"
            checked={useDate}
            onChange={setUseDate}
          />
          {useDate && (
            <label className="goal-setting-row">
              <span>Target Date</span>
              <input
                type="date"
                aria-label="Target Date"
                min={localDate()}
                required
                value={settings.target_date ?? ""}
                onChange={(e) => update("target_date", e.target.value)}
              />
            </label>
          )}
        </section>
        <details className="goal-disclosure dietary-disclosure">
          <summary>
            ✦ Lifestyle &amp; Dietary Preferences <ChevronDown size={16} />
          </summary>
          <div className="diet-options">
            {dietOptions.map((d) => (
              <label key={d}>
                <input
                  type="checkbox"
                  checked={settings.dietary_preferences.includes(d)}
                  onChange={(e) =>
                    update(
                      "dietary_preferences",
                      e.target.checked
                        ? [...settings.dietary_preferences, d]
                        : settings.dietary_preferences.filter((v) => v !== d),
                    )
                  }
                />
                {d}
              </label>
            ))}
          </div>
          <p>
            Saved as your preferences. Meal estimates still follow the food you
            actually log.
          </p>
        </details>
        <p className="goals-section-title">Daily Nutrition Goals</p>
        <section className="goal-settings-group">
          {metrics.slice(0, 4).map((m) =>
            numberField(
              m,
              {
                caloriesKcal: "🔥 Daily Calorie Goal",
                proteinG: "Target Protein",
                carbsG: "Target Carbs",
                fatG: "Target Fat",
              }[m.key],
            ),
          )}
        </section>
        <p className="goals-section-title">Additional Nutrients</p>
        <section className="goal-settings-group">
          {[metrics[5], metrics[4], metrics[6]].map((m) => (
            <React.Fragment key={m.key}>
              <Toggle
                label={`Track ${m.label}`}
                checked={settings[`track_${m.label.toLowerCase()}`]}
                onChange={(value) =>
                  update(`track_${m.label.toLowerCase()}`, value)
                }
              />
              {settings[`track_${m.label.toLowerCase()}`] &&
                numberField(m, `${m.label} target`)}
            </React.Fragment>
          ))}
        </section>
        <details className="goal-disclosure">
          <summary>
            Not sure what values to put here? <ChevronDown size={16} />
          </summary>
          <p>
            Targets are optional. Use targets you already follow or agree them
            with a qualified professional. Weight goals and target dates are
            planning notes and don’t change your daily targets automatically. A
            + on the journal card means some nutrition values are missing; older
            meals may need recalculation or manual values for sugar and sodium.
          </p>
        </details>
        {error && (
          <p className="error-text" role="alert">
            {error}
          </p>
        )}
        <div className="goal-form-actions">
          <button
            type="button"
            className="button secondary"
            onClick={close}
            disabled={app.busy}
          >
            Cancel
          </button>
          <button className="button" disabled={app.busy}>
            Save goals
          </button>
        </div>
      </form>
    </Sheet>
  );
}
