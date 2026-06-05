package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    List<Ward> findByProvinceCodeAndIsDeletedFalseOrderByNameAsc(String provinceCode);
}
