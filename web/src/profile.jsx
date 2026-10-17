import React, { useState } from "react";
import {
  ArrowLeft,
  UserRound,
  Cake,
  MoveVertical,
  Weight,
  Pencil,
  Target,
  Bookmark,
  Sparkles,
  LogOut,
  ChevronRight,
  Smartphone,
  ShieldCheck,
  Download,
} from "lucide-react";
import { Button, Field, IconButton, ProfileArt } from "./ui.jsx";
import { localDate, formatNumber } from "./domain.mjs";
export const profileValues = (p) => ({
  display_name: p?.display_name ?? "",
  date_of_birth: p?.date_of_birth ?? "",
  height_cm: p?.height_cm ?? "",
  weight_kg: p?.weight_kg ?? "",
});
export function validateProfile(v) {
  if (v.display_name.trim().length > 100)
    return "Keep your name under 100 characters.";
  if (
    v.date_of_birth &&
    (!/^\d{4}-\d{2}-\d{2}$/.test(v.date_of_birth) ||
      v.date_of_birth > localDate() ||
      v.date_of_birth < "1900-01-01")
  )
    return "Choose a valid date of birth.";
  if (
    v.height_cm !== "" &&
    (!Number.isFinite(Number(v.height_cm)) ||
      Number(v.height_cm) < 50 ||
      Number(v.height_cm) > 300)
  )
    return "Height must be between 50 and 300 cm.";
  if (
    v.weight_kg !== "" &&
    (!Number.isFinite(Number(v.weight_kg)) ||
      Number(v.weight_kg) < 20 ||
      Number(v.weight_kg) > 500)
  )
    return "Weight must be between 20 and 500 kg.";
  return null;
}
const serialize = (v) => ({
  display_name: v.display_name.trim() || null,
  date_of_birth: v.date_of_birth || null,
  height_cm: v.height_cm === "" ? null : Number(v.height_cm),
  weight_kg: v.weight_kg === "" ? null : Number(v.weight_kg),
});
const fields = [
  ["display_name", "Your name", "What should Aru call you?", "Name"],
  [
    "date_of_birth",
    "Date of birth",
    "This helps keep your profile personal.",
    "YYYY-MM-DD",
  ],
  ["height_cm", "Height", "Centimetres work best for your profile.", "Height"],
  ["weight_kg", "Weight", "Kilograms work best for your profile.", "Weight"],
];
export function ProfileSetup({
  profile,
  busy,
  save,
  replay = false,
  close,
  initialStep = 0,
}) {
  const [step, setStep] = useState(initialStep),
    [values, setValues] = useState(() => profileValues(profile)),
    [error, setError] = useState("");
  const [key, title, subtitle, label] = fields[step];
  async function next(skip = false) {
    const next = { ...values, ...(skip ? { [key]: "" } : {}) };
    const problem = replay
      ? null
      : validateProfile({ ...profileValues(null), [key]: next[key] });
    if (problem) {
      setError(problem);
      return;
    }
    setValues(next);
    setError("");
    if (step < 3) setStep(step + 1);
    else if (replay) close();
    else
      await save({
        ...serialize(next),
        profile_setup_completed: true,
        onboarding_completed: profile?.onboarding_completed ?? false,
      });
  }
  return (
    <main className="profile-setup screen">
      <header className="setup-header">
        <IconButton
          label="Previous question"
          disabled={step === 0 || busy}
          onClick={() => {
            setStep(step - 1);
            setError("");
          }}
        >
          <ArrowLeft size={24} />
        </IconButton>
        <span>{step + 1} of 4</span>
        <button
          className="text-button"
          disabled={busy}
          onClick={() =>
            replay
              ? close()
              : save({
                  ...serialize(profileValues(null)),
                  profile_setup_completed: true,
                  onboarding_completed: profile?.onboarding_completed ?? false,
                })
          }
        >
          {replay ? "Skip" : "Skip for now"}
        </button>
      </header>
      {replay && (
        <p className="preview-label">
          Onboarding preview · your details stay saved
        </p>
      )}
      <div className="setup-progress">
        {fields.map((_, i) => (
          <span key={i} className={i <= step ? "filled" : ""} />
        ))}
      </div>
      <div className="setup-question" key={step}>
        <ProfileArt step={step} />
        <h1>{title}</h1>
        <p>{subtitle}</p>
        <Field
          label={label}
          placeholder={label}
          type={step === 1 ? "date" : step > 1 ? "number" : "text"}
          min={
            step === 1
              ? "1900-01-01"
              : step === 2
                ? 50
                : step === 3
                  ? 20
                  : undefined
          }
          max={
            step === 1
              ? localDate()
              : step === 2
                ? 300
                : step === 3
                  ? 500
                  : undefined
          }
          step={step > 1 ? "0.1" : undefined}
          unit={step === 2 ? "cm" : step === 3 ? "kg" : null}
          maxLength={step === 0 ? 100 : undefined}
          value={values[key]}
          readOnly={replay}
          onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
        />
        {error && (
          <p className="error-text" role="alert">
            {error}
          </p>
        )}
      </div>
      <footer className="setup-footer">
        <Button disabled={busy} onClick={() => next()}>
          {busy
            ? "Saving…"
            : replay
              ? step === 3
                ? "Explore features"
                : "Next"
              : step === 3
                ? "Continue to Aru"
                : "Continue"}
        </Button>
        {!replay && (
          <button
            className="text-button"
            disabled={busy}
            onClick={() => next(true)}
          >
            Skip this question
          </button>
        )}
      </footer>
    </main>
  );
}
export function SettingsPage({ app, back, openSheet, replay }) {
  const p = app.data.profile,
    [editing, setEditing] = useState(false),
    [values, setValues] = useState(() => profileValues(p)),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    const problem = validateProfile(values);
    if (problem) {
      setError(problem);
      return;
    }
    if (
      await app.profile({
        ...serialize(values),
        profile_setup_completed: true,
        onboarding_completed: p?.onboarding_completed ?? true,
      })
    ) {
      setEditing(false);
      setError("");
    }
  }
  const info = [
    { icon: UserRound, label: "Name", value: p?.display_name },
    { icon: Cake, label: "Date of birth", value: p?.date_of_birth },
    {
      icon: MoveVertical,
      label: "Height",
      value: p?.height_cm ? `${formatNumber(p.height_cm)} cm` : null,
    },
    {
      icon: Weight,
      label: "Weight",
      value: p?.weight_kg ? `${formatNumber(p.weight_kg)} kg` : null,
    },
  ];
  return (
    <main className="settings screen">
      <header className="page-header">
        <IconButton label="Back to journal" onClick={back}>
          <ArrowLeft size={25} />
        </IconButton>
        <h1>Settings</h1>
      </header>
      <h2 className="section-label">Your profile</h2>
      <section className="profile-card white-card">
        {editing ? (
          <form onSubmit={submit}>
            <Field
              label="Name"
              value={values.display_name}
              maxLength={100}
              onChange={(e) =>
                setValues((v) => ({ ...v, display_name: e.target.value }))
              }
            />
            <Field
              label="Date of birth"
              type="date"
              min="1900-01-01"
              max={localDate()}
              value={values.date_of_birth}
              onChange={(e) =>
                setValues((v) => ({ ...v, date_of_birth: e.target.value }))
              }
            />
            <Field
              label="Height"
              unit="cm"
              type="number"
              step="0.1"
              min="50"
              max="300"
              value={values.height_cm}
              onChange={(e) =>
                setValues((v) => ({ ...v, height_cm: e.target.value }))
              }
            />
            <Field
              label="Weight"
              unit="kg"
              type="number"
              step="0.1"
              min="20"
              max="500"
              value={values.weight_kg}
              onChange={(e) =>
                setValues((v) => ({ ...v, weight_kg: e.target.value }))
              }
            />
            {error && (
              <p role="alert" className="error-text">
                {error}
              </p>
            )}
            <div className="form-actions">
              <button
                className="text-button"
                type="button"
                disabled={app.busy}
                onClick={() => setEditing(false)}
              >
                Cancel
              </button>
              <Button disabled={app.busy}>
                {app.busy ? "Saving…" : "Save profile"}
              </Button>
            </div>
          </form>
        ) : (
          <>
            {info.map(({ icon: Icon, label, value }) => (
              <div className="profile-value" key={label}>
                <span>
                  <Icon
                    size={22}
                    fill={label === "Name" ? "currentColor" : "none"}
                  />
                </span>
                <div>
                  <small>{label}</small>
                  <strong>{value ?? "Not shared"}</strong>
                </div>
              </div>
            ))}
            <Button
              onClick={() => {
                setValues(profileValues(p));
                setEditing(true);
              }}
            >
              <Pencil size={20} fill="currentColor" />
              Edit profile
            </Button>
          </>
        )}
      </section>
      <div className="settings-links">
        {[
          {
            icon: Target,
            title: "Daily goals",
            subtitle: "Calories and macro targets",
            action: () => openSheet("goals"),
          },
          {
            icon: Bookmark,
            title: "Saved meals",
            subtitle: "Meals you can add instantly",
            action: () => openSheet("saved"),
          },
          {
            icon: Sparkles,
            title: "Replay onboarding",
            subtitle: "Meet Aru and explore every feature",
            action: replay,
          },
          {
            icon: LogOut,
            title: "Sign out",
            subtitle: "Sign out of this account",
            action: app.signOut,
            destructive: true,
          },
        ].map(({ icon: Icon, title, subtitle, action, destructive }) => (
          <button
            key={title}
            className={`settings-link ${destructive ? "destructive" : ""}`}
            onClick={action}
            disabled={
              app.busy ||
              Object.values(app.pending).some(
                (p) => p === "saving" || p === "calculating",
              )
            }
          >
            <Icon
              size={25}
              fill={title === "Saved meals" ? "currentColor" : "none"}
            />
            <div>
              <strong>{title}</strong>
              <small>{subtitle}</small>
            </div>
            <ChevronRight size={23} />
          </button>
        ))}
      </div>
      <div className="web-utilities">
        <button onClick={() => openSheet("install")}>
          <Smartphone size={16} /> Add to home screen
        </button>
        <button onClick={() => openSheet("privacy")}>
          <ShieldCheck size={16} /> Privacy
        </button>
        <button
          onClick={() => {
            const url = URL.createObjectURL(
              new Blob(
                [
                  JSON.stringify(
                    { exportedAt: new Date().toISOString(), ...app.data },
                    null,
                    2,
                  ),
                ],
                { type: "application/json" },
              ),
            );
            const a = document.createElement("a");
            a.href = url;
            a.download = `aru-journal-${localDate()}.json`;
            a.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
          }}
        >
          <Download size={16} /> Export journal
        </button>
      </div>
    </main>
  );
}
