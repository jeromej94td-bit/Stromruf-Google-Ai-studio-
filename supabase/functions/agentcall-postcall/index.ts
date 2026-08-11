// Nachbearbeitung eines KI-Gesprächs: Der Agent bekommt echte CRM-Werkzeuge
// und arbeitet das Transkript ab. Läuft mit dem JWT des Nutzers -> RLS gilt.
//
// Aufruf 1 (nach dem Gespräch):
//   POST { "session_id": "..." }
// Aufruf 2 (Vorschläge übernehmen):
//   POST { "mode": "apply", "action_ids": ["...","..."] }
//
// Deploy: supabase functions deploy agentcall-postcall

import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

Deno.serve(async (req) => {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return json({ error: "Nicht angemeldet" }, 401);

  // Client mit Nutzer-Token: alle Zugriffe unterliegen der normalen RLS
  const db = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: auth } },
  });

  const { data: userData } = await db.auth.getUser();
  const userId = userData?.user?.id;
  if (!userId) return json({ error: "Ungültiges Token" }, 401);

  let body: Record<string, unknown> = {};
  try { body = await req.json(); } catch { /* leer erlaubt */ }

  if (body.mode === "apply") {
    return await applyVorschlaege(db, userId, (body.action_ids as string[]) ?? []);
  }
  return await nachbearbeiten(db, userId, String(body.session_id ?? ""));
});

// ============================================================
// Hauptlauf: Transkript -> Tool-Use-Loop -> Aktionen
// ============================================================
async function nachbearbeiten(db: SupabaseClient, userId: string, sessionId: string) {
  if (!sessionId) return json({ error: "session_id fehlt" }, 400);

  const { data: session } = await db
    .from("agent_call_sessions").select("*").eq("id", sessionId).single();
  if (!session) return json({ error: "Session nicht gefunden" }, 404);

  const { data: cfg } = await db
    .from("agent_runtime_config").select("*").eq("user_id", userId).single();
  if (!cfg?.llm_api_key) return json({ error: "Kein Sprachmodell-Schlüssel hinterlegt" }, 400);

  // Policy laden bzw. anlegen
  let { data: policy } = await db
    .from("agent_tool_policy").select("*").eq("user_id", userId).single();
  if (!policy) {
    const ins = await db.from("agent_tool_policy")
      .insert({ user_id: userId }).select().single();
    policy = ins.data;
  }

  const transcript: Array<{ vomAgent: boolean; text: string }> =
    Array.isArray(session.transcript) ? session.transcript : [];
  if (transcript.length < 2)
    return json({ ok: true, hinweis: "Zu kurzes Gespräch – keine Nachbearbeitung." });

  const erlaubt: string[] = policy?.allowed_tools ?? [];
  const tools = TOOL_SPECS.filter((t) => erlaubt.includes(t.name));
  if (tools.length === 0) return json({ ok: true, hinweis: "Keine Werkzeuge freigegeben." });

  const gespraech = transcript
    .map((z) => (z.vomAgent ? "AGENT: " : "KUNDE: ") + z.text).join("\n");

  const system = systemPrompt(session, policy?.extra_prompt ?? "", policy?.auto_apply === true);

  const messages: any[] = [{
    role: "user",
    content:
`Telefonat vom ${session.started_at}
Agent: ${session.agent_name}
Rufnummer: ${session.remote_number ?? "unbekannt"}
Bekannter Kontakt: ${session.contact_name ?? "keiner zugeordnet"}${
  session.contact_id ? ` (contact_id: ${session.contact_id})` : ""}
Dauer: ${session.duration_sec} Sekunden

--- TRANSKRIPT ---
${gespraech}
--- ENDE ---

Arbeite dieses Gespräch jetzt im CRM nach.`,
  }];

  const ausgefuehrt: any[] = [];
  let ergebnisText = "";
  const maxRunden = 6;

  for (let runde = 0; runde < maxRunden; runde++) {
    const antwort = await anthropic(cfg, system, messages, tools);
    if (!antwort) break;

    const toolUses = (antwort.content ?? []).filter((c: any) => c.type === "tool_use");
    const texte = (antwort.content ?? [])
      .filter((c: any) => c.type === "text").map((c: any) => c.text).join(" ");
    if (texte) ergebnisText = texte;

    if (toolUses.length === 0) break;

    messages.push({ role: "assistant", content: antwort.content });
    const results: any[] = [];

    for (const tu of toolUses) {
      if (ausgefuehrt.length >= (policy?.max_actions ?? 8)) {
        results.push({
          type: "tool_result", tool_use_id: tu.id,
          content: "Aktionslimit erreicht – keine weiteren Änderungen.",
        });
        continue;
      }

      const nurLesen = LESE_TOOLS.includes(tu.name);
      const sofort = nurLesen || policy?.auto_apply === true;
      let result: unknown = null;
      let fehler: string | null = null;

      if (sofort) {
        try {
          result = await fuehreAus(db, userId, session, tu.name, tu.input ?? {});
        } catch (e) { fehler = String(e); }
      } else {
        result = { hinweis: "Als Vorschlag gespeichert – wartet auf Freigabe in der App." };
      }

      if (!nurLesen) {
        const { data: row } = await db.from("agent_actions").insert({
          user_id: userId,
          session_id: sessionId,
          agent_name: session.agent_name,
          tool_name: tu.name,
          arguments: tu.input ?? {},
          reason: (tu.input as any)?._begruendung ?? null,
          status: fehler ? "fehler" : (sofort ? "ausgefuehrt" : "vorgeschlagen"),
          result: fehler ? null : result,
          error: fehler,
          applied_at: sofort && !fehler ? new Date().toISOString() : null,
        }).select().single();
        ausgefuehrt.push({ tool: tu.name, status: row?.status, id: row?.id });
      }

      results.push({
        type: "tool_result", tool_use_id: tu.id,
        content: JSON.stringify(fehler ? { fehler } : result).slice(0, 4000),
        is_error: !!fehler,
      });
    }
    messages.push({ role: "user", content: results });
  }

  // Kurzfazit an der Session festhalten
  if (ergebnisText) {
    await db.from("agent_call_sessions")
      .update({ summary: (session.summary ?? "") + (session.summary ? " " : "") +
                          ergebnisText.slice(0, 400) })
      .eq("id", sessionId);
  }

  return json({ ok: true, aktionen: ausgefuehrt, fazit: ergebnisText });
}

