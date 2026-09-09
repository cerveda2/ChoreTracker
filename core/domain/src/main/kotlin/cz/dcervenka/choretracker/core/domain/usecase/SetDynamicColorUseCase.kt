package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ThemeSettingsRepository
import javax.inject.Inject

class SetDynamicColorUseCase @Inject constructor(
    private val themeSettingsRepository: ThemeSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = themeSettingsRepository.setDynamicColor(enabled)
}
