#!/usr/bin/env python3
import sys
import json
import uuid
import os
import urllib.request
import urllib.error

# Config and credentials
SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co"
SUPABASE_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM"
ACCESS_TOKEN = os.environ.get("STROMRUF_ACCESS_TOKEN")

def log(msg):
    sys.stderr.write(f"[Stromruf-MCP] {msg}\n")
    sys.stderr.flush()

# Load env credentials if available
ENV_EMAIL = os.environ.get("SUPABASE_EMAIL")
ENV_PASSWORD = os.environ.get("SUPABASE_PASSWORD")

def authenticate(email, password):
    global ACCESS_TOKEN
    log(f"Attempting login for email: {email}")
    url = f"{SUPABASE_URL}/auth/v1/token?grant_type=password"
    data = json.dumps({"email": email, "password": password}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"apikey": SUPABASE_KEY, "Content-Type": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            ACCESS_TOKEN = body.get("access_token")
            log("Authentication successful! Session token updated.")
            return True
    except Exception as e:
        log(f"Authentication failed: {e}")
        return False

# Attempt auto login
if ENV_EMAIL and ENV_PASSWORD and not ACCESS_TOKEN:
    log("Auto-authenticating with SUPABASE_EMAIL and SUPABASE_PASSWORD from environment...")
    authenticate(ENV_EMAIL, ENV_PASSWORD)

def make_request(path, method="GET", body=None, prefer=None):
    global ACCESS_TOKEN
    headers = {
        "apikey": SUPABASE_KEY,
        "Content-Type": "application/json"
    }
    if ACCESS_TOKEN:
        headers["Authorization"] = f"Bearer {ACCESS_TOKEN}"
    if prefer:
        headers["Prefer"] = prefer

    url = f"{SUPABASE_URL}/rest/v1/{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    
    req = urllib.request.Request(
        url,
        data=data,
        headers=headers,
        method=method
    )
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode("utf-8")
            if not content:
                return {"success": True}
            return json.loads(content)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        log(f"HTTP Error {e.code}: {err_body}")
        raise Exception(f"Database query failed (HTTP {e.code}): {err_body}")
    except Exception as e:
        log(f"Request Error: {e}")
        raise e

# Tool definitions
TOOLS = [
    {
        "name": "login",
        "description": "Log in to the Stromruf Supabase CRM with your email and password to obtain an access token.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "email": {"type": "string", "description": "Your registered email (e.g., jeromej9465@gmail.com)"},
                "password": {"type": "string", "description": "Your password"}
            },
            "required": ["email", "password"]
        }
    },
    {
        "name": "list_contacts",
        "description": "Fetch contact records from the CRM database. Can filter and limit results.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Max contacts to fetch (default: 50)"},
                "search": {"type": "string", "description": "Search contacts by name or phone number"}
            }
        }
    },
    {
        "name": "upsert_contact",
        "description": "Create or update a contact record in the database. Generates a new UUID if 'id' is omitted.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID of the contact. Omit to create a new one."},
                "name": {"type": "string", "description": "Full name of the contact"},
                "phone": {"type": "string", "description": "Phone number (automatically normalized)"},
                "company": {"type": "string", "description": "Company name"},
                "email": {"type": "string", "description": "Email address"},
                "is_hot_box": {"type": "boolean", "description": "Whether the contact is part of a Hotbox dialing list"},
                "call_reason": {"type": "string", "description": "Reason for the call"},
                "hot_box_list_name": {"type": "string", "description": "Name of the Hotbox list campaign"}
            },
            "required": ["name", "phone"]
        }
    },
    {
        "name": "delete_contact",
        "description": "Delete a contact record by its ID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID of the contact to delete"}
            },
            "required": ["id"]
        }
    },
    {
        "name": "list_followups",
        "description": "Fetch follow-up schedule records from the database.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Max follow-ups to fetch (default: 50)"}
            }
        }
    },
    {
        "name": "upsert_followup",
        "description": "Create or update a follow-up schedule. Omit 'id' to create a new one.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID of the follow-up record. Omit to create a new one."},
                "contact_id": {"type": "string", "description": "UUID of the associated contact"},
                "contact_name": {"type": "string", "description": "Name of the contact"},
                "contact_phone": {"type": "string", "description": "Phone of the contact"},
                "due_at_ms": {"type": "integer", "description": "Epoch timestamp in milliseconds when follow-up is due"},
                "notes": {"type": "string", "description": "Follow-up notes and comments"}
            },
            "required": ["contact_id", "contact_name", "contact_phone", "due_at_ms"]
        }
    },
    {
        "name": "delete_followup",
        "description": "Delete a follow-up record by its ID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID of the follow-up to delete"}
            },
            "required": ["id"]
        }
    },
    {
        "name": "list_call_logs",
        "description": "Fetch historical call logs.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Max call logs to fetch (default: 50)"}
            }
        }
    },
    {
        "name": "insert_call_log",
        "description": "Log a completed call record. Omit 'id' to generate a new UUID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID of the log. Omit to create a new one."},
                "phone": {"type": "string", "description": "Dialed phone number"},
                "contact_name": {"type": "string", "description": "Name of the contact called"},
                "duration_seconds": {"type": "integer", "description": "Duration of the call in seconds"},
                "outcome": {"type": "string", "description": "Outcome description (e.g., reached, voicemail, busy)"},
                "call_type": {"type": "string", "description": "Call category (e.g., hotbox, einwaehlen, rueckruf)"},
                "notes": {"type": "string", "description": "Call notes or details"}
            },
            "required": ["phone", "contact_name", "duration_seconds", "outcome", "call_type"]
        }
    },
    {
        "name": "list_neukunden",
        "description": "Fetch list of new customer/leads records.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Max records to fetch (default: 50)"}
            }
        }
    },
    {
        "name": "upsert_neukunde",
        "description": "Create or update a new customer/lead record. Omit 'id' to create a new one.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "UUID. Omit to create a new one."},
                "name": {"type": "string", "description": "Lead/customer name"},
                "phone": {"type": "string", "description": "Lead phone number"},
                "email": {"type": "string", "description": "Lead email"},
                "company": {"type": "string", "description": "Company name"},
                "status": {"type": "string", "description": "Lead status (e.g., active, interested)"}
            },
            "required": ["name", "phone"]
        }
    },
    {
        "name": "list_customer_messages",
        "description": "Fetch customer messages and transcripts.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Max records to fetch (default: 50)"}
            }
        }
    }
]