// ============================================================
// Vorschläge nachträglich übernehmen
// ============================================================
async function applyVorschlaege(db: SupabaseClient, userId: string, ids: string[]) {
  if (ids.length === 0) return json({ error: "Keine Aktionen angegeben" }, 400);
  const { data: rows } = await db.from("agent_actions")
    .select("*, agent_call_sessions(*)")
    .in("id", ids).eq("status", "vorgeschlagen");
  if (!rows || rows.length === 0) return json({ ok: true, uebernommen: 0 });

  let ok = 0;
  for (const r of rows) {
    try {
      const result = await fuehreAus(
        db, userId, r.agent_call_sessions ?? {}, r.tool_name, r.arguments ?? {});
      await db.from("agent_actions").update({
        status: "ausgefuehrt", result, error: null,
        applied_at: new Date().toISOString(),
      }).eq("id", r.id);
      ok++;
    } catch (e) {
      await db.from("agent_actions")
        .update({ status: "fehler", error: String(e) }).eq("id", r.id);
    }
  }
  return json({ ok: true, uebernommen: ok });
}

// ============================================================
// Werkzeug-Beschreibungen für das Modell
// ============================================================
const LESE_TOOLS = ["kontakt_suchen"];

const TOOL_SPECS = [
  {
    name: "kontakt_suchen",
    description: "Sucht Kontakte nach Name oder Telefonnummer. Immer zuerst suchen, " +
      "bevor ein neuer Kontakt angelegt wird.",
    input_schema: {
      type: "object",
      properties: { suche: { type: "string", description: "Name oder Nummer" } },
      required: ["suche"],
    },
  },
  {
    name: "kontakt_anlegen",
    description: "Legt einen neuen Kontakt an. Nur verwenden, wenn kontakt_suchen " +
      "nichts gefunden hat und im Gespräch ein Name genannt wurde.",
    input_schema: {
      type: "object",
      properties: {
        name: { type: "string" },
        telefon: { type: "string" },
        firma: { type: "string" },
        email: { type: "string" },
        plz: { type: "string" },
        energieart: { type: "string", enum: ["Strom", "Gas"] },
        verbrauch_kwh: { type: "number" },
        anrufgrund: { type: "string" },
        _begruendung: { type: "string", description: "Warum diese Aktion" },
      },
      required: ["name", "telefon"],
    },
  },
  {
    name: "kontakt_aktualisieren",
    description: "Ergänzt oder korrigiert Felder eines bestehenden Kontakts mit " +
      "Informationen, die im Gespräch genannt wurden. Nur genannte Felder senden.",
    input_schema: {
      type: "object",
      properties: {
        contact_id: { type: "string" },
        name: { type: "string" },
        firma: { type: "string" },
        email: { type: "string" },
        plz: { type: "string" },
        energieart: { type: "string", enum: ["Strom", "Gas"] },
        verbrauch_kwh: { type: "number" },
        anrufgrund: { type: "string" },
        letztes_ergebnis: { type: "string" },
        _begruendung: { type: "string" },
      },
      required: ["contact_id"],
    },
  },
  {
    name: "wiedervorlage_anlegen",
    description: "Legt eine Wiedervorlage oder einen Termin an. Der Name MUSS mit " +
      "der Kundennummer beginnen, falls im Gespräch eine genannt wurde " +
      "(Beispiel: '995522 HR Bödecker').",
    input_schema: {
      type: "object",
      properties: {
        contact_id: { type: "string" },
        name: { type: "string", description: "Anzeigename, Kundennummer voranstellen" },
        telefon: { type: "string" },
        faellig_am: { type: "string", description: "ISO-Zeitpunkt, z.B. 2026-07-30T10:00:00" },
        notiz: { type: "string" },
        prioritaet: { type: "string", enum: ["hoch", "mittel", "niedrig"] },
        art: {
          type: "string",
          enum: ["wiedervorlage", "beratung", "angebot_nachfassen",
                 "abschluss", "vertragsverlaengerung"],
        },
        energieart: { type: "string", enum: ["Strom", "Gas"] },
        erwartetes_volumen_kwh: { type: "number" },
        angebotsstatus: {
          type: "string",
          enum: ["kein_angebot", "gesendet", "verhandlung", "angenommen"],
        },
        erinnerung_minuten: { type: "number" },
        _begruendung: { type: "string" },
      },
      required: ["name", "telefon", "faellig_am"],
    },
  },
  {
    name: "gespraechsergebnis_setzen",
    description: "Setzt das Ergebnis des Gesprächs (z.B. 'Termin vereinbart', " +
      "'Kein Interesse', 'Rückruf gewünscht') und eine kurze Zusammenfassung.",
    input_schema: {
      type: "object",
      properties: {
        ergebnis: { type: "string" },
        zusammenfassung: { type: "string" },
        _begruendung: { type: "string" },
      },
      required: ["ergebnis"],
    },
  },
  {
    name: "notiz_an_anruf",
    description: "Schreibt eine Notiz in den Anrufeintrag (erscheint in der Aktivität).",
    input_schema: {
      type: "object",
      properties: { notiz: { type: "string" }, _begruendung: { type: "string" } },
      required: ["notiz"],
    },
  },
  {
    name: "hotbox_setzen",
    description: "Legt einen Kontakt in die Hotbox oder nimmt ihn heraus – z.B. " +
      "wenn der Kunde in den nächsten Tagen erneut kontaktiert werden möchte.",
    input_schema: {
      type: "object",
      properties: {
        contact_id: { type: "string" },
        aktiv: { type: "boolean" },
        listenname: { type: "string" },
        anrufgrund: { type: "string" },
        _begruendung: { type: "string" },
      },
      required: ["contact_id", "aktiv"],
    },
  },
  {
    name: "kontakt_sperren",
    description: "Markiert einen Kontakt als 'nicht mehr anrufen'. Immer verwenden, " +
      "wenn der Kunde ausdrücklich keine weiteren Anrufe wünscht.",
    input_schema: {
      type: "object",
      properties: {
        contact_id: { type: "string" },
        grund: { type: "string" },
        _begruendung: { type: "string" },
      },
      required: ["contact_id"],
    },
  },
];

