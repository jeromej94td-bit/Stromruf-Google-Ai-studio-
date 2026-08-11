// File: supabase/functions/ai-agent/index.ts
// Deploy: supabase functions deploy ai-agent

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.0";
import Anthropic from "https://esm.sh/@anthropic-ai/sdk@0.10.0";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

const client = new Anthropic({
  apiKey: Deno.env.get("ANTHROPIC_API_KEY"),
});

// ============================================================================
// TOOL DEFINITIONS FOR CLAUDE
// ============================================================================

const tools: Anthropic.Tool[] = [
  {
    name: "get_contacts",
    description:
      "Hole alle Kontakte für den aktuellen Benutzer. Optional mit Suchfilter.",
    input_schema: {
      type: "object" as const,
      properties: {
        search_query: {
          type: "string",
          description:
            "Optional: Suchtext für Kontakte (Name, Telefon, Firma)",
        },
        limit: {
          type: "number",
          description: "Maximale Anzahl von Kontakten (default: 100)",
        },
      },
      required: [],
    },
  },
  {
    name: "create_contact",
    description: "Erstelle einen neuen Kontakt mit allen Informationen.",
    input_schema: {
      type: "object" as const,
      properties: {
        name: { type: "string", description: "Name des Kontakts" },
        phone: { type: "string", description: "Telefonnummer" },
        company: {
          type: "string",
          description: "Firmenname (optional)",
        },
        email: {
          type: "string",
          description: "Email-Adresse (optional)",
        },
        call_reason: {
          type: "string",
          description: "Grund für Anruf (optional)",
        },
        is_hot_box: {
          type: "boolean",
          description:
            "Ist dieser Kontakt in der Hot-Box? (default: false)",
        },
        hot_box_start_hour: {
          type: "number",
          description: "Hot-Box Startstunde (0-23)",
        },
        hot_box_end_hour: {
          type: "number",
          description: "Hot-Box Endstunde (0-23)",
        },
        hot_box_weekdays: {
          type: "string",
          description: "Wochentage für Hot-Box (comma-separated: Mo,Di,Mi...)",
        },
        hot_box_list_name: {
          type: "string",
          description: "Name der Hot-Box-Liste",
        },
      },
      required: ["name", "phone"],
    },
  },
  {
    name: "update_contact",
    description: "Aktualisiere einen bestehenden Kontakt.",
    input_schema: {
      type: "object" as const,
      properties: {
        id: { type: "string", description: "Kontakt-ID" },
        name: { type: "string" },
        phone: { type: "string" },
        company: { type: "string" },
        email: { type: "string" },
        last_outcome: {
          type: "string",
          description: "Ergebnis des letzten Anrufs",
        },
        call_reason: { type: "string" },
        is_hot_box: { type: "boolean" },
        hot_box_start_hour: { type: "number" },
        hot_box_end_hour: { type: "number" },
        hot_box_weekdays: { type: "string" },
        hot_box_list_name: { type: "string" },
      },
      required: ["id"],
    },
  },
  {
    name: "delete_contact",
    description: "Lösche einen Kontakt aus der Datenbank.",
    input_schema: {
      type: "object" as const,
      properties: {
        id: { type: "string", description: "Kontakt-ID" },
      },
      required: ["id"],
    },
  },
  {
    name: "get_followups",
    description: "Hole alle Wiedervorlagen/Reminders.",
    input_schema: {
      type: "object" as const,
      properties: {
        status: {
          type: "string",
          enum: ["pending", "completed", "all"],
          description: "Filtere nach Status (default: pending)",
        },
        contact_id: {
          type: "string",
          description: "Optional: Filtere nach Kontakt-ID",
        },
        limit: {
          type: "number",
          description: "Maximale Anzahl (default: 50)",
        },
      },
      required: [],
    },
  },
  {
    name: "create_followup",
    description: "Erstelle eine neue Wiedervorlage/Reminder.",
    input_schema: {
      type: "object" as const,
      properties: {
        contact_id: {
          type: "string",
          description: "ID des Kontakts (optional)",
        },
        contact_phone: {
          type: "string",
          description: "Telefonnummer des Kontakts",
        },
        contact_name: {
          type: "string",
          description: "Name des Kontakts",
        },
        due_at: {
          type: "number",
          description: "Unix Timestamp in Millisekunden",
        },
        note: {
          type: "string",
          description: "Notiz zur Wiedervorlage",
        },
      },
      required: ["contact_phone", "due_at"],
    },
  },
  {
    name: "complete_followup",
    description: "Markiere eine Wiedervorlage als erledigt.",
    input_schema: {
      type: "object" as const,
      properties: {
        id: { type: "string", description: "Wiedervorlagen-ID" },
      },
      required: ["id"],
    },
  },
  {
    name: "delete_followup",
    description: "Lösche eine Wiedervorlage.",
    input_schema: {
      type: "object" as const,
      properties: {
        id: { type: "string", description: "Wiedervorlagen-ID" },
      },
      required: ["id"],
    },
  },
  {
    name: "get_call_logs",
    description: "Hole die Anrufliste mit optionalen Filtern.",
    input_schema: {
      type: "object" as const,
      properties: {
        contact_id: {
          type: "string",
          description: "Optional: Filtere nach Kontakt-ID",
        },
        phone: {
          type: "string",
          description: "Optional: Filtere nach Telefonnummer",
        },
        days_back: {
          type: "number",
          description: "Wie viele Tage in der Vergangenheit? (default: 30)",
        },
        limit: {
          type: "number",
          description: "Maximale Anzahl (default: 100)",
        },
      },
      required: [],
    },
  },
  {
    name: "create_call_log",
    description: "Erstelle einen neuen Anruf-Eintrag.",
    input_schema: {
      type: "object" as const,
      properties: {
        contact_id: {
          type: "string",
          description: "ID des Kontakts (optional)",
        },
        phone: { type: "string", description: "Telefonnummer" },
        duration_seconds: {
          type: "number",
          description: "Anrufdauer in Sekunden",
        },
        outcome: {
          type: "string",
          description:
            "Ergebnis (z.B. 'interessiert', 'nicht_interessiert', 'termin')",
        },
        notes: { type: "string", description: "Notizen zum Anruf" },
        call_type: {
          type: "string",
          enum: ["einwaehlen", "empfangen"],
          description: "Art des Anrufs",
        },
      },
      required: ["phone"],
    },
  },
  {
    name: "get_analytics",
    description: "Hole verschiedene Metriken und Statistiken.",
    input_schema: {
      type: "object" as const,
      properties: {
        metric: {
          type: "string",
          enum: [
            "total_contacts",
            "active_followups",
            "total_calls",
            "calls_this_week",
            "conversion_rate",
          ],
          description: "Welche Metrik?",
        },
      },
      required: ["metric"],
    },
  },
];

