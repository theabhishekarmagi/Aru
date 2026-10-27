import { DescriptionEditor, WaterEntryControls } from "./water.jsx";
import { entryWater } from "./water.mjs";
import React, { useState } from "react";
import { MacroIcon } from "./macro-icon.jsx";
import { MoreHorizontal, ChevronDown, Plus, Check } from "lucide-react";
import { Sheet, Button, Field, IconButton } from "./ui.jsx";
import {
  metrics,
  nutrientTotal,
  formatNumber,
  validateNutrition,
} from "./domain.mjs";
export function NutrientRow({ items }) {
  return (
    <>
      <div className="nutrient-row">
        {metrics.slice(1, 4).map((m, i) => (
          <div key={m.key}>
            <strong>{formatNumber(nutrientTotal(items, m.key))} g</strong>
            <span style={{ color: m.color }}>
              <MacroIcon kind={m.key} /> {m.label}
            </span>
          </div>
        ))}
      </div>
      <div className="nutrient-extras">
        {metrics
          .slice(4)
          .filter((m) => nutrientTotal(items, m.key) != null)
          .map((m) => (
            <span key={m.key}>
              {m.label}{" "}
              <strong>
                {formatNumber(nutrientTotal(items, m.key))} {m.unit}
              </strong>
            </span>
          ))}
      </div>
    </>
  );
}
export function NutritionSummary({ estimate }) {
  return (
    <section className="white-card nutrition-summary">
      <div>
        <span>🔥</span>
        <strong>
          {formatNumber(nutrientTotal(estimate.items, "caloriesKcal"))}
        </strong>
        <small>total calories</small>
      </div>
      <NutrientRow items={estimate.items} />
    </section>
  );
}
export function Details({ entry, app, close }) {
  const [menu, setMenu] = useState(false),
    [manual, setManual] = useState(false),
    [saving, setSaving] = useState(false),
    [name, setName] = useState("");
  const water = entryWater(entry),
    pureWater = !!water?.onlyWater;
  const estimate = pureWater ? null : entry.estimate,
    working =
      app.busy ||
      ["saving", "calculating", "queued"].includes(app.pending[entry.id]);
  const references = [
    ...new Map(
      (estimate?.items ?? [])
        .flatMap((i) => i.sources ?? [])
        .map((s) => [JSON.stringify(s), s]),
    ).values(),
  ];
  const score = estimate?.confidenceScore,
    color = score >= 80 ? "#22b86a" : score >= 60 ? "#e49b19" : "#8354ee";
  return (
    <>
      <Sheet
        title={pureWater ? "Water" : "Nutrition Details"}
        close={close}
        kind="nutrition-sheet"
        action={
          <div className="meal-menu">
            <IconButton label="Meal options" onClick={() => setMenu(!menu)}>
              <MoreHorizontal size={25} />
            </IconButton>
            {menu && (
              <div className="dropdown">
                <button
                  disabled={!estimate || working}
                  onClick={() => {
                    setMenu(false);
                    setSaving(true);
                  }}
                >
                  Save meal
                </button>
                <button
                  className="destructive"
                  disabled={working}
                  onClick={async () => {
                    if (await app.remove(entry)) close();
                  }}
                >
                  Delete meal
                </button>
              </div>
            )}
          </div>
        }
      >
        <DescriptionEditor entry={entry} app={app} working={working} />
        <p className="logged-time">
          {entry.created_at
            ? `Logged at ${new Date(entry.created_at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}`
            : "Logged time unavailable"}
        </p>
        {water && (
          <WaterEntryControls entry={entry} app={app} working={working} />
        )}
        {pureWater ? (
          <p className="small-muted">
            Plain water is recorded separately from calories and macros.
          </p>
        ) : estimate ? (
          <>
            <NutritionSummary estimate={estimate} />
            <h3 className="section-label">Items</h3>
            {estimate.items.map((item, i) => (
              <details className="white-card item-card" key={i}>
                <summary>
                  <div>
                    <strong>{item.name}</strong>
                    <small>
                      {formatNumber(item.portion.quantity)}{" "}
                      {item.portion.unitKey}
                      {item.portion.assumed ? " · estimated portion" : ""}
                    </small>
                  </div>
                  <b>{formatNumber(item.nutrients.caloriesKcal)} cal</b>
                  <ChevronDown size={15} />
                </summary>
                <div className="item-expanded">
                  <NutrientRow items={[item]} />
                  {[
                    ...new Set(
                      [
                        ...(item.assumptions ?? []),
                        item.portion.assumption,
                      ].filter(Boolean),
                    ),
                  ].map((text, n) => (
                    <p key={n}>{text}</p>
                  ))}
                </div>
              </details>
            ))}
            <h3 className="section-label">
              {estimate.manual ? "Your nutrition" : "How confident is Aru?"}
            </h3>
            <div className="white-card confidence-card">
              <div className="confidence-top">
                {estimate.manual ? (
                  <span className="manual-check">
                    <Check />
                  </span>
                ) : (
                  <span
                    className="confidence-ring"
                    style={{
                      "--score": `${Number.isFinite(score) ? score : 0}%`,
                      "--color": color,
                    }}
                  >
                    <b>{Number.isFinite(score) ? score : "—"}</b>
                  </span>
                )}
                <div>
                  <small>
                    {estimate.manual ? "Manually entered" : "Confidence level"}
                  </small>
                  <strong style={{ color }}>
                    {estimate.manual
                      ? "Entered by you"
                      : score >= 80
                        ? "High"
                        : score >= 60
                          ? "Good"
                          : "Review suggested"}
                  </strong>
                </div>
              </div>
              <p>
                {estimate.explanation?.replace(/^AI estimate\s*[—-]\s*/i, "")}
              </p>
              <button className="text-button" onClick={() => setManual(true)}>
                Something off? Edit nutrition
              </button>
            </div>
            {!!references.length && (
              <>
                <h3 className="section-label">References</h3>
                <div className="white-card reference-count">
                  <span className="source-dots">●●●</span>
                  {references.length} reference
                  {references.length === 1 ? "" : "s"}
                </div>
                {references.map((source, i) => (
                  <div className="white-card reference-card" key={i}>
                    <strong>{source.title}</strong>
                    <p>
                      {[
                        source.recordId,
                        source.version,
                        source.market,
                        source.menuItem,
                        source.menuSize,
                        source.basis,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                    {source.url && /^https:\/\//.test(source.url) && (
                      <a
                        className="text-button"
                        target="_blank"
                        rel="noopener noreferrer"
                        href={source.url}
                      >
                        Open source ↗
                      </a>
                    )}
                  </div>
                ))}
                <p className="small-muted">
                  Reference links may be general comparisons; they do not prove
                  an exact database match.
                </p>
              </>
            )}
          </>
        ) : (
          <div className="white-card">
            <strong>Nutrition hasn’t been calculated yet.</strong>
            <p className="muted">
              Nutrition calculates automatically when you pause typing. You can
              also enter values manually. Unknown values stay blank.
            </p>
            {entry._dirty && (
              <button
                className="text-button"
                disabled={working}
                onClick={() => app.commit(entry.id)}
              >
                Save this line again
              </button>
            )}
          </div>
        )}
        {!pureWater && (
          <div className="detail-bottom-actions">
            <button
              className="text-button"
              disabled={working || !entry.body.trim()}
              onClick={() => setManual(!manual)}
            >
              Edit nutrition
            </button>
            {app.pending[entry.id] === "error" && (
              <button
                className="text-button"
                disabled={working}
                onClick={() => app.commit(entry.id)}
              >
                Retry
              </button>
            )}
          </div>
        )}
        {!pureWater && manual && (
          <ManualEditor
            entry={entry}
            busy={working}
            save={async (estimate) => {
              try {
                const saved = await app.persist(entry.id);
                if (!saved) return false;
                const updated = await app.mutate(saved, {
                  estimate,
                  status: "MANUAL",
                });
                if (updated) {
                  setManual(false);
                  return true;
                }
                return false;
              } catch (error) {
                app.notify(error.message);
                return false;
              }
            }}
          />
        )}
      </Sheet>
      {saving && (
        <Sheet
          title="Save meal"
          close={() => setSaving(false)}
          kind="small-sheet"
        >
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              if (await app.favourite(entry, name.trim())) setSaving(false);
            }}
          >
            <Field
              label="Meal name"
              placeholder={estimate?.summary ?? entry.body}
              maxLength={200}
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <div className="form-actions">
              <button
                type="button"
                className="text-button"
                onClick={() => setSaving(false)}
              >
                Cancel
              </button>
              <Button disabled={working || !name.trim()}>Save</Button>
            </div>
          </form>
        </Sheet>
      )}
    </>
  );
}
function ManualEditor({ entry, busy, save }) {
  const original = entry.estimate?.items ?? [
    {
      name: entry.body,
      portion: {
        quantity: 1,
        unitKey: "serving",
        kind: "SERVING",
        grams: null,
        milliliters: null,
        assumed: false,
        assumption: null,
      },
      nutrients: {},
      sources: [],
      assumptions: [],
    },
  ];
  const [items, setItems] = useState(() =>
      original.map((item) => ({
        ...item,
        portion: { ...item.portion },
        nutrients: Object.fromEntries(
          metrics.map((m) => [m.key, item.nutrients[m.key] ?? ""]),
        ),
      })),
    ),
    [error, setError] = useState("");
  const update = (i, section, key, value) =>
    setItems((rows) =>
      rows.map((row, n) =>
        n === i
          ? { ...row, [section]: { ...row[section], [key]: value } }
          : row,
      ),
    );
  return (
    <form
      className="manual-editor"
      onSubmit={(e) => {
        e.preventDefault();
        if (
          !items.every(
            (i) =>
              validateNutrition(i.nutrients) &&
              Number(i.portion.quantity) > 0 &&
              i.portion.unitKey.trim(),
          )
        ) {
          setError(
            "Use valid non-negative nutrition values, a positive quantity, and a unit.",
          );
          return;
        }
        save({
          ...entry.estimate,
          summary: entry.estimate?.summary ?? entry.body.slice(0, 60),
          manual: true,
          needsReview: false,
          confidenceScore: null,
          calculatedAtEpochMillis: Date.now(),
          explanation:
            "Nutrition entered manually. Values are totals for the displayed portions.",
          items: items.map((item) => ({
            ...item,
            portion: {
              ...item.portion,
              quantity: Number(item.portion.quantity),
              assumed: false,
              assumption: null,
            },
            nutrients: Object.fromEntries(
              metrics.map((m) => [
                m.key,
                item.nutrients[m.key] === ""
                  ? null
                  : Number(item.nutrients[m.key]),
              ]),
            ),
            assumptions: [],
            sources: [
              {
                kind: "USER",
                title: "Entered by you",
                basis: "Manual nutrition for this portion.",
              },
            ],
          })),
        });
      }}
    >
      <h3>Manual nutrition</h3>
      <p className="small-muted">
        Enter totals for each portion below. Changing a portion does not
        automatically scale nutrients. Leave unknown values blank.
      </p>
      {items.map((item, i) => (
        <div className="manual-item" key={i}>
          <h4>{item.name}</h4>
          <div className="field-grid">
            <Field
              label={`Quantity${items.length > 1 ? ` (${i + 1})` : ""}`}
              type="number"
              min="0.01"
              step="any"
              value={item.portion.quantity}
              onChange={(e) => update(i, "portion", "quantity", e.target.value)}
              required
            />
            <Field
              label={`Unit${items.length > 1 ? ` (${i + 1})` : ""}`}
              value={item.portion.unitKey}
              maxLength={60}
              onChange={(e) => update(i, "portion", "unitKey", e.target.value)}
              required
            />
          </div>
          <label className="kind-label">
            Portion type
            <select
              value={item.portion.kind}
              onChange={(e) => update(i, "portion", "kind", e.target.value)}
            >
              {["COUNT", "HOUSEHOLD", "WEIGHT", "VOLUME", "SERVING"].map(
                (kind) => (
                  <option key={kind} value={kind}>
                    {kind.toLowerCase()}
                  </option>
                ),
              )}
            </select>
          </label>
          <div className="field-grid">
            {metrics.map((m) => (
              <Field
                key={m.key}
                label={`${m.label}${items.length > 1 ? ` (${i + 1})` : ""}`}
                type="number"
                min="0"
                step="any"
                max={
                  m.key === "caloriesKcal"
                    ? 20000
                    : m.key === "sodiumMg"
                      ? 100000
                      : 2000
                }
                unit={m.unit}
                value={item.nutrients[m.key]}
                onChange={(e) => update(i, "nutrients", m.key, e.target.value)}
              />
            ))}
          </div>
        </div>
      ))}
      {error && (
        <p role="alert" className="error-text">
          {error}
        </p>
      )}
      <Button disabled={busy}>Save nutrition</Button>
    </form>
  );
}
export { Goals } from "./goals.jsx";
export function Saved({ app, date, close }) {
  const [query, setQuery] = useState("");
  return (
    <Sheet title="Saved meals" close={close}>
      {!app.data.saved.length ? (
        <p className="muted">
          Your go-to meals, ready for next time. Open an entry’s nutrition
          details to save one.
        </p>
      ) : (
        <>
          <Field
            label="Search meals"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {app.data.saved
            .filter((m) =>
              `${m.name} ${m.body}`.toLowerCase().includes(query.toLowerCase()),
            )
            .map((meal) => (
              <div className="white-card saved-card" key={meal.id}>
                <div>
                  <strong>{meal.name}</strong>
                  <p>{meal.body}</p>
                  <small>
                    🔥{" "}
                    {formatNumber(
                      nutrientTotal(meal.estimate.items, "caloriesKcal"),
                    )}{" "}
                    cal · P{" "}
                    {formatNumber(
                      nutrientTotal(meal.estimate.items, "proteinG"),
                    )}{" "}
                    · C{" "}
                    {formatNumber(nutrientTotal(meal.estimate.items, "carbsG"))}{" "}
                    · F{" "}
                    {formatNumber(nutrientTotal(meal.estimate.items, "fatG"))}
                  </small>
                </div>
                <IconButton
                  label={`Add ${meal.name}`}
                  disabled={app.busy}
                  onClick={async () => {
                    if (await app.reuse(meal, date)) close();
                  }}
                >
                  <Plus size={24} />
                </IconButton>
              </div>
            ))}
        </>
      )}
    </Sheet>
  );
}
