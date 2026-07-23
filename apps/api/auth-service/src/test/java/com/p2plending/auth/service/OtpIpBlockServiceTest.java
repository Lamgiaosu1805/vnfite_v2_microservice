package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.OtpIpBlock;
import com.p2plending.auth.domain.repository.OtpIpBlockRepository;
import com.p2plending.auth.domain.repository.OtpIpUnblockRequestRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpIpBlockServiceTest {
    private final OtpIpBlockRepository blockRepository = mock(OtpIpBlockRepository.class);
    private final OtpIpUnblockRequestRepository requestRepository = mock(OtpIpUnblockRequestRepository.class);
    private final OtpIpBlockService service = new OtpIpBlockService(blockRepository, requestRepository);

    @Test
    void createsPermanentBlockForRepeatedAutomatedAbuse() {
        when(blockRepository.findByIpAddressAndIsDeletedFalse("203.0.113.8")).thenReturn(Optional.empty());

        service.blockAutomatically("203.0.113.8", "Repeated OTP abuse");

        var captor = forClass(OtpIpBlock.class);
        verify(blockRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.8");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getBlockedBy()).isEqualTo("SYSTEM_SECURITY");
    }

    @Test
    void reactivatesPreviouslyUnblockedIp() {
        OtpIpBlock block = OtpIpBlock.builder()
                .id("block-1")
                .ipAddress("203.0.113.8")
                .active(false)
                .reason("Old reason")
                .blockedBy("CMS")
                .unblockedBy("admin-1")
                .unblockedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
        when(blockRepository.findByIpAddressAndIsDeletedFalse("203.0.113.8"))
                .thenReturn(Optional.of(block));

        service.blockAutomatically("203.0.113.8", "Repeated OTP abuse");

        assertThat(block.isActive()).isTrue();
        assertThat(block.getUnblockedBy()).isNull();
        assertThat(block.getUnblockedAt()).isNull();
        assertThat(block.getReason()).isEqualTo("Repeated OTP abuse");
        verify(blockRepository).save(block);
    }
}