// ============================================================================
// TOOL HANDLERS
// ============================================================================

async function processToolCall(
  toolName: string,
  toolInput: Record<string, unknown>,
  userId: string
): Promise<string> {
  try {
    switch (toolName) {
      // --- CONTACTS ---
      case "get_contacts": {
        const limit = (toolInput.limit as number) || 100;
        const searchQuery = toolInput.search_query as string | undefined;

        let query = supabase
          .from("contacts")
          .select("*")
          .eq("user_id", userId)
          .limit(limit);

        if (searchQuery) {
          query = query.or(
            `name.ilike.%${searchQuery}%,phone.ilike.%${searchQuery}%,company.ilike.%${searchQuery}%`
          );
        }

        const { data, error } = await query;
        if (error) throw error;

        return JSON.stringify({
          success: true,
          count: data?.length || 0,
          contacts: data || [],
        });
      }

      case "create_contact": {
        const { data, error } = await supabase
          .from("contacts")
          .insert([
            {
              id: crypto.randomUUID(),
              user_id: userId,
              name: toolInput.name,
              phone: toolInput.phone,
              company: toolInput.company || null,
              email: toolInput.email || null,
              call_reason: toolInput.call_reason || null,
              is_hot_box: toolInput.is_hot_box || false,
              hot_box_start_hour:
                (toolInput.hot_box_start_hour as number) || null,
              hot_box_end_hour: (toolInput.hot_box_end_hour as number) || null,
              hot_box_weekdays: toolInput.hot_box_weekdays || null,
              hot_box_list_name: toolInput.hot_box_list_name || null,
              created_at: new Date().toISOString(),
            },
          ])
          .select();

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Kontakt "${toolInput.name}" erstellt`,
          contact: data?.[0],
        });
      }

      case "update_contact": {
        const { id, ...updates } = toolInput;
        const { data, error } = await supabase
          .from("contacts")
          .update(updates)
          .eq("id", id as string)
          .eq("user_id", userId)
          .select();

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Kontakt aktualisiert`,
          contact: data?.[0],
        });
      }

      case "delete_contact": {
        const { error } = await supabase
          .from("contacts")
          .delete()
          .eq("id", toolInput.id as string)
          .eq("user_id", userId);

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Kontakt gelöscht`,
        });
      }

      // --- FOLLOWUPS ---
      case "get_followups": {
        const status = toolInput.status || "pending";
        const limit = (toolInput.limit as number) || 50;

        let query = supabase
          .from("followups")
          .select("*")
          .eq("user_id", userId);

        if (status === "pending") {
          query = query.eq("is_completed", false);
        } else if (status === "completed") {
          query = query.eq("is_completed", true);
        }

        if (toolInput.contact_id) {
          query = query.eq("contact_id", toolInput.contact_id as string);
        }

        const { data, error } = await query
          .order("due_at", { ascending: true })
          .limit(limit);

        if (error) throw error;
        return JSON.stringify({
          success: true,
          count: data?.length || 0,
          followups: data || [],
        });
      }

      case "create_followup": {
        const { data, error } = await supabase
          .from("followups")
          .insert([
            {
              id: crypto.randomUUID(),
              user_id: userId,
              contact_id: toolInput.contact_id || null,
              contact_phone: toolInput.contact_phone,
              contact_name: toolInput.contact_name || null,
              due_at: new Date(toolInput.due_at as number).toISOString(),
              note: toolInput.note || null,
              is_completed: false,
              created_at: new Date().toISOString(),
            },
          ])
          .select();

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Wiedervorlage für ${toolInput.contact_name} erstellt`,
          followup: data?.[0],
        });
      }

      case "complete_followup": {
        const { error } = await supabase
          .from("followups")
          .update({
            is_completed: true,
            completed_at: new Date().toISOString(),
          })
          .eq("id", toolInput.id as string)
          .eq("user_id", userId);

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Wiedervorlage abgeschlossen`,
        });
      }

      case "delete_followup": {
        const { error } = await supabase
          .from("followups")
          .delete()
          .eq("id", toolInput.id as string)
          .eq("user_id", userId);

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Wiedervorlage gelöscht`,
        });
      }

      // --- CALL LOGS ---
      case "get_call_logs": {
        const daysBack = (toolInput.days_back as number) || 30;
        const limit = (toolInput.limit as number) || 100;
        const cutoffDate = new Date(
          Date.now() - daysBack * 24 * 60 * 60 * 1000
        );

        let query = supabase
          .from("call_logs")
          .select("*")
          .eq("user_id", userId)
          .gte("timestamp", cutoffDate.toISOString());

        if (toolInput.contact_id) {
          query = query.eq("contact_id", toolInput.contact_id as string);
        }

        if (toolInput.phone) {
          query = query.eq("phone", toolInput.phone as string);
        }

        const { data, error } = await query
          .order("timestamp", { ascending: false })
          .limit(limit);

        if (error) throw error;
        return JSON.stringify({
          success: true,
          count: data?.length || 0,
          call_logs: data || [],
        });
      }

      case "create_call_log": {
        const { data, error } = await supabase
          .from("call_logs")
          .insert([
            {
              id: crypto.randomUUID(),
              user_id: userId,
              contact_id: toolInput.contact_id || null,
              phone: toolInput.phone,
              duration_seconds: (toolInput.duration_seconds as number) || 0,
              notes: toolInput.notes || null,
              outcome: toolInput.outcome || null,
              call_type: toolInput.call_type || "einwaehlen",
              timestamp: new Date().toISOString(),
            },
          ])
          .select();

        if (error) throw error;
        return JSON.stringify({
          success: true,
          message: `Anruf protokolliert für ${toolInput.phone}`,
          call_log: data?.[0],
        });
      }

      // --- ANALYTICS ---
      case "get_analytics": {
        const metric = toolInput.metric as string;
        let result: Record<string, unknown> = {};

        if (metric === "total_contacts") {
          const { count, error } = await supabase
            .from("contacts")
            .select("*", { count: "exact", head: true })
            .eq("user_id", userId);

          if (error) throw error;
          result = { metric, value: count || 0 };
        } else if (metric === "active_followups") {
          const { count, error } = await supabase
            .from("followups")
            .select("*", { count: "exact", head: true })
            .eq("user_id", userId)
            .eq("is_completed", false);

          if (error) throw error;
          result = { metric, value: count || 0 };
        } else if (metric === "total_calls") {
          const { count, error } = await supabase
            .from("call_logs")
            .select("*", { count: "exact", head: true })
            .eq("user_id", userId);

          if (error) throw error;
          result = { metric, value: count || 0 };
        } else if (metric === "calls_this_week") {
          const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
          const { count, error } = await supabase
            .from("call_logs")
            .select("*", { count: "exact", head: true })
            .eq("user_id", userId)
            .gte("timestamp", weekAgo.toISOString());

          if (error) throw error;
          result = { metric, value: count || 0 };
        }

        return JSON.stringify(result);
      }

      default:
        return JSON.stringify({
          error: `Unbekanntes Tool: ${toolName}`,
        });
    }
  } catch (error: unknown) {
    const errorMessage =
      error instanceof Error ? error.message : String(error);
    return JSON.stringify({
      error: errorMessage,
    });
  }
}