def handle_tool_call(name, arguments):
    try:
        if name == "login":
            email = arguments.get("email")
            password = arguments.get("password")
            success = authenticate(email, password)
            if success:
                return f"Successfully authenticated. Session access token retrieved."
            else:
                return f"Authentication failed. Please check your email and password."

        # Verify authentication for other tools
        if not ACCESS_TOKEN:
            return "Error: You are not logged in. Please call the 'login' tool with email and password first, or set 'SUPABASE_EMAIL' and 'SUPABASE_PASSWORD' environment variables."

        if name == "list_contacts":
            limit = arguments.get("limit", 50)
            search = arguments.get("search")
            path = f"contacts?select=*&limit={limit}"
            if search:
                path += f"&or=(name.ilike.*{search}*,phone.ilike.*{search}*)"
            res = make_request(path)
            return json.dumps(res, indent=2, ensure_ascii=False)

        elif name == "upsert_contact":
            contact_id = arguments.get("id") or str(uuid.uuid4())
            body = {
                "id": contact_id,
                "name": arguments.get("name"),
                "phone": arguments.get("phone"),
                "company": arguments.get("company"),
                "email": arguments.get("email"),
                "is_hot_box": arguments.get("is_hot_box", False),
                "call_reason": arguments.get("call_reason"),
                "hot_box_list_name": arguments.get("hot_box_list_name")
            }
            res = make_request("contacts", method="POST", body=body, prefer="resolution=merge-duplicates")
            return f"Contact upserted successfully: {contact_id}\n{json.dumps(body, indent=2)}"

        elif name == "delete_contact":
            contact_id = arguments.get("id")
            make_request(f"contacts?id=eq.{contact_id}", method="DELETE")
            return f"Contact {contact_id} deleted successfully."

        elif name == "list_followups":
            limit = arguments.get("limit", 50)
            res = make_request(f"followups?select=*&limit={limit}")
            return json.dumps(res, indent=2, ensure_ascii=False)

        elif name == "upsert_followup":
            followup_id = arguments.get("id") or str(uuid.uuid4())
            body = {
                "id": followup_id,
                "contact_id": arguments.get("contact_id"),
                "contact_name": arguments.get("contact_name"),
                "contact_phone": arguments.get("contact_phone"),
                "due_at_ms": arguments.get("due_at_ms"),
                "notes": arguments.get("notes")
            }
            make_request("followups", method="POST", body=body, prefer="resolution=merge-duplicates")
            return f"Follow-up upserted successfully: {followup_id}\n{json.dumps(body, indent=2)}"

        elif name == "delete_followup":
            followup_id = arguments.get("id")
            make_request(f"followups?id=eq.{followup_id}", method="DELETE")
            return f"Follow-up {followup_id} deleted successfully."

        elif name == "list_call_logs":
            limit = arguments.get("limit", 50)
            res = make_request(f"call_logs?select=*&limit={limit}")
            return json.dumps(res, indent=2, ensure_ascii=False)

        elif name == "insert_call_log":
            log_id = arguments.get("id") or str(uuid.uuid4())
            body = {
                "id": log_id,
                "phone": arguments.get("phone"),
                "contact_name": arguments.get("contact_name"),
                "duration_seconds": arguments.get("duration_seconds"),
                "outcome": arguments.get("outcome"),
                "call_type": arguments.get("call_type"),
                "notes": arguments.get("notes")
            }
            make_request("call_logs", method="POST", body=body)
            return f"Call log recorded: {log_id}\n{json.dumps(body, indent=2)}"

        elif name == "list_neukunden":
            limit = arguments.get("limit", 50)
            res = make_request(f"neukunden?select=*&limit={limit}")
            return json.dumps(res, indent=2, ensure_ascii=False)

        elif name == "upsert_neukunde":
            lead_id = arguments.get("id") or str(uuid.uuid4())
            body = {
                "id": lead_id,
                "name": arguments.get("name"),
                "phone": arguments.get("phone"),
                "email": arguments.get("email"),
                "company": arguments.get("company"),
                "status": arguments.get("status", "active")
            }
            make_request("neukunden", method="POST", body=body, prefer="resolution=merge-duplicates")
            return f"Lead/New Customer upserted successfully: {lead_id}\n{json.dumps(body, indent=2)}"

        elif name == "list_customer_messages":
            limit = arguments.get("limit", 50)
            res = make_request(f"customer_messages?select=*&limit={limit}")
            return json.dumps(res, indent=2, ensure_ascii=False)

        else:
            return f"Unknown tool: {name}"
    except Exception as e:
        log(f"Error handling tool '{name}': {e}")
        return f"Error executing tool '{name}': {str(e)}"

