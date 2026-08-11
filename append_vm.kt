
data class CustomerMessageDraftState(
    val rawNote: String = "",
    val transcript: String = "",
    val subject: String = "",
    val body: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
