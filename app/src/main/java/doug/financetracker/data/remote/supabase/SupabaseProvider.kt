package doug.financetracker.data.remote.supabase

import doug.financetracker.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Holds the Supabase client. Configuration comes from BuildConfig (populated
 * from gitignored local.properties); when empty, sync is disabled and the app
 * stays fully offline — Room remains the source of truth either way.
 *
 * Only the publishable key is ever embedded. It is safe by design: without a
 * signed-in session it sees zero rows (RLS), and WITH a session it only sees
 * that user's rows. The service-role key must never enter this codebase.
 */
class SupabaseProvider {

    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                // Accept a pasted REST endpoint too; the client needs the base URL.
                supabaseUrl = BuildConfig.SUPABASE_URL.substringBefore("/rest/"),
                supabaseKey = BuildConfig.SUPABASE_KEY
            ) {
                install(Auth)
                install(Postgrest)
            }
        }
    }
}
