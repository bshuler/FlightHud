plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    clientOptions {
        narrator = false
    }
}

tasks.named<Jar>("jar") {
    from("LICENSE")
}
