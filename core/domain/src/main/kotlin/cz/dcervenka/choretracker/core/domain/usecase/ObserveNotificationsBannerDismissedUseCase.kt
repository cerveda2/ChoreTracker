package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.NotificationsBannerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNotificationsBannerDismissedUseCase @Inject constructor(
    private val notificationsBannerRepository: NotificationsBannerRepository,
) {
    operator fun invoke(): Flow<Boolean> = notificationsBannerRepository.observeDismissed()
}
