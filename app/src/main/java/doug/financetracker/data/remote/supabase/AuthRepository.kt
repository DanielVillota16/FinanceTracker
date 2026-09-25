package doug.financetracker.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Supabase Auth for the single user (email + password).
 *
 * Flow: create the account once (sign-up), then sign in on this device; the
 * session persists in app-private storage and every PostgREST request carries
 * the user's JWT, satisfying the owner_id = auth.uid() RLS policies. Sign-in
 * is required before any sync runs — the engine no-ops while signed out.
 */
class AuthRepository(
    private val provider: SupabaseProvider
) {
    data class Session(val userId: String, val email: String?)

    val isConfigured: Boolean get() = provider.isConfigured

    private fun client(): SupabaseClient =
        provider.client ?: throw IllegalStateException("Supabase is not configured.")

    fun observeSession(): Flow<Session?> {
        if (!isConfigured) return kotlinx.coroutines.flow.flowOf(null)
        return client().auth.sessionStatus.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.let {
                Session(it.id, it.email)
            }
        }
    }

    suspend fun currentSession(): Session? {
        if (!isConfigured) return null
        val session = client().auth.currentSessionOrNull() ?: return null
        val user = session.user ?: return null
        return Session(user.id, user.email)
    }

    suspend fun signUp(email: String, password: String) {
        client().auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String) {
        client().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        client().auth.signOut()
    }
}
