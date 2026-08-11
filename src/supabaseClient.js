import { createClient } from "@supabase/supabase-js";

// ==========================================
// 💡 SUPABASE CONFIGURATION
// Replace the values below with your actual Supabase URL and Public Anon Key
// ==========================================
const SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co";
const SUPABASE_PUBLIC_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM";

// Export the initialized Supabase client for use throughout your application
export const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLIC_KEY);
