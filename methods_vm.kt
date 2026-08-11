
    private val _customerMessageDraft = MutableStateFlow(CustomerMessageDraftState())
    val customerMessageDraft: StateFlow<CustomerMessageDraftState> = _customerMessageDraft.asStateFlow()

    fun setCustomerMessageNote(note: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(rawNote = note, error = null)
    }
    
    fun setGeneratedCustomerSubject(subject: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(
            subject = subject,
            error = null
        )
    }

    fun setGeneratedCustomerBody(body: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(
            body = body,
            error = null
        )
    }

    fun transcribeCustomerAudio(context: android.content.Context, audioUri: Uri) {
        viewModelScope.launch {
            _customerMessageDraft.value = _customerMessageDraft.value.copy(isLoading = true, error = null)
            try {
                val text = OpenAiClient(context).transcribeAudio(audioUri)
                _customerMessageDraft.value = _customerMessageDraft.value.copy(
                    transcript = text,
                    isLoading = false
                )
            } catch (e: Exception) {
                _customerMessageDraft.value = _customerMessageDraft.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Transkription fehlgeschlagen"
                )
            }
        }
    }

    fun generateCustomerMessage(
        context: android.content.Context,
        contact: ContactEntity?,
        nextAppointment: String?
    ) {
        viewModelScope.launch {
            val current = _customerMessageDraft.value
            _customerMessageDraft.value = current.copy(isLoading = true, error = null)
            try {
                val generated = OpenAiClient(context).generateCustomerMessage(
                    customerName = contact?.name ?: "Kunde",
                    customerEmail = contact?.email,
                    rawNote = current.rawNote,
                    transcript = current.transcript.takeIf { it.isNotBlank() },
                    nextAppointment = nextAppointment
                )
                _customerMessageDraft.value = current.copy(
                    subject = generated.subject,
                    body = generated.body,
                    isLoading = false
                )
            } catch (e: Exception) {
                _customerMessageDraft.value = current.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "KI-Erstellung fehlgeschlagen"
                )
            }
        }
    }

    fun saveCustomerMessageDraft(contact: ContactEntity?) {
        viewModelScope.launch {
            val draft = _customerMessageDraft.value
            repository.insertCustomerMessage(
                CustomerMessageEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contact?.id,
                    contactName = contact?.name ?: "Unbekannter Kunde",
                    contactEmail = contact?.email,
                    contactPhone = contact?.phone,
                    rawNote = draft.rawNote,
                    transcript = draft.transcript.takeIf { it.isNotBlank() },
                    subject = draft.subject,
                    body = draft.body,
                    provider = null,
                    status = "draft",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun sendCustomerMessage(
        context: android.content.Context,
        contact: ContactEntity?,
        provider: String
    ) {
        viewModelScope.launch {
            val draft = _customerMessageDraft.value
            val email = contact?.email
            if (email.isNullOrBlank()) {
                _customerMessageDraft.value = draft.copy(error = "Keine E-Mail-Adresse beim Kunden hinterlegt.")
                return@launch
            }

            val messageId = UUID.randomUUID().toString()
            val entity = CustomerMessageEntity(
                id = messageId,
                contactId = contact.id,
                contactName = contact.name,
                contactEmail = email,
                contactPhone = contact.phone,
                rawNote = draft.rawNote,
                transcript = draft.transcript.takeIf { it.isNotBlank() },
                subject = draft.subject,
                body = draft.body,
                provider = provider,
                status = "draft",
                createdAt = System.currentTimeMillis()
            )
            repository.insertCustomerMessage(entity)

            try {
                val sender = CustomerMailSender(context)
                if (provider == "gmail") {
                    sender.sendViaGmail(email, draft.subject, draft.body)
                } else {
                    sender.sendViaOutlook(email, draft.subject, draft.body)
                }
                repository.markCustomerMessageSent(messageId)
            } catch (e: Exception) {
                repository.markCustomerMessageFailed(messageId, e.localizedMessage ?: "Versand fehlgeschlagen")
                _customerMessageDraft.value = draft.copy(error = e.localizedMessage ?: "Versand fehlgeschlagen")
            }
        }
    }
