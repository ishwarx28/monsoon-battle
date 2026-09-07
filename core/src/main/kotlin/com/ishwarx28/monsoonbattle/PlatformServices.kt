package com.ishwarx28.monsoonbattle

/** Callbacks can arrive on the platform UI thread; the game marshals them. */
interface PlatformServices {
    val rewardedReady: Boolean
    fun showRewarded(onResult: (Boolean) -> Unit)
    fun speak(hindi: String, english: String)
    fun stopSpeech()
    fun showPrivacyOptions()
}
