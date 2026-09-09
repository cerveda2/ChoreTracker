package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ThemeSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemeSettingsUseCase @Inject constructor(
    private val themeSettingsRepository: ThemeSettingsRepository,
) {
    operator fun invoke(): Flow<ThemeSettings> = themeSettingsRepository.observeSettings()
}