// ============================================================================
// MAIN HANDLER
// ============================================================================

serve(async (req) => {
  // CORS
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization",
      },
    });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const { user_id, api_key, instruction } = await req.json();

    // Verify API Key
    const validApiKey = Deno.env.get("AI_AGENT_API_KEY");
    if (api_key !== validApiKey) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Invalid API key" }),
        {
          status: 401,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    if (!user_id || !instruction) {
      return new Response(
        JSON.stringify({
          error: "Missing required fields: user_id, instruction",
        }),
        {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }
      );
    }

    console.log(
      `[AI-Agent] User: ${user_id}, Instruction: ${instruction.substring(0, 100)}`
    );

    // Build messages for Claude
    const messages: Anthropic.MessageParam[] = [
      {
        role: "user",
        content: instruction,
      },
    ];

    // Agent Loop - Process tool calls
    let response = await client.messages.create({
      model: "claude-3-5-sonnet-20241022",
      max_tokens: 4096,
      tools: tools,
      messages: messages,
      system: `Du bist ein hilfreicher KI-Agent für eine CRM-App namens STROMRUF. 
Du hast Zugriff auf Tools zur Verwaltung von Kontakten, Anrufen und Wiedervorlagen.
Antworte immer auf Deutsch.
Nutze die verfügbaren Tools um die Anfragen des Benutzers zu erfüllen.
Sei präzise und erkläre, was du tust.`,
    });

    // Agentic loop - handle tool calls
    let iterationCount = 0;
    const maxIterations = 10; // Prevent infinite loops

    while (response.stop_reason === "tool_use" && iterationCount < maxIterations) {
      iterationCount++;
      console.log(`[AI-Agent] Tool iteration ${iterationCount}`);

      const toolResults: Anthropic.ToolResultBlockParam[] = [];

      // Process all tool calls in this response
      for (const block of response.content) {
        if (block.type === "tool_use") {
          console.log(`[AI-Agent] Executing tool: ${block.name}`);

          const toolResult = await processToolCall(
            block.name,
            block.input as Record<string, unknown>,
            user_id
          );

          toolResults.push({
            type: "tool_result",
            tool_use_id: block.id,
            content: toolResult,
          });
        }
      }

      // Add assistant response and tool results to messages
      messages.push({
        role: "assistant",
        content: response.content,
      });

      messages.push({
        role: "user",
        content: toolResults,
      });

      // Continue conversation
      response = await client.messages.create({
        model: "claude-3-5-sonnet-20241022",
        max_tokens: 4096,
        tools: tools,
        messages: messages,
        system: `Du bist ein hilfreicher KI-Agent für eine CRM-App namens STROMRUF. 
Du hast Zugriff auf Tools zur Verwaltung von Kontakten, Anrufen und Wiedervorlagen.
Antworte immer auf Deutsch.
Nutze die verfügbaren Tools um die Anfragen des Benutzers zu erfüllen.
Sei präzise und erkläre, was du tust.`,
      });
    }

    // Extract final text response
    let finalResponse = "";
    for (const block of response.content) {
      if (block.type === "text") {
        finalResponse += block.text;
      }
    }

    console.log(`[AI-Agent] Response generated (${finalResponse.length} chars)`);

    return new Response(
      JSON.stringify({
        success: true,
        response: finalResponse,
        iterations: iterationCount,
      }),
      {
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
        },
      }
    );
  } catch (error: unknown) {
    const errorMessage =
      error instanceof Error ? error.message : String(error);
    console.error("[AI-Agent] Error:", errorMessage);

    return new Response(
      JSON.stringify({
        error: errorMessage,
      }),
      {
        status: 500,
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
        },
      }
    );
  }
});
