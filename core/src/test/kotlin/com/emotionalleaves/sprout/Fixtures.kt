package com.emotionalleaves.sprout

object Fixtures {
    /** Reads src/test/resources/simplefin/<name>. All fixture data is fake. */
    fun simplefin(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/simplefin/$name")) { "Missing fixture $name" }.readText()

    /** The only access URL form tests use. */
    const val DEMO_ACCESS_URL = "https://demo:demo@bridge.example.test/simplefin"
}
