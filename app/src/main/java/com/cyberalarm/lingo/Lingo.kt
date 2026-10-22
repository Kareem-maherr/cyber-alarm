package com.cyberalarm.lingo

import kotlin.random.Random

data class Quote(val text: String, val by: String)

/**
 * Night City street talk. Choom = friend, gonk = idiot, preem = premium/great, nova = cool,
 * delta = leave, eddies = money, flatline = die, zeroed = killed, chrome = cyberware.
 */
object Lingo {

    /** Rotating quotes on the alarm list. Only the first two are from the game itself. */
    val quotes = listOf(
        Quote("Wake up, Samurai. We have a city to burn.", "JOHNNY SILVERHAND"),
        Quote("Never fade away.", "SAMURAI"),
        Quote("Night City waits for no one, choom. Least of all you.", "AFTERLIFE REGULAR"),
        Quote("Sleep's for the flatlined. You're not flatlined. Yet.", "WATSON RIPPERDOC"),
        Quote("Eddies don't earn themselves. Get up.", "PACIFICA FIXER"),
        Quote("Even netrunners jack out of the pillow eventually.", "NETWATCH BULLETIN"),
        Quote("Arasaka doesn't sleep in. Neither do you.", "CORPO MEMO"),
        Quote("Snooze once more and I'm wiring an alarm straight into your skull.", "YOUR RIPPERDOC"),
        Quote("Best chrome in the world won't help if your eyes stay shut.", "VIKTOR'S CLINIC"),
        Quote("Rise and grind, choom. The city's already moving.", "NOMAD CONVOY"),
    )

    /** Big line on the ringing screen. */
    val wakeLines = listOf(
        "WAKE UP, SAMURAI.",
        "RISE AND SHINE, CHOOM.",
        "EYES OPEN, CHOOM.",
        "DELTA OUTTA BED.",
        "NO GONKS SLEEP IN.",
        "THE CITY'S CALLING, CHOOM.",
    )

    val wrongAnswer = listOf(
        "GONK MOVE // TRY AGAIN",
        "NEGATIVE, CHOOM // AGAIN",
        "ICE HELD // RETRY",
        "NOT EVEN CLOSE, CHOOM",
    )

    val rightAnswer = listOf("PREEM", "NOVA", "CLEAN HIT", "NICE, CHOOM")

    val lockedOut = listOf(
        "Nice try, choom. This one stays locked.",
        "Back to bed through this app? Don't be a gonk.",
        "Stay frosty, choom. Prove you're awake first.",
        "This app is zeroed until the timer runs out.",
    )

    /** Spoken after a dismissal (VoiceLines). Written to be said out loud. */
    val voiceWake = listOf(
        "Preem work, choom. You're up.",
        "Good morning, merc. Eddies won't earn themselves.",
        "Eyes open. Now don't you dare crawl back into bed.",
        "Rise and shine, choom. Night City's already moving.",
        "Nova. Up and running. Keep it that way.",
    )

    val voiceAfterRelapse = listOf(
        "Fell back asleep, huh? Don't make it a habit, choom.",
        "Second time's the charm. Stay up this time, gonk.",
    )

    fun <T> pick(list: List<T>, random: Random = Random.Default): T = list[random.nextInt(list.size)]
}
