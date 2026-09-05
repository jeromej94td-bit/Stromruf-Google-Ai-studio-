# Easybell TLS registration fallback

This change keeps the previously documented Linphone registration path as the first attempt and adds one compatibility fallback only after a real registration failure.

Primary path: `voip.easybell.de:5061` with Linphone `TransportType.Tls`, the existing SIP identity and outbound proxy enabled.

Fallback path: `secure.sip.easybell.de:5061` with the same TLS transport and credentials. Easybell documents this hostname as a classic encrypted registrar for clients that do not use DNS-SRV reliably.

Authentication remains automatic when the optional auth field is empty: the normal SIP user is used. An additional Easybell realm match for `sip.easybell.de` is registered so a server-side realm change does not prevent digest authentication.

No custom SIP, RTP or SRTP implementation is reintroduced. Linphone remains the only active SIP/media stack.
