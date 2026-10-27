import React, { useEffect, useRef, useState } from "react";
import { Sheet, Button, Field } from "./ui.jsx";
import { NutritionSummary } from "./nutrition.jsx";
import { compressPhoto, estimateMeal } from "./api.mjs";
export function ImageImport({
  source,
  app,
  close,
  analyzeImage = estimateMeal,
  compress = compressPhoto,
}) {
  const [url, setUrl] = useState(""),
    [stage, setStage] = useState("ready"),
    [analysis, setAnalysis] = useState(null),
    [selection, setSelection] = useState({}),
    [search, setSearch] = useState(""),
    [review, setReview] = useState(null),
    [error, setError] = useState("");
  const request = useRef(null),
    lock = useRef(false);
  useEffect(() => {
    const u = URL.createObjectURL(source.file);
    setUrl(u);
    return () => {
      request.current?.abort();
      URL.revokeObjectURL(u);
    };
  }, [source.file]);
  function cancel() {
    if (stage === "saving") return;
    request.current?.abort();
    close();
  }
  async function run(next, operation) {
    if (lock.current) return;
    lock.current = true;
    setStage(next);
    setError("");
    const abort = new AbortController();
    request.current = abort;
    try {
      await operation(abort.signal);
    } catch (e) {
      if (!abort.signal.aborted) {
        setError(e.message || "Couldn’t analyze this image. Please retry.");
        setStage(
          next === "analyzing"
            ? analysis?.kind === "other"
              ? "other"
              : "ready"
            : "choices",
        );
      }
    } finally {
      lock.current = false;
    }
  }
  function scan() {
    run("analyzing", async (signal) => {
      const imageBase64 = await compress(source.file);
      if (signal.aborted) return;
      const result = await analyzeImage(
        { id: crypto.randomUUID(), revision: 1 },
        { imageBase64, scan: true, signal },
      );
      if (signal.aborted) return;
      if (
        !["dish", "menu", "ingredients", "other"].includes(result.kind) ||
        !Array.isArray(result.options) ||
        result.options.length > 60
      )
        throw new Error("The image response was invalid. Please retry.");
      setAnalysis(result);
      if (result.kind === "dish" && result.estimate?.items?.length) {
        setReview({
          description: result.description,
          estimate: result.estimate,
        });
        setStage("review");
      } else setStage(result.kind === "other" ? "other" : "choices");
    });
  }
  const selected =
    analysis?.options.filter((o) => Object.hasOwn(selection, o.id)) ?? [];
  function continueSelected() {
    run("estimating", async (signal) => {
      const description = selected
        .map(
          (o) =>
            `${selection[o.id].trim()} ${o.name}${o.description ? ` (${o.description})` : ""}`,
        )
        .join("; ");
      if (!description || description.length > 2000)
        throw new Error(
          "Shorten the selected descriptions or choose fewer items.",
        );
      const result = await analyzeImage(
        { id: crypto.randomUUID(), revision: 1, body: description },
        { signal },
      );
      if (signal.aborted) return;
      setReview({ description, estimate: result.estimate });
      setStage("review");
    });
  }
  async function save() {
    if (lock.current) return;
    lock.current = true;
    setStage("saving");
    setError("");
    try {
      if (
        await app.saveImageMeal(
          review.description,
          review.estimate,
          source.date,
        )
      )
        close();
      else {
        setStage("review");
        setError("Couldn’t save this meal. Please try again.");
      }
    } catch {
      setStage("review");
      setError("Couldn’t save this meal. Please try again.");
    } finally {
      lock.current = false;
    }
  }
  const busy = ["analyzing", "estimating", "saving"].includes(stage),
    filtered =
      analysis?.options.filter((o) =>
        `${o.name} ${o.description} ${o.category}`
          .toLowerCase()
          .includes(search.toLowerCase()),
      ) ?? [];
  return (
    <Sheet title="Log with Aru" close={cancel} kind="image-import-sheet">
      {url && (
        <img
          className={`import-photo ${stage === "choices" ? "menu-photo" : ""}`}
          src={url}
          alt="Image selected for Aru"
        />
      )}
      <p className="small-muted">Journal date: {source.date}</p>
      {stage === "ready" && (
        <>
          <h3>What would you like to log?</h3>
          <p>
            Share a dish, menu or ingredient list. Aru will help you choose what
            you ate before saving.
          </p>
          <p className="small-muted">
            Analyze sends this image to Aru’s AI provider. Aru does not keep the
            image. Avoid faces and personal information.
          </p>
          <Button onClick={scan}>Analyze image</Button>
        </>
      )}
      {stage === "other" && (
        <>
          <h3>No readable food found</h3>
          <p>{analysis.description}</p>
          <p className="small-muted">
            Try a clearer image or crop the menu to the items you want.
          </p>
          <Button onClick={scan}>Try again</Button>
        </>
      )}
      {stage === "choices" && (
        <>
          <h3>What would you like to log?</h3>
          <p>
            {analysis.kind === "ingredients"
              ? "Choose the ingredients and amounts you consumed."
              : "Choose the food you ate from this menu."}
          </p>
          <p className="small-muted">
            Found {analysis.options.length} readable items. Choose what you ate
            and adjust each amount. Select up to 12 items; defaults are
            estimated servings. Only readable items are listed; crop dense menus
            for more.
          </p>
          <Field
            label="Search items"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="import-choices">
            {filtered.map((option) => {
              const checked = Object.hasOwn(selection, option.id);
              return (
                <section
                  className={`white-card import-choice ${checked ? "chosen" : ""}`}
                  key={option.id}
                >
                  <label>
                    <span>
                      <small>{option.category}</small>
                      <strong>{option.name}</strong>
                      {option.description && <span>{option.description}</span>}
                    </span>
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={!checked && selected.length >= 12}
                      onChange={(e) =>
                        setSelection((previous) => {
                          const next = { ...previous };
                          if (e.target.checked)
                            next[option.id] = option.portionHint || "1 serving";
                          else delete next[option.id];
                          return next;
                        })
                      }
                    />
                  </label>
                  {checked && (
                    <Field
                      label={`Amount for ${option.name}`}
                      value={selection[option.id]}
                      maxLength={100}
                      onChange={(e) =>
                        setSelection((s) => ({
                          ...s,
                          [option.id]: e.target.value,
                        }))
                      }
                    />
                  )}
                </section>
              );
            })}
          </div>
          {!filtered.length && <p>No matching items. Try another search.</p>}
          <div className="import-actions">
            <Button
              disabled={
                !selected.length ||
                selected.some((o) => !selection[o.id].trim())
              }
              onClick={continueSelected}
            >
              Continue · {selected.length} selected
            </Button>
          </div>
        </>
      )}
      {(stage === "review" || stage === "saving") && review && (
        <>
          <h3>{review.estimate.summary || "Meal preview"}</h3>
          <NutritionSummary estimate={review.estimate} />
          <h3>Food details</h3>
          <section className="white-card">{review.description}</section>
          <h3>Items</h3>
          {review.estimate.items.map((item, i) => (
            <section className="white-card" key={i}>
              <strong>{item.name}</strong>
              <p className="small-muted">
                {item.portion.quantity} {item.portion.unitKey}
                {item.portion.assumed ? " · estimated portion" : ""}
              </p>
            </section>
          ))}
          <p className="small-muted">
            {review.estimate.explanation} Review estimates and portions. You can
            edit nutrition after adding.
          </p>
          <div className="import-actions">
            {analysis.kind !== "dish" && (
              <Button
                secondary
                disabled={busy}
                onClick={() => setStage("choices")}
              >
                Edit selection
              </Button>
            )}
            <Button disabled={busy || app.busy} onClick={save}>
              {stage === "saving" ? "Adding…" : "+ Add to Aru"}
            </Button>
          </div>
        </>
      )}
      {(stage === "analyzing" || stage === "estimating") && (
        <p role="status" className="import-loading">
          <span className="spin">✦</span>
          {stage === "analyzing"
            ? "Reading your image…"
            : "Calculating selected items…"}
        </p>
      )}
      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}
    </Sheet>
  );
}