// ============================================================
// Ausführung gegen die STROMRUF-Tabellen
// ============================================================
async function fuehreAus(
  db: SupabaseClient, userId: string, session: any, tool: string, a: any,
): Promise<unknown> {
  const jetzt = Date.now();

  switch (tool) {
    case "kontakt_suchen": {
      const s = String(a.suche ?? "").trim();
      const ziffern = s.replace(/\D/g, "");
      let q = db.from("contacts").select("id,name,phone,company,email,energy_type," +
        "consumption,zip_code,is_hot_box,last_outcome").limit(5);
      q = ziffern.length >= 5
        ? q.ilike("phone", `%${ziffern.slice(-8)}%`)
        : q.ilike("name", `%${s}%`);
      const { data, error } = await q;
      if (error) throw new Error(error.message);
      return { treffer: data ?? [] };
    }

    case "kontakt_anlegen": {
      const id = crypto.randomUUID();
      const row = {
        id, user_id: userId,
        name: a.name, phone: a.telefon,
        company: a.firma ?? null, email: a.email ?? null,
        zip_code: a.plz ?? null, energy_type: a.energieart ?? null,
        consumption: a.verbrauch_kwh ?? null,
        call_reason: a.anrufgrund ?? "Aus KI-Gespräch",
        last_call_at: jetzt,
        last_outcome: session.outcome ?? "KI-Gespräch",
        is_hot_box: false, has_been_called_in_hot_cycle: false,
        date_created: jetzt,
      };
      const { error } = await db.from("contacts").insert(row);
      if (error) throw new Error(error.message);
      // Session mit dem neuen Kontakt verknüpfen
      await db.from("agent_call_sessions")
        .update({ contact_id: id, contact_name: a.name }).eq("id", session.id);
      return { angelegt: true, contact_id: id };
    }

    case "kontakt_aktualisieren": {
      const upd: Record<string, unknown> = { last_call_at: jetzt };
      if (a.name) upd.name = a.name;
      if (a.firma) upd.company = a.firma;
      if (a.email) upd.email = a.email;
      if (a.plz) upd.zip_code = a.plz;
      if (a.energieart) upd.energy_type = a.energieart;
      if (a.verbrauch_kwh != null) upd.consumption = a.verbrauch_kwh;
      if (a.anrufgrund) upd.call_reason = a.anrufgrund;
      if (a.letztes_ergebnis) upd.last_outcome = a.letztes_ergebnis;
      const { error } = await db.from("contacts").update(upd).eq("id", a.contact_id);
      if (error) throw new Error(error.message);
      return { aktualisiert: Object.keys(upd) };
    }

    case "wiedervorlage_anlegen": {
      const faellig = Date.parse(a.faellig_am);
      if (isNaN(faellig)) throw new Error("faellig_am ist kein gültiger Zeitpunkt");
      if (faellig < jetzt - 86400000) throw new Error("Zeitpunkt liegt in der Vergangenheit");
      const id = crypto.randomUUID();
      const row = {
        id, user_id: userId,
        contact_id: a.contact_id ?? session.contact_id ?? null,
        contact_name: a.name,
        contact_phone: a.telefon,
        note: a.notiz ?? null,
        due_at: faellig,
        is_completed: false,
        call_reason: "Aus KI-Gespräch",
        priority: a.prioritaet ?? "mittel",
        appointment_type: a.art ?? "wiedervorlage",
        expected_volume_kwh: a.erwartetes_volumen_kwh ?? null,
        energy_type: a.energieart ?? null,
        offer_status: a.angebotsstatus ?? "kein_angebot",
        reminder_minutes: a.erinnerung_minuten ?? null,
        result: null,
      };
      const { error } = await db.from("followups").insert(row);
      if (error) throw new Error(error.message);
      return { angelegt: true, followup_id: id, faellig_am: new Date(faellig).toISOString() };
    }

    case "gespraechsergebnis_setzen": {
      await db.from("agent_call_sessions").update({
        outcome: a.ergebnis,
        summary: a.zusammenfassung ?? session.summary ?? null,
      }).eq("id", session.id);
      if (session.call_log_id) {
        await db.from("call_logs").update({
          outcome: a.ergebnis,
          note: (a.zusammenfassung ?? "").slice(0, 500) || null,
        }).eq("id", session.call_log_id);
      }
      if (session.contact_id) {
        await db.from("contacts")
          .update({ last_outcome: a.ergebnis, last_call_at: jetzt })
          .eq("id", session.contact_id);
      }
      return { gesetzt: a.ergebnis };
    }

    case "notiz_an_anruf": {
      if (!session.call_log_id) return { hinweis: "Kein Anrufeintrag vorhanden" };
      const { error } = await db.from("call_logs")
        .update({ note: String(a.notiz).slice(0, 500) })
        .eq("id", session.call_log_id);
      if (error) throw new Error(error.message);
      return { gespeichert: true };
    }

    case "hotbox_setzen": {
      const upd: Record<string, unknown> = {
        is_hot_box: a.aktiv === true,
        has_been_called_in_hot_cycle: false,
      };
      if (a.listenname) upd.hot_box_list_name = a.listenname;
      if (a.anrufgrund) upd.call_reason = a.anrufgrund;
      const { error } = await db.from("contacts").update(upd).eq("id", a.contact_id);
      if (error) throw new Error(error.message);
      return { hotbox: a.aktiv === true };
    }

    case "kontakt_sperren": {
      const { error } = await db.from("contacts").update({
        is_hot_box: false,
        last_outcome: "Nicht mehr anrufen",
        call_reason: a.grund ? `Gesperrt: ${a.grund}` : "Gesperrt (Kundenwunsch)",
      }).eq("id", a.contact_id);
      if (error) throw new Error(error.message);
      return { gesperrt: true };
    }

    default:
      throw new Error(`Unbekanntes Werkzeug: ${tool}`);
  }
}

