package cz.dcervenka.choretracker.feature.settings.impl.screen

enum class AppLanguage(val tag: String, val displayName: String) {
    System(tag = "", displayName = "System default"),
    English(tag = "en", displayName = "English"),
    Czech(tag = "cs", displayName = "Čeština"),
}
