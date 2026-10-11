package com.aru.journal.data

import com.aru.journal.auth.AccountRequiredException
import com.aru.journal.auth.SessionProvider
import com.aru.journal.domain.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseUserProfileStore(
    private val client: SupabaseClient,
    private val session: SessionProvider
) {
    suspend fun load(): UserProfile? {
        val owner = session.currentSession()?.accountId ?: throw AccountRequiredException()
        return client.from("user_profiles").select {
            filter { eq("user_id", owner) }
        }.decodeList<ProfileRow>().singleOrNull()?.toDomain().also { profile ->
            require(profile == null || profile.accountId == owner)
        }
    }

    suspend fun save(profile: UserProfile): UserProfile {
        val owner = session.currentSession()?.accountId ?: throw AccountRequiredException()
        require(profile.accountId == owner)
        client.from("user_profiles").upsert(ProfileRow.from(profile)) { onConflict = "user_id" }
        return profile
    }
}

@Serializable
private data class ProfileRow(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("profile_setup_completed") val profileSetupCompleted: Boolean = false,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false
) {
    fun toDomain() = UserProfile(userId, displayName, dateOfBirth, heightCm, weightKg, profileSetupCompleted, onboardingCompleted)

    companion object {
        fun from(profile: UserProfile) = ProfileRow(
            profile.accountId,
            profile.displayName?.trim()?.takeIf { it.isNotEmpty() },
            profile.dateOfBirth,
            profile.heightCm,
            profile.weightKg,
            profile.profileSetupCompleted,
            profile.onboardingCompleted
        )
    }
}
