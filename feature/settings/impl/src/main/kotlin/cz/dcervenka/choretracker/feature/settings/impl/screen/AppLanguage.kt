package cz.dcervenka.choretracker.feature.settings.impl.screen

// Languages are listed by their own name (endonym), which stays untranslated; only System has a
// localized label, resolved in the UI.
enum class AppLanguage(val tag: String, val displayName: String?) {
    System(tag = "", displayName = null),
    English(tag = "en", displayName = "English"),
    Czech(tag = "cs", displayName = "Čeština"),
}
