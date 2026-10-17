import { createClient } from "@supabase/supabase-js";
import config from "./config.json";
export const supabase = createClient(config.url, config.publishableKey, {
  auth: {
    flowType: "pkce",
    detectSessionInUrl: true,
    persistSession: true,
    autoRefreshToken: true,
  },
});
const requireResult = ({ data, error }) => {
  if (error) throw error;
  return data;
};
async function loadRows(table, userId, order) {
  const rows = [];
  for (let offset = 0; ; offset += 500) {
    const page = requireResult(
      await supabase
        .from(table)
        .select("*")
        .eq("user_id", userId)
        .order(order)
        .order("id")
        .range(offset, offset + 499),
    );
    rows.push(...page);
    if (page.length < 500) return rows;
  }
}
export async function loadJournal(userId) {
  const [entries, saved, goals, profile] = await Promise.all([
    loadRows("journal_entries", userId, "updated_at"),
    loadRows("saved_meals", userId, "name"),
    supabase
      .from("nutrition_goals")
      .select("*")
      .eq("user_id", userId)
      .maybeSingle(),
    supabase
      .from("user_profiles")
      .select("*")
      .eq("user_id", userId)
      .maybeSingle(),
  ]);
  return {
    entries,
    saved,
    goals: requireResult(goals),
    profile: requireResult(profile),
  };
}
export async function createEntry(
  userId,
  date,
  body,
  estimate = null,
  id = crypto.randomUUID(),
) {
  return requireResult(
    await supabase
      .from("journal_entries")
      .insert({
        id,
        user_id: userId,
        journal_date: date,
        time_zone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        body,
        estimate,
        status: estimate ? "READY" : "DRAFT",
      })
      .select()
      .single(),
  );
}
export async function saveProfile(userId, previous, values) {
  const row = {
    ...values,
    user_id: userId,
    updated_at: new Date().toISOString(),
  };
  const query = previous
    ? supabase
        .from("user_profiles")
        .update(row)
        .eq("user_id", userId)
        .eq("updated_at", previous.updated_at)
    : supabase.from("user_profiles").insert(row);
  const result = requireResult(await query.select().maybeSingle());
  if (!result)
    throw new Error(
      "Your profile changed on another device. Refresh and try again.",
    );
  return result;
}
export async function updateEntry(entry, patch) {
  const data = requireResult(
    await supabase
      .from("journal_entries")
      .update({
        ...patch,
        revision: entry.revision + 1,
        updated_at: new Date().toISOString(),
      })
      .eq("id", entry.id)
      .eq("user_id", entry.user_id)
      .eq("revision", entry.revision)
      .select()
      .maybeSingle(),
  );
  if (!data)
    throw new Error(
      "This meal changed on another device. Refresh your journal before editing it again.",
    );
  return data;
}
export async function saveMeal(userId, entry, name) {
  return requireResult(
    await supabase
      .from("saved_meals")
      .insert({
        id: crypto.randomUUID(),
        user_id: userId,
        name,
        body: entry.body,
        estimate: entry.estimate,
      })
      .select()
      .single(),
  );
}
export async function saveGoals(userId, old, values) {
  const row = {
    ...values,
    user_id: userId,
    revision: (old?.revision ?? 0) + 1,
    updated_at: new Date().toISOString(),
  };
  const query = old
    ? supabase
        .from("nutrition_goals")
        .update(row)
        .eq("user_id", userId)
        .eq("revision", old.revision)
    : supabase.from("nutrition_goals").insert(row);
  const result = requireResult(await query.select().maybeSingle());
  if (!result)
    throw new Error(
      "Your goals changed on another device. Refresh and try again.",
    );
  return result;
}
const messages = {
  rate_limited:
    "The nutrition request limit has been reached. You can still log meals manually and try later.",
  no_food: "No food was identified. Try including a dish name and portion.",
  account_required: "Please sign in again to estimate nutrition.",
  provider_rate_limit:
    "The nutrition provider is busy. Try again later or enter values manually.",
  timeout: "This estimate took too long. Your meal is saved; you can retry.",
  not_configured:
    "Nutrition estimation is unavailable. Your meal is saved; you can enter values manually.",
};
export async function estimateMeal(entry, { imageBase64, signal } = {}) {
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session) throw new Error(messages.account_required);
  const requestId = crypto.randomUUID();
  const payload = {
    entryId: entry.id,
    requestId,
    revision: entry.revision,
    ...(imageBase64
      ? { mode: "photo", mimeType: "image/jpeg", imageBase64 }
      : { text: entry.body }),
  };
  const response = await fetch(
    `${config.url}/functions/v1/estimate-nutrition`,
    {
      method: "POST",
      headers: {
        apikey: config.publishableKey,
        Authorization: `Bearer ${session.access_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
      signal: signal ?? AbortSignal.timeout(65000),
    },
  );
  const result = await response.json();
  if (!response.ok)
    throw new Error(
      messages[result.error] ??
        "Nutrition estimation is unavailable. Your meal is saved; retry later or enter values manually.",
    );
  if (
    result.entryId !== entry.id ||
    result.requestId !== requestId ||
    result.revision !== entry.revision ||
    !Array.isArray(result.estimate?.items) ||
    !result.estimate.items.length
  )
    throw new Error("An invalid estimate was returned. Please retry.");
  return result;
}
export async function compressPhoto(file) {
  if (!/^image\//.test(file.type))
    throw new Error(
      "Choose a photo in JPEG, PNG or another browser-supported image format.",
    );
  if (file.size > 25_000_000)
    throw new Error("Choose a photo smaller than 25 MB.");
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    const scale = Math.min(1, 1280 / Math.max(image.width, image.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(image.width * scale);
    canvas.height = Math.round(image.height * scale);
    const context = canvas.getContext("2d");
    context.fillStyle = "#fff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const encoded = canvas.toDataURL("image/jpeg", 0.8).split(",")[1];
    if (encoded.length > 2_000_000)
      throw new Error("This photo is still too large. Try a smaller photo.");
    return encoded;
  } catch (error) {
    if (error.name === "EncodingError")
      throw new Error("This photo format is unsupported. Try JPEG or PNG.");
    throw error;
  } finally {
    URL.revokeObjectURL(url);
  }
}
