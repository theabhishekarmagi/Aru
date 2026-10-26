import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Settings,
  Mic,
  Plus,
  Camera,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  X,
  Image as ImageIcon,
  SwitchCamera,
  SquarePen,
  Bookmark,
  Sparkles,
  ChartNoAxesCombined,
  Check,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import { useJournal } from "./useJournal.jsx";
import { IconButton, Button, Sheet, Field, Loading } from "./ui.jsx";
import { ProfileSetup, SettingsPage } from "./profile.jsx";
import { GoalsProgress } from "./goals.jsx";
import { Details, Goals, Saved } from "./nutrition.jsx";
import {
  dailyTotals,
  localDate,
  nutrientTotal,
  formatNumber,
  shiftDate,
} from "./domain.mjs";
import "./style.css";
import Landing from "./landing.jsx";

function App() {
  const app = useJournal(),
    [date, setDate] = useState(localDate()),
    [page, setPage] = useState("journal"),
    [sheet, setSheet] = useState(null),
    [selected, setSelected] = useState(null),
    [active, setActive] = useState(null),
    [focusId, setFocusId] = useState(null),
    [replay, setReplay] = useState(null),
    [listening, setListening] = useState(false),
    [online, setOnline] = useState(navigator.onLine);
  const speech = useRef(null),
    file = useRef();
  const all = app.data.entries.filter(
      (e) => e.journal_date === date && !e.deleted_at,
    ),
    entries = all.filter((e) => e.body.trim()),
    totals = dailyTotals(entries),
    detail = app.data.entries.find((e) => e.id === selected && !e.deleted_at);
  useEffect(() => {
    document.title = "Aru · Food journal";
    const update = () =>
      document.documentElement.style.setProperty(
        "--app-height",
        `${window.visualViewport?.height ?? innerHeight}px`,
      );
    update();
    window.visualViewport?.addEventListener("resize", update);
    window.addEventListener("resize", update);
    const net = () => setOnline(navigator.onLine);
    window.addEventListener("online", net);
    window.addEventListener("offline", net);
    return () => {
      window.visualViewport?.removeEventListener("resize", update);
      window.removeEventListener("resize", update);
      window.removeEventListener("online", net);
      window.removeEventListener("offline", net);
      speech.current?.abort();
    };
  }, []);
  useEffect(() => {
    setPage("journal");
    setSheet(null);
    setSelected(null);
    setActive(null);
    setReplay(null);
    speech.current?.abort();
  }, [app.session?.user.id]);
  function blur() {
    document.activeElement?.blur();
    setActive(null);
  }
  function open(value) {
    blur();
    setSheet(value);
  }
  function addLine() {
    if (app.error) return;
    const blank = all.find((e) => !e.body.trim());
    const id = blank?.id ?? app.addLine(date);
    setFocusId(id);
    setActive(id);
  }
  function dictate(target = active, onText) {
    const Recognition =
      window.SpeechRecognition ?? window.webkitSpeechRecognition;
    if (!Recognition) {
      app.notify(
        "Dictation isn’t available in this browser. Use your keyboard’s microphone or type your meal.",
      );
      return;
    }
    if (listening) {
      speech.current?.stop();
      return;
    }
    const id = target ?? (onText ? null : app.addLine(date)),
      base = app.data.entries.find((e) => e.id === id)?.body ?? "";
    const recognition = new Recognition();
    speech.current = recognition;
    recognition.lang = navigator.language || "en-IN";
    recognition.interimResults = true;
    recognition.onstart = () => setListening(true);
    recognition.onend = () => setListening(false);
    recognition.onerror = (e) => {
      setListening(false);
      app.notify(
        e.error === "not-allowed"
          ? "Microphone access was declined. You can still type your meal."
          : "Dictation couldn’t hear that. Please try again.",
      );
    };
    recognition.onresult = (e) => {
      let words = "";
      for (let i = 0; i < e.results.length; i++)
        words += e.results[i][0].transcript + " ";
      const result = [base, words.trim()].filter(Boolean).join(" ");
      if (onText) onText(result);
      else {
        app.edit(id, result);
        setFocusId(id);
        setActive(id);
      }
    };
    try {
      recognition.start();
    } catch {
      setListening(false);
      app.notify("Dictation couldn’t start. Please try again.");
    }
  }
  const close = () => setSheet(null);
  let content;
  if (app.authLoading || app.loading) content = <Loading />;
  else if (!app.session) content = <AccountGate app={app} />;
  else if (app.error)
    content = (
      <main className="screen error-screen">
        <p>{app.error}</p>
        <Button onClick={app.refresh}>Try again</Button>
        <button className="text-button" onClick={app.signOut}>
          Sign out
        </button>
      </main>
    );
  else if (replay === "profile")
    content = (
      <ProfileSetup
        profile={app.data.profile}
        busy={false}
        replay
        close={() => setReplay("tour")}
      />
    );
  else if (
    replay === "tour" ||
    (app.data.profile?.profile_setup_completed &&
      !app.data.profile?.onboarding_completed)
  )
    content = (
      <FeatureTour
        backIntro={() => setReplay("profile")}
        app={app}
        replay={!!replay}
        close={async () => {
          if (replay) {
            setReplay(null);
            setPage("settings");
          } else
            await app.profile({
              ...app.data.profile,
              onboarding_completed: true,
            });
        }}
        open={open}
        dictate={dictate}
        listening={listening}
      />
    );
  else if (!app.data.profile?.profile_setup_completed)
    content = (
      <ProfileSetup
        profile={app.data.profile}
        busy={app.busy}
        save={app.profile}
      />
    );
  else if (page === "settings")
    content = (
      <SettingsPage
        app={app}
        back={() => setPage("journal")}
        openSheet={open}
        replay={() => setReplay("profile")}
      />
    );
  else
    content = (
      <main className="journal screen">
        <header className="journal-header">
          <div className="header-side">
            <img className="journal-logo" src="/aru-logo.png" alt="Aru" />
          </div>
          <button
            className="pill date-pill"
            aria-label="Choose journal date"
            onClick={() => open("calendar")}
          >
            {date === localDate()
              ? "Today"
              : new Date(date + "T12:00:00").toLocaleDateString("en-GB", {
                  day: "numeric",
                  month: "short",
                })}
          </button>
          <div className="header-side right">
            <button
              className="pill settings-pill"
              aria-label="Settings"
              onClick={() => {
                blur();
                setPage("settings");
              }}
            >
              <span>🔥 {entries.length}</span>
              <Settings size={23} fill="currentColor" strokeWidth={1.1} />
            </button>
          </div>
        </header>
        {!online && (
          <p className="connection-notice" role="status">
            You’re offline. Your typing draft is kept on this browser.
          </p>
        )}
        <section className="journal-lines" aria-label="Food journal">
          {all.map((entry) => (
            <NoteLine
              key={entry.id}
              entry={entry}
              phase={app.pending[entry.id]}
              focused={focusId === entry.id}
              onFocused={() => setFocusId(null)}
              onFocus={() => setActive(entry.id)}
              onBlur={() =>
                setActive((current) => (current === entry.id ? null : current))
              }
              edit={(text) => app.edit(entry.id, text)}
              details={() => {
                setSelected(entry.id);
                open("details");
              }}
              next={() => {
                app.commit(entry.id);
                const id = app.addLine(date);
                setActive(id);
                setFocusId(id);
              }}
            />
          ))}
          <button className="add-line" onClick={addLine}>
            {all.length ? "+ Add a line" : "What did you eat today?"}
          </button>
          {!all.length && (
            <p className="empty-hint">Just write it down, like a note.</p>
          )}
        </section>
        <footer className="journal-footer">
          {app.undo && (
            <div className="undo">
              <span>Entry deleted</span>
              <button onClick={app.restore} disabled={app.busy}>
                Undo
              </button>
            </div>
          )}
          {app.photo && (
            <div className="white-card photo-attached">
              <img src={app.photo} alt="Attached meal photo" />
              <div>
                <strong>Meal photo attached</strong>
                <small>Identifying dishes and portions…</small>
              </div>
              <LoaderCircle className="spin" size={22} />
            </div>
          )}
          {entries.some((e) => !e.estimate || e.estimate.needsReview) && (
            <p className="pending-label">
              {[
                entries.filter((e) => !e.estimate).length
                  ? `${entries.filter((e) => !e.estimate).length} not calculated`
                  : null,
                entries.filter((e) => e.estimate?.needsReview).length
                  ? `${entries.filter((e) => e.estimate?.needsReview).length} to review`
                  : null,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          )}
          {active ? (
            <div className="compact-bar">
              <button
                className="pill calorie-remaining"
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => open("goals")}
              >
                🔥{" "}
                {app.data.goals?.calories_kcal
                  ? `${formatNumber(Math.abs(app.data.goals.calories_kcal - totals.caloriesKcal.value))} ${totals.caloriesKcal.value <= app.data.goals.calories_kcal ? "left" : "over"}`
                  : `${formatNumber(totals.caloriesKcal.value)} cal`}
              </button>
              <IconButton
                label={listening ? "Stop dictation" : "Dictate meal"}
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => dictate()}
                className={`icon-button action-button ${listening ? "listening" : ""}`}
              >
                <Mic size={25} />
              </IconButton>
              <IconButton
                label="Saved meals"
                className="icon-button action-button"
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => open("saved")}
              >
                <Plus size={27} />
              </IconButton>
              <IconButton
                label="Photograph meal"
                className="icon-button action-button"
                disabled={app.busy}
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => app.consent(() => open("camera"))}
              >
                <Camera size={25} />
              </IconButton>
            </div>
          ) : (
            <div className="goals-footer">
              <GoalsProgress
                goals={app.data.goals}
                totals={totals}
                onClick={() => open("goals")}
              />
              <DailyBar totals={totals} onClick={() => open("goals")} />
            </div>
          )}
        </footer>
      </main>
    );
  return (
    <>
      <div className="app-surface">{content}</div>
      {sheet === "calendar" && (
        <Calendar
          date={date}
          close={close}
          select={(value) => {
            setDate(value);
            setActive(null);
            setFocusId(null);
            close();
          }}
        />
      )}
      {sheet === "goals" && (
        <Goals
          app={app}
          totals={totals}
          close={close}
          editProfile={() => {
            close();
            setPage("settings");
          }}
        />
      )}{" "}
      {sheet === "saved" && <Saved app={app} date={date} close={close} />}{" "}
      {sheet === "details" && detail && (
        <Details entry={detail} app={app} close={close} />
      )}{" "}
      {sheet === "camera" && (
        <MealCamera
          app={app}
          close={close}
          choose={() => file.current.click()}
          captured={(blob) => {
            close();
            app.photoChosen(blob, date);
          }}
        />
      )}
      <input
        hidden
        ref={file}
        type="file"
        accept="image/*"
        onChange={(e) => {
          const item = e.target.files?.[0];
          e.target.value = "";
          if (item) {
            close();
            app.photoChosen(item, date);
          }
        }}
      />
      {app.notice && (
        <Sheet
          title="Analyze your meal"
          close={app.dismissNotice}
          kind="small-sheet"
        >
          <p>
            Aru sends meal text or your selected photo to OpenRouter and its AI
            provider to estimate nutrition. Aru doesn’t keep the photo; the
            description and nutrition are saved. Avoid including faces or
            personal information.
          </p>
          <p className="small-muted">
            Estimates can be wrong. Review the portions and ingredients.
          </p>
          <div className="form-actions">
            <button className="text-button" onClick={app.dismissNotice}>
              Keep it manual
            </button>
            <Button onClick={app.acceptNotice}>Continue</Button>
          </div>
        </Sheet>
      )}
      {["install", "privacy"].includes(sheet) && (
        <Sheet
          title={
            sheet === "install"
              ? "Add Aru to your home screen"
              : "Privacy & your data"
          }
          close={close}
        >
          {sheet === "install" ? (
            <div className="help-content">
              <Smartphone size={32} />
              <h3>On your iPhone</h3>
              <p>
                Open this link in Safari. Tap Share, then Add to Home Screen.
                Turn on Open as Web App if shown, then tap Add.
              </p>
              <h3>On Android or desktop</h3>
              <p>
                Use your browser’s Install app or Add to Home screen option when
                available, or bookmark this page.
              </p>
            </div>
          ) : (
            <div className="help-content">
              <ShieldCheck size={32} />
              <p>
                Google handles sign-in. Your web journal, saved meals, profile
                and goals are protected by account ownership rules in Aru’s
                existing Supabase service.
              </p>
              <p>
                Typing drafts and your sign-in session are stored in this
                browser. AI estimation sends meal text or a transient photo to
                OpenRouter and its provider; provider retention policies can
                vary.
              </p>
              <p>
                Removing a meal hides it from your journal and can be undone.
                Export your web journal from Settings. Existing Android journal
                entries are stored locally and are not automatically imported
                here.
              </p>
            </div>
          )}
        </Sheet>
      )}
      {app.message && (
        <div
          className={`toast ${app.message.type}`}
          role={app.message.type === "error" ? "alert" : "status"}
        >
          <span>{app.message.text}</span>
          <IconButton
            label="Dismiss message"
            onClick={() => app.setMessage(null)}
          >
            <X size={18} />
          </IconButton>
        </div>
      )}
    </>
  );
}
function AccountGate({ app }) {
  return (
    <main className="account-gate screen">
      <div className="account-top-space" />
      <img className="welcome-logo" src="/aru-logo.png" alt="Aru logo" />
      <div className="welcome-copy">
        <h1>Welcome to Aru 👋</h1>
        <p>
          The simplest way to track calories and keep a personal food journal.
        </p>
        <p>Built for Indian meals, restaurant food, and everyday portions.</p>
      </div>
      <div className="account-middle-space" />
      <button
        className="google-signin"
        aria-label="Sign in with Google"
        disabled={app.busy}
        onClick={app.signIn}
      >
        <img src="/google-signin.png" alt="Sign in with Google" />
      </button>
      {app.busy && <div className="indeterminate-progress" />}
      <p className="signin-caption">
        Sign in to keep your journal connected to your account.
      </p>
    </main>
  );
}
function NoteLine({
  entry,
  phase,
  focused,
  onFocused,
  onFocus,
  onBlur,
  edit,
  details,
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
  const summary = entry.estimate?.summary || entry.body;
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
          className={!editing && entry.estimate ? "summarized" : ""}
        />
        {!editing && entry.estimate && (
          <button className="summary-label" onClick={() => ref.current.focus()}>
            {summarizing ? (
              <span className="summarizing-label">✦ Summarizing meal…</span>
            ) : (
              summary
            )}
          </button>
        )}
      </div>
      <button
        className={`calorie-label ${phase === "calculating" || phase === "queued" ? "thinking" : ""} ${reveal === "calories" ? "calorie-reveal" : ""}`}
        aria-label={`Nutrition for ${entry.body || "new entry"}`}
        onClick={details}
      >
        {phase === "calculating" || phase === "saving" || phase === "queued" ? (
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
        )}
      </button>
    </div>
  );
}
export function DailyBar({ totals, onClick }) {
  return (
    <button
      className="pill daily-bar"
      aria-label="Daily totals and goals"
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
function Calendar({ date, close, select }) {
  const [chosen, setChosen] = useState(date),
    [month, setMonth] = useState(date.slice(0, 7));
  const first = new Date(`${month}-01T12:00:00`),
    days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate(),
    offset = first.getDay();
  return (
    <Sheet title="Select date" close={close} kind="calendar-sheet">
      <h3 className="selected-calendar-date">
        {new Date(chosen + "T12:00:00").toLocaleDateString("en", {
          weekday: "short",
          month: "short",
          day: "numeric",
        })}
      </h3>
      <div className="calendar-month">
        <span>
          {first.toLocaleDateString("en", { month: "long", year: "numeric" })}
        </span>
        <IconButton
          label="Previous month"
          onClick={() => setMonth(shiftDate(`${month}-01`, -1).slice(0, 7))}
        >
          <ChevronLeft size={22} />
        </IconButton>
        <IconButton
          label="Next month"
          onClick={() =>
            setMonth(
              shiftDate(`${month}-${String(days).padStart(2, "0")}`, 1).slice(
                0,
                7,
              ),
            )
          }
        >
          <ChevronRight size={22} />
        </IconButton>
      </div>
      <div className="calendar-grid">
        {["S", "M", "T", "W", "T", "F", "S"].map((day, i) => (
          <small key={"day" + i}>{day}</small>
        ))}
        {Array.from({ length: offset }, (_, i) => (
          <span key={"empty" + i} />
        ))}
        {Array.from({ length: days }, (_, i) => {
          const value = `${month}-${String(i + 1).padStart(2, "0")}`;
          return (
            <button
              key={value}
              aria-label={value}
              aria-pressed={value === chosen}
              className={`${value === chosen ? "selected" : ""} ${value === localDate() ? "today" : ""}`}
              onClick={() => setChosen(value)}
            >
              {i + 1}
            </button>
          );
        })}
      </div>
      <Field
        label="Choose date"
        type="date"
        value={chosen}
        onChange={(e) => {
          if (e.target.value) {
            setChosen(e.target.value);
            setMonth(e.target.value.slice(0, 7));
          }
        }}
      />
      <div className="form-actions calendar-actions">
        <button className="text-button" onClick={() => select(localDate())}>
          Today
        </button>
        <button className="text-button" onClick={() => select(chosen)}>
          Done
        </button>
      </div>
    </Sheet>
  );
}
function MealCamera({ app, close, choose, captured }) {
  const video = useRef(),
    stream = useRef(),
    [ready, setReady] = useState(false),
    [error, setError] = useState(""),
    [facing, setFacing] = useState("environment");
  useEffect(
    () => () => stream.current?.getTracks().forEach((track) => track.stop()),
    [],
  );
  async function start(direction = facing) {
    stream.current?.getTracks().forEach((track) => track.stop());
    setError("");
    try {
      const media = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: direction },
        audio: false,
      });
      stream.current = media;
      video.current.srcObject = media;
      await video.current.play();
      setReady(true);
    } catch {
      setReady(false);
      setError(
        "Camera couldn’t start on this device. You can choose a photo instead.",
      );
    }
  }
  function snap() {
    const canvas = document.createElement("canvas");
    canvas.width = video.current.videoWidth;
    canvas.height = video.current.videoHeight;
    canvas.getContext("2d").drawImage(video.current, 0, 0);
    canvas.toBlob(
      (blob) => {
        if (blob) {
          stream.current?.getTracks().forEach((t) => t.stop());
          captured(new File([blob], "meal.jpg", { type: "image/jpeg" }));
        }
      },
      "image/jpeg",
      0.85,
    );
  }
  return (
    <Sheet title="Meal camera" close={close} kind="camera-sheet">
      <div className="camera-view">
        <video ref={video} playsInline muted />
        {!ready && <Button onClick={() => start()}>Open camera</Button>}
        {ready && (
          <>
            <IconButton
              className="icon-button camera-switch"
              label="Switch camera"
              onClick={() => {
                const next = facing === "environment" ? "user" : "environment";
                setFacing(next);
                start(next);
              }}
            >
              <SwitchCamera size={25} />
            </IconButton>
            <p>Center the whole meal</p>
          </>
        )}
        <div className="camera-controls">
          <IconButton label="Choose meal photo" onClick={choose}>
            <ImageIcon size={26} />
          </IconButton>
          <button
            className="camera-shutter"
            aria-label="Take meal photo"
            disabled={!ready}
            onClick={snap}
          />
          <span />
        </div>
      </div>
      {error && (
        <p role="alert" className="error-text">
          {error}
        </p>
      )}
      <button className="text-button" onClick={choose}>
        Choose a photo
      </button>
    </Sheet>
  );
}
function FeatureTour({
  app,
  replay,
  close,
  backIntro,
  open,
  dictate,
  listening,
}) {
  const [step, setStep] = useState(0),
    [textId, setTextId] = useState(null),
    [spoken, setSpoken] = useState(""),
    [added, setAdded] = useState(false);
  const icons = [SquarePen, Mic, Camera, Bookmark, ChartNoAxesCombined],
    Icon = icons[step],
    titles = [
      "Write what you ate",
      "Dictate your meal",
      "Photograph your plate",
      "Reuse saved meals",
      "Understand your day",
    ],
    bodies = [
      "Try your own meal below. After you pause, Aru calculates its nutrition and saves it in today’s journal.",
      "Tap the microphone, describe your meal, then review the words below. Add it to your journal when you’re ready.",
      "Try the compact camera. The identified meal and nutrition go into your journal; Aru does not keep the photo.",
      "Tap + to save your meal for next time. Adding a saved meal reuses its nutrition instantly.",
      "Tap your daily bar to see calories and macros together. These totals come from your journal.",
    ];
  const entry = app.data.entries.find((e) => e.id === textId),
    candidate = app.data.entries.find(
      (e) => e.journal_date === localDate() && e.estimate && !e.deleted_at,
    ),
    alreadySaved =
      candidate && app.data.saved.some((s) => s.body === candidate.body),
    totals = dailyTotals(
      app.data.entries.filter(
        (e) => e.journal_date === localDate() && !e.deleted_at && e.body.trim(),
      ),
    );
  return (
    <main className="feature-tour screen">
      <header>
        <strong>{replay ? "Try Aru’s features" : "Welcome to Aru"}</strong>
        <button className="text-button" disabled={app.busy} onClick={close}>
          Skip
        </button>
      </header>
      <section className="tour-content">
        <div className="tour-icon">
          <Icon size={40} />
        </div>
        <h1>{titles[step]}</h1>
        <p>{bodies[step]}</p>
        {step === 0 && (
          <>
            <label className="tour-input">
              <span>Your meal</span>
              <textarea
                aria-label="Tour meal text"
                maxLength={2000}
                rows={3}
                placeholder="e.g. 2 idlis and a bowl of sambar"
                value={entry?.body ?? ""}
                onChange={(e) => {
                  const id = textId ?? app.addLine(localDate());
                  if (!textId) setTextId(id);
                  app.edit(id, e.target.value);
                }}
              />
            </label>
            {entry && (
              <strong className="tour-result">
                {entry.estimate
                  ? `${formatNumber(nutrientTotal(entry.estimate.items, "caloriesKcal"))} cal · Saved to today’s journal`
                  : app.pending[entry.id] === "error"
                    ? "Your draft is kept. Try editing it again."
                    : "Thinking…"}
              </strong>
            )}
          </>
        )}
        {step === 1 && (
          <>
            <IconButton
              label={listening ? "Stop tour dictation" : "Start tour dictation"}
              className="icon-button tour-microphone"
              onClick={() =>
                dictate(null, (result) => {
                  setSpoken(result);
                  setAdded(false);
                })
              }
            >
              <Mic size={30} />
            </IconButton>
            <span className="tour-mic-caption">
              {listening ? "Listening…" : "Tap to dictate"}
            </span>
            <label className="tour-input">
              <span>Your dictated meal</span>
              <textarea
                aria-label="Tour dictated text"
                maxLength={2000}
                placeholder="Your words will appear here"
                rows={3}
                value={spoken}
                onChange={(e) => {
                  setSpoken(e.target.value);
                  setAdded(false);
                }}
              />
            </label>
            <Button
              disabled={!spoken.trim() || added || listening}
              onClick={() => {
                const id = app.addLine(localDate());
                app.edit(id, spoken);
                app.commit(id);
                setAdded(true);
              }}
            >
              {added ? "Added to journal" : "Add to journal"}
            </Button>
          </>
        )}
        {step === 2 && (
          <>
            <Button
              disabled={app.busy}
              onClick={() => app.consent(() => open("camera"))}
            >
              <Camera size={20} /> Open camera
            </Button>
            <p className="small-muted">
              You can also choose an existing photo in the camera sheet.
            </p>
            {app.photo && (
              <img
                className="tour-photo"
                src={app.photo}
                alt="Attached meal photo"
              />
            )}
          </>
        )}
        {step === 3 && (
          <>
            {candidate ? (
              <>
                <div className="white-card tour-saved">
                  <strong>
                    {candidate.estimate.summary ?? candidate.body}
                  </strong>
                  <IconButton
                    label="Save tour meal"
                    disabled={alreadySaved || app.busy}
                    onClick={() =>
                      app.favourite(
                        candidate,
                        (candidate.estimate.summary ?? candidate.body).slice(
                          0,
                          100,
                        ),
                      )
                    }
                  >
                    {alreadySaved ? <Check size={23} /> : <Plus size={25} />}
                  </IconButton>
                </div>
                <p className="tour-result">
                  {alreadySaved
                    ? "Saved! Find this meal under + in your journal."
                    : "Tap + to save this meal"}
                </p>
              </>
            ) : (
              <>
                <p className="muted">
                  Calculate a meal first, then save it here.
                </p>
                <button className="text-button" onClick={() => setStep(0)}>
                  Try writing a meal
                </button>
              </>
            )}
            {app.data.saved.slice(0, 3).map((meal) => (
              <Button
                secondary
                key={meal.id}
                disabled={app.busy}
                onClick={() => app.reuse(meal, localDate())}
              >
                Add {meal.name} to today
              </Button>
            ))}
          </>
        )}
        {step === 4 && (
          <>
            <DailyBar totals={totals} onClick={() => open("goals")} />
            <p className="small-muted">Tap the bar to see your daily goals.</p>
          </>
        )}
      </section>
      <footer className="tour-footer">
        <div className="tour-dots">
          {titles.map((_, i) => (
            <span key={i} className={i === step ? "selected" : ""} />
          ))}
        </div>
        <div>
          <Button
            secondary
            onClick={() => (step === 0 ? backIntro() : setStep(step - 1))}
            disabled={step === 0 && !replay}
          >
            Previous
          </Button>
          <Button
            disabled={app.busy}
            onClick={() => (step === 4 ? close() : setStep(step + 1))}
          >
            {step === 4 ? "Start journaling" : "Next"}
          </Button>
        </div>
      </footer>
    </main>
  );
}
const params = new URLSearchParams(location.search);
const showJournal =
  params.get("view") === "journal" ||
  params.has("code") ||
  /(?:access_token|error_description)=/.test(location.hash);
createRoot(document.getElementById("root")).render(
  showJournal ? <App /> : <Landing />,
);