# JSON-RPC standard input/output loop
def main():
    log("Stromruf MCP Server starting up...")
    
    for line in sys.stdin:
        if not line.strip():
            continue
        try:
            req = json.loads(line)
            req_id = req.get("id")
            method = req.get("method")
            
            # Response object
            response = {
                "jsonrpc": "2.0",
                "id": req_id
            }
            
            if method == "initialize":
                response["result"] = {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "stromruf-mcp",
                        "version": "1.0.0"
                    }
                }
            elif method == "notifications/initialized":
                continue # No response needed for notifications
                
            elif method == "tools/list":
                response["result"] = {
                    "tools": TOOLS
                }
                
            elif method == "tools/call":
                params = req.get("params", {})
                tool_name = params.get("name")
                tool_args = params.get("arguments", {})
                
                tool_result = handle_tool_call(tool_name, tool_args)
                
                response["result"] = {
                    "content": [
                        {
                            "type": "text",
                            "text": tool_result
                        }
                    ]
                }
            else:
                response["error"] = {
                    "code": -32601,
                    "message": f"Method not found: {method}"
                }
                
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()
            
        except Exception as e:
            log(f"Critical error in main loop: {e}")
            try:
                err_resp = {
                    "jsonrpc": "2.0",
                    "error": {
                        "code": -32603,
                        "message": str(e)
                    }
                }
                sys.stdout.write(json.dumps(err_resp) + "\n")
                sys.stdout.flush()
            except Exception:
                pass

if __name__ == "__main__":
    main()
