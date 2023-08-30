plugins {
    id("org.jetbrains.kotlin.android")
}
// use an integer for version numbers
version = 3

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "Use Aniyomi Extensions in CloudStream!\nNot guaranteed to work perfectly."
    authors = listOf("CranberrySoup")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    requiresResources = true

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Others")
    iconUrl = "https://www.google.com/s2/favicons?domain=aniyomi.org&sz=%size%"
}

dependencies {
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("androidx.preference:preference:1.2.1")
}