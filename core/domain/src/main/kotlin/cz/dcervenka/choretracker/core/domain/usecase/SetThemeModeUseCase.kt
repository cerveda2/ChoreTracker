package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ThemeSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val themeSettingsRepository: ThemeSettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) = themeSettingsRepository.setMode(mode)
}