// ============================================================
// Systemprompt
// ============================================================
function systemPrompt(session: any, extra: string, autoApply: boolean): string {
  return `Du bist der Nachbearbeitungs-Assistent von STROMRUF, einem deutschen
Vertriebs-CRM für Energieverträge. Du bekommst das Transkript eines gerade
beendeten Telefonats und pflegst das CRM anhand dessen, was tatsächlich
gesagt wurde.

GRUNDREGELN
- Erfinde NICHTS. Trage nur Informationen ein, die im Transkript stehen.
- Bei unklarer Aussage: lieber nichts eintragen als etwas Falsches.
- Suche IMMER zuerst mit kontakt_suchen, bevor du einen Kontakt anlegst.
- Bei jeder Wiedervorlage: falls im Gespräch eine Kundennummer genannt wurde,
  stelle sie dem Namen voran (Beispiel: "995522 HR Bödecker").
- Relative Termine ("nächste Woche Dienstag", "in drei Tagen") rechnest du
  ausgehend von ${session.started_at} in einen konkreten ISO-Zeitpunkt um.
  Ohne genannte Uhrzeit nimm 10:00 Uhr.
- Setze am Ende immer ein Gesprächsergebnis mit gespraechsergebnis_setzen.
- Wünscht der Kunde keine weiteren Anrufe, nutze zwingend kontakt_sperren.
- Arbeite sparsam: nur Aktionen, die wirklich nötig sind.
- Fasse zum Schluss in ein bis zwei deutschen Sätzen zusammen, was du
  gemacht hast.

${autoApply
  ? "Deine Aktionen werden sofort ausgeführt. Sei entsprechend sorgfältig."
  : "Deine Aktionen werden dem Nutzer zur Freigabe vorgelegt."}

${extra ? `ZUSÄTZLICHE REGELN DES NUTZERS:\n${extra}` : ""}`;
}

// ============================================================
// Anthropic-Aufruf mit Werkzeugen
// ============================================================
async function anthropic(cfg: any, system: string, messages: any[], tools: any[]) {
  const url = `${String(cfg.llm_base_url).replace(/\/$/, "")}/v1/messages`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": cfg.llm_api_key,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: cfg.llm_model ?? "claude-sonnet-4-6",
      max_tokens: 1500,
      system,
      messages,
      tools,
    }),
  });
  if (!res.ok) {
    console.error("Anthropic", res.status, await res.text());
    return null;
  }
  return await res.json();
}

function json(obj: unknown, status = 200) {
  return new Response(JSON.stringify(obj), {
    status, headers: { "Content-Type": "application/json" },
  });
}
