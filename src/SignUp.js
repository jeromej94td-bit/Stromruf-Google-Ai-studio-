import React, { useState } from "react";
import { supabase } from "./supabaseClient.js";

export default function SignUp() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSignUp = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess(false);

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const { data, error: signUpError } = await supabase.auth.signUp({
        email,
        password,
      });

      if (signUpError) {
        setError(signUpError.message);
      } else {
        // Supabase sign up is successful
        setSuccess(true);
        // Automatically redirect to Home ("/") after a brief delay
        setTimeout(() => {
          window.location.href = "/";
        }, 1500);
      }
    } catch (err) {
      setError("An unexpected error occurred. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setError("");
    try {
      const { error: googleError } = await supabase.auth.signInWithOAuth({
        provider: "google",
        options: {
          redirectTo: window.location.origin,
        },
      });

      if (googleError) {
        setError(googleError.message);
      }
    } catch (err) {
      setError("Failed to initiate Google login.");
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h2 style={styles.title}>Sign Up</h2>
        
        {success ? (
          <div style={styles.successContainer}>
            <p style={styles.successText}>Registration successful!</p>
            <p style={styles.successSubtext}>Redirecting to home page...</p>
          </div>
        ) : (
          <>
            <form onSubmit={handleSignUp} style={styles.form}>
              <div style={styles.inputGroup}>
                <label style={styles.label}>Email Address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  required
                  style={styles.input}
                />
              </div>
              <div style={styles.inputGroup}>
                <label style={styles.label}>Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  style={styles.input}
                />
              </div>
              <div style={styles.inputGroup}>
                <label style={styles.label}>Confirm Password</label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  style={styles.input}
                />
              </div>

              <button type="submit" disabled={loading} style={styles.button}>
                {loading ? "Signing up..." : "Sign Up"}
              </button>
            </form>

            {error && <p style={styles.errorText}>{error}</p>}

            <div style={styles.divider}>
              <span style={styles.dividerLine}></span>
              <span style={styles.dividerText}>or</span>
              <span style={styles.dividerLine}></span>
            </div>

            <button onClick={handleGoogleLogin} style={styles.googleButton}>
              <svg style={styles.googleIcon} viewBox="0 0 24 24" width="18" height="18">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                />
              </svg>
              Sign up with Google
            </button>
          </>
        )}

        <p style={styles.footerText}>
          Already have an account? <a href="/signin" style={styles.link}>Sign In</a>
        </p>
      </div>
    </div>
  );
}

const styles = {
  container: {
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    minHeight: "100vh",
    backgroundColor: "#f8fafc",
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
  },
  card: {
    width: "100%",
    maxWidth: "400px",
    padding: "32px",
    borderRadius: "12px",
    backgroundColor: "#ffffff",
    boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)",
    textAlign: "center",
  },
  title: {
    fontSize: "24px",
    fontWeight: "700",
    color: "#0f172a",
    marginBottom: "24px",
  },
  form: {
    textAlign: "left",
  },
  inputGroup: {
    marginBottom: "16px",
  },
  label: {
    display: "block",
    fontSize: "14px",
    fontWeight: "500",
    color: "#475569",
    marginBottom: "6px",
  },
  input: {
    width: "100%",
    padding: "10px 12px",
    fontSize: "14px",
    borderRadius: "6px",
    border: "1px solid #cbd5e1",
    outline: "none",
    boxSizing: "border-box",
    transition: "border-color 0.2s",
  },
  button: {
    width: "100%",
    padding: "12px",
    fontSize: "16px",
    fontWeight: "600",
    color: "#ffffff",
    backgroundColor: "#2563eb",
    border: "none",
    borderRadius: "6px",
    cursor: "pointer",
    transition: "background-color 0.2s",
  },
  errorText: {
    fontSize: "14px",
    color: "#dc2626",
    marginTop: "12px",
    marginBottom: "0",
  },
  successContainer: {
    padding: "16px",
    borderRadius: "6px",
    backgroundColor: "#f0fdf4",
    border: "1px solid #bbf7d0",
    marginBottom: "16px",
  },
  successText: {
    fontSize: "16px",
    fontWeight: "600",
    color: "#16a34a",
    margin: "0 0 4px 0",
  },
  successSubtext: {
    fontSize: "14px",
    color: "#15803d",
    margin: "0",
  },
  divider: {
    display: "flex",
    alignItems: "center",
    margin: "20px 0",
  },
  dividerLine: {
    flex: 1,
    height: "1px",
    backgroundColor: "#cbd5e1",
  },
  dividerText: {
    padding: "0 10px",
    fontSize: "14px",
    color: "#64748b",
  },
  googleButton: {
    width: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "10px",
    padding: "10px",
    fontSize: "14px",
    fontWeight: "500",
    color: "#334155",
    backgroundColor: "#ffffff",
    border: "1px solid #cbd5e1",
    borderRadius: "6px",
    cursor: "pointer",
    transition: "background-color 0.2s",
  },
  googleIcon: {
    display: "block",
  },
  footerText: {
    fontSize: "14px",
    color: "#64748b",
    marginTop: "24px",
  },
  link: {
    color: "#2563eb",
    textDecoration: "none",
    fontWeight: "500",
  },
};
