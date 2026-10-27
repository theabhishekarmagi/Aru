import { hydrateWater, waterForText } from "./water.mjs";
import { useEffect, useRef, useState } from "react";
import {
  supabase,
  loadJournal,
  createEntry,
  updateEntry,
  estimateMeal,
  saveMeal,
  saveGoals,
  saveProfile,
} from "./api.mjs";
import { localDate } from "./domain.mjs";
const empty = () => ({ entries: [], saved: [], goals: null, profile: null });
export function useJournal() {
  const [session, setSession] = useState(null),
    [authLoading, setAuthLoading] = useState(true),
    [loading, setLoading] = useState(false),
    [data, setData] = useState(empty),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [pending, setPending] = useState({}),
    [notice, setNotice] = useState(null),
    [message, setMessage] = useState(null),
    [undo, setUndo] = useState(null),
    [photo, setPhoto] = useState(null),
    [imageImport, setImageImport] = useState(null);
  const owner = useRef(null),
    state = useRef(data),
    persisted = useRef(new Map()),
    queues = useRef(new Map()),
    timers = useRef(new Map()),
    controllers = useRef(new Map()),
    drafts = useRef({});
  const change = (fn) => {
    state.current = fn(state.current);
    setData(state.current);
  };
  const notify = (text, type = "error") =>
    setMessage({ text, type, id: Date.now() });
  const cache = () => {
    if (!owner.current) return;
    try {
      localStorage.setItem(
        `aru-notes-v2:${owner.current}`,
        JSON.stringify(drafts.current),
      );
    } catch {
      notify(
        "This browser could not retain your typing draft. Keep this page open until it is saved.",
      );
    }
  };
  const record = (raw) => {
    const entry = hydrateWater(raw);
    return change((d) => ({
      ...d,
      entries: d.entries.some((e) => e.id === entry.id)
        ? d.entries.map((e) => (e.id === entry.id ? entry : e))
        : [...d.entries, entry],
    }));
  };
  const phase = (id, value) => setPending((p) => ({ ...p, [id]: value }));
  const stop = (id) => {
    clearTimeout(timers.current.get(id));
    controllers.current.get(id)?.abort();
  };
  const enqueue = (id, operation) => {
    const next = (queues.current.get(id) ?? Promise.resolve())
      .catch(() => {})
      .then(operation);
    queues.current.set(id, next);
    return next;
  };
  async function refresh() {
    const id = owner.current;
    if (!id) return;
    setLoading(true);
    setError("");
    setImageImport(null);
    try {
      const result = await loadJournal(id);
      result.entries = result.entries.map(hydrateWater);
      if (owner.current !== id) return;
      persisted.current = new Map(result.entries.map((e) => [e.id, e]));
      let recovered = {};
      try {
        recovered = JSON.parse(
          localStorage.getItem(`aru-notes-v2:${id}`) ?? "{}",
        );
      } catch {}
      drafts.current = recovered;
      const merged = [...result.entries];
      for (const [key, draft] of Object.entries(recovered)) {
        if (
          !draft ||
          draft.user_id !== id ||
          typeof draft.body !== "string" ||
          !/^\d{4}-\d{2}-\d{2}$/.test(draft.journal_date)
        )
          continue;
        const index = merged.findIndex((e) => e.id === key);
        if (index >= 0 && merged[index].body === draft.body) {
          delete drafts.current[key];
          continue;
        }
        const existing = index >= 0 ? merged[index] : null;
        const row = {
          ...existing,
          ...draft,
          id: key,
          _local: !existing,
          _dirty: true,
          _conflict: !!existing && existing.revision !== draft.revision,
          estimate: null,
        };
        if (index >= 0) merged[index] = row;
        else merged.push(row);
      }
      cache();
      change(() => ({ ...result, entries: merged.map(hydrateWater) }));
    } catch {
      if (owner.current === id)
        setError(
          "Couldn’t load your journal. Check your connection and try again.",
        );
    } finally {
      if (owner.current === id) setLoading(false);
    }
  }
  useEffect(() => {
    let active = true;
    supabase.auth
      .getSession()
      .then(({ data, error }) => {
        if (active) {
          setSession(data.session);
          setAuthLoading(false);
          if (error) notify("Please sign in again.");
        }
      })
      .catch(() => {
        if (active) setAuthLoading(false);
      });
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, value) => {
      if (active) {
        setSession(value);
        setAuthLoading(false);
      }
    });
    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, []);
  useEffect(() => {
    owner.current = session?.user.id ?? null;
    for (const timer of timers.current.values()) clearTimeout(timer);
    for (const controller of controllers.current.values()) controller.abort();
    timers.current.clear();
    controllers.current.clear();
    queues.current.clear();
    persisted.current.clear();
    drafts.current = {};
    change(() => empty());
    setPending({});
    setNotice(null);
    setUndo(null);
    setImageImport(null);
    setLoading(false);
    setBusy(false);
    setError("");
    if (owner.current) refresh();
  }, [session?.user.id]);
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(null), 6500);
    return () => clearTimeout(timer);
  }, [message]);
  function addLine(date) {
    const entry = {
      id: crypto.randomUUID(),
      user_id: owner.current,
      journal_date: date,
      body: "",
      revision: 1,
      status: "DRAFT",
      estimate: null,
      _local: true,
      _dirty: true,
    };
    record(entry);
    return entry.id;
  }
  function edit(id, body) {
    const entry = state.current.entries.find((e) => e.id === id);
    if (!entry) return;
    stop(id);
    const water = waterForText(body, entry.water);
    const next = {
      ...entry,
      water,
      body,
      estimate: null,
      status: water?.onlyWater ? "READY" : "DRAFT",
      _dirty: true,
    };
    record(next);
    drafts.current[id] = {
      id,
      user_id: owner.current,
      journal_date: entry.journal_date,
      body,
      revision: persisted.current.get(id)?.revision ?? 1,
    };
    cache();
    phase(id, body.trim() ? "queued" : null);
    if (body.trim())
      timers.current.set(
        id,
        setTimeout(() => commit(id).catch(() => {}), 1800),
      );
  }
  async function persist(id) {
    const userId = owner.current;
    const current = state.current.entries.find((e) => e.id === id);
    if (!userId || !current) return null;
    const text = current.body;
    return enqueue(id, async () => {
      if (owner.current !== userId) return null;
      const entry = state.current.entries.find((e) => e.id === id);
      if (entry?._conflict)
        throw new Error(
          "This entry changed on another device. Your typing draft is kept. Refresh or save it as a new line.",
        );
      const base = persisted.current.get(id);
      if (base?.body === text) {
        if (state.current.entries.find((e) => e.id === id)?.body === text) {
          record(base);
          delete drafts.current[id];
          cache();
        }
        return base;
      }
      if (!text.trim() && !base) return null;
      const saved = base
        ? await updateEntry(base, {
            body: text,
            water: waterForText(text, current.water),
            estimate: null,
            status: waterForText(text, current.water)?.onlyWater
              ? "READY"
              : "DRAFT",
          })
        : await createEntry(
            userId,
            current.journal_date,
            text,
            null,
            id,
            current.water,
          );
      if (owner.current !== userId) return null;
      persisted.current.set(id, saved);
      const latest = state.current.entries.find((e) => e.id === id);
      if (latest?.body === text) {
        record(saved);
        delete drafts.current[id];
        cache();
      } else if (latest) {
        record({ ...latest, revision: saved.revision, _local: false });
        if (drafts.current[id]) drafts.current[id].revision = saved.revision;
        cache();
      }
      return saved;
    });
  }
  function consent(action) {
    let accepted = false;
    try {
      accepted =
        localStorage.getItem(`aru-ai-consent:${owner.current}`) === "yes";
    } catch {}
    if (accepted) action();
    else setNotice({ action });
  }
  function acceptNotice() {
    try {
      localStorage.setItem(`aru-ai-consent:${owner.current}`, "yes");
    } catch {}
    const action = notice?.action;
    setNotice(null);
    action?.();
  }
  async function commit(id) {
    clearTimeout(timers.current.get(id));
    phase(id, "saving");
    try {
      const saved = await persist(id);
      const latest = state.current.entries.find((e) => e.id === id);
      if (
        saved &&
        latest?.body === saved.body &&
        !saved.estimate &&
        !waterForText(saved.body, saved.water)?.onlyWater &&
        saved.body.trim()
      ) {
        phase(id, null);
        consent(() => calculate(id));
      } else phase(id, null);
      return saved;
    } catch (e) {
      phase(id, "error");
      notify(e.message ?? "Couldn’t save this line. Your draft is kept.");
      return null;
    }
  }
  async function calculate(id, imageBase64) {
    const userId = owner.current;
    const base = persisted.current.get(id);
    if (!base || !userId || waterForText(base.body, base.water)?.onlyWater)
      return;
    controllers.current.get(id)?.abort();
    const controller = new AbortController();
    controllers.current.set(id, controller);
    phase(id, "calculating");
    const timer = setTimeout(() => controller.abort(), 65000);
    try {
      const result = await estimateMeal(base, {
        imageBase64,
        signal: controller.signal,
      });
      if (owner.current !== userId || controller.signal.aborted) return;
      await enqueue(id, async () => {
        const latest = state.current.entries.find((e) => e.id === id),
          current = persisted.current.get(id);
        if (latest?.body !== base.body || current?.revision !== base.revision)
          return;
        const updated = await updateEntry(current, {
          body: imageBase64 ? result.description : base.body,
          estimate: result.estimate,
          status: result.estimate.needsReview ? "NEEDS_REVIEW" : "READY",
        });
        if (owner.current === userId) {
          persisted.current.set(id, updated);
          record(updated);
        }
      });
      if (owner.current === userId) phase(id, null);
    } catch (e) {
      if (owner.current === userId && !controller.signal.aborted) {
        phase(id, "error");
        notify(e.message);
      } else if (owner.current === userId) phase(id, null);
    } finally {
      clearTimeout(timer);
      if (controllers.current.get(id) === controller)
        controllers.current.delete(id);
      if (owner.current === userId) setPhoto(null);
    }
  }
  async function mutate(entry, patch) {
    stop(entry.id);
    const userId = owner.current;
    setBusy(true);
    try {
      return await enqueue(entry.id, async () => {
        const base = persisted.current.get(entry.id);
        if (!base)
          throw new Error("Save the entry before changing its nutrition.");
        const updated = await updateEntry(base, patch);
        if (owner.current !== userId) return null;
        persisted.current.set(entry.id, updated);
        record(updated);
        return updated;
      });
    } catch (e) {
      notify(e.message);
      return null;
    } finally {
      if (owner.current === userId) setBusy(false);
    }
  }
  async function remove(entry) {
    stop(entry.id);
    if (!persisted.current.has(entry.id)) {
      change((d) => ({
        ...d,
        entries: d.entries.filter((e) => e.id !== entry.id),
      }));
      delete drafts.current[entry.id];
      cache();
      return true;
    }
    const updated = await mutate(entry, {
      deleted_at: new Date().toISOString(),
    });
    if (updated) {
      setUndo(updated);
      delete drafts.current[entry.id];
      cache();
      return true;
    }
    return false;
  }
  async function restore() {
    if (!undo) return;
    const updated = await mutate(undo, { deleted_at: null });
    if (updated) setUndo(null);
  }
  async function favourite(entry, name) {
    const id = owner.current;
    setBusy(true);
    try {
      if (!entry.estimate)
        throw new Error(
          "Calculate or enter nutrition before saving this meal.",
        );
      const meal = await saveMeal(id, entry, name);
      if (owner.current === id) {
        change((d) => ({ ...d, saved: [...d.saved, meal] }));
        notify("Meal saved", "success");
        return true;
      }
    } catch (e) {
      notify(e.message);
    } finally {
      if (owner.current === id) setBusy(false);
    }
    return false;
  }
  async function reuse(meal, date) {
    const id = owner.current;
    setBusy(true);
    try {
      const entry = await createEntry(
        id,
        date,
        meal.body,
        meal.estimate,
        undefined,
        meal.water,
      );
      if (owner.current === id) {
        persisted.current.set(entry.id, entry);
        record(entry);
        return true;
      }
    } catch {
      notify("Couldn’t add this saved meal. Try again.");
    } finally {
      if (owner.current === id) setBusy(false);
    }
    return false;
  }
  async function addWater(date, ml = 250) {
    const userId = owner.current;
    if (!userId) return false;
    setBusy(true);
    try {
      const entry = await createEntry(userId, date, `${ml} ml water`);
      if (owner.current !== userId) return false;
      persisted.current.set(entry.id, entry);
      record(entry);
      return entry;
    } catch (e) {
      notify(e.message ?? "Couldn’t save water. Try again.");
      return false;
    } finally {
      if (owner.current === userId) setBusy(false);
    }
  }
  async function water(entry, ml, label) {
    if (!Number.isInteger(ml) || ml < 1 || ml > 20000) return false;
    const userId = owner.current;
    setBusy(true);
    try {
      const saved = await persist(entry.id);
      if (!saved || owner.current !== userId) return false;
      const current = waterForText(saved.body, saved.water);
      if (!current) return false;
      return !!(await mutate(saved, {
        water: {
          ...current,
          milliliters: ml,
          portionLabel: label,
          assumed: label !== "Custom amount" && ml !== 1000,
        },
      }));
    } catch (e) {
      notify(e.message ?? "Couldn’t save water. Try again.");
      return false;
    } finally {
      if (owner.current === userId) setBusy(false);
    }
  }
  async function goals(values) {
    const id = owner.current;
    setBusy(true);
    try {
      const result = await saveGoals(id, state.current.goals, values);
      if (owner.current === id) {
        change((d) => ({ ...d, goals: result }));
        return true;
      }
    } catch (e) {
      notify(e.message);
    } finally {
      if (owner.current === id) setBusy(false);
    }
    return false;
  }
  async function profile(values) {
    const id = owner.current;
    setBusy(true);
    try {
      const result = await saveProfile(id, state.current.profile, values);
      if (owner.current === id) {
        change((d) => ({ ...d, profile: result }));
        return true;
      }
    } catch (e) {
      notify(e.message);
    } finally {
      if (owner.current === id) setBusy(false);
    }
    return false;
  }
  function photoChosen(file, date) {
    if (file && owner.current)
      setImageImport({
        file,
        date,
        owner: owner.current,
        id: crypto.randomUUID(),
      });
  }
  async function saveImageMeal(description, estimate, date) {
    const id = owner.current;
    if (!id) return false;
    setBusy(true);
    try {
      const entry = await createEntry(id, date, description, estimate);
      if (owner.current !== id) return false;
      persisted.current.set(entry.id, entry);
      record(entry);
      return true;
    } catch (e) {
      if (owner.current === id)
        notify(e.message ?? "Couldn’t save this meal. Please try again.");
      return false;
    } finally {
      if (owner.current === id) setBusy(false);
    }
  }
  async function signIn() {
    setBusy(true);
    try {
      const { error } = await supabase.auth.signInWithOAuth({
        provider: "google",
        options: {
          redirectTo: `${location.origin}/?view=journal`,
          queryParams: { prompt: "select_account" },
        },
      });
      if (error) throw error;
    } catch {
      notify("Google sign-in couldn’t finish. Please try again.");
      setBusy(false);
    }
  }
  async function signOut() {
    if (
      busy ||
      Object.values(pending).some((p) => p === "saving" || p === "calculating")
    )
      return;
    for (const timer of timers.current.values()) clearTimeout(timer);
    cache();
    const { error } = await supabase.auth.signOut({ scope: "local" });
    if (error) notify("Couldn’t sign out. Please try again.");
  }
  return {
    session,
    authLoading,
    loading,
    data,
    error,
    busy,
    pending,
    notice,
    message,
    undo,
    photo,
    notify,
    setMessage,
    refresh,
    addLine,
    edit,
    commit,
    persist,
    consent,
    acceptNotice,
    dismissNotice: () => setNotice(null),
    calculate,
    mutate,
    remove,
    restore,
    favourite,
    reuse,
    goals,
    water,
    addWater,
    profile,
    photoChosen,
    imageImport,
    closeImageImport: () => setImageImport(null),
    saveImageMeal,
    signIn,
    signOut,
  };
}
