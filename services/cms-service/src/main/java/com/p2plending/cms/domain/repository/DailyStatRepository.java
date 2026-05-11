package com.p2plending.cms.domain.repository;

import com.p2plending.cms.domain.entity.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatRepository extends JpaRepository<DailyStat, String> {

    Optional<DailyStat> findByStatDate(LocalDate date);

    List<DailyStat> findTop30ByOrderByStatDateDesc();
}
