import sys
import re

# Fix MainActivity.kt
with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    ma = f.read()

# Add @Composable back to CallScreen if missing
ma = ma.replace('@Composable\nfun CallScreen(', 'fun CallScreen(') # remove all first
ma = ma.replace('fun CallScreen(', '@Composable\nfun CallScreen(')

# Revert my bad prefixing of com.example.service.DialerInCallService
ma = ma.replace('com.example.service.DialerInCallService.activeCall.value', 'activeCall.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallNumber.value', 'activeCallNumber.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallName.value', 'activeCallName.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallCompany.value', 'activeCallCompany.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallReason.value', 'activeCallReason.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallNotes.value', 'activeCallNotes.value')
ma = ma.replace('com.example.service.DialerInCallService.callDurationSeconds.value', 'callDurationSeconds.value')
ma = ma.replace('com.example.service.DialerInCallService.activeCallTranscript.value', 'activeCallTranscript.value')
ma = ma.replace('com.example.service.DialerInCallService.currentAudioState.value', 'currentAudioState.value')
ma = ma.replace('com.example.service.DialerInCallService.isRecognizerListening.value', 'isRecognizerListening.value')

ma = ma.replace('com.example.service.DialerInCallService.instance?.answerCall()', 'answerCall()')
ma = ma.replace('com.example.service.DialerInCallService.instance?.declineCall()', 'declineCall()')
ma = ma.replace('com.example.service.DialerInCallService.instance?.hangUp()', 'hangUp()')
ma = ma.replace('com.example.service.DialerInCallService.instance?.playDtmf(', 'playDtmf(')
ma = ma.replace('com.example.service.DialerInCallService.instance?.startSpeechToText()', 'startSpeechToText()')
ma = ma.replace('com.example.service.DialerInCallService.instance?.stopSpeechToText()', 'stopSpeechToText()')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(ma)


# Rebuild DialerInCallService.kt cleanly
# Actually, I'll just check it out from git.
