package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.NotificationsBannerRepository
import javax.inject.Inject

class DismissNotificationsBannerUseCase @Inject constructor(
    private val notificationsBannerRepository: NotificationsBannerRepository,
) {
    suspend operator fun invoke() = notificationsBannerRepository.dismiss()
}
